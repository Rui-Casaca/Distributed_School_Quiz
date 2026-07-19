package pt.isec.common.dto.answer;
import pt.isec.common.model.question.OptionLetter;
import java.io.Serializable;

/**
 * Request for a student to submit an answer to a question.
 *
 * @param questionId     id of the question
 * @param studentId      student identifier
 * @param selectedOption chosen option letter
 */
public record SubmitAnswerDTO(
        Integer questionId,
        Integer studentId,
        OptionLetter selectedOption
) implements Serializable {}
