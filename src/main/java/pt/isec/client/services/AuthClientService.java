package pt.isec.client.services;

import pt.isec.client.core.ClientManager;
import pt.isec.client.core.IClientControllerContext;
import pt.isec.client.core.IClientServiceContext;
import pt.isec.common.dto.auth.*;
import pt.isec.common.messages.TcpMessage;
import pt.isec.common.messages.MessageType;

/**
 * Client-side service for authentication and registration operations.
 * <p>
 * Communicates with the server through {@link IClientControllerContext} and enqueues
 * messages to be sent by {@code RequestSenderThread}.
 * <p>
 * It does not wait for responses; results are delivered by
 * {@code ResponseHandlerThread} via property change events.
 */
@SuppressWarnings("ClassCanBeRecord")
public class AuthClientService {

    private final IClientServiceContext service;

    /**
     * Creates a new authentication client service.
     *
     * @param service underlying client service
     */
    public AuthClientService(IClientServiceContext service) {
        this.service = service;
    }

    /**
     * Attempts to log in with the given credentials.
     * <p>
     * The request is enqueued and the method returns immediately.
     *
     * @param email    user email
     * @param password user password
     * @throws InterruptedException if the thread is interrupted while enqueuing
     */
    public void login(String email, String password) throws InterruptedException {
        LoginRequestDTO dto = new LoginRequestDTO(email, password);
        service.getRequestQueue().put(new TcpMessage<>(MessageType.LOGIN, dto));
    }

    /**
     * Registers a new teacher.
     *
     * @param name        teacher name
     * @param email       teacher email
     * @param password    teacher password
     * @param teacherCode institution-provided teacher code
     * @throws InterruptedException if the thread is interrupted while enqueuing
     */
    public void registerTeacher(String name, String email,
                                String password, String teacherCode) throws InterruptedException {
        RegisterTeacherDTO dto = new RegisterTeacherDTO(name, email, password, teacherCode);
        service.getRequestQueue().put(new TcpMessage<>(MessageType.REGISTER_TEACHER, dto));
    }

    /**
     * Registers a new student.
     *
     * @param name          student name
     * @param email         student email
     * @param password      student password
     * @param studentNumber student number
     * @throws InterruptedException if the thread is interrupted while enqueuing
     */
    public void registerStudent(String name, String email,
                                String password, Long studentNumber) throws InterruptedException {
        RegisterStudentDTO dto = new RegisterStudentDTO(name, email, password, studentNumber);
        service.getRequestQueue().put(new TcpMessage<>(MessageType.REGISTER_STUDENT, dto));
    }

    /**
     * Updates student data (optionally including password change).
     *
     * @param dto student update payload
     * @throws InterruptedException if the thread is interrupted while enqueuing
     */
    public void updateStudent(UpdateStudentDTO dto) throws InterruptedException {
        if (dto == null) {
            return;
        }
        service.getRequestQueue().put(new TcpMessage<>(MessageType.UPDATE_STUDENT, dto));
    }

    /**
     * Updates teacher data (optionally including password change).
     *
     * @param dto teacher update payload
     * @throws InterruptedException if the thread is interrupted while enqueuing
     */
    public void updateTeacher(UpdateTeacherDTO dto) throws InterruptedException {
        if (dto == null) {
            return;
        }
        service.getRequestQueue().put(new TcpMessage<>(MessageType.UPDATE_TEACHER, dto));
    }

    /**
     * Logs out the current user.
     * <p>
     * Only enqueues the message; when the server responds with ACK,
     * {@link ClientManager} will update the authentication state
     * via {@code PROP_AUTHENTICATED}.
     *
     * @throws InterruptedException if the thread is interrupted while enqueuing
     */
    public void logout() throws InterruptedException {
        service.getRequestQueue().put(new TcpMessage<>(MessageType.LOGOUT, null));
    }
}
