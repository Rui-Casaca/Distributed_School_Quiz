package pt.isec.client;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.fusesource.jansi.AnsiConsole;
import pt.isec.client.core.ClientManager;
import pt.isec.client.ui.auth.AuthenticationController;
import pt.isec.client.ui.student.StudentDashboardController;
import pt.isec.client.ui.teacher.TeacherDashboardController;
import java.beans.PropertyChangeListener;

/**
 * JavaFX main application class that bootstraps the client UI and wires it
 * with the {@link ClientManager}.
 */
public class ClientApplication extends Application {

    /* ===================== CONSTANTS ===================== */

    private static final String DIRECTORY_IP = "localhost";
    //private static final String DIRECTORY_IP = "10.84.85.89";
    private static final int DIRECTORY_PORT = 9999;

    private static final String ICON_RESOURCE = "/imgs/app-icon.png";

    /* ===================== FIELDS ===================== */

    private AuthenticationController authController;
    private TeacherDashboardController teacherController;
    private StudentDashboardController studentController;

    /**
     * Listener for connection status changes (used to show reconnection/permanent error state).
     */
    private PropertyChangeListener connectionListener;

    private ClientManager clientManager;
    private Stage primaryStage;

    /**
     * Non-modal alert used to indicate reconnection attempts.
     */
    private Alert reconnectAlert;

    /* ===================== JAVAFX LIFE CYCLE ===================== */

    /**
     * JavaFX application entry point.
     *
     * @param stage primary application stage
     */
    @Override
    // TODO Cliente: código estruturado com separação entre vista e lógica de comunicação
    public void start(Stage stage) {
        // Enable colored console output (server/client logs).
        AnsiConsole.systemInstall();

        this.primaryStage = stage;
        this.clientManager = new ClientManager(DIRECTORY_IP, DIRECTORY_PORT);
        this.authController = new AuthenticationController(primaryStage, clientManager, this);

        stage.getIcons().clear();
        var iconStream = getClass().getResourceAsStream(ICON_RESOURCE);
        if (iconStream != null) {
            stage.getIcons().add(new Image(iconStream));
        }

        // Allows ClientManager to close the UI when it stops
        clientManager.setUiCloser(() -> Platform.runLater(() -> {
            if (primaryStage.isShowing()) {
                primaryStage.close();
            }
            Platform.exit();
        }));

        // Listener for connection state (reconnecting / permanently disconnected)
        connectionListener = evt -> {
            String status = evt.getNewValue() == null ? "" : evt.getNewValue().toString();

            Platform.runLater(() -> {
                // Reconnecting state -> show non-modal indicator
                if (ClientManager.STATUS_RECONNECTING.equals(status)) {
                    if (reconnectAlert == null) {
                        reconnectAlert = new Alert(Alert.AlertType.INFORMATION);
                        reconnectAlert.initOwner(primaryStage);
                        reconnectAlert.initModality(Modality.NONE);
                        reconnectAlert.setHeaderText(null);
                        //noinspection SpellCheckingInspection
                        reconnectAlert.setTitle("A tentar reconectar");

                        ProgressIndicator pi = new ProgressIndicator();
                        pi.setPrefSize(24, 24);
                        //noinspection SpellCheckingInspection
                        Label msg = new Label("Ligação perdida. A tentar reconectar...");
                        HBox content = new HBox(10, pi, msg);
                        content.setStyle("-fx-padding:10;");
                        reconnectAlert.getDialogPane().setContent(content);

                        reconnectAlert.show();
                    }
                    return;
                }

                // Successfully reconnected -> hide indicator
                if (ClientManager.STATUS_CONNECTED.equals(status)) {
                    if (reconnectAlert != null) {
                        try {
                            reconnectAlert.close();
                        } catch (Exception ignored) {
                            // ignore and just drop the alert reference
                        }
                        reconnectAlert = null;
                    }
                    return;
                }

                // Permanent error / disconnected
                if ("DIRECTORY_ERROR".equals(status)
                        || "SERVER_ERROR".equals(status)
                        || "DISCONNECTED".equals(status)
                        || ClientManager.STATUS_DISCONNECTED_PERMANENT.equals(status)) {

                    if (reconnectAlert != null) {
                        try {
                            reconnectAlert.close();
                        } catch (Exception ignored) {
                            // ignore and just drop the alert reference
                        }
                        reconnectAlert = null;
                    }

                    Alert a = new Alert(
                            Alert.AlertType.ERROR,
                            "Ligação perdida permanentemente. A aplicação vai encerrar."
                    );
                    a.initOwner(primaryStage);
                    a.setHeaderText(null);
                    a.show();
                    // Actual closing is performed via uiCloser in ClientManager.
                }
            });
        };

        // Register connection listener
        clientManager.addPropertyChangeListener(
                ClientManager.PROP_CONNECTION_STATUS,
                connectionListener
        );

        primaryStage.setTitle("Sistema de Gestão de Perguntas");

        showAuthentication();
        primaryStage.show();

        // Start the client in a separate thread
        new Thread(clientManager::start, "client-core").start();
    }

    /**
     * Called when the JavaFX application is stopping.
     * <p>
     * Removes the connection listener and stops the {@link ClientManager}.
     */
    @Override
    public void stop() {
        if (clientManager != null) {
            if (connectionListener != null) {
                try {
                    clientManager.removePropertyChangeListener(
                            ClientManager.PROP_CONNECTION_STATUS,
                            connectionListener
                    );
                } catch (Exception ignored) {
                    // best-effort cleanup
                }
                connectionListener = null;
            }
            clientManager.stop();
        }
    }

    /* ===================== NAVIGATION HELPERS ===================== */

    /**
     * Shows the authentication screen and clears any existing dashboard controllers.
     */
    public void showAuthentication() {
        teacherController = null;
        studentController = null;
        authController.show();
    }

    /**
     * Shows the teacher dashboard for the given user.
     *
     * @param name  teacher name
     * @param email teacher email
     */
    public void showTeacherDashboard(String name, String email) {
        teacherController = new TeacherDashboardController(primaryStage, clientManager, this, name, email);
        teacherController.show();
    }

    /**
     * Shows the student dashboard for the given user.
     *
     * @param name  student name
     * @param email student email
     */
    public void showStudentDashboard(String name, String email) {
        studentController = new StudentDashboardController(primaryStage, clientManager, this, name, email);
        studentController.show();
    }
}
