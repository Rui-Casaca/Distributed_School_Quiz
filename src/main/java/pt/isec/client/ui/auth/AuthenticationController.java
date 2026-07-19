package pt.isec.client.ui.auth;

import javafx.scene.paint.Color;
import javafx.stage.Stage;
import pt.isec.client.ClientApplication;
import pt.isec.client.core.ClientManager;
import pt.isec.client.core.IClientControllerContext;
import pt.isec.client.ui.IDisposableProp;
import pt.isec.client.ui.util.AlertUtils;
import pt.isec.client.ui.util.UiUtils;
import pt.isec.common.dto.auth.AuthResponseDTO;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * Controller for the authentication interface.
 * <p>
 * Responsible for:
 * <ul>
 *     <li>Validating input</li>
 *     <li>Calling authentication services</li>
 *     <li>Reacting to login/register responses</li>
 *     <li>Navigating to the appropriate dashboard</li>
 * </ul>
 */
public class AuthenticationController implements IDisposableProp {

    private enum Mode { LOGIN, REGISTER }

    private static final Color BLUE = Color.web("#3498db");
    private static final Color RED = Color.web("#A01316");

    private final Stage stage;
    private final IClientControllerContext clientControllerContext;
    private final ClientApplication application;
    private final AuthenticationView view;

    private Mode mode = Mode.LOGIN;
    private volatile boolean authBusy = false;

    // Listeners stored as fields for proper removal
    private final PropertyChangeListener authListener = this::handleAuthenticationChange;
    private final PropertyChangeListener loginOkListener = this::handleLoginSuccessResponse;
    private final PropertyChangeListener loginFailListener = this::handleLoginFailResponse;
    private final PropertyChangeListener registerOkListener = this::handleRegisterOkResponse;
    private final PropertyChangeListener connectionStatusListener = this::handleConnectionStatusChange;

    /**
     * Creates a new authentication controller.
     *
     * @param stage         primary stage
     * @param clientControllerContext client manager for service access
     * @param application   main JavaFX application
     */
    public AuthenticationController(Stage stage, ClientManager clientControllerContext, ClientApplication application) {
        this.stage = stage;
        this.clientControllerContext = clientControllerContext;
        this.application = application;
        this.view = new AuthenticationView();
        this.view.createView();
        this.view.registerHandlers(this);
        view.showLoginMode();
        setupPropertyChangeListeners();
    }

    /**
     * Registers all needed property change listeners on {@link ClientManager}.
     */
    private void setupPropertyChangeListeners() {

        clientControllerContext.addPropertyChangeListener(ClientManager.PROP_AUTHENTICATED, authListener);
        clientControllerContext.addPropertyChangeListener(ClientManager.PROP_LOGIN_OK, loginOkListener);
        clientControllerContext.addPropertyChangeListener(ClientManager.PROP_FAIL, loginFailListener);
        clientControllerContext.addPropertyChangeListener(ClientManager.PROP_REGISTER_OK, registerOkListener);
        clientControllerContext.addPropertyChangeListener(ClientManager.PROP_CONNECTION_STATUS, connectionStatusListener);
    }

    /**
     * Handles authentication state changes (authenticated / not authenticated).
     */
    private void handleAuthenticationChange(PropertyChangeEvent evt) {
        boolean authenticated = (boolean) evt.getNewValue();
        if (authenticated) {
            UiUtils.runOnUiThread(this::openDashboard);
        }
    }

    /**
     * Opens the proper dashboard (teacher / student) according to the user type.
     */
    private void openDashboard() {
        view.clearLoginFields();
        view.clearRegisterFields();

        String userType = clientControllerContext.getUserType();
        String email = clientControllerContext.getUserEmail();
        String name = clientControllerContext.getUserName();

        if ("TEACHER".equalsIgnoreCase(userType)) {
            application.showTeacherDashboard(name, email);
        } else {
            application.showStudentDashboard(name, email);
        }
    }

    /**
     * Handles successful login responses.
     * <p>
     * Updates client-side session state and shows inline + modal feedback.
     */
    private void handleLoginSuccessResponse(PropertyChangeEvent evt) {
        AuthResponseDTO data = (AuthResponseDTO) evt.getNewValue();
        try {
            clientControllerContext.setUserId(Integer.parseInt(data.userId()));
        } catch (NumberFormatException ignored) {
            clientControllerContext.setUserId(null);
        }
        clientControllerContext.setUserType(data.userType());
        clientControllerContext.setUserEmail(data.email());
        clientControllerContext.setUserName(data.name());
        clientControllerContext.setAuthenticated(true);

        UiUtils.runOnUiThread(() -> {
            view.setLoginStatus("✔️ Login OK! ", Color.GREEN, false, true);

            AlertUtils.showInfo(
                    stage,
                    "Login efetuado",
                    "Login efetuado com sucesso."
            );
        });
    }

    /**
     * Handles both {@code LOGIN_FAIL} and generic {@code ERROR} responses.
     * <p>
     * Shows feedback in the appropriate form (login/register) and via a modal dialog.
     */
    private void handleLoginFailResponse(PropertyChangeEvent evt) {
        String data = (String) evt.getNewValue();
        String baseMsg = (data == null || data.isBlank()) ? "Ocorreu um erro." : data;

        UiUtils.runOnUiThread(() -> {
            if (mode == Mode.LOGIN) {
                // Reuse helper to ensure label + modal are always shown
                showLoginError("Login falhou: " + baseMsg);
            } else {
                showRegisterError("Registo falhou: " + baseMsg);
            }
            authBusy = false;
            view.setAuthBusy(false);
        });
    }

    /**
     * Handles successful register responses.
     * <p>
     * Updates local state, shows inline success feedback and pops up a modal dialog.
     */
    private void handleRegisterOkResponse(PropertyChangeEvent evt) {
        AuthResponseDTO data = (AuthResponseDTO) evt.getNewValue();
        try {
            clientControllerContext.setUserId(Integer.parseInt(data.userId()));
        } catch (NumberFormatException ignored) {
            clientControllerContext.setUserId(null);
        }
        clientControllerContext.setUserType(data.userType());
        clientControllerContext.setUserEmail(data.email());
        clientControllerContext.setUserName(data.name());

        UiUtils.runOnUiThread(() -> {
            view.setRegisterStatus(
                    "✔️ Registo concluído! Já pode iniciar sessão com os seus dados.",
                    Color.GREEN,
                    true
            );

            // Modal de confirmação de sucesso
            AlertUtils.showInfo(
                    stage,
                    "Registo concluído",
                    "A sua conta foi criada com sucesso.\n" +
                            "Já pode iniciar sessão com os seus dados."
            );

            view.clearRegisterFields();
            view.clearLoginFields();

            if (    data.email() != null) {
                view.prefillLoginEmail(data.email());
            }

            mode = Mode.LOGIN;
            view.showLoginMode();

            authBusy = false;
            view.setAuthBusy(false);
        });
    }

    /**
     * Handles connection status changes (directory/server).
     */
    private void handleConnectionStatusChange(PropertyChangeEvent evt) {
        String status = (String) evt.getNewValue();

        switch (status) {
            case "DIRECTORY_CONNECTING" -> UiUtils.runOnUiThread(() ->
                    view.showGlobalLoading("A contactar a diretoria...")
            );
            case "SERVER_CONNECTING" -> UiUtils.runOnUiThread(() ->
                    view.showGlobalLoading("A ligar ao servidor principal...")
            );
            case "CONNECTED" -> UiUtils.runOnUiThread(() -> {
                view.hideGlobalLoading();
                if (!authBusy) {
                    view.setLoginStatus(
                            "Ligação estabelecida. Introduza as suas credenciais.",
                            BLUE,
                            false,
                            true
                    );
                }
            });
            case "DISCONNECTED" -> UiUtils.runOnUiThread(() ->
                    view.showGlobalLoading("Ligação ao servidor perdida. A tentar reconectar...")
            );
            case "DIRECTORY_ERROR" -> {
                UiUtils.runOnUiThread(() ->
                        view.showGlobalError(
                                "Não foi possível contactar a diretoria/servidor. \nA aplicação vai encerrar..."
                        )
                );
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException ignored) {
                }
            }
            case "SERVER_ERROR" -> UiUtils.runOnUiThread(() ->
                    view.showGlobalError(
                            "Não foi possível ligar ao servidor principal.\nA aplicação vai encerrar..."
                    )
            );
            default -> {
            }
        }
    }

    /**
     * Toggles between login and register modes.
     */
    public void onToggleMode() {
        if (authBusy) {
            return;
        }
        if (mode == Mode.LOGIN) {
            mode = Mode.REGISTER;
            view.showRegisterMode();
        } else {
            mode = Mode.LOGIN;
            view.showLoginMode();
        }
    }

    /**
     * Login button handler.
     * <p>
     * Forwards the raw credentials to the server without performing local
     * email/password validation. All validation is done on the server and
     * any error message is propagated back to the UI via the login fail
     * listener.
     */
    public void onLogin() throws InterruptedException {
        if (mode != Mode.LOGIN || authBusy) {
            return;
        }

        String email = view.getLoginEmail();
        String password = view.getLoginPassword();

        // No client-side validation of email/password format or presence.
        // The server is responsible for validating and returning a message.

        setAuthBusy(true);
        view.setLoginStatus("A autenticar...", BLUE, true, false);

        clientControllerContext.getAuthService().login(email, password);
    }

    /**
     * Register button handler.
     * <p>
     * Forwards all registration data to the server without performing local
     * validation of name/email/password or institutional code/student number.
     * The server performs all validation and returns either a successful
     * response or an error message that is shown in the UI.
     */
    public void onRegister() {
        if (mode != Mode.REGISTER || authBusy) {
            return;
        }

        String type = view.getSelectedRegisterType();
        String name = view.getRegisterName();
        String email = view.getRegisterEmail();
        String password = view.getRegisterPassword();
        String extra = view.getRegisterExtra(); // number (student) ou código (teacher)

        // 1) Cliente só garante que os campos estão preenchidos
        if (name == null || name.isBlank()
                || email == null || email.isBlank()
                || password == null || password.isBlank()
                || extra == null || extra.isBlank()) {

            showRegisterError("Todos os campos são obrigatórios.");
            return;
        }

        setAuthBusy(true);
        view.setRegisterStatus("A registar...", BLUE, false);

        try {
            if ("STUDENT".equalsIgnoreCase(type)) {
                String trimmedExtra = extra.trim();

                if (!trimmedExtra.matches("\\d+")) {
                    showRegisterError("O número de estudante só pode conter dígitos (0-9).");
                    setAuthBusy(false);
                    return;
                }

                Long number = Long.parseLong(trimmedExtra);
                clientControllerContext
                        .getAuthService()
                        .registerStudent(name, email, password, number);

            } else {
                // Teacher: o código é tratado como string, validação fica 100% no servidor
                clientControllerContext
                        .getAuthService()
                        .registerTeacher(name, email, password, extra);
            }
        } catch (Exception e) {
            UiUtils.runOnUiThread(() ->
                    showRegisterError("Erro no registo: " + e.getMessage())
            );
            setAuthBusy(false);
        }
    }


    /**
     * Handler for changes in register type (student/teacher).
     *
     * @param type "STUDENT" or "TEACHER"
     */
    public void onRegisterTypeChanged(String type) {
        if ("STUDENT".equalsIgnoreCase(type)) {
            view.setRegisterExtraLabel("Número de Estudante");
        } else {
            view.setRegisterExtraLabel("Código de Docente");
        }
    }

    /**
     * Sets the busy state and updates the view.
     */
    private void setAuthBusy(boolean busy) {
        authBusy = busy;
        UiUtils.runOnUiThread(() -> view.setAuthBusy(busy));
    }

    /**
     * Shows a login error both inline (status label) and via a modal dialog.
     *
     * @param message error message to display
     */
    private void showLoginError(String message) {
        String msg = (message == null || message.isBlank())
                ? "Ocorreu um erro ao efetuar o login."
                : message;

        view.setLoginStatus("❌ " + msg, RED, false, true);

        AlertUtils.showError(
                stage,
                "Erro de autenticação",
                msg
        );
    }

    /**
     * Shows a register error both inline (status label) and via a modal dialog.
     *
     * @param message error message to display
     */
    private void showRegisterError(String message) {
        String msg = (message == null || message.isBlank())
                ? "Ocorreu um erro ao efetuar o registo."
                : message;

        view.setRegisterStatus("❌ " + msg, RED, true);

        AlertUtils.showError(
                stage,
                "Erro no registo",
                msg
        );
    }
    /**
     * Resets the authentication view and shows it.
     */
    public void show() {
        authBusy = false;
        setAuthBusy(false);
        mode = Mode.LOGIN;
        view.showLoginMode();
        view.setLoginStatus("", BLUE, false, true);
        view.setRegisterStatus("", BLUE, true);
        view.clearLoginFields();
        view.clearRegisterFields();
        view.showBusy(false);
        stage.setScene(view.getScene());
    }

    /**
     * Removes all listeners registered on the {@link ClientManager}.
     */
    @Override
    public void dispose() {
        clientControllerContext.removePropertyChangeListener(ClientManager.PROP_AUTHENTICATED, authListener);
        clientControllerContext.removePropertyChangeListener(ClientManager.PROP_LOGIN_OK, loginOkListener);
        clientControllerContext.removePropertyChangeListener(ClientManager.PROP_FAIL, loginFailListener);
        clientControllerContext.removePropertyChangeListener(ClientManager.PROP_REGISTER_OK, registerOkListener);
        clientControllerContext.removePropertyChangeListener(ClientManager.PROP_CONNECTION_STATUS, connectionStatusListener);
    }
}
