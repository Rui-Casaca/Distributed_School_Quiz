package pt.isec.server.threads;
import pt.isec.server.core.IServerThreadContext;
import pt.isec.server.core.ServerManager;
import pt.isec.common.messages.TcpMessage;
import pt.isec.common.messages.MessageType;
import pt.isec.common.util.Log;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.BlockingQueue;

/**
 * Thread responsible for multicast heartbeats and database copy between servers.
 * <p>
 * Responsibilities:
 * <ul>
 *     <li>Primary node: sends multicast heartbeats, optionally with pending SQL.</li>
 *     <li>Backup nodes: receive heartbeats, apply SQL or request DB copy.</li>
 *     <li>Primary node: handles DB copy requests via a separate TCP server socket.</li>
 * </ul>
 */
public class ClusterHeartbeatThread implements Runnable, AutoCloseable {

    /** Interval between heartbeats in milliseconds (when there is no pending SQL). */
    private static final int HEARTBEAT_INTERVAL_MS = 5000;
    /** Sleep time between loop iterations in milliseconds. */
    private static final int LOOP_SLEEP_MS = 50;
    /** Multicast time-to-live. */
    private static final int MULTICAST_TTL = 1;
    /** Receive timeout for the multicast socket (milliseconds). */
    private static final int RX_TIMEOUT_MS = 500;
    /** Accept timeout for the DB copy server socket (milliseconds). */
    private static final int ACCEPT_TIMEOUT_MS = 500;
    /** Buffer size for UDP packets. */
    private static final int BUFFER_SIZE = 4096;

    private final IServerThreadContext threadInfo;
    private MulticastSocket ms;
    private ServerSocket dbCopyServerSocket;

    /**
     * Creates a new cluster heartbeat thread.
     *
     * @param threadInfo server manager providing multicast and DB information
     */
    public ClusterHeartbeatThread(IServerThreadContext threadInfo) {
        this.threadInfo = threadInfo;
    }

    /**
     * Main loop:
     * <ul>
     *     <li>If primary, send heartbeats and pending SQL.</li>
     *     <li>If backup, receive heartbeats and apply SQL or request DB copy.</li>
     *     <li>Handle DB copy requests (primary side) via TCP.</li>
     * </ul>
     */
    @Override
    public void run() {
        try (MulticastSocket _ms = new MulticastSocket(threadInfo.multicastPort());
             ServerSocket _ss = new ServerSocket(threadInfo.dbCopyPort())) {

            this.ms = _ms;
            this.dbCopyServerSocket = _ss;

            _ms.setReuseAddress(true);
            _ms.setSoTimeout(RX_TIMEOUT_MS);
            _ms.setTimeToLive(MULTICAST_TTL);
            _ms.setNetworkInterface(threadInfo.multicastInterface());
            configureLoopbackMode(_ms);

            InetAddress serverGroupAddr = InetAddress.getByName(threadInfo.multicastGroup());
            _ms.joinGroup(new InetSocketAddress(serverGroupAddr, threadInfo.multicastPort()), threadInfo.multicastInterface());

            _ss.setSoTimeout(ACCEPT_TIMEOUT_MS);

            long lastSent = 0L;
            byte[] buf = new byte[BUFFER_SIZE];
            DatagramPacket packet = new DatagramPacket(buf, buf.length);

            while (threadInfo.isRunning()) {
                long now = System.currentTimeMillis();

                // PRIMARY: send heartbeats (with or without SQL)
                if (threadInfo.isPrimary()) {
                    lastSent = handlePrimaryHeartbeatLoop(_ms, serverGroupAddr, lastSent, now);
                }

                // BACKUP: receive heartbeat and apply updates
                if (!threadInfo.isPrimary()) {
                    handleBackupHeartbeatLoop(_ms, packet);
                }

                // Accept DB copy requests (primary side)
                // TODO Servidor principal (e secundários): thread dedicada à receção de pedidos de ligação pelos servidores secundários via TCP + uma thread para cada um para tratar da transferência do ficheiro da BD
                handleDbCopyAcceptLoop(_ss);

                Thread.sleep(LOOP_SLEEP_MS);
            }
        } catch (Exception e) {
            if (threadInfo.isRunning()) {
                Log.error(ClusterHeartbeatThread.class,
                        "[MC-LOOP] Error in main cluster loop: %s", e.getMessage());
            }
        }
        Log.info(ClusterHeartbeatThread.class, "Cluster heartbeat thread terminated.");
    }

    /**
     * Closes multicast and DB copy sockets if open.
     */
    @Override
    public void close() {
        if (ms != null) {
            try {
                ms.close();
            } catch (Exception ignore) {
                // ignore
            }
        }
        if (dbCopyServerSocket != null) {
            try {
                dbCopyServerSocket.close();
            } catch (Exception ignore) {
                // ignore
            }
        }
    }

    /* ===================================================================== */
    /* ===============   PRIMARY / BACKUP LOOP HELPERS   ==================== */
    /* ===================================================================== */

    /**
     * Primary-only part of the loop: sends heartbeats, optionally including SQL statements.
     *
     * @param _ms      multicast socket
     * @param serversGroupAddr      multicast group
     * @param lastSent timestamp of last heartbeat
     * @param now      current time in millis
     * @return new {@code lastSent} timestamp
     * @throws IOException if sending the heartbeat fails
     */
    // TODO Servidores principal e secundários: thread (ou esquema alternativo como Timer) dedicada ao envio periódico, por multicast
    private long handlePrimaryHeartbeatLoop(MulticastSocket _ms,
                                            InetAddress serversGroupAddr,
                                            long lastSent,
                                            long now) throws IOException {

        List<String> sqlToSend = null;

        BlockingQueue<List<String>> q = threadInfo.queue();
        if (q != null) {
            sqlToSend = q.poll();
        }

        if (sqlToSend != null) {
            // heartbeat with pending SQL
            sendHeartbeat(_ms, serversGroupAddr, sqlToSend);

            Log.infoMaster(
                    ClusterHeartbeatThread.class,
                    "[MC] Heartbeat sent from PRIMARY %s:%d | dbVersion=%d | sqlBatch=%d statements",
                    threadInfo.serverTcpIp(),
                    threadInfo.serverTcpPort(),
                    threadInfo.dbVersion(),
                    sqlToSend.size()
            );

            lastSent = now;
        } else if (now - lastSent >= HEARTBEAT_INTERVAL_MS) {
            // periodic heartbeat without SQL
            sendHeartbeat(_ms, serversGroupAddr, null);

            Log.infoMaster(
                    ClusterHeartbeatThread.class,
                    "[MC] Heartbeat sent from PRIMARY %s:%d | dbVersion=%d | sqlBatch=none",
                    threadInfo.serverTcpIp(),
                    threadInfo.serverTcpPort(),
                    threadInfo.dbVersion()
            );

            lastSent = now;
        }


        return lastSent;
    }

    /**
     * Backup-only part of the loop: receives heartbeats and applies updates.
     *
     * @param _ms multicast socket
     * @param pkt datagram packet reused for reception
     */
    private void handleBackupHeartbeatLoop(MulticastSocket _ms, DatagramPacket pkt) {
        try {
            _ms.receive(pkt);
            String senderIp = pkt.getAddress().getHostAddress();
            String msg = new String(pkt.getData(), 0, pkt.getLength(), StandardCharsets.UTF_8);

            if (msg.startsWith("MC_HB;")) {
                long rxClientPort = extractLong(msg, "clientPort");
                // ignore our own heartbeats
                boolean fromMe = senderIp.equals(threadInfo.serverTcpIp()) && rxClientPort == threadInfo.serverTcpPort();
                if (!fromMe) {
                    long rxVersion = extractLong(msg, "version");
                    int  rxDbPort  = (int) extractLong(msg, "dbPort");

                    boolean versionMismatch;

                    // Apply SQL updates encoded in base64 (incremental replication)
                    String sqlEncoded = extractString(msg, "sql");
                    if (sqlEncoded != null && !sqlEncoded.isBlank()) {
                        Log.infoMaster(
                                ClusterHeartbeatThread.class,
                                "[MC] Heartbeat received from PRIMARY %s:%d | version=%d | sql=present",
                                senderIp,
                                rxClientPort,
                                rxVersion
                        );

                        long localBefore = threadInfo.dbVersion();

                        if (rxVersion < 0) {
                            Log.warn(ClusterHeartbeatThread.class,
                                    "[MC] Heartbeat with SQL but remote version is invalid (rxVersion=%d). Ignored.",
                                    rxVersion);
                            return;
                        }

                        // TODO Servidor secundário: encerramento quando deteta um número de versão incoerente num heartbeat do servidorprincipal
                        if (rxVersion == localBefore + 1) {
                            byte[] bytes = Base64.getDecoder().decode(sqlEncoded);
                            String joined = new String(bytes, StandardCharsets.UTF_8);

                            // Apply the whole batch of SQL in a single transaction;
                            // DbCommands will increment config.db_version once.
                            // TODO Servidor secundário: BD sincronizada com a BD do servidor principal (mantém o mesmo conteúdo)
                            threadInfo.getDb().runInTransaction(tx -> {
                                String[] stmts = joined.split(";;");
                                for (String s : stmts) {
                                    String trimmed = s.trim();
                                    if (trimmed.isEmpty()) {
                                        continue;
                                    }
                                    tx.executeUpdate(trimmed);
                                    Log.info(ClusterHeartbeatThread.class,
                                            "[MC] SQL executed from heartbeat: %s", trimmed);
                                }
                            });

                            long localAfter = threadInfo.dbVersion();
                            Log.info(ClusterHeartbeatThread.class,
                                    "[MC] DB version after applying SQL: local=%d, remote=%d",
                                    localAfter, rxVersion);

                            // ensure we ended up aligned with the primary
                            if (rxVersion != localAfter) {
                                Log.error(ClusterHeartbeatThread.class,
                                        "[MC] DB version after applying SQL does not match remote " +
                                                "(local=%d, remote=%d). Ask for a complete copy.",
                                        localAfter, rxVersion);
                                requestDbCopy(senderIp, rxDbPort, rxVersion);
                            }
                        } else if (rxVersion > localBefore + 1) {
                            Log.warn(ClusterHeartbeatThread.class,
                                    "[MC] Version jump detected (local=%d, remote=%d). We will request a full database copy from the PRIMARY.",
                                    localBefore, rxVersion);
                            requestDbCopy(senderIp, rxDbPort, rxVersion);
                        } else {
                            Log.info(ClusterHeartbeatThread.class,
                                    "[MC] Heartbeat with SQL but remote version <= local (local=%d, remote=%d). SQL ignored.",
                                    localBefore, rxVersion);
                        }

                    } else {
                        // No SQL in heartbeat → version check / DB copy
                        Path dbPath = threadInfo.dbPath();
                        boolean missingDb = !Files.exists(dbPath);

                        if (missingDb) {
                            Log.warn(ClusterHeartbeatThread.class,
                                    "[MC] DB copy required: missingDb=%s, localVersion=%d, remoteVersion=%d, " +
                                            "primary=%s:%d",
                                    true, threadInfo.dbVersion(), rxVersion, senderIp, rxDbPort);

                            if (rxDbPort > 0) {
                                requestDbCopy(senderIp, rxDbPort, rxVersion);
                            } else {
                                Log.error(ClusterHeartbeatThread.class,
                                        "[MC] Heartbeat received without a valid dbPort – cannot request DB copy.");
                            }
                            return;
                        }

                        versionMismatch = rxVersion >= 0 && rxVersion != threadInfo.dbVersion();

                        Log.info(ClusterHeartbeatThread.class,
                                "[MC] DB version check: received=%d, local=%d",
                                rxVersion, threadInfo.dbVersion());

                        if (versionMismatch) {
                            Log.error(ClusterHeartbeatThread.class,
                                    "[MC] DB version mismatch detected (received=%d, local=%d). " +
                                            "Shutting down server.",
                                    rxVersion, threadInfo.dbVersion());
                            threadInfo.shutdownServer();
                        }
                    }
                }
            }
        } catch (SocketTimeoutException ignore) {
            // no heartbeat in this cycle
        } catch (Exception e) {
            if (threadInfo.isRunning()) {
                Log.error(ClusterHeartbeatThread.class,
                        "[MC-LOOP] Error receiving heartbeat: %s", e.getMessage());
            }
        }
    }
    /**
     * Request for a full copy of the database from the PRIMARY server.
     * Wraps the use of {@link ServerManager#tryLockCopy()} to prevent
     * multiple concurrent copy operations.
     *
     * @param primaryIp     IP address of the primary server
     * @param primaryDbPort TCP port used for the database copy
     * @param rxVersion     remote version announced in the heartbeat (used for logging only)
     */
    private void requestDbCopy(String primaryIp, int primaryDbPort, long rxVersion) {
        if (primaryDbPort <= 0) {
            Log.error(ClusterHeartbeatThread.class,
                    "[MC] Cannot request DB copy – invalid dbPort (%d).", primaryDbPort);
            return;
        }

        if (threadInfo instanceof ServerManager sn) {
            if (!sn.tryLockCopy()) {
                Log.warn(ClusterHeartbeatThread.class,
                        "[MC] DB copy request ignored: a copy is already in progress.");
                return;
            }
            try {
                requestDbCopyFromPrimary(primaryIp, primaryDbPort, rxVersion);
            } finally {
                sn.unlockCopy();
            }
        } else {
            requestDbCopyFromPrimary(primaryIp, primaryDbPort, rxVersion);
        }
    }

    /**
     * Primary-only part: accepts DB copy requests and handles them.
     *
     * @param _ss TCP server socket used for DB copy sessions
     */
    //TODO thread dedicada à receção de pedidos de ligação pelos servers secundários via tcp
    private void handleDbCopyAcceptLoop(ServerSocket _ss) {
        try {
            Socket s = _ss.accept(); // short timeout
            handleDbCopySession(s);
        } catch (SocketTimeoutException ignore) {
            // no incoming request
        } catch (Exception e) {
            if (threadInfo.isRunning()) {
                Log.error(ClusterHeartbeatThread.class,
                        "[DB COPY] Error accepting DB copy request: %s", e.getMessage());
            }
        }
    }

    /* ===================================================================== */
    /* ===================   HEARTBEAT / DB COPY I/O   ====================== */
    /* ===================================================================== */

    /**
     * Sends a multicast heartbeat with optional SQL payload (base64 encoded).
     *
     * @param ms  multicast socket
     * @param grp multicast group
     * @param sql list of SQL statements to include, or {@code null} for none
     * @throws IOException if sending fails
     */
    private void sendHeartbeat(MulticastSocket ms, InetAddress grp, List<String> sql) throws IOException {
        String encodedSql = "";
        if (sql != null && !sql.isEmpty()) {
            String joined = String.join(";;", sql);
            encodedSql = Base64.getEncoder() //encoded string with base64
                    .encodeToString(joined.getBytes(StandardCharsets.UTF_8));
        }

        String beat = "MC_HB;id=servidor" + threadInfo.serverTcpPort() +
                ";role=MASTER" +
                ";version=" + threadInfo.dbVersion() +
                ";dbPort=" + threadInfo.dbCopyPort() +
                ";clientPort=" + threadInfo.serverTcpPort() +
                ";sql=" + encodedSql;

        byte[] data = beat.getBytes(StandardCharsets.UTF_8);
        ms.send(new DatagramPacket(data, data.length, grp, threadInfo.multicastPort()));
    }

    /**
     * Backup side: requests a DB copy from the primary and replaces the local DB file.
     *
     * @param primaryIp   primary server IP
     * @param primaryPort primary DB copy port
     * @param rxVersion   DB version received from heartbeat (used for logging)
     */
    // TODO Servidor secundário: obtenção da base de dados/ficheiro ".db" completo do servidor principal, no arranque (termina se a operação falhar, informando o serviço de diretoria)
    private void requestDbCopyFromPrimary(String primaryIp, int primaryPort, long rxVersion) {
        Path target = threadInfo.dbPath();
        Path tmp = target.resolveSibling(target.getFileName().toString() + ".tmp");

        try (NetworkTcpConnection conn = NetworkTcpConnection.connect(
                primaryIp, primaryPort, Duration.ofSeconds(5))) {

            conn.setReadTimeout(Duration.ofSeconds(30));
            conn.sendMessage(new TcpMessage<>(MessageType.DB_REQUEST_COPY, "please"));

            TcpMessage<?> resp = conn.receiveMessage();
            if (resp == null || resp.getType() != MessageType.ACK) {
                Log.error(ClusterHeartbeatThread.class,
                        "[DB COPY/RQ] Invalid response to copy request (expected ACK copy-start).");
                return;
            }

            Files.createDirectories(target.getParent());
            long size = conn.readLong();

            long total;
            try (FileOutputStream fos = new FileOutputStream(tmp.toFile())) {
                total = conn.receiveExactly(fos, size);
            }
            Log.info(ClusterHeartbeatThread.class,
                    "[DB COPY/RQ] %d bytes received for DB copy -> %s%n", total, tmp);

            boolean moved = false;
            try {
                Files.move(tmp, target,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
                moved = true;
            } catch (Exception ignore) {
                // ignore and try fallback strategies
            }
            if (!moved) {
                try {
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                    moved = true;
                } catch (Exception ignore) {
                    // ignore and try fallback strategies
                }
            }
            if (!moved) {
                try {
                    Files.copy(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                    Files.deleteIfExists(tmp);
                    moved = true;
                } catch (Exception e) {
                    Log.error(ClusterHeartbeatThread.class,
                            "[DB COPY/RQ] Failed copy+delete when replacing DB file: %s", e.getMessage());
                }
            }

            if (moved) {
                Log.info(ClusterHeartbeatThread.class,
                        "[DB COPY/RQ] DB copy completed at %s (new version=%d)", target, rxVersion);
                // No local version cache to update: DB version comes from the copied file itself.
            } else {
                Log.error(ClusterHeartbeatThread.class,
                        "[DB COPY/RQ] Could not replace %s (temporary file left: %s)", target, tmp);
            }

        } catch (Exception e) {
            Log.error(ClusterHeartbeatThread.class,
                    "[DB COPY/RQ] Error during DB copy: %s.\nERROR = Original database not found!", e.getMessage());
            try {
                Files.deleteIfExists(tmp);
                // Comunica para a diretoria que vai encerrar
                try (DatagramSocket s = new DatagramSocket()) {
                    s.setSoTimeout(3000);

                    InetAddress dirAddr = InetAddress.getByName(threadInfo.directoryHost());
                    int dirPort = threadInfo.directoryPort();

                    // DEREGISTER
                    String registerMsg =
                            "TYPE = DB_ERROR | ID = "+ threadInfo.id()+" | TCP = "
                            +threadInfo.serverTcpIp() + ":" + threadInfo.serverTcpPort()
                            +" | DBV = " + threadInfo.dbVersion()
                            + " | DBP = " + (threadInfo.dbCopyPort())
                            + " | ERROR = Original database not found!"; // DB copy port
                    byte[] data = registerMsg.getBytes(StandardCharsets.UTF_8);
                    s.send(new DatagramPacket(data, data.length, dirAddr, dirPort));

                }
                threadInfo.shutdownServer();
            } catch (Exception ignore) {
                // ignore cleanup failure
            }

        }
    }

    /**
     * Primary side: handles a single DB copy session for {@link MessageType#DB_REQUEST_COPY}.
     *
     * @param acceptedSocket accepted socket for the DB copy session
     */
    private void handleDbCopySession(Socket acceptedSocket) {
        try (Socket s = acceptedSocket;
             NetworkTcpConnection connection = new NetworkTcpConnection(s)) {

            TcpMessage<?> req = connection.receiveMessage();
            if (req == null || req.getType() != MessageType.DB_REQUEST_COPY) {
                connection.sendMessage(new TcpMessage<>(MessageType.NACK, "bad-request", String.class));
                return;
            }

            if (!threadInfo.isPrimary()) {
                connection.sendMessage(new TcpMessage<>(MessageType.NACK, "not-primary", String.class));
                return;
            }

            connection.sendMessage(new TcpMessage<>(MessageType.ACK, "copy-start"));

            Path dbFile = threadInfo.dbPath();
            long size = Files.size(dbFile);
            connection.writeLong(size);

            try (FileInputStream fis = new FileInputStream(dbFile.toFile())) {
                long sent = connection.sendStreamViaObjectOut(fis, size);
                Log.info(ClusterHeartbeatThread.class,
                        "[DB COPY] %d bytes sent in DB copy -> %s%n", sent, dbFile);
            }

        } catch (Exception e) {
            Log.error(ClusterHeartbeatThread.class,
                    "[DB COPY] Error in DB copy session: %s", e.getMessage());
        }
    }

    /* ===================================================================== */
    /* =========================   UTIL METHODS   =========================== */
    /* ===================================================================== */

    /**
     * Extracts a long value from a semicolon-separated {@code key=value} payload.
     *
     * @param payload full payload string
     * @param key     key to search for
     * @return parsed long value or {@code -1} on failure
     */
    private static long extractLong(String payload, String key) {
        String needle = key + "=";
        int i = payload.indexOf(needle);
        if (i < 0) {
            return -1;
        }
        int j = payload.indexOf(';', i + needle.length());
        String raw = (j > 0 ? payload.substring(i + needle.length(), j)
                : payload.substring(i + needle.length()));
        try {
            return Long.parseLong(raw.trim());
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * Extracts a string value from a semicolon-separated {@code key=value} payload.
     *
     * @param payload full payload string
     * @param key     key to search for
     * @return string value or {@code null} if not found
     */
    @SuppressWarnings("SameParameterValue")
    private static String extractString(String payload, String key) {
        String needle = key + "=";
        int i = payload.indexOf(needle);
        if (i < 0) {
            return null;
        }
        int j = payload.indexOf(';', i + needle.length());
        String raw = (j > 0 ? payload.substring(i + needle.length(), j)
                : payload.substring(i + needle.length()));
        return raw.trim();
    }

    /**
     * Configures loopback mode for the multicast socket.
     * <p>
     * The method isolates the call to {@link MulticastSocket#setLoopbackMode(boolean)},
     * which is deprecated, so that the associated warning can be suppressed in a single place.
     *
     * @param socket multicast socket to configure
     */
    @SuppressWarnings("deprecation")
    private void configureLoopbackMode(MulticastSocket socket) {
        try {
            socket.setLoopbackMode(false);
        } catch (Throwable ignore) {
            // Some JVMs may not support this; ignore any failure.
        }
    }
}
