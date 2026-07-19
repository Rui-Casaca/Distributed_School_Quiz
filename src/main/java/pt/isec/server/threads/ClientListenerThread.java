package pt.isec.server.threads;

import pt.isec.server.core.IServerThreadContext;
import pt.isec.common.util.Log;

import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.List;

/**
 * Thread responsible for accepting TCP connections from clients and creating
 * a {@link ClientHandlerThread} for each session.
 */ //TODO thread dedicada à receção de pedidos de ligação dos cliente via TCP
public class ClientListenerThread implements Runnable, AutoCloseable {

    private static final int THREAD_POOL_SIZE = 8;
    private final ExecutorService pool = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

    private final IServerThreadContext threadInfo;
    private ServerSocket serverSocket;

    private final List<ClientHandlerThread> handlers = new CopyOnWriteArrayList<>();

    /**
     * Creates a new client listener thread.
     *
     * @param threadInfo server manager context
     */
    public ClientListenerThread(IServerThreadContext threadInfo) {
        this.threadInfo = threadInfo;
    }

    /**
     * Main accept loop:
     * <ul>
     *     <li>Opens a {@link ServerSocket} on the server TCP port</li>
     *     <li>Accepts client connections while the server is running</li>
     *     <li>Creates a {@link ClientHandlerThread} for each accepted socket</li>
     * </ul>
     */
    @Override
    public void run() {
        try {
            // Create the TCP socket that accepts client connections
            serverSocket = new ServerSocket(threadInfo.serverTcpPort());
            serverSocket.setSoTimeout(1000); // 1 second
            Log.info(ClientListenerThread.class,
                    "[ACCEPT] Listening for TCP client connections on port %d", threadInfo.serverTcpPort());

            // Main loop — accept clients while the server is running
            while (threadInfo.isRunning()) {
                try {
                    // TODO Servidor principal e secundários: thread dedicada à receção de pedidos de ligação dos clientes via TCP
                    Socket newSocket = serverSocket.accept(); //waits until accepts the new client
                    ClientHandlerThread handler =  //creates a thread to deal with the client
                            new ClientHandlerThread(threadInfo, new NetworkTcpConnection(newSocket));
                    handlers.add(handler);
                    pool.execute(handler);

                } catch (SocketTimeoutException e) {
                    // Normal timeout: loop again and check tInfo.isRunning()
                }
            }

        } catch (Exception e) {
            if (threadInfo.isRunning()) {
                Log.error(ClientListenerThread.class,
                        "[ACCEPT] Error in client accept loop: %s", e.getMessage());
            }
        } finally {
            Log.info(ClientListenerThread.class, "Client accept thread terminated.");
            try {
                close();
            } catch (Exception ignore) {
            }
        }
    }

    /**
     * Closes the server socket, all active client handlers and shuts down
     * the thread pool.
     *
     * @throws Exception if closing the server socket fails
     */
    @Override
    public void close() throws Exception {
        if (serverSocket != null) {
            serverSocket.close();
        }

        for (ClientHandlerThread h : handlers) {
            try {
                h.close();
            } catch (Exception ignored) {
            }
        }
        pool.shutdownNow(); // terminate all client threads
    }
}
