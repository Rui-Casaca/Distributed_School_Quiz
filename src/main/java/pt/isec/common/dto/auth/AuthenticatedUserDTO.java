package pt.isec.common.dto.auth;

import java.io.Serializable;

/**
 * Lightweight DTO representing an authenticated user.
 *
 * @param id       user identifier
 * @param name     user name
 * @param email    email address
 * @param userType user type ("TEACHER" or "STUDENT")
 */
public record AuthenticatedUserDTO(
        String id,
        String name,
        String email,
        String userType
) implements Serializable {}
