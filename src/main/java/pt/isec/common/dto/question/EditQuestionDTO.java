package pt.isec.common.dto.question;

import pt.isec.common.model.question.OptionLetter;
import pt.isec.common.model.question.Option;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Request to edit an existing question (teacher side).
 *
 * @param questionId    id of the question to edit
 * @param teacherId     teacher identifier
 * @param statement     question statement
 * @param options       list of options
 * @param correctOption correct option
 * @param startAt       availability start date/time
 * @param endAt         availability end date/time
 */
public record EditQuestionDTO(
        Integer questionId,
        Integer teacherId,
        String statement,
        List<Option> options,
        OptionLetter correctOption,
        LocalDateTime startAt,
        LocalDateTime endAt
) implements Serializable {}
