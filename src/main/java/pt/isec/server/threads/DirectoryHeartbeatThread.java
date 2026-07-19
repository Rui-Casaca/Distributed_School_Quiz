package pt.isec.server.threads;

import pt.isec.common.util.Log;
import pt.isec.server.core.IServerThreadContext;
import pt.isec.server.core.ServerManager;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Objects;

/**
 * Thread responsible for communicating with the directory service via UDP.
 * <p>
 * Responsibilities:
 * <ul>
 *     <li>Send {@code REGISTER} on startup</li>
 *     <li>Receive and process the current primary server information</li>
 *     <li>Initialize database path based on primary/backup role</li>
 *     <li>Send periodic {@code HEARTBEAT}</li>
 *     <li>Send {@code DEREGISTER} on shutdown</li>
 * </ul>
 */
public class DirectoryHeartbeatThread implements Runnable, AutoCloseable {

    /** Socket receive timeout in milliseconds. */
    private static final int SOCKET_TIMEOUT_MS = 3000;
    /** Interval between heartbeats in milliseconds. */
    private static final int HEARTBEAT_INTERVAL_MS = 5000;
    /** Maximum number of retries when waiting for the principal. */
    private static final int RETRY_COUNT = 3;
    /** Sleep interval between loop iterations in milliseconds. */
    private static final int SLEEP_INTERVAL_MS = 50;
    /** UDP buffer size in bytes. */
    private static final int BUFFER_SIZE = 512;

    /** Context with directory and database information. */
    private final IServerThreadContext threadInfo;
    /** UDP socket used for directory communication. */
    private DatagramSocket socket;

    /**
     * Creates a new directory heartbeat thread.
     *
     * @param threadInfo server manager providing directory and DB information
     */
    public DirectoryHeartbeatThread(IServerThreadContext threadInfo) {
        this.threadInfo = threadInfo;
    }

    /**
     * Main loop: register, obtain the primary server, initialize DB (primary/backup),
     * send heartbeats, and deregister on shutdown.
     */
    @Override
    public void run() {
        try (DatagramSocket socket = new DatagramSocket()) {
            this.socket = socket;
            socket.setSoTimeout(SOCKET_TIMEOUT_MS);

            InetAddress dirAddr = InetAddress.getByName(threadInfo.directoryHost());
            int dirPort = threadInfo.directoryPort();

            // REGISTER
            // TODO Servidor: registo no serviço de diretoria e determinação do papel (principal ou secundário)
            String registerMsg = requestKeyValue(
                    "TYPE", "REGISTER",
                    "ID", threadInfo.id(),
                    "TCP", threadInfo.serverTcpIp() + ":" + threadInfo.serverTcpPort(), // this server TCP endpoint
                    "DBV", String.valueOf(threadInfo.dbVersion()),                      // DB version (from DB when available)
                    "DBP", String.valueOf(threadInfo.dbCopyPort())                      // DB copy port
            );
            send(socket, dirAddr, dirPort, registerMsg);

            // waits "200 PRINCIPAL ip:port[|DBV=X]"
            Endpoint reply = waitPrincipal(socket);
            if (reply == null) {
                // TODO Servidor: encerramento se não receber qualquer resposta do serviço de diretoria
                Log.error(DirectoryHeartbeatThread.class,
                        "[DIR] No response from directory for REGISTER; shutting down server.");
                threadInfo.shutdownServer();
                return;
            }

            threadInfo.setPrimary(reply.ip, reply.port);
            boolean iAmPrimary =
                    Objects.equals(reply.ip, threadInfo.serverTcpIp()) &&
                            reply.port == threadInfo.serverTcpPort();

            // Directory DB version is now only used for logging; the authoritative
            // version is stored in the local database itself.
            if (reply.dbv != null) {
                Log.info(DirectoryHeartbeatThread.class,
                        "[DIR] Directory reports cluster DB version: %d", reply.dbv);
            }

            if (threadInfo instanceof ServerManager node) {
                if (iAmPrimary) {
                    try {
                        // primary chooses DB: newest or new
                        // TODO Servidor principal: quando arranca, cria a base de dados se não existir (esquema, mas sem dados) ou utiliza a mais recente
                        node.initDbPathAsPrincipalOnStartup();
                        // create/open DB and schema
                        node.initDatabaseLayerIfNeeded();
                    } catch (Exception e) {
                        Log.error(DirectoryHeartbeatThread.class,
                                "[DB] Failed to initialize primary server database: %s", e.getMessage());
                    }
                } else {
                    // backup: define local DB path
                    node.initDbPathAsBackupOnStartup();

                    if (!Files.exists(node.dbPath())) {
                        Log.info(DirectoryHeartbeatThread.class,
                                "[DB] Backup server without local database; will await multicast heartbeat to copy.");
                    }
                }
            } else {
                Log.error(DirectoryHeartbeatThread.class,
                        "[DB] IServerThreadContext instance is not a ServerManager; unexpected configuration.");
            }

            Log.infoMaster(DirectoryHeartbeatThread.class,
                    "[DIR] Current PRIMARY server: %s:%d | isPrimary=%s | localDbVersion=%d",
                    reply.ip, reply.port, iAmPrimary, threadInfo.dbVersion());

            long last = 0;

            // HEARTBEAT — always send current DB version (threadInfo.dbVersion())
            while (threadInfo.isRunning()) {
                long now = System.currentTimeMillis();
                //TODO e por UDP unicast ao serviço de diretoria, de Heartbeats + estrutura de Heartbeats
                if (now - last >= HEARTBEAT_INTERVAL_MS) {
                    String hb = requestKeyValue("[ROLE", iAmPrimary ? "MASTER] " : "BACKUP] ",
                            "TYPE", "HEARTBEAT",
                            "ID", threadInfo.id()
                    );
                    send(socket, dirAddr, dirPort, hb);
                    last = now;
                }

                Endpoint cur = tryReceivePrincipal(socket);
                if (cur != null) {
                    threadInfo.setPrimary(cur.ip, cur.port);
                    iAmPrimary =
                            Objects.equals(cur.ip, threadInfo.serverTcpIp()) &&
                                    cur.port == threadInfo.serverTcpPort();
                    Log.infoMaster(
                            DirectoryHeartbeatThread.class,
                            "[DIR] Directory reports PRIMARY=%s:%d | ME=%s:%d | amPrimary=%s",
                            cur.ip, cur.port,
                            threadInfo.serverTcpIp(), threadInfo.serverTcpPort(),
                            iAmPrimary
                    );
                }
                Thread.sleep(SLEEP_INTERVAL_MS);
            }

            // DEREGISTER
            String deregMsg = requestKeyValue(
                    "TYPE", "DEREGISTER",
                    "ID", threadInfo.id()
            );
            send(socket, dirAddr, dirPort, deregMsg);

        } catch (Exception e) {
            if (threadInfo.isRunning()) {
                Log.error(DirectoryHeartbeatThread.class,
                        "[DIR] Error in directory heartbeat thread: %s", e.getMessage());
            }
        } finally {
            Log.info(DirectoryHeartbeatThread.class, "Directory heartbeat thread terminated.");
            try {
                close();
            } catch (Exception ignore) {
                // ignore
            }
        }
    }

    /* ---------- UDP helpers ---------- */

    /**
     * Simple DTO-like record holding directory response details.
     *
     * @param ip   primary server IP
     * @param port primary server TCP port
     * @param dbv  DB version (may be {@code null})
     */
    private record Endpoint(String ip, int port, Integer dbv) {
    }

    /**
     * Builds a {@code KEY=VALUE|KEY=VALUE|...} style message from an array of key/value pairs.
     *
     * @param keyValue array of key/value pairs (must have even length)
     * @return formatted message
     */
    private static String requestKeyValue(String... keyValue) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < keyValue.length; i += 2) {
            b.append(keyValue[i]).append('=').append(keyValue[i + 1]).append('|');
        }
        return b.toString();
    }

    /**
     * Sends a UDP message to the specified address/port.
     *
     * @param s    datagram socket
     * @param addr destination address
     * @param port destination port
     * @param msg  message to send
     * @throws IOException if sending fails
     */
    private void send(DatagramSocket s, InetAddress addr, int port, String msg) throws IOException {
        byte[] data = msg.getBytes(StandardCharsets.UTF_8);
        s.send(new DatagramPacket(data, data.length, addr, port));
    }

    /**
     * Attempts to receive primary server information with a limited number of retries.
     *
     * @param s datagram socket
     * @return {@link Endpoint} information or {@code null} if no valid response is received
     */
    private Endpoint waitPrincipal(DatagramSocket s) {
        for (int i = 0; i < RETRY_COUNT; i++) {
            Endpoint ep = tryReceivePrincipal(s);
            if (ep != null) {
                return ep;
            }
        }
        return null;
    }

    /**
     * Tries to receive and parse a directory response with primary information.
     *
     * @param socket datagram socket
     * @return {@link Endpoint} data or {@code null} on timeout/error
     */
    //TODO thread dedicada à receção de datagramas UDP
    private Endpoint tryReceivePrincipal(DatagramSocket socket) {
        try {
            byte[] buf = new byte[BUFFER_SIZE];
            DatagramPacket packet = new DatagramPacket(buf, buf.length);
            socket.receive(packet);
            String resp = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8).trim();

            // Another server already using this endpoint
            if (resp.startsWith("409 CONFLICT DUP_ENDPOINT")) {
                Log.error(DirectoryHeartbeatThread.class,
                        "[DIR] Another server is already active with this endpoint (ip:port). Terminating process.");
                System.exit(2);
                return null; // unreachable
            }

            // After TTL without heartbeat, directory may send SHUTDOWN / 404 NO_PRINCIPAL
            if (resp.startsWith("SHUTDOWN") || resp.startsWith("404 NO_PRINCIPAL")) {
                threadInfo.shutdownServer();
                Log.info(DirectoryHeartbeatThread.class,
                        "[DIR] Directory requested shutdown (or no primary available). Server will be terminated.");
                return null;
            }

            String body = resp.substring("200 OK ".length());
            String[] mainAndRest = body.split("\\|", 2);
            String[] ipPort = mainAndRest[0].split(":");
            if (ipPort.length != 2) {
                return null;
            }

            String ip = ipPort[0];
            int port = Integer.parseInt(ipPort[1]);

            // Optional DBV=version field
            Integer dbv = getInteger(body);

            return new Endpoint(ip, port, dbv);
        } catch (SocketTimeoutException e) {
            // normal timeout: no response in this interval
            return null;
        } catch (Exception e) {
            // generic error receiving or parsing the response
            return null;
        }
    }

    /**
     * Extracts the optional {@code DBV} (database version) integer value
     * from the directory response body.
     *
     * @param body full response body text
     * @return parsed database version, or {@code null} if not present or invalid
     */
    private static Integer getInteger(String body) {
        Integer dbv = null;
        String dbvToken = "DBV=";
        int idx = body.indexOf(dbvToken);
        if (idx >= 0) {
            int end = body.indexOf('|', idx + dbvToken.length());
            String dbvStr = (end >= 0
                    ? body.substring(idx + dbvToken.length(), end)
                    : body.substring(idx + dbvToken.length()));
            try {
                dbv = Integer.parseInt(dbvStr.trim());
            } catch (NumberFormatException ignore) {
                // keep dbv as null if parsing fails
            }
        }
        return dbv;
    }

    /**
     * Closes the UDP socket if it is open.
     */
    @Override
    public void close() {
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }
}
