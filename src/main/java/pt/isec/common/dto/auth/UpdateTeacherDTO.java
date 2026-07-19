package pt.isec.common.dto.auth;
import java.io.Serializable;

/**
 * Request to update a teacher's profile.
 *
 * @param userId      internal user id
 * @param teacherId   teacher id (as used elsewhere in the model)
 * @param name        teacher name
 * @param email       email address
 * @param oldPassword current password (required if changing password)
 * @param newPassword new password (optional)
 */
public record UpdateTeacherDTO(
        Integer userId,
        Integer teacherId,
        String name,
        String email,
        String oldPassword,
        String newPassword
) implements Serializable {}
