package pt.isec.server;
import org.fusesource.jansi.AnsiConsole;
import pt.isec.common.util.Log;
import pt.isec.server.core.ServerManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Entry point for the server application.
 * <p>
 * Expects the following command-line arguments:
 * <pre>
 *   &lt;dirHost&gt; &lt;dirPort&gt; &lt;dataDir|PROJECT|HOME&gt; &lt;multicastIfIp|AUTO&gt; &lt;clientPort&gt; &lt;dbCopyPort&gt;
 * </pre>
 */
public class MainServer {

    /**
     * Private constructor to prevent instantiation.
     * <p>
     * This class is a pure bootstrap utility with a single {@code main} method.
     */
    private MainServer() {}

    /**
     * Starts the server, configures the directory service, data folder and
     * registers a shutdown hook.
     *
     * @param args command-line arguments:
     *             {@code dirHost}, {@code dirPort}, {@code dataDir|PROJECT|HOME},
     *             {@code multicastIfIp|AUTO}, {@code clientPort}, {@code dbCopyPort}
     * @throws Exception if an error occurs during initialization
     */
    public static void main(String[] args) throws Exception {
        // Install ANSI colors in the console
        AnsiConsole.systemInstall();

        if (args.length != 6) {
            Log.error(MainServer.class,
                    "Usage: <dirHost> <dirPort> <dataDir|PROJECT|HOME> <multicastIfIp|AUTO> <clientPort> <dbCopyPort>");
            return;
        }
        Class.forName("org.sqlite.JDBC");

        String dirHost = args[0];
        int dirPort = Integer.parseInt(args[1]);
        String dataArg = args[2];
        String multicastInterfaceIp = args[3];
        int clientPort = Integer.parseInt(args[4]);
        int dbCopyPort = Integer.parseInt(args[5]);

        // Portable data folder
        Path dataDir;
        if ("PROJECT".equalsIgnoreCase(dataArg)) {
            // Put the "data" folder at the same level as "batchFiles"
            // user.dir: folder where the Java command was executed (here, batchFiles)
            Path here = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
            Path project = here.getParent(); // one level up
            if (project == null) {
                project = here; // fallback
            }
            dataDir = project.resolve("data").toAbsolutePath();
        } else if ("HOME".equalsIgnoreCase(dataArg)) {
            // noinspection SpellCheckingInspection
            dataDir = Paths.get(System.getProperty("user.home"), "quizdb").toAbsolutePath();
        } else {
            dataDir = Paths.get(dataArg).toAbsolutePath();
        }

        // Ensure data directory exists
        Files.createDirectories(dataDir);

        Log.info(MainServer.class, "[DB] dir : %s", dataDir);
        Log.info(MainServer.class, "[DB] file: %s (exists=%s)", dataDir, Files.exists(dataDir));

        // Do not use try-with-resources here, we want the server to stay alive
        ServerManager serverManager =
                new ServerManager(dirHost, dirPort, multicastInterfaceIp, clientPort, dbCopyPort, dataDir);

        // Catch CTRL+C
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                serverManager.shutdownServer();
            } catch (Exception ignored) {
            }
            Log.info(MainServer.class, "Server shutdown completed.");
        }));

        serverManager.run();
        Log.info(MainServer.class, "Server started. Use CTRL+C to terminate.");
    }
}
