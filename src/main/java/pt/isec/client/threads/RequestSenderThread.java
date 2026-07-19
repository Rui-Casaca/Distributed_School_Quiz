package pt.isec.client.threads;
import pt.isec.client.core.IClientThreadContext;
import pt.isec.common.messages.TcpMessage;
import pt.isec.common.util.Log;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/**
 * Thread that sends messages from the request queue to the server over TCP.
 */
@SuppressWarnings("ClassCanBeRecord")
public class RequestSenderThread implements Runnable {

    private final IClientThreadContext service;

    /**
     * Creates a new request sender thread.
     *
     * @param service client service interface
     */
    public RequestSenderThread(IClientThreadContext service) {
        this.service = service;
    }

    @Override
    public void run() {
        Log.info(RequestSenderThread.class, "Started sending requests...");

        while (service.isRunning()) {
            TcpMessage<? extends Serializable> request = null;
            try {
                // Blocks until there is a request to send
                request = service.getRequestQueue().take();

                ObjectOutputStream out = service.getOutputStream();
                if (out == null) {
                    // No valid stream — try to reconnect and requeue the request
                    Log.error(RequestSenderThread.class,
                            "No output stream available, requeueing request: " + request.getType());
                    service.getRequestQueue().put(request);
                    service.handleConnectionLost();
                    break;
                }

                Log.info(RequestSenderThread.class, "Sending: " + request.getType());
                out.writeObject(request);
                out.flush();
            } catch (IOException e) {
                if (service.isRunning()) {
                    Log.error(RequestSenderThread.class, "Failed to send: " + e.getMessage());
                    try {
                        if (request != null) {
                            service.getRequestQueue().put(request);
                        }
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                    service.handleConnectionLost();
                }
                break;
            } catch (InterruptedException e) {
                Log.warn(RequestSenderThread.class, "Interrupted while sending requests");
                Thread.currentThread().interrupt();
                break;
            }
        }

        Log.info(RequestSenderThread.class, "Stopped sending requests");
    }
}
