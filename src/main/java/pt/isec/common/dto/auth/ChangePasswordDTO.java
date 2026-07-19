package pt.isec.common.dto.auth;
import java.io.Serializable;

/**
 * Request to change the password of the currently authenticated user.
 *
 * @param sessionId  session identifier / user id
 * @param oldPassword current password
 * @param newPassword new password
 * @param userType    "TEACHER" or "STUDENT"
 */
public record ChangePasswordDTO(
        Integer sessionId,
        String oldPassword,
        String newPassword,
        String userType
) implements Serializable {}
