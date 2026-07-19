package pt.isec.client.ui.student;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * Student dashboard view (dark theme).
 * <p>
 * Styling is defined in {@code dashboard.css}.
 */
public class StudentDashboardView {

    private String userName;
    private String userEmail;

    private Scene scene;

    // Header
    private Label welcomeLabel;
    private Label headerEmailLabel;

    // Notifications
    private TextArea notificationArea;

    // Sidebar / profile
    private Label profileNameLabel;
    private Label avatarInitialsLabel;
    private VBox profileCard;

    // Menu buttons
    private Button answerQuestionBtn;
    private Button historyBtn;
    private Button logoutBtn;
    private Button profileBtn;

    // Lightweight loading overlay in header
    private HBox loadingBox;

    /**
     * Creates a student dashboard view.
     *
     * @param userName initial student name
     * @param userEmail initial student email
     */
    public StudentDashboardView(String userName, String userEmail) {
        this.userName = userName;
        this.userEmail = userEmail;
    }

    /**
     * Builds the main scene and UI components.
     */
    public void createView() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("dashboard-root-dark");

        root.setTop(createHeader());
        root.setLeft(createSidebar());
        root.setCenter(createMainArea());

        scene = new Scene(root, 1000, 700);
        try {
            var cssUrl = getClass().getResource("/styles/dashboard.css");
            if (cssUrl != null) {
                scene.getStylesheets().add(cssUrl.toExternalForm());
            }
        } catch (Exception ignored) {
        }
    }

    /* =================== HEADER =================== */

    private VBox createHeader() {
        VBox header = new VBox(10);
        header.getStyleClass().add("dashboard-header-dark");

        welcomeLabel = new Label("Bem-vindo, " +
                (userName != null ? userName : "Estudante") + "!");
        welcomeLabel.setFont(Font.font("Arial", FontWeight.NORMAL, 24));
        welcomeLabel.getStyleClass().add("header-welcome");

        headerEmailLabel = new Label(userEmail != null ? userEmail : "");
        headerEmailLabel.getStyleClass().add("header-email-dark");

        // Non-modal loading indicator under header
        loadingBox = new HBox(8);
        loadingBox.setAlignment(Pos.CENTER_LEFT);
        loadingBox.setPadding(new Insets(4, 0, 0, 0));
        ProgressIndicator pi = new ProgressIndicator();
        pi.setPrefSize(16, 16);
        Label loadingLabel = new Label("");
        loadingLabel.setStyle("-fx-text-fill: #7f8c8d;");
        loadingBox.getChildren().addAll(pi, loadingLabel);
        loadingBox.setVisible(false);

        header.getChildren().addAll(welcomeLabel, headerEmailLabel, loadingBox);
        return header;
    }

    /**
     * Shows a small non-modal loading indicator in the header.
     *
     * @param message message to display next to the spinner
     */
    public void showLoading(String message) {
        if (loadingBox == null) {
            return;
        }
        Platform.runLater(() -> {
            Label label = (Label) loadingBox.getChildren().get(1);
            label.setText(message);
            loadingBox.setVisible(true);
        });
    }

    /**
     * Hides the header loading indicator.
     */
    public void hideLoading() {
        if (loadingBox == null) {
            return;
        }
        Platform.runLater(() -> loadingBox.setVisible(false));
    }

    /* =================== SIDEBAR / PROFILE =================== */

    private VBox createSidebar() {
        VBox sidebar = new VBox(12);
        sidebar.getStyleClass().add("dashboard-sidebar-dark");
        sidebar.setPrefWidth(240);

        profileCard = createProfileCard();

        Label menuLabel = new Label("MENU");
        menuLabel.getStyleClass().add("sidebar-title-dark");

        HBox menuWrapper = new HBox(menuLabel);
        menuWrapper.setAlignment(Pos.CENTER);

        answerQuestionBtn = createMenuButton("Responder Pergunta");
        profileBtn = createMenuButton("Perfil");
        historyBtn = createMenuButton("Histórico");
        logoutBtn = createMenuButton("Logout");

        sidebar.getChildren().addAll(
                profileCard,
                new Separator(),
                menuWrapper,
                new Separator(),
                profileBtn,
                answerQuestionBtn,
                historyBtn,
                logoutBtn
        );

        return sidebar;
    }

    private VBox createProfileCard() {
        VBox card = new VBox(8);
        card.getStyleClass().add("profile-card");
        card.setAlignment(Pos.CENTER);

        StackPane avatarCircle = new StackPane();
        avatarCircle.getStyleClass().add("profile-avatar-circle");

        avatarInitialsLabel = new Label(getInitials(userName != null ? userName : userEmail));
        avatarInitialsLabel.getStyleClass().add("profile-avatar-initials");
        avatarCircle.getChildren().add(avatarInitialsLabel);

        profileNameLabel = new Label(userName != null ? userName : "Utilizador");
        profileNameLabel.getStyleClass().add("profile-name-label");

        Label roleLabel = new Label("Estudante");
        roleLabel.getStyleClass().add("profile-role-label");

        card.getChildren().addAll(avatarCircle, profileNameLabel, roleLabel);
        return card;
    }

    private String getInitials(String text) {
        if (text == null || text.isBlank()) {
            return "?";
        }
        String[] parts = text.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, 1).toUpperCase();
        }
        return ("" + parts[0].charAt(0) + parts[parts.length - 1].charAt(0)).toUpperCase();
    }

    /**
     * Updates the profile name, welcome message and avatar initials.
     *
     * @param name new student name
     */
    public void setProfileName(String name) {
        if (name == null || name.isBlank()) {
            return;
        }
        this.userName = name;
        if (profileNameLabel != null) {
            profileNameLabel.setText(name);
        }
        if (welcomeLabel != null) {
            welcomeLabel.setText("Bem-vindo, " + name + "!");
        }
        if (avatarInitialsLabel != null) {
            avatarInitialsLabel.setText(getInitials(name));
        }
    }

    /* =================== MAIN AREA =================== */

    private VBox createMainArea() {
        VBox mainArea = new VBox(20);
        mainArea.getStyleClass().add("dashboard-main-area");
        mainArea.setPadding(new Insets(20));

        Label notifLabel = new Label("Notificações");
        notifLabel.setFont(Font.font("Arial", FontWeight.NORMAL, 18));
        notifLabel.getStyleClass().add("dashboard-section-title");

        notificationArea = new TextArea();
        notificationArea.setEditable(false);
        notificationArea.setPrefHeight(150);
        notificationArea.setPromptText("Aguardando notificações do servidor...");
        notificationArea.getStyleClass().add("notification-area-dark");
        notificationArea.setWrapText(true);

        Label instructionsLabel = new Label("Como começar:");
        instructionsLabel.setFont(Font.font("Arial", FontWeight.NORMAL, 16));
        instructionsLabel.getStyleClass().add("dashboard-section-title");

        VBox instructions = new VBox(10);
        instructions.getStyleClass().add("instructions-box-dark");
        instructions.setAlignment(Pos.TOP_LEFT);

        Label inst1 = new Label("1. Obtenha o código da pergunta com o seu docente");
        Label inst2 = new Label("2. Clique em 'Responder Pergunta' no menu lateral");
        Label inst3 = new Label("3. Insira o código e responda à pergunta");
        Label inst4 = new Label("4. Verifique seu histórico de respostas a qualquer momento");

        inst1.getStyleClass().add("instruction-line");
        inst2.getStyleClass().add("instruction-line");
        inst3.getStyleClass().add("instruction-line");
        inst4.getStyleClass().add("instruction-line");

        instructions.getChildren().addAll(inst1, inst2, inst3, inst4);

        mainArea.getChildren().addAll(
                notifLabel,
                notificationArea,
                instructionsLabel,
                instructions
        );
        return mainArea;
    }

    private Button createMenuButton(String text) {
        Button btn = new Button(text);
        btn.getStyleClass().add("sidebar-button-dark");
        btn.setPrefWidth(Double.MAX_VALUE);
        return btn;
    }

    /* =================== PUBLIC API =================== */

    /**
     * Returns the JavaFX scene for this view.
     *
     * @return scene
     */
    public Scene getScene() {
        return scene;
    }

    private String deriveNameFromEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "Estudante";
        }
        String part = email.substring(0, email.indexOf('@'));
        if (part.isEmpty()) {
            return "Estudante";
        }
        return Character.toUpperCase(part.charAt(0)) + part.substring(1);
    }

    /**
     * Keeps name/email in sync with the server (header + profile + avatar).
     *
     * @param name  new name
     * @param email new email
     */
    public void updateUserInfo(String name, String email) {
        if (name != null && !name.isBlank()) {
            setProfileName(name);
        } else if ((this.userName == null || this.userName.isBlank()) && email != null) {
            String derived = deriveNameFromEmail(email);
            setProfileName(derived);
        }

        if (email != null && !email.isBlank()) {
            this.userEmail = email;
            if (headerEmailLabel != null) {
                headerEmailLabel.setText(email);
            }
        }

        String baseForInitials = (this.userName != null && !this.userName.isBlank())
                ? this.userName
                : (this.userEmail != null ? this.userEmail : null);
        if (baseForInitials != null && avatarInitialsLabel != null) {
            avatarInitialsLabel.setText(getInitials(baseForInitials));
        }
    }

    /**
     * Registers handlers that delegate actions to the given controller.
     *
     * @param controller student dashboard controller
     */
    public void registerHandlers(StudentDashboardController controller) {
        answerQuestionBtn.setOnAction(_ -> controller.onAnswerQuestion());
        historyBtn.setOnAction(_ -> controller.onShowHistory());
        logoutBtn.setOnAction(_ -> controller.onLogout());
        profileBtn.setOnAction(_ -> controller.onProfile());

        if (profileCard != null) {
            profileCard.setOnMouseClicked(_ -> controller.onProfile());
        }
    }

    /**
     * Hook for future UI updates.
     */
    public void update() {
        // nothing extra for now
    }

    /**
     * Appends a timestamped notification to the notification area.
     *
     * @param message notification message
     */
    public void addNotification(String message) {
        if (notificationArea == null) {
            return;
        }
        String timestamp = java.time.LocalTime.now().format(
                java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")
        );
        notificationArea.appendText("[" + timestamp + "] " + message + "\n");
    }
}
