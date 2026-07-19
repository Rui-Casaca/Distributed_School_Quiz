package pt.isec.common.messages;

/**
 * Types of messages exchanged between clients and server.
 */
public enum MessageType {
    // Authentication
    LOGIN,
    LOGIN_OK,
    LOGIN_FAIL,
    REGISTER_OK,
    REGISTER_STUDENT,
    REGISTER_TEACHER,
    ACK,
    NACK,
    ERROR,
    LOGOUT,
    RESUME_SESSION,
    RESUME_SESSION_OK,
    RESUME_SESSION_FAIL,
    PONG,

    // Questions – teacher side
    CREATE_QUESTION,
    CREATE_QUESTION_RESPONSE,
    CREATE_QUESTION_FAIL,
    EDIT_QUESTION,
    EDIT_QUESTION_FAIL,
    DELETE_QUESTION,
    LIST_QUESTIONS,
    LIST_QUESTIONS_RESPONSE,

    // Question – student side
    JOIN_QUESTION,
    QUESTION_DETAILS,

    // Answers
    SUBMIT_ANSWER,
    SUBMIT_OK,
    SUBMIT_FAIL,
    VIEW_ANSWERS,
    VIEW_ANSWERS_RESPONSE,
    LIST_ANSWERED_QUESTIONS,
    LIST_ANSWERED_RESPONSE,

    // Notification to teacher when an answer is submitted
    ANSWER_SUBMITTED,

    // Profile
    UPDATE_STUDENT,
    UPDATE_TEACHER,
    UPDATE_PROFILE_OK,
    UPDATE_PROFILE_FAIL,

    // Incremental replication via SQL (heartbeat)
    SQL_UPDATE,
    DB_REQUEST_COPY
}
