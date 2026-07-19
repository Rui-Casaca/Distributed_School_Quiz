package pt.isec.common.dto.question;
import java.io.Serializable;

/**
 * Request for a student to join a question using an access code.
 *
 * @param accessCode code associated with the question
 * @param studentId  student identifier
 */
public record JoinQuestionDTO(
        String accessCode,
        Integer studentId
) implements Serializable {}
