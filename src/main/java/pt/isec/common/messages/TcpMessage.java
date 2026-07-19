package pt.isec.common.messages;
import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * Generic wrapper for messages exchanged over TCP between clients and servers.
 *
 * @param <T> payload type (must be {@link Serializable})
 */
public class TcpMessage<T extends Serializable> implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /** Type of the message (LOGIN, REGISTER, etc.). */
    private MessageType msgType;

    /** Payload carried by this message. */
    private T data;

    /** Runtime class of the payload (e.g., {@code RegisterStudentDTO.class}). */
    private Class<T> payloadType;

    /**
     * Default constructor for serialization frameworks.
     */
    public TcpMessage() {}

    /**
     * Creates a message with type and payload.
     *
     * @param msgType message type
     * @param data    payload object
     */
    public TcpMessage(MessageType msgType, T data) {
        this.msgType = msgType;
        this.data = data;
    }

    /**
     * Creates a message with type, payload and explicit payload type.
     *
     * @param msgType     message type
     * @param data        payload object
     * @param payloadType runtime type of the payload
     */
    public TcpMessage(MessageType msgType, T data, Class<T> payloadType) {
        this.msgType = msgType;
        this.data = data;
        this.payloadType = payloadType;
    }

    public MessageType getType() {
        return msgType;
    }

    /**
     * Because Java uses type erasure for generics, at runtime we cannot know
     * the real type of {@code T}. When the server receives a {@link TcpMessage},
     * it may want to cast the payload to a specific DTO class.
     * <p>
     * This helper:
     * <ul>
     *     <li>Receives the expected class (e.g. {@code RegisterStudentDTO.class})</li>
     *     <li>Performs the cast via {@link Class#cast(Object)}</li>
     *     <li>Throws {@link ClassCastException} if the type is not compatible</li>
     * </ul>
     *
     * @param expected expected payload type
     * @param <U>      concrete type
     * @return payload cast to {@code U}
     */
    public <U> U getDataAs(Class<U> expected) {
        return expected.cast(data);
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TcpMessage<?> messages = (TcpMessage<?>) o;
        return msgType == messages.msgType && Objects.equals(data, messages.data);
    }

    @Override
    public int hashCode() {
        return Objects.hash(msgType, data);
    }

    @Override
    public String toString() {
        return "Messages{" +
                "msgType=" + msgType +
                ", data=" + data +
                '}';
    }
}
