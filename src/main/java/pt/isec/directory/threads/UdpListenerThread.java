package pt.isec.directory.threads;
import pt.isec.common.messages.UdpMessage;
import pt.isec.common.util.Log;
import pt.isec.directory.core.IDirectoryThreadContext;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.net.SocketTimeoutException;

/**
 * UDP listener thread.
 * <p>
 * Responsibilities:
 * <ul>
 *     <li>Receive UDP datagrams from clients and servers</li>
 *     <li>Wrap them into {@link UdpMessage}</li>
 *     <li>Push them into the shared queue for worker threads</li>
 * </ul>
 * <p>
 * Protocol (text, {@code KEY=VALUE} pairs separated by {@code '|'}):
 * <p>
 * <b>Client messages (no VER):</b>
 * <ul>
 *     <li>{@code TYPE=LOGIN}</li>
 * </ul>
 * <p>
 * <b>Server messages:</b>
 * <ul>
 *     <li>{@code TYPE=REGISTER   | ID=&lt;serverId&gt; | TCP=&lt;ip:port&gt; | DBV=&lt;dbVersion&gt;}</li>
 *     <li>{@code TYPE=HEARTBEAT  | ID=&lt;serverId&gt; | DBV=&lt;dbVersion&gt;}</li>
 *     <li>{@code TYPE=DEREGISTER | ID=&lt;serverId&gt;}</li>
 * </ul>
 * <p>
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
@SuppressWarnings("ClassCanBeRecord")
public class UdpListenerThread implements Runnable {

    /* ======================= FIELDS ======================= */

    private final IDirectoryThreadContext threadInfo;

    /* ======================= CONSTRUCTOR ======================= */

    /**
     * Creates a new UDP listener bound to the given directory context.
     *
     * @param threadInfo directory thread context providing socket, configuration and queue
     */
    public UdpListenerThread(IDirectoryThreadContext threadInfo) {
        this.threadInfo = threadInfo;
    }

    /* ======================= MAIN LOOP ======================= */

    /**
     * Main listening loop:
     * <ul>
     *     <li>Blocks on {@link DatagramSocket#receive(DatagramPacket)}</li>
     *     <li>Copies the received bytes into a fresh array</li>
     *     <li>Enqueues a {@link UdpMessage} in the shared queue</li>
     * </ul>
     * Socket and queue errors are logged; on shutdown the loop exits cleanly.
     */
    @Override
    public void run() {
        DatagramSocket socket = threadInfo.socket();
        Log.info(UdpListenerThread.class,
                "Directoria UDP a escutar na porta %d...", threadInfo.udpPort());

        byte[] buffer = new byte[threadInfo.maxPacketSize()];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

        while (threadInfo.isRunning()) {
            try {
                // TODO Servidores secundários (e secundários): thread dedicada à receção de datagramas UDP
                // Block until a UDP datagram is received
                socket.receive(packet);
                //Byte array to recive payload
                byte[] data = new byte[packet.getLength()];
                // Copy received bytes from the packet's buffer into the new array.
                // Uses packet.getOffset() because the valid data may start at a non‑zero offset.
                System.arraycopy(packet.getData(), packet.getOffset(), data, 0, packet.getLength());

                // Wrap the sender info and the copied payload into a UdpMessage and put it
                // into the shared blocking queue.
                threadInfo.queue().put(
                        new UdpMessage(packet.getAddress(), packet.getPort(), data, data.length)
                );

            } catch (SocketTimeoutException ste) {
                // Socket configured with timeout; simply re-check loop condition and continue.
            } catch (SocketException se) {
                // Socket intentionally closed on shutdown will cause SocketException here.
                if (!threadInfo.isRunning() || socket.isClosed()) {
                    // Ordered shutdown – exit loop quietly.
                    break;
                }
                Log.error(UdpListenerThread.class,
                        "Erro de socket UDP: " + se.getMessage());
                pauseAfterError();
            } catch (IOException e) {
                if (threadInfo.isRunning()) {
                    Log.error(UdpListenerThread.class,
                            "Erro a receber UDP: " + e.getMessage());
                }
                pauseAfterError();
            } catch (InterruptedException ie) {
                // Thread interrupted while blocked on queue.put().
                Thread.currentThread().interrupt();
                break;
            }
        }
        Log.info(UdpListenerThread.class, "UdpListenerThread terminou.");
    }

    /* ======================= INTERNAL HELPERS ======================= */

    /**
     * Short pause used after non-fatal socket/IO errors to avoid tight spinning.
     */
    private static void pauseAfterError() {
        try {
            Thread.sleep(50);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
