package pt.isec.client.core;

import pt.isec.common.dto.auth.AuthResponseDTO;
import pt.isec.common.dto.question.CreateQuestionResponseDTO;
import pt.isec.common.messages.TcpMessage;
import pt.isec.common.model.question.Answer;
import pt.isec.common.model.question.Question;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.List;
import java.util.concurrent.BlockingQueue;

/**
 * Context interface used by networking threads.
 * <p>
 * Provides access to:
 * <ul>
 *     <li>TCP streams</li>
 *     <li>Request/response queues</li>
 *     <li>Lifecycle flags</li>
 *     <li>Event callbacks used by {@code ResponseHandlerThread}</li>
 * </ul>
 */
public interface IClientThreadContext extends IClientServiceContext{

    /**
     * Handles a lost-connection event and typically starts a reconnection flow.
     */
    void handleConnectionLost();

    /**
     * Indicates whether the client runtime is active.
     *
     * @return {@code true} if threads should keep running
     */
    boolean isRunning();

    /**
     * Gets the current TCP output stream to the server.
     *
     * @return output stream or {@code null} if not connected
     */
    ObjectOutputStream getOutputStream();

    /**
     * Gets the current TCP input stream from the server.
     *
     * @return input stream or {@code null} if not connected
     */
    ObjectInputStream getInputStream();

    /**
     * Returns the response queue used by {@code ClientListenerThread}
     * and consumed by {@code ResponseHandlerThread}.
     *
     * @return incoming TCP messages queue
     */
    BlockingQueue<TcpMessage<? extends Serializable>> getResponseQueue();

    /* ==================== Events used by ResponseHandlerThread ==================== */

    // AUTH EVENTS

    void setPropLoginOk(AuthResponseDTO dto);

    void setPropError(String s);

    void setPropRegisterOk(AuthResponseDTO dto);

    // QUESTION / ANSWER EVENTS

    void setPropCreateQuestionResponse(CreateQuestionResponseDTO dto);

    void setPropCreateQuestionError(String msg);

    void setPropEditQuestionResponse(String message);

    void setPropEditQuestionError(String message);

    void setPropListQuestionsResponse(List<Question> questions);

    void setPropJoinQuestionResponse(Question question);

    void setPropSubmitAnswerOk(String message);

    void setPropSubmitAnswerFail(String message);

    void setPropViewAnswersResponse(List<Answer> answers);

    void setPropListAnsweredResponse(List<Answer> answers);

    void setPropAnswerSubmitted(Integer questionId);

    void setPropDeleteQuestionResponse(String message);

    void setPropUpdateProfileOk(AuthResponseDTO dto);

    void setPropUpdateProfileFail(String message);
}
