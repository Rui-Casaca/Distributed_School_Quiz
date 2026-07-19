package pt.isec.directory.core;

import pt.isec.common.messages.UdpMessage;
import pt.isec.directory.threads.ServerInfo;

import java.net.DatagramSocket;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentMap;

/**
 * Abstraction for the directory manager.
 * <p>
 * Exposes configuration, global state and access to the server registry and message queue.
 */
public interface IDirectoryThreadContext {

    /* ===================== UDP CONFIGURATION ===================== */

    /**
     * Returns the UDP port on which the directory listens.
     *
     * @return UDP port
     */
    int udpPort();

    /**
     * Returns the maximum UDP packet size accepted by the directory.
     *
     * @return maximum packet size in bytes
     */
    int maxPacketSize();

    /**
     * Returns the underlying UDP socket.
     *
     * @return datagram socket
     */
    DatagramSocket socket();


    /* ===================== GLOBAL STATE ===================== */

    /**
     * Indicates whether the directory is still running.
     *
     * @return {@code true} if running
     */
    boolean isRunning();


    /* ===================== SERVER REGISTRATION / MANAGEMENT ===================== */

    /**
     * Returns the concurrent map of servers indexed by UUID.
     *
     * @return concurrent map of server info
     */
    ConcurrentMap<String, ServerInfo> servers();

    /**
     * Returns an insertion-ordered view of servers, used to pick the primary.
     *
     * @return ordered server map
     */
    Map<String, ServerInfo> serversOrdered();

    /**
     * Returns a lock object used to synchronize operations on {@link #serversOrdered()}.
     *
     * @return lock object
     */
    Object serversLock();

    /**
     * Returns the number of registered servers.
     *
     * @return server count
     */
    int serversCount();

    /**
     * Returns the UUID of the primary server, or {@code null} if there is none.
     *
     * @return UUID of master server or {@code null}
     */
    String masterServerUuid();

    /**
     * Returns the TCP port of a server given its UUID.
     *
     * @param uuid server UUID
     * @return TCP port or {@code -1} if unknown
     */
    int serverTcpPort(String uuid);

    /**
     * Removes servers that have been inactive for longer than the TTL.
     *
     * @param currTime current time in epoch milliseconds
     */
    void removeServersFromList(long currTime);


    /* ===================== MESSAGE PROCESSING ===================== */

    /**
     * Returns the blocking queue used between the UDP listener and worker threads.
     *
     * @return message queue
     */
    BlockingQueue<UdpMessage> queue();
}
