package pt.isec.directory.threads;
import pt.isec.common.messages.UdpMessage;
import pt.isec.common.util.Log;
import pt.isec.directory.core.IDirectoryThreadContext;
import java.io.IOException;
import java.net.DatagramPacket;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Worker thread responsible for processing UDP messages consumed from the
 * shared queue.
 * <p>
 * Responsibilities:
 * <ul>
 *     <li>Parse incoming protocol messages</li>
 *     <li>Update server registry (REGISTER / HEARTBEAT / DEREGISTER)</li>
 *     <li>Handle client LOGIN discovery</li>
 *     <li>Send textual responses back over UDP</li>
 * </ul>
 *
 * Protocol (text, {@code KEY=VALUE} pairs separated by {@code '|'}):
 * <p>
 * <b>Client messages (no VER):</b>
 * <ul>
 *     <li>{@code TYPE=LOGIN}</li>
 * </ul>
 *
 * <b>Server messages:</b>
 * <ul>
 *     <li>{@code TYPE=REGISTER   | ID=&lt;serverId&gt; | TCP=&lt;ip:port&gt; | DBV=&lt;dbVersion&gt;}</li>
 *     <li>{@code TYPE=HEARTBEAT  | ID=&lt;serverId&gt; | DBV=&lt;dbVersion&gt;}</li>
 *     <li>{@code TYPE=DEREGISTER | ID=&lt;serverId&gt;}</li>
 * </ul>
 *
 * Replies (text):
 * <ul>
 *     <li>{@code "200 OK"}</li>
 *     <li>{@code "200 PRINCIPAL &lt;ip:port&gt;"}</li>
 *     <li>{@code "400 BAD_REQUEST &lt;reason&gt;"}</li>
 *     <li>{@code "404 NO_PRINCIPAL"}</li>
 *     <li>{@code "409 CONFLICT &lt;reason&gt;"}</li>
 *     <li>{@code "500 ERROR &lt;reason&gt;"}</li>
 * </ul>
 */
@SuppressWarnings({ "ClassCanBeRecord", "resource" })
public class WorkerThread implements Runnable {

    /** Shared directory context with queues, registry and socket. */
    private final IDirectoryThreadContext threadInfo;

    /**
     * Creates a new worker bound to a directory context.
     *
     * @param threadInfo directory context shared between directory threads
     */
    public WorkerThread(IDirectoryThreadContext threadInfo) {
        this.threadInfo = threadInfo;
    }

    /**
     * Main processing loop:
     * <ul>
     *     <li>Polls the shared queue with a timeout</li>
     *     <li>Parses each message</li>
     *     <li>Dispatches to the appropriate handler based on {@code TYPE}</li>
     *     <li>Sends a text reply via UDP</li>
     * </ul>
     */
    @Override
    public void run() {
        Log.info(WorkerThread.class, "Worker started...");
        try {
            // Keep processing while:
            //  - the system is still running, OR
            //  - there are still messages waiting in the queue
            while (threadInfo.isRunning() || !threadInfo.queue().isEmpty()) {
                UdpMessage msg = threadInfo.queue().poll(500, TimeUnit.MILLISECONDS);
                if (msg == null) {
                    // Timeout: re-check isRunning() and queue condition in the loop
                    continue;
                }

                String payload = new String(msg.data(), 0, msg.length(), StandardCharsets.UTF_8);



                Map<String, String> kv = parseKv(payload);
                String type = kv.get("TYPE");

                boolean isPrimaryHeartbeat = "HEARTBEAT".equals(type) && isHeartbeatFromPrincipal(kv);

                if (payload.startsWith("TYPE=") || payload.startsWith("ROLE=")) {
                    if (isPrimaryHeartbeat) {
                        Log.infoMaster(WorkerThread.class, "Received: %s", payload);
                    } else {
                        Log.info(WorkerThread.class, "Received: %s", payload);
                    }
                } else {
                    Log.info(WorkerThread.class,
                            "Received non-protocol datagram (%d bytes) from %s:%d",
                            msg.length(), msg.addr(), msg.port());
                }

                if (type == null) {
                    send(msg, "400 BAD_REQUEST TYPE");
                    continue;
                }

                String reply;

                switch (type) {
                    case "LOGIN" -> {
                        Log.info(WorkerThread.class, "Client requests server discovery.");
                        reply = handleLogin();
                    }

                    case "REGISTER" -> {
                        Log.info(WorkerThread.class, "Server requests registration.");
                        reply = handleRegister(kv, msg.port());
                    }

                    case "HEARTBEAT" -> {
                        if (isPrimaryHeartbeat) {
                            Log.infoMaster(WorkerThread.class, "Server sends heartbeat.");
                        } else {
                            Log.info(WorkerThread.class, "Server sends heartbeat.");
                        }
                        reply = handleHeartbeat(kv);
                    }

                    case "DEREGISTER" -> {
                        Log.info(WorkerThread.class, "Server requests deregistration.");
                        reply = handleDeregister(kv);
                    }

                    default -> {
                        Log.info(WorkerThread.class, "Unknown TYPE: %s", type);
                        reply = "400 BAD_REQUEST TYPE";
                    }
                }

                send(msg, reply);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            Log.error(WorkerThread.class, "Error sending UDP: " + e.getMessage());
        } catch (Exception e) {
            Log.error(WorkerThread.class, "Unexpected error in Worker: " + e.getMessage());
        }

        Log.info(WorkerThread.class, "Worker terminated.");
    }

    /* ===================== HANDLERS ===================== */

    /**
     * Handles a client login / server discovery request.
     * <p>
     * Request:
     * <pre>TYPE=LOGIN</pre>
     * Response:
     * <ul>
     *     <li>{@code 200 PRINCIPAL <ip>:<port>} – primary server is available</li>
     *     <li>{@code 404 NO_PRINCIPAL} – no registered server</li>
     * </ul>
     *
     * @return textual reply for the client
     */
    private String handleLogin() {
        ServerInfo principal = findPrincipal();
        if (principal == null) {
            Log.info(WorkerThread.class, "Client login request: no primary server available.");
            return "404 NO_PRINCIPAL";
        }

        Log.infoMaster(
                WorkerThread.class,
                "Client login request: returning primary %s (id=%s)",
                principal.tcpEndpoint(),
                principal.getId()
        );

        return "200 PRINCIPAL " + principal.tcpEndpoint();
    }

    /**
     * Handles a server deregistration request.
     * <p>
     * Request:
     * <pre>TYPE=DEREGISTER|ID=&lt;uuid&gt;</pre>
     * Response:
     * <ul>
     *     <li>{@code 200 OK}</li>
     *     <li>{@code 400 BAD_REQUEST ID} – missing/blank ID</li>
     *     <li>{@code 409 CONFLICT UNKNOWN_ID} – unknown ID</li>
     * </ul>
     *
     * @param kv parsed key/value pairs from the UDP payload
     * @return textual reply for the server
     */
    private String handleDeregister(Map<String, String> kv) {
        String id = kv.get("ID");
        if (id == null || id.isEmpty()) {
            return "400 BAD_REQUEST ID";
        }

        ServerInfo removed = threadInfo.servers().remove(id);
        if (removed == null) {
            return "409 CONFLICT UNKNOWN_ID";
        }

        synchronized (threadInfo.serversLock()) {
            threadInfo.serversOrdered().remove(id);
        }

        return "200 OK";
    }

    /**
     * Handles a heartbeat request from a server.
     * <p>
     * Request:
     * <pre>TYPE=HEARTBEAT|ID=&lt;uuid&gt;</pre>
     * Response:
     * <ul>
     *     <li>{@code 200 OK <ip>:<port>} – ACK + current primary endpoint</li>
     *     <li>{@code 400 BAD_REQUEST ID}</li>
     *     <li>{@code 409 CONFLICT UNKNOWN_ID}</li>
     *     <li>{@code 404 NO_PRINCIPAL}</li>
     * </ul>
     *
     * @param kv parsed key/value pairs from the UDP payload
     * @return textual reply for the server
     */
    private String handleHeartbeat(Map<String, String> kv) {
        String id = kv.get("ID");
        if (id == null || id.isBlank()) {
            return "400 BAD_REQUEST ID";
        }

        ServerInfo si = threadInfo.servers().get(id);
        if (si == null) {
            return "409 CONFLICT UNKNOWN_ID";
        }

        long now = System.currentTimeMillis();
        si.setLastSeenMillis(now);

        ServerInfo principal = findPrincipal();
        if (principal == null) {
            Log.info(WorkerThread.class,
                    "Heartbeat from %s received, but no primary is currently elected.", id);
            return "404 NO_PRINCIPAL";
        }

        boolean isPrincipal = principal.getId().equals(id);
        if (isPrincipal) {
            Log.infoMaster(
                    WorkerThread.class,
                    "Heartbeat from PRIMARY %s: current primary=%s (id=%s)",
                    id, principal.tcpEndpoint(), principal.getId()
            );
        } else {
            Log.info(
                    WorkerThread.class,
                    "Heartbeat from %s: current primary=%s (id=%s)",
                    id, principal.tcpEndpoint(), principal.getId()
            );
        }

        return "200 OK " + principal.tcpEndpoint();
    }

    /**
     * Handles registration of a new server.
     * <p>
     * Request:
     * <pre>TYPE=REGISTER|ID=&lt;uuid&gt;|TCP=&lt;ip&gt;:&lt;port&gt;|DBV=&lt;dbVersion&gt;</pre>
     * Response:
     * <ul>
     *     <li>{@code 200 OK <ip>:<port>} – OK + current primary endpoint</li>
     *     <li>{@code 400 BAD_REQUEST ID/TCP/TCP_PORT}</li>
     *     <li>{@code 404 NO_PRINCIPAL}</li>
     *     <li>{@code 409 CONFLICT DUP_ENDPOINT}</li>
     * </ul>
     *
     * @param kv      parsed key/value pairs from the UDP payload
     * @param udpPort UDP port of the registering server
     * @return textual reply for the server
     */
    private String handleRegister(Map<String, String> kv, int udpPort) {
        String id  = kv.get("ID");
        String tcp = kv.get("TCP");
        String dbv = kv.get("DBV"); // optional

        if (id == null || id.isBlank()) {
            return "400 BAD_REQUEST ID";
        }
        if (tcp == null || !tcp.contains(":")) {
            return "400 BAD_REQUEST TCP";
        }

        String[] parts = tcp.split(":", 2);
        String ip = parts[0].trim();
        int port;
        try {
            port = Integer.parseInt(parts[1].trim());
        } catch (NumberFormatException nfe) {
            return "400 BAD_REQUEST TCP_PORT";
        }
        if (ip.isEmpty() || port <= 0 || port > 65535) {
            return "400 BAD_REQUEST TCP";
        }

        // Rule: do not allow two servers on the same <ip:port> with different IDs
        for (ServerInfo other : threadInfo.servers().values()) {
            if (other.getIp().equals(ip) && other.getTcpPort() == port && !other.getId().equals(id)) {
                Log.info(WorkerThread.class,
                        "Rejected REGISTER: duplicated endpoint %s:%d for ID=%s (already used by %s).",
                        ip, port, id, other.getId());
                return "409 CONFLICT DUP_ENDPOINT";
            }
        }

        int dbVersion = 0;
        if (dbv != null && !dbv.isBlank()) {
            try {
                dbVersion = Integer.parseInt(dbv.trim());
            } catch (NumberFormatException ex) {
                Log.warn(WorkerThread.class,
                        "Invalid DBV '%s' received from server %s.", dbv, id);
            }
        }

        ServerInfo si = threadInfo.servers().get(id);
        long now = System.currentTimeMillis();
        if (si == null) {
            si = new ServerInfo(id, ip, port, udpPort);
            si.setLastSeenMillis(now);
            threadInfo.servers().put(id, si);
            synchronized (threadInfo.serversLock()) {
                threadInfo.serversOrdered().put(id, si);
            }
        } else {
            // same ID coming back: update last seen
            si.setLastSeenMillis(now);
        }

        Log.info(WorkerThread.class,
                "Server registered/updated: id=%s, endpoint=%s:%d, dbVersion=%d",
                id, ip, port, dbVersion);

        ServerInfo principal = findPrincipal();
        if (principal == null) {
            Log.info(WorkerThread.class,
                    "Server registered/updated: id=%s, endpoint=%s:%d, dbVersion=%d, but no primary is available.",
                    id, ip, port, dbVersion);
            return "404 NO_PRINCIPAL";
        }

        Log.info(WorkerThread.class,
                "Server registered/updated: id=%s, endpoint=%s:%d, dbVersion=%d",
                id, ip, port, dbVersion);

        Log.infoMaster(
                WorkerThread.class,
                "Current primary after register: %s (id=%s)",
                principal.tcpEndpoint(),
                principal.getId()
        );

        return "200 OK " + principal.tcpEndpoint();
    }

    /* ===================== LOW-LEVEL HELPERS ===================== */

    /**
     * Sends a UDP text response to the sender.
     *
     * @param to   original UDP message (contains sender address and port)
     * @param text response text (e.g., "200 OK", "404 NO_PRINCIPAL")
     * @throws IOException if sending fails
     */
    private void send(UdpMessage to, String text) throws IOException {
        byte[] out = text.getBytes(StandardCharsets.UTF_8);
        DatagramPacket dp = new DatagramPacket(out, out.length, to.addr(), to.port());
        // DatagramSocket instance is owned by the directory manager; we must not close it here.
        threadInfo.socket().send(dp);
    }

    /**
     * Returns the current "principal" server, defined as the first entry in the
     * ordered server map (insertion order).
     *
     * @return {@link ServerInfo} of the principal server, or {@code null} if none
     */
    private ServerInfo findPrincipal() {
        synchronized (threadInfo.serversLock()) {
            var iterator = threadInfo.serversOrdered().values().iterator();
            return iterator.hasNext() ? iterator.next() : null;
        }
    }

    /**
     * Parses a message in the form {@code KEY=VALUE|KEY=VALUE|...}.
     * <p>
     * Example:
     * <pre>"TYPE=REGISTER|ID=abc123|TCP=192.168.1.10:9999"</pre>
     *
     * @param s string with key-value pairs separated by pipe {@code '|'}
     * @return map with extracted key/value pairs
     */
    private static Map<String, String> parseKv(String s) {
        Map<String, String> m = new LinkedHashMap<>();
        for (String token : s.split("\\|")) {
            String t = token.trim();
            if (t.isEmpty()) {
                continue;
            }
            int eq = t.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String k = t.substring(0, eq).trim();
            String v = t.substring(eq + 1).trim();
            if (!k.isEmpty()) {
                m.put(k, v);
            }
        }
        return m;
    }
    /**
     * Returns {@code true} if the given HEARTBEAT message was sent by
     * the current principal server (same ID as {@link #findPrincipal()}).
     */
    private boolean isHeartbeatFromPrincipal(Map<String, String> kv) {
        if (!"HEARTBEAT".equals(kv.get("TYPE"))) {
            return false;
        }
        String id = kv.get("ID");
        if (id == null || id.isBlank()) {
            return false;
        }
        ServerInfo principal = findPrincipal();
        return principal != null && id.equals(principal.getId());
    }
}
