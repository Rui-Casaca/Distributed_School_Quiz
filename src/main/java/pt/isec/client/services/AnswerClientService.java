package pt.isec.client.services;

import pt.isec.client.core.ClientManager;
import pt.isec.client.core.IClientServiceContext;
import pt.isec.common.dto.answer.SubmitAnswerDTO;
import pt.isec.common.dto.answer.ViewAnswersDTO;
import pt.isec.common.messages.TcpMessage;
import pt.isec.common.messages.MessageType;

/**
 * Client-side service for answer-related operations.
 * <p>
 * All methods enqueue a request message; responses are handled by
 * {@code ResponseHandlerThread} and propagated as events by
 * {@link ClientManager}.
 */
@SuppressWarnings("ClassCanBeRecord")
public class AnswerClientService {

    private final IClientServiceContext service;

    /**
     * Creates a new answer client service.
     *
     * @param service underlying client service
     */
    public AnswerClientService(IClientServiceContext service) {
        this.service = service;
    }

    /**
     * Submits a student's answer for a question.
     *
     * @param dto answer payload
     */
    // TODO Estudante: submissão da resposta associada a uma pergunta visualizada, dentro do seu período de validade
    public void submitAnswer(SubmitAnswerDTO dto) {
        try {
            service.getRequestQueue().put(new TcpMessage<>(MessageType.SUBMIT_ANSWER, dto));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Lists answers submitted to a question (teacher view).
     *
     * @param dto view answers payload
     */
    // TODO Estudante: consulta das perguntas expiradas respondidas, podendo ser aplicados filtros de pesquisa
    public void viewAnswersForTeacher(ViewAnswersDTO dto) {
        try {
            service.getRequestQueue().put(new TcpMessage<>(MessageType.VIEW_ANSWERS, dto));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Lists the answer history of a student (for expired questions).
     *
     * @param studentId student identifier
     */
    public void viewAnswersForStudent(Integer studentId) {
        try {
            service.getRequestQueue().put(new TcpMessage<>(MessageType.LIST_ANSWERED_QUESTIONS, studentId));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
