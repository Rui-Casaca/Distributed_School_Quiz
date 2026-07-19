package pt.isec.common.dto.auth;
import java.io.Serializable;

/**
 * Request to register a new student.
 *
 * @param name          student name
 * @param email         email address
 * @param password      raw password
 * @param studentNumber unique student number
 */
public record RegisterStudentDTO(
        String name,
        String email,
        String password,
        Long studentNumber
) implements Serializable {}
