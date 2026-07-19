package pt.isec.client.threads;
import pt.isec.client.core.IClientControllerContext;
import pt.isec.client.core.IClientThreadContext;
import pt.isec.common.dto.auth.AuthResponseDTO;
import pt.isec.common.dto.question.CreateQuestionResponseDTO;
import pt.isec.common.messages.TcpMessage;
import pt.isec.common.messages.MessageType;
import pt.isec.common.model.question.Answer;
import pt.isec.common.model.question.Question;
import pt.isec.common.util.Log;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Thread that processes responses from the server taken from the response queue
 * and executes the appropriate logic for each message type.
 * <p>
 * This class delegates to {@link IClientControllerContext} to fire property change events
 * so that UI controllers can update the interface accordingly.
 */

@SuppressWarnings("ClassCanBeRecord")
public class ResponseHandlerThread implements Runnable {

    private final IClientThreadContext tInfo;

    /**
     * Creates a new response handler thread.
     *
     * @param tInfo client service interface
     */
    public ResponseHandlerThread(IClientThreadContext tInfo) {
        this.tInfo = tInfo;
    }

    /**
     * Main processing loop for server responses.
     * <p>
     * Continuously takes {@link TcpMessage} instances from the response queue,
     * dispatches each one to {@link #processResponse(TcpMessage)}, and keeps
     * running while the client is active. If the thread is interrupted, the
     * loop is terminated and the thread exits gracefully.
     */
    @Override
    public void run() {
        Log.info(ResponseHandlerThread.class, "Started processing responses...");

        while (tInfo.isRunning()) {
            try {
                // TODO Aplicação cliente: indicação assíncrona de alterações na BD
                TcpMessage<? extends Serializable> response = tInfo.getResponseQueue().take();
                processResponse(response);
            } catch (InterruptedException e) {
                Log.warn(ResponseHandlerThread.class, "Interrupted while processing responses");
                Thread.currentThread().interrupt();
                break;
            }
        }

        Log.info(ResponseHandlerThread.class, "Stopped processing responses");
    }

    /**
     * Processes a single response, delegating to {@link IClientControllerContext}
     * to emit the proper property events.
     *
     * @param response TCP message received from the server
     */
    private void processResponse(TcpMessage<? extends Serializable> response) {
        MessageType type = response.getType();

        Log.info(ResponseHandlerThread.class, "Processing: " + type);

        switch (type) {
            /* ===== AUTHENTICATION ===== */
            case LOGIN_OK -> {
                Log.info(ResponseHandlerThread.class, "Login successful");
                Serializable data = response.getData();
                if (data instanceof AuthResponseDTO dto) {
                    tInfo.setPropLoginOk(dto);
                } else {
                    Log.error(ResponseHandlerThread.class,
                            "LOGIN_OK received unexpected payload: " +
                                    (data == null ? "null" : data.getClass().getName()));
                }
            }
            case LOGIN_FAIL -> {
                Log.error(ResponseHandlerThread.class, "Login failed: " + response.getData());
                if (response.getData() instanceof String s) {
                    tInfo.setPropError(s);
                }
            }
            case REGISTER_OK -> {
                Log.info(ResponseHandlerThread.class, "Register successful");
                Serializable data = response.getData();

                if (data instanceof AuthResponseDTO dto) {
                    tInfo.setPropRegisterOk(dto);
                } else {
                    Log.error(ResponseHandlerThread.class,
                            "REGISTER_OK received unexpected payload type: " +
                                    (data == null ? "null" : data.getClass().getName()));
                }
            }

            /* ===== ACK/NACK/ERROR generic ===== */
            case ACK -> {
                Log.info(ResponseHandlerThread.class, "Operation acknowledged");
                if (response.getData() instanceof String s) {
                    if ("edit-ok".equalsIgnoreCase(s)) {
                        tInfo.setPropEditQuestionResponse("edit-ok");
                    } else if ("delete-ok".equalsIgnoreCase(s)) {
                        tInfo.setPropDeleteQuestionResponse("delete-ok");
                    }
                }
            }
            case NACK -> {
                Log.error(ResponseHandlerThread.class, "Operation failed: " + response.getData());
                if (response.getData() instanceof String s) {
                    if ("invalid-code".equalsIgnoreCase(s)) {
                        tInfo.setPropJoinQuestionResponse(null);
                    } else if ("edit-fail".equalsIgnoreCase(s)) {
                        tInfo.setPropEditQuestionResponse("edit-fail");
                    } else if ("delete-fail".equalsIgnoreCase(s)) {
                        tInfo.setPropDeleteQuestionResponse("delete-fail");
                    }
                }
            }
            case ERROR -> {
                Log.error(ResponseHandlerThread.class, "Server error: " + response.getData());
                if (response.getData() instanceof String s) {
                    tInfo.setPropError(s);
                }
            }

            case PONG -> Log.info(ResponseHandlerThread.class, "Pong received");

            /* ===== QUESTION OPERATIONS ===== */
            case CREATE_QUESTION_RESPONSE -> {
                Log.info(ResponseHandlerThread.class, "New question created");
                CreateQuestionResponseDTO dto = response.getDataAs(CreateQuestionResponseDTO.class);
                tInfo.setPropCreateQuestionResponse(dto);
            }
            case CREATE_QUESTION_FAIL -> {
                Log.error(ResponseHandlerThread.class, "Server error: " + response.getData());
                if(response.getData() instanceof String s)
                    tInfo.setPropCreateQuestionError(s);
            }
            case LIST_QUESTIONS_RESPONSE -> {
                Log.info(ResponseHandlerThread.class, "Questions list received");
                Serializable data = response.getData();

                List<Question> qList = new ArrayList<>();

                if (data instanceof List<?> rawList) {
                    for (Object o : rawList) {
                        if (o instanceof Question q) {
                            qList.add(q);
                        } else {
                            Log.error(ResponseHandlerThread.class,
                                    "LIST_QUESTIONS_RESPONSE contains unexpected element: "
                                            + (o == null ? "null" : o.getClass().getName()));
                        }
                    }
                } else {
                    Log.error(ResponseHandlerThread.class,
                            "LIST_QUESTIONS_RESPONSE received unexpected type: "
                                    + (data == null ? "null" : data.getClass().getName()));
                }

                tInfo.setPropListQuestionsResponse(qList);
            }

            case EDIT_QUESTION_FAIL -> {
                Log.error(ResponseHandlerThread.class, "Server error: " + response.getData());
                if(response.getData() instanceof String s)
                    tInfo.setPropEditQuestionError(s);
            }

            case QUESTION_DETAILS -> {
                Log.info(ResponseHandlerThread.class, "Question details received");
                Serializable data = response.getData();

                if (data instanceof Question q) {
                    tInfo.setPropJoinQuestionResponse(q);
                } else {
                    Log.error(ResponseHandlerThread.class,
                            "QUESTION_DETAILS received unexpected payload type: " +
                                    (data == null ? "null" : data.getClass().getName()));
                    // Safe fallback
                    tInfo.setPropJoinQuestionResponse(null);
                }
            }


            /* ===== ANSWER OPERATIONS ===== */
            case SUBMIT_OK -> {
                Log.info(ResponseHandlerThread.class, "Answer submitted successfully");
                Serializable data = response.getData();

                String msg;
                if (data instanceof String s) {
                    msg = s;
                } else {
                    Log.error(ResponseHandlerThread.class,
                            "SUBMIT_OK received unexpected payload type: " +
                                    (data == null ? "null" : data.getClass().getName()) +
                                    " — using default message.");
                    msg = "Answer submitted successfully!";
                }

                tInfo.setPropSubmitAnswerOk(msg);
            }

            case SUBMIT_FAIL -> {
                Log.error(ResponseHandlerThread.class, "Failed to submit answer");
                Serializable data = response.getData();

                String msg;
                if (data instanceof String s) {
                    msg = s;
                } else {
                    Log.error(ResponseHandlerThread.class,
                            "SUBMIT_FAIL received unexpected payload type: " +
                                    (data == null ? "null" : data.getClass().getName()) +
                                    " — using default failure message.");
                    msg = "Failed to submit answer!";
                }

                tInfo.setPropSubmitAnswerFail(msg);
            }

            case ANSWER_SUBMITTED -> {
                Log.info(ResponseHandlerThread.class, "Notification: answer submitted");
                Object d = response.getData();
                if (d instanceof Integer qid) {
                    tInfo.setPropAnswerSubmitted(qid);
                } else if (d instanceof String s) {
                    try {
                        tInfo.setPropAnswerSubmitted(Integer.parseInt(s));
                    } catch (Exception ignored) {}
                }
            }

            case VIEW_ANSWERS_RESPONSE -> {
                Log.info(ResponseHandlerThread.class, "Answers received");
                Serializable data = response.getData();

                List<Answer> answers = new ArrayList<>();
                if (data instanceof List<?> tmp) {
                    for (Object o : tmp) {
                        if (o instanceof Answer a) {
                            answers.add(a);
                        } else {
                            Log.error(ResponseHandlerThread.class,
                                    "VIEW_ANSWERS_RESPONSE contains unexpected element: "
                                            + (o == null ? "null" : o.getClass().getName()));
                        }
                    }
                } else {
                    Log.error(ResponseHandlerThread.class,
                            "VIEW_ANSWERS_RESPONSE received unexpected type: "
                                    + (data == null ? "null" : data.getClass().getName()));
                }

                tInfo.setPropViewAnswersResponse(answers);
            }

            case LIST_ANSWERED_RESPONSE -> {
                Log.info(ResponseHandlerThread.class, "Answered questions history received");
                Serializable data = response.getData();

                List<Answer> answers = new ArrayList<>();
                if (data instanceof List<?> tmp) {
                    for (Object o : tmp) {
                        if (o instanceof Answer a) {
                            answers.add(a);
                        } else {
                            Log.error(ResponseHandlerThread.class,
                                    "LIST_ANSWERED_RESPONSE contains unexpected element: "
                                            + (o == null ? "null" : o.getClass().getName()));
                        }
                    }
                } else {
                    Log.error(ResponseHandlerThread.class,
                            "LIST_ANSWERED_RESPONSE received unexpected type: "
                                    + (data == null ? "null" : data.getClass().getName()));
                }
                tInfo.setPropListAnsweredResponse(answers);
            }

            /* ===== PROFILE ===== */
            case UPDATE_PROFILE_OK -> {
                Log.info(ResponseHandlerThread.class, "Profile updated successfully");
                Serializable data = response.getData();

                if (data instanceof AuthResponseDTO dto) {
                    tInfo.setPropUpdateProfileOk(dto);
                } else {
                    Log.warn(ResponseHandlerThread.class,
                            "UPDATE_PROFILE_OK received unexpected payload type: " +
                                    (data == null ? "null" : data.getClass().getName()) +
                                    " — using default OK DTO.");
                    tInfo.setPropUpdateProfileOk(
                            new AuthResponseDTO(null, null, null, null, "ok", null)
                    );
                }
            }

            case UPDATE_PROFILE_FAIL -> {
                Log.error(ResponseHandlerThread.class, "Failed to update profile");
                String msg = response.getData() instanceof String s ? s : "Erro ao atualizar perfil";
                tInfo.setPropUpdateProfileFail(msg);
            }

            default -> Log.info(ResponseHandlerThread.class, "Unhandled message type: " + type);
        }
    }
}
