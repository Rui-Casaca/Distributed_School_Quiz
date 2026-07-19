package pt.isec.common.dto.question;
import java.io.Serializable;

/**
 * Request to list questions created by a given teacher with an optional state filter.
 *
 * @param teacherId teacher identifier
 * @param filter    "active" | "future" | "expired" | null (no filter)
 */
public record ListQuestionsDTO(
        Integer teacherId,
        String filter
) implements Serializable {}
