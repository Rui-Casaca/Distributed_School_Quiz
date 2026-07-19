package pt.isec.client.ui.auth;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;

import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * Authentication view (login/register).
 * <p>
 * Appearance and styling are defined in {@code authentication.css}.
 */
public class AuthenticationView {

    private Scene scene;

    // Toggle login/register
    private Button toggleModeButton;

    // Titles on the right panel
    private Label rightTitleLabel;
    private Label rightSubtitleLabel;

    // Forms
    private VBox loginFormBox;
    private VBox registerFormBox;

    // Login components
    private TextField loginEmailField;
    private PasswordField loginPasswordField;
    private Button loginButton;
    private ProgressIndicator loginProgress;
    private Label loginStatusLabel;

    // Register components
    private RadioButton rbStudent;
    private RadioButton rbTeacher;
    private Label registerExtraLabel;
    private TextField registerNameField;
    private TextField registerEmailField;
    private PasswordField registerPasswordField;
    private TextField registerExtraField;
    private Button registerButton;
    private Label registerStatusLabel;
    private TextFormatter<String> studentNumberFormatter;

    // Semi-transparent overlay to block the UI
    private StackPane busyOverlay;
    private Label busyLabel;
    private ProgressIndicator busySpinner;

    /**
     * Creates and configures the full authentication UI.
     */
    public void createView() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("auth-root");

        // Left panel (logo + message)
        VBox leftPane = new VBox();
        leftPane.getStyleClass().add("left-pane");
        leftPane.setPrefWidth(380);

        VBox leftInner = new VBox(20);
        leftInner.getStyleClass().add("left-inner");

        ImageView logoView = new ImageView();
        var is = AuthenticationView.class.getResourceAsStream("/imgs/logo_isec.png");
        if (is != null) {
            Image logo = new Image(is);
            logoView.setImage(logo);
            logoView.setPreserveRatio(true);
            logoView.setFitHeight(120);
        }

        Label welcomeTitle = new Label("Bem-vindo!");
        welcomeTitle.getStyleClass().add("left-title");

        Label welcomeText = new Label("Autentique-se ou crie uma conta para continuar.");
        welcomeText.getStyleClass().add("left-subtitle");
        welcomeText.setWrapText(true);

        toggleModeButton = new Button("CRIAR CONTA");
        toggleModeButton.getStyleClass().add("toggle-mode-button");

        leftInner.getChildren().addAll(logoView, welcomeTitle, welcomeText, toggleModeButton);
        leftPane.getChildren().add(leftInner);

        // Right panel (forms)
        VBox rightPane = new VBox();
        rightPane.getStyleClass().add("right-pane");
        rightPane.setAlignment(Pos.CENTER);
        rightPane.setPadding(new Insets(40));

        rightTitleLabel = new Label("Entrar");
        rightTitleLabel.getStyleClass().add("right-title");

        rightSubtitleLabel = new Label("Use o seu email e password.");
        rightSubtitleLabel.getStyleClass().add("right-subtitle");
        rightSubtitleLabel.setWrapText(true);

        VBox header = new VBox(5, rightTitleLabel, rightSubtitleLabel);
        header.setAlignment(Pos.CENTER);

        StackPane formsContainer = new StackPane();
        formsContainer.setPadding(new Insets(30, 0, 0, 0));

        loginFormBox = createLoginForm();
        registerFormBox = createRegisterForm();

        formsContainer.getChildren().addAll(registerFormBox, loginFormBox);

        VBox centerBox = new VBox(30, header, formsContainer);
        centerBox.setAlignment(Pos.CENTER);
        rightPane.getChildren().add(centerBox);

        root.setLeft(leftPane);
        root.setCenter(rightPane);

        // Root stack with overlay
        StackPane stack = new StackPane();
        stack.getChildren().add(root);

        // Global loading overlay
        busyOverlay = new StackPane();
        busyOverlay.setVisible(false);
        busyOverlay.setPickOnBounds(true);
        busyOverlay.setMouseTransparent(false);
        busyOverlay.setStyle("-fx-background-color: rgba(0,0,0,0.55);");

        VBox loadingBox = new VBox(12);
        loadingBox.setAlignment(Pos.CENTER);
        loadingBox.setStyle(
                "-fx-background-color: #1b2a3a;" +
                        "-fx-padding: 32;" +
                        "-fx-background-radius: 20;"
        );

        busySpinner = new ProgressIndicator();
        busySpinner.setMaxSize(60, 60);

        busyLabel = new Label("Aguarde...");
        busyLabel.getStyleClass().add("status-label");
        busyLabel.setWrapText(true);
        busyLabel.setMaxWidth(420);
        busyLabel.setMinWidth(350);
        busyLabel.setPrefWidth(380);
        busyLabel.setAlignment(Pos.CENTER);
        busyLabel.setTextFill(Color.web("#e0e6f0"));

        loadingBox.getChildren().addAll(busySpinner, busyLabel);
        busyOverlay.getChildren().add(loadingBox);
        StackPane.setAlignment(loadingBox, Pos.CENTER);

        busyOverlay.prefWidthProperty().bind(stack.widthProperty());
        busyOverlay.prefHeightProperty().bind(stack.heightProperty());

        stack.getChildren().add(busyOverlay);

        scene = new Scene(stack, 900, 600);

        try {
            var cssUrl = getClass().getResource("/styles/authentication.css");
            if (cssUrl != null) {
                scene.getStylesheets().add(cssUrl.toExternalForm());
            }
        } catch (Exception ignored) {
        }

        showLoginMode();
    }

    /**
     * Creates the login form.
     *
     * @return login form container
     */
    private VBox createLoginForm() {
        VBox form = new VBox(12);
        form.getStyleClass().add("auth-form");
        form.setAlignment(Pos.CENTER);
        form.setMaxWidth(340);

        loginEmailField = new TextField();
        loginEmailField.setPromptText("Email");
        loginEmailField.getStyleClass().add("form-field");

        loginPasswordField = new PasswordField();
        loginPasswordField.setPromptText("Password");
        loginPasswordField.getStyleClass().add("form-field");

        loginButton = new Button("ENTRAR");
        loginButton.getStyleClass().add("primary-pill-button");
        loginButton.setPrefWidth(220);
        loginButton.setDefaultButton(true);

        loginProgress = new ProgressIndicator();
        loginProgress.setMaxSize(30, 30);
        loginProgress.setVisible(false);

        loginStatusLabel = new Label();
        loginStatusLabel.getStyleClass().add("status-label");
        loginStatusLabel.setWrapText(true);
        loginStatusLabel.setAlignment(Pos.CENTER);
        loginStatusLabel.setMaxWidth(340);

        form.getChildren().addAll(
                loginEmailField,
                loginPasswordField,
                loginButton,
                loginProgress,
                loginStatusLabel
        );
        return form;
    }

    /**
     * Shows the global overlay in normal (information) mode.
     *
     * @param message message to display
     */
    public void showGlobalLoading(String message) {
        if (busyOverlay == null) {
            return;
        }

        busySpinner.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        busyLabel.setText(message != null ? message : "Aguarde...");
        busyLabel.setTextFill(Color.web("#e0e6f0"));
        busyOverlay.setVisible(true);
        busyOverlay.toFront();
    }

    /**
     * Shows the global overlay in error mode (red message).
     *
     * @param message error message
     */
    public void showGlobalError(String message) {
        if (busyOverlay == null) {
            return;
        }

        busySpinner.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        busyLabel.setText(message != null ? message : "Ocorreu um erro.");
        busyLabel.setTextFill(Color.web("#e74c3c"));
        busyLabel.setTextAlignment(TextAlignment.CENTER);
        busyOverlay.setVisible(true);
        busyOverlay.toFront();
    }

    /**
     * Hides the global loading overlay.
     */
    public void hideGlobalLoading() {
        if (busyOverlay != null) {
            busyOverlay.setVisible(false);
        }
    }

    /**
     * Creates the register form.
     *
     * @return register form container
     */
    private VBox createRegisterForm() {
        VBox form = new VBox(12);
        form.getStyleClass().add("auth-form");
        form.setAlignment(Pos.CENTER);
        form.setMaxWidth(340);

        ToggleGroup typeGroup = new ToggleGroup();
        rbStudent = new RadioButton("Estudante");
        rbTeacher = new RadioButton("Docente");
        rbStudent.setToggleGroup(typeGroup);
        rbTeacher.setToggleGroup(typeGroup);
        rbStudent.setSelected(true);
        rbStudent.getStyleClass().add("radio-dark");
        rbTeacher.getStyleClass().add("radio-dark");

        HBox typeBox = new HBox(15, rbStudent, rbTeacher);
        typeBox.setAlignment(Pos.CENTER);

        registerNameField = new TextField();
        registerNameField.setPromptText("Nome completo");
        registerNameField.getStyleClass().add("form-field");

        registerEmailField = new TextField();
        registerEmailField.setPromptText("Email");
        registerEmailField.getStyleClass().add("form-field");

        registerPasswordField = new PasswordField();
        registerPasswordField.setPromptText("Password (mínimo 6)");
        registerPasswordField.getStyleClass().add("form-field");

        registerExtraLabel = new Label("Número de Estudante");
        registerExtraLabel.getStyleClass().add("label-dark");

        registerExtraField = new TextField();
        registerExtraField.setPromptText("Ex: 123456");
        registerExtraField.getStyleClass().add("form-field");

        // ---------- NOVO: TextFormatter apenas para NÚMERO DE ESTUDANTE ----------
        UnaryOperator<TextFormatter.Change> digitsFilter = change -> {
            String newText = change.getControlNewText();
            if (newText.matches("\\d*")) { // só dígitos
                return change;
            }
            return null; // rejeita letras, símbolos, etc.
        };
        studentNumberFormatter = new TextFormatter<>(digitsFilter);

        // estado inicial do formulário é "Estudante"
        registerExtraField.setTextFormatter(studentNumberFormatter);

        registerButton = new Button("CRIAR CONTA");
        registerButton.getStyleClass().add("primary-pill-button");
        registerButton.setPrefWidth(220);
        registerButton.setDefaultButton(false);

        registerStatusLabel = new Label();
        registerStatusLabel.getStyleClass().add("status-label");
        registerStatusLabel.setWrapText(true);
        registerStatusLabel.setMaxWidth(340);

        form.getChildren().addAll(
                typeBox,
                registerNameField,
                registerEmailField,
                registerPasswordField,
                registerExtraLabel,
                registerExtraField,
                registerButton,
                registerStatusLabel
        );
        return form;
    }


    /**
     * Switches the view to login mode.
     */
    public void showLoginMode() {
        rightTitleLabel.setText("Entrar");
        rightSubtitleLabel.setText("Use o seu email e password.");
        rightSubtitleLabel.setWrapText(true);
        loginFormBox.setVisible(true);
        loginFormBox.setManaged(true);

        registerFormBox.setVisible(false);
        registerFormBox.setManaged(false);

        toggleModeButton.setText("CRIAR CONTA");
        loginButton.setDefaultButton(true);
        registerButton.setDefaultButton(false);
    }

    /**
     * Switches the view to register mode.
     */
    public void showRegisterMode() {
        rightTitleLabel.setText("Criar Conta");
        rightSubtitleLabel.setText("Use o seu email institucional.");
        rightSubtitleLabel.setWrapText(true);
        loginFormBox.setVisible(false);
        loginFormBox.setManaged(false);

        registerFormBox.setVisible(true);
        registerFormBox.setManaged(true);

        toggleModeButton.setText("ENTRAR");
        loginButton.setDefaultButton(false);
        registerButton.setDefaultButton(true);
    }

    /* ===== Public API for the controller ===== */

    public Scene getScene() {
        return scene;
    }

    public String getLoginEmail() {
        return loginEmailField.getText().trim();
    }

    public String getLoginPassword() {
        return loginPasswordField.getText();
    }

    public String getRegisterName() {
        return registerNameField.getText().trim();
    }

    public String getRegisterEmail() {
        return registerEmailField.getText().trim();
    }

    public String getRegisterPassword() {
        return registerPasswordField.getText();
    }

    public String getRegisterExtra() {
        return registerExtraField.getText().trim();
    }

    public String getSelectedRegisterType() {
        return rbStudent.isSelected() ? "STUDENT" : "TEACHER";
    }

    public void setRegisterExtraLabel(String text) {
        registerExtraLabel.setText(text);

        if ("Número de Estudante".equalsIgnoreCase(text)) {
            // Modo ALUNO: só números permitidos
            registerExtraField.setPromptText("Ex: 123456");
            registerExtraField.setTextFormatter(studentNumberFormatter);

            // limpa quaisquer caracteres não numéricos que já lá estejam
            String current = registerExtraField.getText();
            if (current != null) {
                registerExtraField.setText(current.replaceAll("\\D", ""));
            }
        } else {
            // Modo DOCENTE: pode escrever letras e números (ex: DOCENTE2025)
            registerExtraField.setPromptText("Código fornecido pela instituição");
            registerExtraField.setTextFormatter(null); // remove o filtro, volta a ser livre
        }
    }


    public void prefillLoginEmail(String email) {
        loginEmailField.setText(email);
    }

    /**
     * Updates the login status message and progress indicator.
     */
    public void setLoginStatus(String message, Color color,
                               boolean showProgress, boolean loginButtonEnabled) {
        loginStatusLabel.setText(message == null ? "" : message);
        loginStatusLabel.setTextFill(color != null ? color : Color.WHITE);
        loginProgress.setVisible(showProgress);
        loginButton.setDisable(!loginButtonEnabled);
        loginButton.setDefaultButton(loginButtonEnabled);
    }

    /**
     * Updates the register status message.
     */
    public void setRegisterStatus(String message, Color color, boolean buttonEnabled) {
        registerStatusLabel.setText(message == null ? "" : message);
        registerStatusLabel.setTextFill(color != null ? color : Color.WHITE);
        registerButton.setDisable(!buttonEnabled);
        registerButton.setDefaultButton(buttonEnabled);
    }

    /**
     * Enables/disables all auth fields and shows/hides the overlay.
     */
    public void setAuthBusy(boolean busy) {
        toggleModeButton.setDisable(busy);
        loginEmailField.setDisable(busy);
        loginPasswordField.setDisable(busy);
        rbStudent.setDisable(busy);
        rbTeacher.setDisable(busy);
        registerNameField.setDisable(busy);
        registerEmailField.setDisable(busy);
        registerPasswordField.setDisable(busy);
        registerExtraField.setDisable(busy);
        showBusy(busy);
    }

    public void showBusy(boolean busy) {
        if (busy) {
            showGlobalLoading("Aguarde...");
        } else {
            hideGlobalLoading();
        }
    }

    public void clearLoginFields() {
        loginEmailField.clear();
        loginPasswordField.clear();
        setLoginStatus("", Color.WHITE, false, true);
    }

    public void clearRegisterFields() {
        registerNameField.clear();
        registerEmailField.clear();
        registerPasswordField.clear();
        registerExtraField.clear();
        rbStudent.setSelected(true);
        rbTeacher.setSelected(false);
        setRegisterExtraLabel("Número de Estudante");
        setRegisterStatus("", Color.WHITE, true);
    }

    /**
     * Registers handlers that delegate actions to the given controller.
     *
     * @param controller authentication controller
     */
    public void registerHandlers(AuthenticationController controller) {
        toggleModeButton.setOnAction(_ -> controller.onToggleMode());

        loginPasswordField.setOnAction(_ -> {
            try {
                controller.onLogin();
            } catch (InterruptedException ex) {
                throw new RuntimeException(ex);
            }
        });
        loginButton.setOnAction(_ -> {
            try {
                controller.onLogin();
            } catch (InterruptedException ex) {
                throw new RuntimeException(ex);
            }
        });

        registerButton.setOnAction(_ -> controller.onRegister());
        rbStudent.setOnAction(_ -> controller.onRegisterTypeChanged("STUDENT"));
        rbTeacher.setOnAction(_ -> controller.onRegisterTypeChanged("TEACHER"));
    }

    /**
     * Hook for future UI updates.
     */
    public void update() {}
}
