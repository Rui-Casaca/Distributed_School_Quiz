package pt.isec.server.core;
import pt.isec.common.dto.auth.AuthResponseDTO;
import pt.isec.common.dto.auth.LoginRequestDTO;
import pt.isec.common.util.Log;
import pt.isec.server.db.DbCommands;
import pt.isec.server.db.DbCreate;
import pt.isec.server.services.auth.AuthService;
import pt.isec.server.services.auth.IAuthService;
import pt.isec.server.services.question.AnswerService;
import pt.isec.server.services.question.IAnswerService;
import pt.isec.server.services.question.IQuestionService;
import pt.isec.server.services.question.QuestionService;
import pt.isec.server.threads.ClusterHeartbeatThread;
import pt.isec.server.threads.ClientListenerThread;
import pt.isec.server.threads.DirectoryHeartbeatThread;
import pt.isec.server.threads.NetworkTcpConnection;
import java.io.IOException;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Main server coordinator.
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>Manages database initialization and access</li>
 *   <li>Creates and exposes application services (auth, questions, answers)</li>
 *   <li>Coordinates cluster behaviour (primary/backup, multicast heartbeats, DB copies)</li>
 *   <li>Tracks active sessions and TCP client connections</li>
 *   <li>Starts and stops background threads for directory, cluster and client listeners</li>
 * </ul>
 */
public class ServerManager implements IServerThreadContext, IQuestionAnswerContext {

    /* ======================= CONSTANTS ======================= */

    /** Default multicast group used by the cluster. */
    private static final String DEFAULT_MULTICAST_GROUP = "230.30.30.30";

    /** Default multicast port used by the cluster. */
    private static final int DEFAULT_MULTICAST_PORT = 3030;

    /* ======================= DB / SERVICES STATE ======================= */

    private volatile boolean dbInitialised = false;
    private DbCommands dbCommands;

    /** Application authentication service. */
    private IAuthService authService;
    /** Application question management service. */
    private IQuestionService questionService;
    /** Application answer management service. */
    private IAnswerService answerService;

    /* ======================= SESSIONS / REPLICATION ======================= */

    /** Queue of SQL commands to replicate to other nodes. */
    private final BlockingQueue<List<String>> sqlToBroadcast = new LinkedBlockingQueue<>();

    /* ======================= IDENTITY / NETWORK ======================= */

    private final String id;
    private final String serverIdShort;
    private final String ip;
    private final int clientPort;
    private final int dbCopyPort;

    private final String dirHost;
    private final int dirPort;

    /** Network interface used for multicast. */
    private final NetworkInterface multicastInterface;

    /** Base directory for data files (including the .db file). */
    private final Path dataDir;

    /** Absolute path of the SQLite database currently in use. */
    private volatile Path dbPath;

    /* ======================= GLOBAL STATE / ROLE ======================= */

    private volatile boolean running = true;
    private volatile boolean isPrimary;

    private final AtomicBoolean copying = new AtomicBoolean(false);

    /* ======================= MAIN THREADS ======================= */

    private Thread threadClusterHeartbeat;
    private Thread tDirectoryHeartbeat;
    private Thread threadClientListener;

    /* ======================= ACTIVE TCP CONNECTIONS ======================= */

    /** userId -> current TCP connection (if any). */
    private final Map<Long, NetworkTcpConnection> activeClientConnections = new ConcurrentHashMap<>();

    /* ======================= CONSTRUCTOR ======================= */

    /**
     * Creates a new server manager.
     *
     * @param dirHost       directory service host
     * @param dirPort       directory service port
     * @param mcIfIp        multicast interface IP or {@code "AUTO"}
     * @param clientPort    TCP port used to accept client connections
     * @param dbCopyPort    TCP port used to handle DB copy requests
     * @param initialDbPath initial DB path hint (used to derive {@code dataDir})
     * @throws Exception if resolving multicast interface fails
     */
    public ServerManager(String dirHost, int dirPort, String mcIfIp,
                         int clientPort, int dbCopyPort, Path initialDbPath) throws Exception {
        this.id = UUID.randomUUID().toString();
        this.serverIdShort = id.substring(0, 8);
        this.ip = InetAddress.getLocalHost().getHostAddress();
        this.clientPort = clientPort;
        this.dbCopyPort = dbCopyPort;
        this.dirHost = dirHost;
        this.dirPort = dirPort;

        this.dataDir = initialDbPath; //se existir usa o diretório

        this.isPrimary = false;

        this.multicastInterface = resolveMulticastInterface(mcIfIp);
        if (this.multicastInterface == null) {
            throw new IllegalArgumentException("Invalid network interface/IP for multicast: " + mcIfIp);
        }

        Log.info(ServerManager.class,
                "[MC] interface multicast selecionada: %s", multicastInterface.getName());
        Log.info(ServerManager.class,
                "[DB] caminho inicial da BD=%s (versão=%d, role=BACKUP)",
                dbPath, dbVersion());
    }

    /* ======================= STATIC HELPERS ======================= */

    /**
     * Resolves the multicast network interface for a given IP or uses "AUTO" selection.
     *
     * @param mcIfIp IP address of desired interface or {@code "AUTO"}
     * @return a multicast-capable {@link NetworkInterface} or {@code null} if none found
     * @throws Exception if address resolution fails
     */
    private static NetworkInterface resolveMulticastInterface(String mcIfIp) throws Exception {
        if (mcIfIp == null || mcIfIp.isBlank() || "AUTO".equalsIgnoreCase(mcIfIp)) {
            return pickDefaultMulticastInterface();
        }
        InetAddress addr = InetAddress.getByName(mcIfIp);
        NetworkInterface ni = NetworkInterface.getByInetAddress(addr);
        if (ni != null && ni.isUp() && ni.supportsMulticast() && !ni.isLoopback()) {
            return ni;
        }
        return pickDefaultMulticastInterface();
    }

    /**
     * Picks a default multicast-capable network interface.
     *
     * @return a multicast-capable non-loopback interface or {@code null} if none found
     * @throws Exception if listing interfaces fails
     */
    private static NetworkInterface pickDefaultMulticastInterface() throws Exception {
        Enumeration<NetworkInterface> ifs = NetworkInterface.getNetworkInterfaces();
        while (ifs.hasMoreElements()) {
            NetworkInterface ni = ifs.nextElement();
            if (!ni.isUp() || ni.isLoopback() || !ni.supportsMulticast()) {
                continue;
            }
            var addrs = ni.getInetAddresses();
            while (addrs.hasMoreElements()) {
                InetAddress a = addrs.nextElement();
                if (a instanceof Inet4Address) {
                    return ni;
                }
            }
        }
        return null;
    }

    /**
     * Finds the most recently modified {@code .db} file in a directory.
     *
     * @param dir directory to search
     * @return newest DB path or {@code null} if none found
     * @throws IOException if listing or reading attributes fails
     */
    private static Path findNewestDbInDir(Path dir) throws IOException {
        try (var stream = Files.list(dir)) {
            return stream
                    .filter(p -> p.toString().endsWith(".db"))
                    .max(Comparator.comparingLong(p -> p.toFile().lastModified()))
                    .orElse(null);
        }
    }

    /* ======================= DATABASE PATH CHOOSING ======================= */

    /**
     * Initializes DB path when starting as primary node.
     * <p>
     * If an existing DB is found in {@code dataDir}, uses the newest one;
     * otherwise creates a new file name.
     *
     * @throws IOException if directory access fails
     */
    public synchronized void initDbPathAsPrincipalOnStartup() throws IOException {
        if (this.dbPath != null) {
            return; // already chosen
        }

        // TODO Servidor: esquema de nomeação dos ficheiros SQLite (".db") que evita apagar ficheiros existentes
        Path newest = findNewestDbInDir(dataDir);
        //TODO Servidor: evita apagar ficheiros existentes
        if (newest != null) {
            this.dbPath = newest.toAbsolutePath();
            Log.info(ServerManager.class,
                    "[DB] PRIMARY: a usar a BD mais recente no diretório: %s", this.dbPath);
        } else {
            //TODO Servidor: esquema de nomeação dos ficheiros SQLite (".db")
            String name = String.format("quiz-%s.db", serverIdShort);
            this.dbPath = dataDir.resolve(name).toAbsolutePath();
            Log.info(ServerManager.class,
                    "[DB] PRIMARY: nenhuma BD encontrada; novo ficheiro será: %s", this.dbPath);
        }
    }

    /**
     * Initializes DB path when starting as backup node.
     * <p>
     * Uses a local DB file name derived from the server identifier.
     */
    public synchronized void initDbPathAsBackupOnStartup() {
        if (this.dbPath != null) {
            return; // already chosen
        }

        String name = String.format("quiz-%s.db", serverIdShort);
        this.dbPath = dataDir.resolve(name).toAbsolutePath();
        Log.info(ServerManager.class,
                "[DB] BACKUP: BD local deste servidor será: %s", this.dbPath);
    }

    /**
     * Rebuilds the DB path using the current role (primary/backup) and short server id.
     * <p>
     * Note: currently not used on role change so that we keep the same DB file.
     */
    @SuppressWarnings("unused")
    private synchronized void refreshDbPath() {
        String role = isPrimary ? "primary" : "backup";
        String name = String.format("quiz-%s-%s.db", role, serverIdShort);

        this.dbPath = dataDir.resolve(name).toAbsolutePath();
        Log.info(ServerManager.class,
                "[DB] agora a usar: %s (role=%s, versão=%d)",
                this.dbPath, (isPrimary ? "PRIMARY" : "BACKUP"), dbVersion());
    }

    /* ======================= DB INITIALIZATION ======================= */

    /**
     * Lazily initializes the database layer (schema + DbCommands + services).
     * Safe to call multiple times.
     */
    @Override
    public void initDatabaseLayerIfNeeded() {
        if (dbInitialised) {
            return;
        }
        synchronized (this) {
            if (dbInitialised) {
                return;
            }
            try {
                // TODO Servidor principal: quando arranca, cria a base de dados se não existir (esquema, mas sem dados) ou utiliza a mais recente
                DbCreate.createIfMissing(this.dbPath, "/db/schema.sql");
                this.dbCommands = new DbCommands("jdbc:sqlite:" + this.dbPath.toAbsolutePath());
                IQuestionAnswerContext qaContext = this;

                this.authService = new AuthService(qaContext, dbCommands);
                this.questionService = new QuestionService(qaContext, dbCommands);
                this.answerService = new AnswerService(qaContext, dbCommands);

                dbInitialised = true;

                long v = dbVersion();
                Log.info(ServerManager.class,
                        "[DB] data layer initialized at %s (db_version=%d)", dbPath, v);
            } catch (Exception e) {
                Log.error(ServerManager.class,
                        "[DB] erro a inicializar a camada de dados: %s", e.getMessage());
                throw new RuntimeException("Falha a inicializar DB/DAOs/AuthService", e);
            }
        }
    }

    /* ======================= SERVICES ======================= */

    /** {@inheritDoc} */
    @Override
    public IAuthService getAuthService() {
        initDatabaseLayerIfNeeded();
        return authService;
    }

    /** {@inheritDoc} */
    @Override
    public IQuestionService getQuestionService() {
        initDatabaseLayerIfNeeded();
        return questionService;
    }

    /** {@inheritDoc} */
    @Override
    public IAnswerService getAnswerService() {
        initDatabaseLayerIfNeeded();
        return answerService;
    }

    /** {@inheritDoc} */
    @Override
    public DbCommands getDb() {
        initDatabaseLayerIfNeeded();
        return dbCommands;
    }

    /* ======================= IDENTITY / NETWORK CONFIG ======================= */

    /** {@inheritDoc} */
    @Override
    public String id() {
        return id;
    }

    /** {@inheritDoc} */
    @Override
    public String serverTcpIp() {
        return ip;
    }

    /** {@inheritDoc} */
    @Override
    public int serverTcpPort() {
        return clientPort;
    }

    /** {@inheritDoc} */
    @Override
    public int dbCopyPort() {
        return dbCopyPort;
    }

    /** {@inheritDoc} */
    @Override
    public String directoryHost() {
        return dirHost;
    }

    /** {@inheritDoc} */
    @Override
    public int directoryPort() {
        return dirPort;
    }

    /** {@inheritDoc} */
    @Override
    public String multicastGroup() {
        return DEFAULT_MULTICAST_GROUP;
    }

    /** {@inheritDoc} */
    @Override
    public int multicastPort() {
        return DEFAULT_MULTICAST_PORT;
    }

    /** {@inheritDoc} */
    @Override
    public NetworkInterface multicastInterface() {
        return multicastInterface;
    }

    /* ======================= GLOBAL STATE ======================= */

    /** {@inheritDoc} */
    @Override
    public boolean isRunning() {
        return running;
    }

    /** {@inheritDoc} */
    @Override
    public void shutdownServer() throws Exception {
        close();
    }

    /** {@inheritDoc} */
    @Override
    public long dbVersion() {
        try {
            // If the DB layer is not initialized yet, only auto-initialize when the DB file already exists.
            if (!dbInitialised || dbCommands == null) {
                Path p = this.dbPath;
                if (p == null || !Files.exists(p)) {
                    // DB not chosen or not created yet – version cannot be read.
                    return -1L;
                }
                // Safe to initialize the data layer using the existing DB file.
                initDatabaseLayerIfNeeded();
            }

            return (dbCommands != null) ? dbCommands.get_db_version() : -1L;
        } catch (Exception e) {
            Log.error(ServerManager.class,
                    "[DB] Failed to read db_version from database: %s", e.getMessage());
            return -1L;
        }
    }

    /** {@inheritDoc} */
    @Override
    public void setDbVersion(long v) {
        // Deprecated: DB version is no longer cached in memory; it is always read from the database.
        // Method kept only for backwards compatibility with older code paths.
    }

    /** {@inheritDoc} */
    @Override
    public Path dbPath() {
        return dbPath;
    }

    /* ======================= CLIENT CONNECTIONS ======================= */

    @Override
    public void sendToUser(long userId, pt.isec.common.messages.TcpMessage<?> msg) {
        NetworkTcpConnection conn = activeClientConnections.get(userId);
        if (conn == null) {
            Log.info(ServerManager.class,
                    "Sem ligação TCP registada para o utilizador %d, não foi possível enviar %s",
                    userId, msg.getType());
            return;
        }

        try {
            conn.sendMessage(msg);
        } catch (IOException e) {
            Log.error(ServerManager.class,
                    "Erro ao enviar %s para o utilizador %d: %s",
                    msg.getType(), userId, e.getMessage());
        }
    }

    /** {@inheritDoc} */
    @Override
    public void registerClientConnection(long userId, NetworkTcpConnection conn) {
        if (conn == null) {
            return;
        }
        activeClientConnections.put(userId, conn);
    }

    /** {@inheritDoc} */
    @Override
    public void unregisterClientConnection(long userId) {
        activeClientConnections.remove(userId);
    }

    /* ======================= REPLICATION / DB COPY ======================= */

    /** {@inheritDoc} */
    // TODO: Servidor principal: o acesso à base de dados local deve ser feito de forma atómica para garantir que, enquanto umthread executa uma query e divulga a operação através de um hearbeat , ou transfere o ficheiro para um servidorsecundário que arrancou, não existem outras threads a aceder à base de dados.
    @Override
    public boolean tryLockCopy() {
        return copying.compareAndSet(false, true);
    }

    /** {@inheritDoc} */
    @Override
    public void unlockCopy() {
        copying.set(false);
    }

    /** {@inheritDoc} */
    @Override
    public BlockingQueue<List<String>> queue() {
        return sqlToBroadcast;
    }


    /* ======================= PRIMARY/BACKUP ROLE ======================= */

    /** {@inheritDoc} */
    @Override
    public boolean isPrimary() {
        return isPrimary;
    }

    /** {@inheritDoc} */
    @Override
    public void setPrimary(String ip, int port) {
        boolean newIsPrimary = this.ip.equals(ip) && this.clientPort == port;
        if (this.isPrimary != newIsPrimary) {
            this.isPrimary = newIsPrimary;
            Log.info(ServerManager.class,
                    "Role alterado para %s", (isPrimary ? "PRIMARY" : "BACKUP"));
            // No refreshDbPath here – we keep using the same DB file
        }
    }

    /* ======================= LIFE CYCLE / THREADS ======================= */

    /**
     * Starts the main worker threads:
     * <ul>
     *   <li>Directory heartbeat</li>
     *   <li>Cluster heartbeat (multicast + DB copy)</li>
     *   <li>Client listener (TCP accept loop)</li>
     * </ul>
     */
    public void run() {
        tDirectoryHeartbeat = new Thread(new DirectoryHeartbeatThread(this), "directory-heartbeat");
        threadClusterHeartbeat = new Thread(new ClusterHeartbeatThread(this), "cluster-heartbeat");
        threadClientListener = new Thread(new ClientListenerThread(this), "client-listener");

        threadClusterHeartbeat.start();
        tDirectoryHeartbeat.start();
        threadClientListener.start();
    }

    /**
     * Performs a graceful shutdown:
     * <ol>
     *   <li>Signals global termination to all threads</li>
     *   <li>Reduces read timeouts of active TCP connections</li>
     *   <li>Waits for directory, cluster and client threads to finish</li>
     * </ol>
     *
     * @throws Exception if any close operation fails unexpectedly
     */
    public void close() throws Exception {
        // 1) global signal – all threads start terminating
        running = false;

        // 2) reduce read timeout of active TCP connections
        for (NetworkTcpConnection conn : activeClientConnections.values()) {
            try {
                conn.setReadTimeout(Duration.ofSeconds(1));
            } catch (IOException ignored) {
            }
        }

        // 3) wait for main threads to terminate (avoid joining the current thread)
        Thread current = Thread.currentThread();

        if (tDirectoryHeartbeat != null && current != tDirectoryHeartbeat) {
            try {
                tDirectoryHeartbeat.join();
            } catch (InterruptedException ignored) {
            }
        }

        if (threadClusterHeartbeat != null && current != threadClusterHeartbeat) {
            try {
                threadClusterHeartbeat.join();
            } catch (InterruptedException ignored) {
            }
        }

        if (threadClientListener != null && current != threadClientListener) {
            try {
                threadClientListener.join();
            } catch (InterruptedException ignored) {
            }
        }

        Log.info(ServerManager.class, "Shutdown completo do servidor.");
    }
}
