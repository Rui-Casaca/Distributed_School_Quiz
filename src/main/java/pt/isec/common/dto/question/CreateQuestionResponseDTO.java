package pt.isec.common.dto.question;
import java.io.Serializable;

/**
 * Response returned after creating a question.
 *
 * @param questionId id of the created question
 * @param accessCode generated access code for students
 */
public record CreateQuestionResponseDTO(
        Integer questionId,
        String accessCode
) implements Serializable {}
