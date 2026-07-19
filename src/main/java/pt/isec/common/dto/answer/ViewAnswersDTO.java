package pt.isec.common.dto.answer;
import java.io.Serializable;

/**
 * Request for a teacher to view all answers to a question.
 *
 * @param questionId id of the question
 * @param teacherId  teacher identifier
 */
public record ViewAnswersDTO(
        Integer questionId,
        Integer teacherId
) implements Serializable {}
