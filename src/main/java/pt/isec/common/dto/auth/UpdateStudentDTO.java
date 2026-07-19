package pt.isec.common.dto.auth;
import java.io.Serializable;

/**
 * Request to update a student's profile.
 *
 * @param userId        internal user id
 * @param studentNumber student number
 * @param name          student name
 * @param email         email address
 * @param oldPassword   current password (required if changing password)
 * @param newPassword   new password (optional)
 */
public record UpdateStudentDTO(
        Integer userId,
        Long studentNumber,
        String name,
        String email,
        String oldPassword,
        String newPassword
) implements Serializable {}
