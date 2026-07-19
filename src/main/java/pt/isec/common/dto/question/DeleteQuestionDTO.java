package pt.isec.common.dto.question;
import java.io.Serializable;

/**
 * Request to delete a question.
 *
 * @param questionId id of the question
 * @param teacherId  teacher identifier
 */
public record DeleteQuestionDTO(
        Integer questionId,
        Integer teacherId
) implements Serializable {}
