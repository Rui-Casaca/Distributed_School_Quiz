package pt.isec.client.threads;
import pt.isec.client.core.IClientThreadContext;
import pt.isec.common.messages.TcpMessage;
import pt.isec.common.util.Log;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;

/**
 * Thread that continuously listens for messages from the server over TCP
 * and enqueues them into the response queue to be processed.
 */
@SuppressWarnings("ClassCanBeRecord")
public class ClientListenerThread implements Runnable {

    private final IClientThreadContext service;

    /**
     * Creates a new client listener thread.
     *
     * @param service client service interface
     */
    public ClientListenerThread(IClientThreadContext service) {
        this.service = service;
    }

    @Override
    public void run() {
        Log.info(ClientListenerThread.class, "Listening for messages from the server...");

        while (service.isRunning()) {
            try {
                ObjectInputStream in = service.getInputStream(); //ready to read
                if (in == null) {
                    // No valid stream (e.g. during reconnection); wait a bit
                    Thread.sleep(200);
                    continue;
                }

                // TODO Cliente: thread nos clientes dedicada à receção de notificações assíncronas do servidor principal, via TCP
                TcpMessage<? extends Serializable> response =
                        (TcpMessage<? extends Serializable>) in.readObject(); //waits to receive msg

                if (response != null) {
                    Log.info(ClientListenerThread.class, "Received: " + response.getType());
                    service.getResponseQueue().put(response);
                }
            } catch (IOException e) {
                if (service.isRunning()) {
                    Log.error(ClientListenerThread.class, "Connection lost: " + e.getMessage());
                    service.handleConnectionLost();
                }
                break;
            } catch (ClassNotFoundException e) {
                Log.error(ClientListenerThread.class,
                        "Unknown message type received: " + e.getMessage());
            } catch (InterruptedException e) {
                Log.warn(ClientListenerThread.class, "Listener interrupted");
                Thread.currentThread().interrupt();
                break;
            }
        }

        Log.info(ClientListenerThread.class, "Listener stopped");
    }
}
