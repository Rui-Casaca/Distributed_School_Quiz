package pt.isec.common.dto.auth;
import java.io.Serializable;

/**
 * Response containing authentication result and basic user info.
 *
 * @param sessionId     session token
 * @param userId        user identifier as string
 * @param studentNumber student number (if applicable, null for teachers)
 * @param userType      "TEACHER" or "STUDENT"
 * @param name          user name
 * @param email         email address
 */
public record AuthResponseDTO(
        String sessionId,
        String userId,
        Long studentNumber,
        String userType,
        String name,
        String email
) implements Serializable {}
