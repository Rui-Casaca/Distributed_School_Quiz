package pt.isec.directory;
import org.fusesource.jansi.AnsiConsole;
import pt.isec.common.util.Log;
import pt.isec.directory.core.DirectoryManager;

/**
 * Entry point for the Directory service.
 * <p>
 * It listens for UDP messages from clients and quiz servers, keeps track of
 * available servers and designates a primary one.
 */
public class MainDirectory {

    /* ===================== DEFAULT CONFIGURATION ===================== */

    /** Default UDP port used by the directory. */
    private static final int DEF_UDP_PORT = 9999;

    /** Default queue capacity for incoming requests. */
    private static final int DEF_QUEUE = 1024;

    /** Default maximum UDP packet size. */
    private static final int DEF_MAX_PKT = 65535;

    /** Default time-to-live (in milliseconds) for server entries. */
    private static final long DEF_TTL_MS = 17_000L;

    /* ============================== MAIN ============================== */

    /**
     * Starts the directory with optional command line arguments:
     * <pre>
     *   java MainDirectory
     *   java MainDirectory &lt;udpPort&gt;
     *   java MainDirectory &lt;udpPort&gt; &lt;queueCapacity&gt; &lt;maxPacketSize&gt; &lt;ttlMillis&gt;
     * </pre>
     *
     * @param args command line arguments
     */
    public static void main(String[] args) {
        // Enable colored output in the console
        AnsiConsole.systemInstall();

        int udpPort       = DEF_UDP_PORT;
        int queueCapacity = DEF_QUEUE;
        int maxPacketSize = DEF_MAX_PKT;
        long ttlMillis    = DEF_TTL_MS;

        try {
            switch (args.length) {
                case 0 -> { /* all defaults */ }
                case 1 -> udpPort       = parseIntOr(args[0], DEF_UDP_PORT);
                case 4 -> {
                    udpPort       = parseIntOr(args[0], DEF_UDP_PORT);
                    queueCapacity = parseIntOr(args[1], DEF_QUEUE);
                    maxPacketSize = parseIntOr(args[2], DEF_MAX_PKT);
                    ttlMillis     = parseLongOr(args[3]);
                }
                default -> {
                    Log.info(MainDirectory.class, """
                        Uso:
                          java MainDirectory
                          java MainDirectory <udpPort>
                          java MainDirectory <udpPort> <queueCapacity> <maxPacketSize> <ttlMillis>
                        """.trim());
                    Log.info(MainDirectory.class, "(Invalid arguments: starting with default values.)");
                }
            }

            // Light validation (fallback to defaults when invalid)
            udpPort       = (udpPort >= 1 && udpPort <= 65535) ? udpPort : DEF_UDP_PORT;
            queueCapacity = (queueCapacity > 0) ? queueCapacity : DEF_QUEUE;
            // 65535 is UDP maximum; accept something reasonable (>=512) to avoid tiny packets
            maxPacketSize = (maxPacketSize >= 512 && maxPacketSize <= 65535) ? maxPacketSize : DEF_MAX_PKT;
            ttlMillis     = (ttlMillis > 0) ? ttlMillis : DEF_TTL_MS;

            Log.info(MainDirectory.class,
                    "=== Directory ===%nUDP:%d | queue:%d | maxPkt:%d | TTL(ms):%d",
                    udpPort, queueCapacity, maxPacketSize, ttlMillis
            );

            DirectoryManager ds = new DirectoryManager(udpPort, queueCapacity, maxPacketSize);
            // If your DirectoryService supports TTL as a parameter, wire it here.

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    ds.stop();
                } catch (Exception ignored) {
                }
                Log.info(MainDirectory.class, "Diretoria terminated.");
            }));

            ds.run();
            Log.info(MainDirectory.class, "Diretoria is running. CTRL+C to shutdown.");
        } catch (Exception e) {
            Log.error(MainDirectory.class, "Error starting the directory: " + e.getMessage());
        }
    }

    /* ========================== HELPERS ========================== */

    /**
     * Parses an integer or returns a default value if parsing fails.
     *
     * @param s   string to parse
     * @param def default value
     * @return parsed int or {@code def} on error
     */
    private static int parseIntOr(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return def;
        }
    }

    /**
     * Parses a long or returns {@link #DEF_TTL_MS} if parsing fails.
     *
     * @param s string to parse
     * @return parsed long or {@link #DEF_TTL_MS} on error
     */
    private static long parseLongOr(String s) {
        try {
            return Long.parseLong(s.trim());
        } catch (Exception e) {
            return DEF_TTL_MS;
        }
    }
}
