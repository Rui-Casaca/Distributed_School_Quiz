package pt.isec.server.core;

import pt.isec.common.messages.TcpMessage;

import java.util.List;
import java.util.concurrent.BlockingQueue;

/**
 * Minimal context shared by question/answer services to interact with the cluster and clients.
 */
public interface IQuestionAnswerContext {

    /* ===================== CLUSTER SYNCHRONIZATION ===================== */

    /**
     * Returns the queue used to send SQL batches for replication to backup nodes.
     *
     * @return queue with SQL batches
     */
    BlockingQueue<List<String>> queue();


    /* ===================== USER MANAGEMENT / NOTIFICATIONS ===================== */

    /**
     * Sends a message to the client associated with the given user id, if connected.
     *
     * @param userId user identifier
     * @param msg    message to send
     */
    void sendToUser(long userId, TcpMessage<?> msg);
}
