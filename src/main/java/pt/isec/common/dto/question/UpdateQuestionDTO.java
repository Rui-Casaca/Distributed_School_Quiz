package pt.isec.common.dto.question;

import pt.isec.common.model.question.Option;
import pt.isec.common.model.question.OptionLetter;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Request to update an existing question.
 *
 * @param questionId    id of the question to update
 * @param statement     updated question statement
 * @param teacherId     id of the teacher owning the question
 * @param options       updated list of options
 * @param correctOption updated correct option
 * @param startAt       updated start date/time
 * @param endAt         updated end date/time
 */
public record UpdateQuestionDTO (Integer questionId,
                                 String statement,
                                 Integer teacherId,
                                 List<Option> options,
                                 OptionLetter correctOption,
                                 LocalDateTime startAt,
                                 LocalDateTime endAt) implements Serializable {}
