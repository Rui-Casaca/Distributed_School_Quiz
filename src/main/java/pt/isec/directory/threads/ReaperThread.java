package pt.isec.directory.threads;
import pt.isec.common.util.Log;
import pt.isec.directory.core.IDirectoryThreadContext;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

/**
 * Periodic reaper thread for the directory.
 * <p>
 * Responsibilities:
 * <ul>
 *     <li>Periodically removes inactive servers based on a time-to-live (TTL) policy</li>
 *     <li>On shutdown, sends a {@code SHUTDOWN} UDP message to all known servers</li>
 *     <li>Closes the shared UDP socket so that the listener thread can terminate</li>
 * </ul>
 */
@SuppressWarnings("ClassCanBeRecord")
public class ReaperThread implements Runnable {

    /* ======================= FIELDS ======================= */

    /** Access to shared directory state and resources. */
    private final IDirectoryThreadContext threadInfo;

    /** Interval between reaper sweeps, in milliseconds. */
    private final long periodMs;

    /* ======================= CONSTRUCTOR ======================= */

    /**
     * Creates a new {@link ReaperThread}.
     *
     * @param threadInfo directory thread context (shared state and socket)
     * @param periodMs   interval between sweeps in milliseconds
     */
    public ReaperThread(IDirectoryThreadContext threadInfo, long periodMs) {
        this.threadInfo = threadInfo;
        this.periodMs = periodMs;
    }

    /* ======================= MAIN LOOP ======================= */

    /**
     * Periodically removes expired servers while the directory is running.
     * <p>
     * When the loop finishes (because the directory is stopping), this method:
     * <ol>
     *     <li>Sends a {@code SHUTDOWN} message to all registered servers</li>
     *     <li>Closes the UDP socket so that the listener thread is unblocked</li>
     * </ol>
     */
    @Override
    public void run() {
        try {
            while (threadInfo.isRunning()) {
                long now = System.currentTimeMillis();

                // Ask the context to purge servers that exceeded TTL
                threadInfo.removeServersFromList(now);

                // Periodic sweep delay
                Thread.sleep(periodMs);
            }
        } catch (InterruptedException ie) {
            // Interrupt is not used for normal shutdown; if it happens, just stop the thread
            Thread.currentThread().interrupt();
        } catch (Throwable t) {
            Log.error(ReaperThread.class, "Unexpected error in ReaperThread: " + t.getMessage());
        } finally {
            // Directory is shutting down: send SHUTDOWN to all servers and close the socket.
            DatagramSocket socket = threadInfo.socket();
            if (socket != null && !socket.isClosed()) {
                for (ServerInfo s : new ArrayList<>(threadInfo.servers().values())) {
                    try {
                        String text = "SHUTDOWN";
                        byte[] out = text.getBytes(StandardCharsets.UTF_8);
                        DatagramPacket dp = new DatagramPacket(
                                out,
                                out.length,
                                InetAddress.getByName(s.getIp()),
                                s.getUdpPort()
                        );
                        socket.send(dp);
                    } catch (IOException e) {
                        Log.error(
                                ReaperThread.class,
                                "Failed to send SHUTDOWN to " + s.getIp() + ":" + s.getUdpPort()
                                        + " – " + e.getMessage()
                        );
                    }
                }

                // Closing the socket here will cause a SocketException in UdpListenerThread.receive()
                // which allows that thread to exit its loop.
                socket.close();
            }

            Log.info(ReaperThread.class, "Reaper thread finished.");
        }
    }
}
