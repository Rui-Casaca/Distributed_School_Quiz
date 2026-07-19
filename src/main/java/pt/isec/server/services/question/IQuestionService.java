package pt.isec.server.services.question;

import pt.isec.common.dto.question.*;
import pt.isec.common.model.question.Question;

import java.util.List;

/**
 * Contract for question-related operations (CRUD + queries).
 */
public interface IQuestionService {

    /* ===================== CRUD ===================== */

    /**
     * Creates a new question.
     *
     * @param dto question data
     * @return response containing ID and access code
     * @throws Exception on validation or DB errors
     */
    CreateQuestionResponseDTO createQuestion(CreateQuestionDTO dto) throws Exception;

    /**
     * Edits an existing question.
     *
     * @param dto edit data
     * @return {@code true} if updated successfully
     * @throws Exception on validation or DB errors
     */
    boolean editQuestion(EditQuestionDTO dto) throws Exception;

    /**
     * Deletes a question.
     *
     * @param dto delete data
     * @return {@code true} if deleted successfully
     * @throws Exception on validation or DB errors
     */
    boolean deleteQuestion(DeleteQuestionDTO dto) throws Exception;


    /* ===================== QUERIES ===================== */

    /**
     * Lists questions for a teacher, with optional filter.
     *
     * @param dto list parameters
     * @return list of questions
     * @throws Exception on DB errors
     */
    List<Question> listQuestions(ListQuestionsDTO dto) throws Exception;

    /**
     * Joins a question using an access code.
     *
     * @param dto parameters containing the access code
     * @return the question, or {@code null} if not found
     * @throws Exception on DB errors
     */
    Question joinQuestion(JoinQuestionDTO dto) throws Exception;

}
