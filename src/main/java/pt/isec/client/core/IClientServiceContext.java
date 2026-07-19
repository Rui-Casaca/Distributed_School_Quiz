package pt.isec.client.core;

import pt.isec.common.messages.TcpMessage;

import java.io.Serializable;
import java.util.concurrent.BlockingQueue;

/**
 * Minimal context interface used by client-side services.
 * <p>
 * Services only need to enqueue requests to be sent by the
 * {@code RequestSenderThread}.
 */
public interface IClientServiceContext {

    /**
     * Returns the request queue where services enqueue messages
     * to be sent to the server.
     *
     * @return outgoing TCP messages queue
     */
    BlockingQueue<TcpMessage<? extends Serializable>> getRequestQueue();
}
