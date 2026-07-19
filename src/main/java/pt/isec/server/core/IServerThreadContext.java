package pt.isec.server.core;
import pt.isec.server.db.DbCommands;
import pt.isec.server.services.auth.IAuthService;
import pt.isec.server.services.question.IAnswerService;
import pt.isec.server.services.question.IQuestionService;
import pt.isec.server.threads.NetworkTcpConnection;

import java.net.NetworkInterface;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.BlockingQueue;

/**
 * Abstraction for the main server manager.
 * <p>
 * Provides access to configuration, lifecycle, database and business services.
 */
public interface IServerThreadContext {

    /* ===================== IDENTIFICATION / ENDPOINTS ===================== */

    /**
     * Returns the unique server identifier.
     *
     * @return server id
     */
    String id();

    /**
     * Returns the IP used for TCP client connections.
     *
     * @return server IP
     */
    String serverTcpIp();

    /**
     * Returns the TCP port used to accept client connections.
     *
     * @return server TCP port
     */
    int serverTcpPort();

    /**
     * Returns the TCP port used for DB copy sessions.
     *
     * @return DB copy port
     */
    int dbCopyPort();

    /**
     * Returns the directory service host.
     *
     * @return directory host
     */
    String directoryHost();

    /**
     * Returns the directory service port.
     *
     * @return directory port
     */
    int directoryPort();


    /* ===================== MULTICAST / CLUSTER ===================== */

    /**
     * Returns the multicast group address used for cluster heartbeats.
     *
     * @return multicast group
     */
    String multicastGroup();

    /**
     * Returns the multicast UDP port.
     *
     * @return multicast port
     */
    int multicastPort();

    /**
     * Returns the network interface used for multicast.
     *
     * @return multicast-capable interface
     */
    NetworkInterface multicastInterface();

    /**
     * Returns the queue of SQL statements that should be broadcast to backup nodes.
     *
     * @return queue with SQL batches
     */
    BlockingQueue<List<String>> queue();


    /* ===================== LIFE CYCLE / STATE ===================== */

    /**
     * Indicates whether the server is still running.
     *
     * @return {@code true} if running
     */
    boolean isRunning();

    /**
     * Requests a server shutdown (graceful).
     *
     * @throws Exception if shutdown fails
     */
    void shutdownServer() throws Exception;

    /**
     * Indicates whether this node currently acts as primary.
     *
     * @return {@code true} if primary
     */
    boolean isPrimary();

    /**
     * Updates the primary server endpoint and adjusts local role accordingly.
     *
     * @param ip   primary IP
     * @param port primary TCP port
     */
    void setPrimary(String ip, int port);


    /* ===================== DATABASE ===================== */

    /**
     * Returns the current database version.
     * <p>
     * Implementations are expected to read this value directly from the database
     * (for example from {@code config.db_version}) rather than from an in-memory cache.
     *
     * @return DB version, or {@code -1} if it cannot be determined
     */
    long dbVersion();

    /**
     * Deprecated: kept only for backwards compatibility.
     * <p>
     * Implementations are free to ignore this call, since the database version is
     * now managed exclusively in the database itself.
     *
     * @param v new DB version (ignored)
     */
    @Deprecated
    void setDbVersion(long v);

    /**
     * Returns the current path of the DB file used by this server.
     *
     * @return DB file path
     */
    Path dbPath();

    /**
     * Lazily initializes DB layer and services if needed.
     */
    void initDatabaseLayerIfNeeded();

    /**
     * Returns the low-level DB command helper.
     *
     * @return {@link DbCommands} instance
     */
    DbCommands getDb();

    /**
     * Attempts to acquire a lock indicating that a DB copy is in progress.
     *
     * @return {@code true} if lock acquired
     */
    boolean tryLockCopy();

    /**
     * Releases the DB copy lock.
     */
    void unlockCopy();


    /* ===================== BUSINESS SERVICES ===================== */

    /**
     * Returns the authentication service.
     *
     * @return auth service
     */
    IAuthService getAuthService();

    /**
     * Returns the question service.
     *
     * @return question service
     */
    IQuestionService getQuestionService();

    /**
     * Returns the answer service.
     *
     * @return answer service
     */
    IAnswerService getAnswerService();

    /* ===================== ACTIVE TCP CONNECTIONS ===================== */

    /**
     * Registers an active TCP connection for a user, enabling server push notifications.
     *
     * @param userId user identifier
     * @param conn   active TCP connection
     */
    void registerClientConnection(long userId, NetworkTcpConnection conn);

    /**
     * Unregisters the active TCP connection for a user.
     *
     * @param userId user identifier
     */
    void unregisterClientConnection(long userId);
}
