package pt.isec.server.services.auth;

import pt.isec.common.dto.auth.*;

/**
 * Authentication and profile management service contract.
 */
public interface IAuthService {

    /* ===================== REGISTRATION ===================== */

    /**
     * Marks a user session as inactive in the database.
     *
     * @param userId    id of the user
     * @param sessionId session identifier to invalidate
     */
    void invalidateSession(long userId, String sessionId, String typeUser, String name, String email);

    /**
     * Registers a new teacher account.
     *
     * @param registerTeacherDTO teacher registration data
     * @return authentication response with session and user info
     * @throws Exception if validation or database access fails
     */
    AuthResponseDTO registerTeacher(RegisterTeacherDTO registerTeacherDTO) throws Exception;

    /**
     * Registers a new student account.
     *
     * @param registerStudentDTO student registration data
     * @return authentication response with session and user info
     * @throws Exception if validation or database access fails
     */
    AuthResponseDTO registerStudent(RegisterStudentDTO registerStudentDTO) throws Exception;


    /* ===================== AUTHENTICATION ===================== */

    /**
     * Authenticates a user (teacher or student) by email and password.
     *
     * @param dto login request data
     * @return authentication response with session and user info
     * @throws Exception if validation or database access fails
     */
    AuthResponseDTO login(LoginRequestDTO dto) throws Exception;


    /* ===================== PROFILE / CHANGES ===================== */

    /**
     * Updates a student profile (name, email, student number and optionally password).
     *
     * @param dto profile update data
     * @return updated authentication response (without changing session id)
     * @throws Exception if validation or database access fails
     */
    AuthResponseDTO updateStudent(UpdateStudentDTO dto) throws Exception;

    /**
     * Updates a teacher profile (name, email and optionally password).
     *
     * @param dto profile update data
     * @return updated authentication response (without changing session id)
     * @throws Exception if validation or database access fails
     */
    AuthResponseDTO updateTeacher(UpdateTeacherDTO dto) throws Exception;

    /**
     * Resumes a previously created user session.
     * <p>
     * The implementation should validate that the session exists, has not been
     * explicitly terminated and has not expired, and then return the current
     * authentication details for the associated user.
     *
     * @param sessionId unique identifier of the session to resume
     * @return an authentication response containing user and session information
     * @throws Exception if the session is invalid, expired, or if a database error occurs
     */
    AuthResponseDTO resumeSession(String sessionId) throws Exception;

    /**
     * Changes the password for an existing user.
     *
     * @param changePasswordDTO password change data
     */
    void changePassword(ChangePasswordDTO changePasswordDTO);
}
