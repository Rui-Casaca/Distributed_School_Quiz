package pt.isec.common.dto.question;
import pt.isec.common.model.question.OptionLetter;
import pt.isec.common.model.question.Option;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Request to create a new question (teacher side).
 *
 * @param statement     question statement
 * @param teacherId     teacher identifier
 * @param options       list of options
 * @param correctOption correct option
 * @param startAt       availability start date/time
 * @param endAt         availability end date/time
 */
public record CreateQuestionDTO(
        String statement,
        Integer teacherId,
        List<Option> options,
        OptionLetter correctOption,
        LocalDateTime startAt,
        LocalDateTime endAt
) implements Serializable {}
