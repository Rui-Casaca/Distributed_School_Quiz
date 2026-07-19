package pt.isec.directory.threads;

import pt.isec.common.util.Log;
import pt.isec.directory.core.IDirectoryThreadContext;

/**
 * Periodic metrics/logging thread for the directory service.
 * <p>
 * Responsibilities:
 * <ul>
 *     <li>Periodically reads summary information from {@link IDirectoryThreadContext}</li>
 *     <li>Logs the number of known servers and which one is currently the primary</li>
 * </ul>
 * The thread runs while the directory is marked as running and then terminates
 * quietly when interrupted or when {@link IDirectoryThreadContext#isRunning()} becomes {@code false}.
 */
@SuppressWarnings("ClassCanBeRecord")
public class MetricsThread implements Runnable {

    /* ======================= FIELDS ======================= */

    /** Shared directory state and query methods. */
    private final IDirectoryThreadContext threadInfo;

    /** Interval between metric logs, in milliseconds. */
    private final long periodMs;

    /* ======================= CONSTRUCTOR ======================= */

    /**
     * Creates a new {@link MetricsThread}.
     *
     * @param threadInfo directory thread context providing metrics data
     * @param periodMs   log interval in milliseconds
     */
    public MetricsThread(IDirectoryThreadContext threadInfo, long periodMs) {
        this.threadInfo = threadInfo;
        this.periodMs = periodMs;
    }

    /* ======================= MAIN LOOP ======================= */

    /**
     * Periodically logs basic directory metrics while the directory is running.
     * <p>
     * The loop wakes up every {@code periodMs} milliseconds and logs:
     * <ul>
     *     <li>total number of registered servers</li>
     *     <li>identifier and TCP port of the current primary server (if any)</li>
     * </ul>
     * The thread stops when:
     * <ul>
     *     <li>{@link IDirectoryThreadContext#isRunning()} returns {@code false}, or</li>
     *     <li>the thread is interrupted</li>
     * </ul>
     */
    @Override
    public void run() {
        while (threadInfo.isRunning()) {
            try {
                String masterUuid = threadInfo.masterServerUuid();
                int totalServers = threadInfo.serversCount();

                ServerInfo masterInfo = null;
                int masterPort;
                String masterEndpoint = "NONE";

                if (masterUuid != null) {
                    masterInfo = threadInfo.servers().get(masterUuid);
                }

                if (masterInfo != null) {
                    masterPort = masterInfo.getTcpPort();
                    masterEndpoint = masterInfo.getIp() + ":" + masterPort;
                }

                Log.info(
                        MetricsThread.class,
                        "Directory metrics: servers=%d, primary=%s (id=%s)",
                        totalServers,
                        masterEndpoint,
                        masterUuid != null ? masterUuid : "NONE"
                );

                Thread.sleep(periodMs);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            } catch (Throwable t) {
                Log.error(MetricsThread.class, "Unexpected error in MetricsThread: " + t.getMessage(), t);
                try {
                    Thread.sleep(periodMs);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        Log.info(MetricsThread.class, "MetricsThread finished.");
    }
}
