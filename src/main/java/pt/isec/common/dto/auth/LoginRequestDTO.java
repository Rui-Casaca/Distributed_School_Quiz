package pt.isec.common.dto.auth;
import java.io.Serializable;

/**
 * Request to authenticate a user.
 *
 * @param email    email address
 * @param password raw password
 */
public record LoginRequestDTO(
        String email,
        String password
) implements Serializable {}
