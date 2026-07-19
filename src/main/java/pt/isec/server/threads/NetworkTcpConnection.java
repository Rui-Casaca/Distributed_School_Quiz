package pt.isec.server.threads;
import pt.isec.common.messages.TcpMessage;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;

/**
 * Abstraction over a TCP connection between server and client.
 * <p>
 * Uses {@link ObjectInputStream} and {@link ObjectOutputStream} to send and receive
 * {@link TcpMessage} instances, and also allows sending/receiving raw binary data
 * through the same stream.
 */
public class NetworkTcpConnection implements AutoCloseable {
    private static final int BUFFER_SIZE = 64 * 1024;
    private static final int MAX_INT_TIMEOUT = Integer.MAX_VALUE;

    private final Socket socket;
    private final ObjectOutputStream out;
    private final ObjectInputStream in;

    /**
     * Creates a new connection wrapper for an already connected socket.
     *
     * @param socket connected TCP socket
     * @throws IOException if object streams cannot be created
     */
    public NetworkTcpConnection(Socket socket) throws IOException {
        this.socket = socket;
        this.out = new ObjectOutputStream(socket.getOutputStream());
        this.in = new ObjectInputStream(socket.getInputStream());
    }

    /**
     * Establishes a new TCP connection to the given host and port with a connection timeout.
     *
     * @param host    target host
     * @param port    target port
     * @param timeout maximum time to establish the connection
     * @return a connected {@link NetworkTcpConnection}
     * @throws IOException if the connection fails or times out
     */
    public static NetworkTcpConnection connect(String host, int port, Duration timeout) throws IOException {
        Socket s = new Socket();
        int to = (int) Math.min(MAX_INT_TIMEOUT, Math.max(0, timeout.toMillis()));
        s.connect(new InetSocketAddress(host, port), to);
        return new NetworkTcpConnection(s);
    }

    /**
     * Sets the socket read timeout (SO_TIMEOUT).
     *
     * @param timeout maximum blocking time for read operations
     * @throws IOException if socket configuration fails
     */
    public void setReadTimeout(Duration timeout) throws IOException {
        int to = (int) Math.min(MAX_INT_TIMEOUT, Math.max(0, timeout.toMillis()));
        socket.setSoTimeout(to);
    }

    /**
     * Sends a serializable {@link TcpMessage} through the {@link ObjectOutputStream}.
     *
     * @param tcpMessage message to send
     * @param <T>        type of payload carried by the message
     * @throws IOException if a write error occurs
     */
    public <T extends Serializable> void sendMessage(TcpMessage<T> tcpMessage) throws IOException {
        out.writeObject(tcpMessage);
        out.flush();
        out.reset();
    }

    /**
     * Receives a {@link TcpMessage} from the {@link ObjectInputStream}.
     *
     * @return the received message
     * @throws IOException            if a read error occurs
     * @throws ClassNotFoundException if the message type cannot be resolved
     */
    public TcpMessage<?> receiveMessage() throws IOException, ClassNotFoundException {
        return (TcpMessage<?>) in.readObject();
    }

    /* ===== primitive types and binary stream using the SAME ObjectStream ===== */

    /**
     * Writes a {@code long} value to the {@link ObjectOutputStream}.
     *
     * @param v value to write
     * @throws IOException if a write error occurs
     */
    public void writeLong(long v) throws IOException {
        out.writeLong(v);
        out.flush();
        out.reset();
    }

    /**
     * Reads a {@code long} value from the {@link ObjectInputStream}.
     *
     * @return the value read
     * @throws IOException if a read error occurs
     */
    public long readLong() throws IOException {
        return in.readLong();
    }

    /**
     * Sends exactly {@code size} bytes from the given {@link InputStream}
     * using the same {@link ObjectOutputStream}.
     *
     * @param src  source stream
     * @param size number of bytes to send
     * @return total bytes actually sent
     * @throws IOException if a read or write error occurs
     */
    public long sendStreamViaObjectOut(InputStream src, long size) throws IOException {
        try (src) {
            byte[] buf = new byte[BUFFER_SIZE];
            long sent = 0;
            int read;
            while (sent < size &&
                    (read = src.read(buf, 0, (int) Math.min(buf.length, size - sent))) >= 0) {
                out.write(buf, 0, read);
                sent += read;
            }
            out.flush();
            out.reset();
            return sent;
        }
    }

    /**
     * Reads exactly {@code size} bytes from the same {@link ObjectInputStream}
     * and writes them to {@code dst}.
     *
     * @param dst  destination stream
     * @param size expected number of bytes
     * @return total bytes actually received
     * @throws IOException if a read error occurs or the stream ends prematurely
     */
    public long receiveExactly(OutputStream dst, long size) throws IOException {
        try (dst) {
            byte[] buf = new byte[BUFFER_SIZE];
            long got = 0;
            while (got < size) {
                int want = (int) Math.min(buf.length, size - got);
                int read = in.read(buf, 0, want);
                if (read < 0) {
                    throw new EOFException("Stream ended before receiving the expected number of bytes");
                }
                dst.write(buf, 0, read);
                got += read;
            }
            dst.flush();
            return got;
        }
    }

    /* ===== legacy methods (kept for compatibility) ===== */

    /**
     * Returns the underlying {@link Socket} instance.
     *
     * @return the underlying socket
     */
    public Socket socket() {
        return socket;
    }

    /**
     * Closes the object streams and the underlying socket.
     *
     * @throws IOException if closing the socket fails
     */
    @Override
    public void close() throws IOException {
        try {
            out.close();
        } catch (Exception ignore) {
        }
        try {
            in.close();
        } catch (Exception ignore) {
        }
        try {
            socket.close();
        } catch (Exception ignore) {
        }
    }
}
