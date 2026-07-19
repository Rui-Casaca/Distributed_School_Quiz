package pt.isec.directory.threads;

/**
 * Holds runtime information about a quiz server registered in the directory.
 */
public class ServerInfo {

    /* ======================= FIELDS ======================= */

    /** TCP port used by the server to accept client connections. */
    private final int tcpPort;

    /** UDP port used by the server to communicate with the directory. */
    private final int udpPort;

    /** Unique server identifier (UUID or similar). */
    private final String id;

    /** Server IP address. */
    private final String ip;

    /** Timestamp (epoch millis) of the last time this server was seen alive. */
    private long lastSeenMillis;

    /* ======================= CONSTRUCTOR ======================= */

    /**
     * Creates a new {@link ServerInfo} instance.
     *
     * @param id      unique server identifier
     * @param ip      server IP address
     * @param tcpPort TCP port used to accept client connections
     * @param udpPort UDP port used by the server for directory communications
     */
    public ServerInfo(String id, String ip, int tcpPort, int udpPort) {
        this.tcpPort = tcpPort;
        this.id = id;
        this.ip = ip;
        this.udpPort = udpPort;
    }

    /* ======================= PUBLIC API ======================= */

    /**
     * Returns the TCP endpoint as {@code "<ip>:<port>"}.
     *
     * @return TCP endpoint string
     */
    public String tcpEndpoint() {
        return ip + ":" + tcpPort;
    }

    /**
     * Returns the timestamp (epoch millis) when this server was last seen.
     *
     * @return last seen timestamp in milliseconds
     */
    public long getLastSeenMillis() {
        return lastSeenMillis;
    }

    /**
     * Updates the timestamp when this server was last seen.
     *
     * @param v epoch milliseconds
     */
    public void setLastSeenMillis(long v) {
        this.lastSeenMillis = v;
    }

    /**
     * Returns the TCP port used by this server for client connections.
     *
     * @return TCP port
     */
    public int getTcpPort() {
        return tcpPort;
    }

    /**
     * Returns the unique identifier of this server.
     *
     * @return server id
     */
    public String getId() {
        return id;
    }

    /**
     * Returns the IP address of this server.
     *
     * @return server IP
     */
    public String getIp() {
        return ip;
    }

    /**
     * Returns the UDP port used by this server to talk with the directory.
     *
     * @return UDP port
     */
    public int getUdpPort() {
        return udpPort;
    }
}
