package pt.isec.common.dto.auth;
import java.io.Serializable;

/**
 * Request to register a new teacher.
 *
 * @param name               teacher name
 * @param email              email address
 * @param password           raw password
 * @param teacherRegisterCode registration code required for teachers
 */
public record RegisterTeacherDTO(
        String name,
        String email,
        String password,
        String teacherRegisterCode
) implements Serializable {}
