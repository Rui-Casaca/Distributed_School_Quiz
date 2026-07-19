package pt.isec.server.services.question;

import pt.isec.common.dto.answer.SubmitAnswerDTO;
import pt.isec.common.dto.answer.ViewAnswersDTO;
import pt.isec.common.model.question.Answer;

import java.util.List;

/**
 * Contract for answer-related operations.
 */
public interface IAnswerService {

    /* ===================== ANSWERS ===================== */

    /**
     * Submits an answer for a question.
     *
     * @param dto submission data
     * @return {@code true} if successfully recorded
     * @throws Exception on validation or DB errors
     */
    boolean submitAnswer(SubmitAnswerDTO dto) throws Exception;

    /**
     * Returns the answers to a question (teacher view).
     *
     * @param dto query parameters
     * @return list of answers
     * @throws Exception on DB errors
     */
    List<Answer> viewAnswers(ViewAnswersDTO dto) throws Exception;

    /* ===================== HISTORY ===================== */

    /**
     * Returns the answer history for a student.
     *
     * @param studentId student ID
     * @return list of answers for that student
     * @throws Exception on DB errors
     */
    List<Answer> getStudentHistory(Integer studentId) throws Exception;
}
