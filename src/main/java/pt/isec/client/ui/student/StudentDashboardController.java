package pt.isec.client.ui.student;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;
import pt.isec.client.ClientApplication;
import pt.isec.client.core.ClientManager;
import pt.isec.client.core.IClientControllerContext;
import pt.isec.client.ui.IDisposableProp;
import pt.isec.client.ui.util.AlertUtils;
import pt.isec.client.ui.util.UiUtils;
import pt.isec.client.ui.util.dialogs.StudentDialogs;
import pt.isec.common.dto.answer.SubmitAnswerDTO;
import pt.isec.common.dto.auth.AuthResponseDTO;
import pt.isec.common.dto.auth.UpdateStudentDTO;
import pt.isec.common.dto.question.JoinQuestionDTO;
import pt.isec.common.model.question.Answer;
import pt.isec.common.model.question.Question;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;

/**
 * Controller for the student dashboard.
 * <p>
 * Interacts with the server to:
 * <ul>
 *     <li>Join questions and submit answers</li>
 *     <li>Fetch the student's answer history</li>
 *     <li>Update profile information</li>
 * </ul>
 * Uses the event-based model of {@link ClientManager}, with all requests enqueued
 * and responses delivered via property change events.
 */
public class StudentDashboardController implements IDisposableProp {

    private final Stage stage;
    private final IClientControllerContext clientControllerContext;
    private final ClientApplication application;
    private String userEmail;
    private String userName;
    private final StudentDashboardView view;

    // Listeners
    private final PropertyChangeListener notificationListener;
    private final PropertyChangeListener joinQuestionListener;
    private final PropertyChangeListener submitAnswerOkListener;
    private final PropertyChangeListener submitAnswerFailListener;
    private final PropertyChangeListener listAnsweredListener;
    private final PropertyChangeListener updateProfileOkListener;
    private final PropertyChangeListener updateProfileFailListener;
    private final PropertyChangeListener userNameListener;
    private final PropertyChangeListener userEmailListener;
    private final PropertyChangeListener studentNumberListener;

    // Waiting flags
    private volatile boolean awaitingJoinQuestion = false;
    private volatile boolean awaitingSubmitAnswer = false;
    private volatile boolean awaitingHistory = false;

    private volatile String currentFilter = "Todas";

    /**
     * Creates a new controller for the student dashboard.
     *
     * @param stage          primary stage
     * @param clientControllerContext  client manager with all services
     * @param application    reference to the main application
     * @param userName       initial student name
     * @param userEmail      initial student email
     */
    public StudentDashboardController(Stage stage,
                                      ClientManager clientControllerContext,
                                      ClientApplication application,
                                      String userName,
                                      String userEmail) {
        this.stage = stage;
        this.clientControllerContext = clientControllerContext;
        this.application = application;
        this.userName = userName;
        this.userEmail = userEmail;

        this.view = new StudentDashboardView(userName, userEmail);
        view.createView();
        view.registerHandlers(this);

        // ----------------- Listeners -----------------
        notificationListener = evt -> {
            String notification = (String) evt.getNewValue();
            if (notification != null) {
                Platform.runLater(() -> {
                    view.addNotification(notification);
                    view.update();
                });
            }
        };

        joinQuestionListener = evt -> {
            if (!awaitingJoinQuestion) {
                return;
            }
            awaitingJoinQuestion = false;
            Question q = (Question) evt.getNewValue();

            UiUtils.runOnUiThread(() -> {
                try {
                    view.hideLoading();
                } catch (Exception ignored) {
                }
                if (q == null) {
                    showErrorAlert("Código inválido ou pergunta não existente.");
                    view.addNotification("Falha ao carregar pergunta para o código indicado.");
                } else if (!q.isActive()) {
                    showErrorAlert("Não é possivel responder à pergunta.");
                    view.addNotification("Pergunta " + q.getAccessCode() + " não está ativa para resposta.");
                } else {
                    view.addNotification("Pergunta " + q.getAccessCode() + " carregada para resposta.");
                    openQuestionDialog(q);
                }
            });
        };

        submitAnswerOkListener = evt -> {
            awaitingSubmitAnswer = false;
            String msg = (String) evt.getNewValue();
            UiUtils.runOnUiThread(() -> {
                AlertUtils.showInfo(getOwnerWindow(), "Resposta submetida com sucesso!", msg == null ? "" : msg);
                view.addNotification("Resposta submetida com sucesso.");
            });
        };

        submitAnswerFailListener = evt -> {
            awaitingSubmitAnswer = false;
            String msg = (String) evt.getNewValue();
            UiUtils.runOnUiThread(() -> {
                String full = "Falha ao submeter a resposta: " + (msg == null ? "" : msg);
                showErrorAlert(full);
                view.addNotification(full);
            });
        };

        listAnsweredListener = evt -> {
            if (!awaitingHistory) {
                return;
            }
            List<Answer> history = new ArrayList<>();
            Object newValue = evt.getNewValue();

            if (newValue instanceof List<?> tmp) {
                for (Object o : tmp) {
                    if (o instanceof Answer a) {
                        history.add(a);
                    }
                }
            }

            UiUtils.runOnUiThread(() -> {
                StudentDialogs.showHistoryDialog(
                        getOwnerWindow(),
                        history,
                        currentFilter,
                        newFilter -> currentFilter = newFilter
                );
                view.addNotification("Histórico de respostas carregado (" +
                        (history == null ? 0 : history.size()) + " registos).");
            });
        };

        updateProfileOkListener = evt -> {
            AuthResponseDTO dto = (AuthResponseDTO) evt.getNewValue();
            UiUtils.runOnUiThread(() -> {
                // Update local fields
                this.userName = dto.name();
                this.userEmail = dto.email();
                // Update view
                view.updateUserInfo(this.userName, this.userEmail);

                AlertUtils.showInfo(getOwnerWindow(),
                        "Perfil atualizado",
                        "Os dados do perfil foram atualizados com sucesso.");
                view.addNotification("Perfil atualizado: " + this.userName + " (" + this.userEmail + ").");
            });
        };

        updateProfileFailListener = evt -> {
            Object v = evt.getNewValue();
            String msg = (v == null) ? "Erro desconhecido" : v.toString();
            UiUtils.runOnUiThread(() -> {
                AlertUtils.showError(getOwnerWindow(),
                        "Falha ao actualizar perfil",
                        msg);
                view.addNotification("Falha ao atualizar perfil: " + msg);
            });
        };

        userNameListener = evt -> {
            this.userName = (String) evt.getNewValue();
            UiUtils.runOnUiThread(() -> view.updateUserInfo(this.userName, this.userEmail));
        };

        userEmailListener = evt -> {
            this.userEmail = (String) evt.getNewValue();
            UiUtils.runOnUiThread(() -> view.updateUserInfo(this.userName, this.userEmail));
        };

        studentNumberListener = _ -> {
            // No direct UI update needed for student number in the main dashboard view.
            // Internal state is maintained in ClientManager/Service.
        };

        setupPropertyChangeListeners();
    }

    /**
     * Shows the student dashboard.
     */
    public void show() {
        stage.setScene(view.getScene());
        stage.setMaximized(true);
    }

    /**
     * Registers all property change listeners in the {@link ClientManager}.
     */
    private void setupPropertyChangeListeners() {
        clientControllerContext.addPropertyChangeListener(ClientManager.PROP_NOTIFICATION, notificationListener);
        clientControllerContext.addPropertyChangeListener(ClientManager.PROP_JOIN_QUESTION_RESPONSE, joinQuestionListener);
        clientControllerContext.addPropertyChangeListener(ClientManager.PROP_SUBMIT_ANSWER_OK, submitAnswerOkListener);
        clientControllerContext.addPropertyChangeListener(ClientManager.PROP_SUBMIT_ANSWER_FAIL, submitAnswerFailListener);
        clientControllerContext.addPropertyChangeListener(ClientManager.PROP_LIST_ANSWERED_RESPONSE, listAnsweredListener);
        clientControllerContext.addPropertyChangeListener(ClientManager.PROP_UPDATE_PROFILE_OK, updateProfileOkListener);
        clientControllerContext.addPropertyChangeListener(ClientManager.PROP_UPDATE_PROFILE_FAIL, updateProfileFailListener);
        clientControllerContext.addPropertyChangeListener(ClientManager.PROP_USER_NAME, userNameListener);
        clientControllerContext.addPropertyChangeListener(ClientManager.PROP_USER_EMAIL, userEmailListener);
        clientControllerContext.addPropertyChangeListener(ClientManager.PROP_STUDENT_NUMBER, studentNumberListener);
    }

    /**
     * Removes all listeners and hides any loading indicator.
     */
    @Override
    public void dispose() {
        clientControllerContext.removePropertyChangeListener(ClientManager.PROP_NOTIFICATION, notificationListener);
        clientControllerContext.removePropertyChangeListener(ClientManager.PROP_JOIN_QUESTION_RESPONSE, joinQuestionListener);
        clientControllerContext.removePropertyChangeListener(ClientManager.PROP_SUBMIT_ANSWER_OK, submitAnswerOkListener);
        clientControllerContext.removePropertyChangeListener(ClientManager.PROP_SUBMIT_ANSWER_FAIL, submitAnswerFailListener);
        clientControllerContext.removePropertyChangeListener(ClientManager.PROP_LIST_ANSWERED_RESPONSE, listAnsweredListener);
        clientControllerContext.removePropertyChangeListener(ClientManager.PROP_UPDATE_PROFILE_OK, updateProfileOkListener);
        clientControllerContext.removePropertyChangeListener(ClientManager.PROP_UPDATE_PROFILE_FAIL, updateProfileFailListener);
        clientControllerContext.removePropertyChangeListener(ClientManager.PROP_USER_NAME, userNameListener);
        clientControllerContext.removePropertyChangeListener(ClientManager.PROP_USER_EMAIL, userEmailListener);
        clientControllerContext.removePropertyChangeListener(ClientManager.PROP_STUDENT_NUMBER, studentNumberListener);

        try {
            view.hideLoading();
        } catch (Exception ignored) {
        }
    }


    // ----------------------------------------------------------
    // ANSWER QUESTION
    // ----------------------------------------------------------

    /**
     * Handler to request a question by access code and open the answering dialog.
     * <p>
     * Validates the access code locally before sending the request to the server
     * and provides user feedback in all error/success scenarios.
     */
    public void onAnswerQuestion() {
        StudentDialogs.showEnterQuestionCodeDialog(getOwnerWindow(), code -> {
            String trimmedCode = (code != null ? code.trim() : "");

            if (trimmedCode.isEmpty()) {
                showErrorAlert("O código da pergunta é obrigatório.");
                view.addNotification("Falha ao procurar pergunta: código de acesso em branco.");
                return;
            }

            Integer studentId = clientControllerContext.getUserId();
            if (studentId == null) {
                showErrorAlert("Sessão inválida. Faça login novamente.");
                view.addNotification("Falha ao procurar pergunta: sessão inválida.");
                return;
            }

            awaitingJoinQuestion = true;
            try {
                view.showLoading("A carregar pergunta...");
                clientControllerContext.getQuestionService()
                        .joinQuestion(new JoinQuestionDTO(trimmedCode, studentId));
            } catch (Exception e) {
                try {
                    view.hideLoading();
                } catch (Exception ignored) {
                }
                awaitingJoinQuestion = false;
                String msg = "Erro ao procurar pergunta: " + e.getMessage();
                showErrorAlert(msg);
                view.addNotification(msg);
            }
        });
    }

    /**
     * Opens the dialog with the question and sends the selected answer when submitted.
     *
     * @param question question to answer
     */
    private void openQuestionDialog(Question question) {
        Integer studentId = clientControllerContext.getUserId();
        if (studentId == null) {
            showErrorAlert("Sessão inválida. Faça login novamente.");
            view.addNotification("Sessão inválida ao tentar responder à pergunta.");
            return;
        }

        StudentDialogs.showAnswerQuestionDialog(
                getOwnerWindow(),
                question,
                studentId,
                (SubmitAnswerDTO dto) -> {
                    awaitingSubmitAnswer = true;
                    try {
                        clientControllerContext.getAnswerService().submitAnswer(dto);
                    } catch (Exception ex) {
                        awaitingSubmitAnswer = false;
                        String msg = "Erro ao submeter resposta: " + ex.getMessage();
                        showErrorAlert(msg);
                        view.addNotification(msg);
                    }
                });
    }

    // ----------------------------------------------------------
    // HISTORY
    // ----------------------------------------------------------

    /**
     * Handler to request and display the student's answer history.
     */
    public void onShowHistory() {
        Integer studentId = clientControllerContext.getUserId();
        if (studentId == null) {
            showErrorAlert("Sessão inválida. Faça login novamente.");
            view.addNotification("Falha ao obter histórico: sessão inválida.");
            return;
        }
        awaitingHistory = true;
        try {
            clientControllerContext.getAnswerService().viewAnswersForStudent(studentId);
        } catch (Exception e) {
            awaitingHistory = false;
            String msg = "Erro ao obter histórico: " + e.getMessage();
            showErrorAlert(msg);
            view.addNotification(msg);
        }
    }

    // ----------------------------------------------------------
    // PROFILE (edit + password)
    // ----------------------------------------------------------

    /**
     * Opens the editable student profile dialog (name, email, student number and password).
     * <p>
     * All semantic validation (email format, password rules, uniqueness, etc.)
     * is performed on the server. This handler only collects the raw values,
     * forwards them to the server and relies on the update profile listeners
     * to show any validation error message returned by the backend.
     */
    public void onProfile() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Perfil do Estudante");
        dialog.setHeaderText("Editar dados de perfil");

        VBox content = new VBox(10);
        content.setPadding(new Insets(20));

        Label numberLabel = new Label("Número de estudante:");
        TextField numberField = new TextField();
        numberField.setPromptText("Número de estudante");
        Long currentNumber = clientControllerContext.getStudentNumber();
        if (currentNumber != null) {
            numberField.setText(String.valueOf(currentNumber));
        }

        Label nameLabel = new Label("Nome:");
        TextField nameField = new TextField();
        nameField.setText(clientControllerContext.getUserName() != null
                ? clientControllerContext.getUserName() : "");

        Label emailLabel = new Label("Email:");
        TextField emailField = new TextField();
        emailField.setText(clientControllerContext.getUserEmail() != null
                ? clientControllerContext.getUserEmail() : "");

        Label oldPwLabel = new Label("Password atual (só necessária se pretende alterar):");
        PasswordField oldPwField = new PasswordField();

        Label newPwLabel = new Label("Nova password (deixe vazio para não alterar):");
        PasswordField newPwField = new PasswordField();

        content.getChildren().addAll(
                numberLabel, numberField,
                nameLabel, nameField,
                emailLabel, emailField,
                oldPwLabel, oldPwField,
                newPwLabel, newPwField
        );

        dialog.getDialogPane().setContent(content);
        ButtonType saveButtonType = new ButtonType("Guardar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CLOSE);

        dialog.showAndWait().ifPresent(bt -> {
            if (bt != saveButtonType) {
                return;
            }

            String numberText = numberField.getText();
            String name = nameField.getText();
            String email = emailField.getText();
            String oldPw = oldPwField.getText();
            String newPw = newPwField.getText();

            // Student number is passed as Long, but semantic validation is done on the server
            Long number = null;
            if (numberText != null) {
                String trimmed = numberText.trim();
                if (!trimmed.isEmpty()) {
                    try {
                        number = Long.parseLong(trimmed);
                    } catch (NumberFormatException ignored) {
                        // Let the server treat null/invalid as "Número de estudante inválido"
                    }
                }
            }

            try {
                UpdateStudentDTO dto = new UpdateStudentDTO(
                        clientControllerContext.getUserId(),
                        number,
                        name != null ? name.trim() : null,
                        email != null ? email.trim() : null,
                        (oldPw == null || oldPw.isBlank()) ? null : oldPw,
                        (newPw == null || newPw.isBlank()) ? null : newPw
                );
                clientControllerContext.getAuthService().updateStudent(dto);
                // Success / error feedback is delivered via updateProfile listeners
                view.addNotification("Pedido de atualização de perfil enviado.");
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                showErrorAlert("Erro ao enviar atualização de perfil: " + ie.getMessage());
                view.addNotification("Erro ao enviar atualização de perfil: " + ie.getMessage());
            }
        });
    }

    // ----------------------------------------------------------
    // LOGOUT
    // ----------------------------------------------------------

    /**
     * Handles the logout action, including confirmation, service logout and navigation.
     */
    public void onLogout() {
        boolean confirm = AlertUtils.showConfirmation(
                getOwnerWindow(),
                "Confirmar Logout",
                "Deseja realmente sair?",
                "Será necessário fazer login novamente."
        );
        if (!confirm) {
            return;
        }

        try {
            clientControllerContext.getAuthService().logout();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        dispose();
        clientControllerContext.logout();
        application.showAuthentication();
    }

    // ----------------------------------------------------------
    // HELPERS
    // ----------------------------------------------------------

    /**
     * Returns the owner window for dialogs (either from the view scene or the stage).
     *
     * @return owner window
     */
    private Window getOwnerWindow() {
        if (view != null && view.getScene() != null) {
            return view.getScene().getWindow();
        }
        return stage;
    }

    /**
     * Shows an error dialog with a standard title.
     *
     * @param message error message
     */
    private void showErrorAlert(String message) {
        AlertUtils.showError(getOwnerWindow(), "Erro", message);
    }
}
