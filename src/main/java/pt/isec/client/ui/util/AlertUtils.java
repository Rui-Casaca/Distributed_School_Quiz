package pt.isec.client.ui.util;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.layout.Region;
import javafx.stage.Window;

/**
 * Utility methods for showing standard JavaFX alerts (error, info,
 * confirmation) in a consistent way across the application.
 * <p>
 * UI texts are in Portuguese, but all comments and documentation
 * are written in English.
 */
public final class AlertUtils {

    private AlertUtils() {
        // Utility class – no instances
    }

    /* =========================================================
     *                      PUBLIC API
     * ========================================================= */

    /**
     * Shows an error dialog.
     *
     * @param owner   owner window (may be {@code null})
     * @param title   dialog title/header (in Portuguese)
     * @param message error message to display (in Portuguese)
     */
    public static void showError(Window owner, String title, String message) {
        showAlert(owner, Alert.AlertType.ERROR, title, message);
    }

    /**
     * Shows an information dialog.
     *
     * @param owner   owner window (may be {@code null})
     * @param title   dialog title/header (in Portuguese)
     * @param message information message to display (in Portuguese)
     */
    public static void showInfo(Window owner, String title, String message) {
        showAlert(owner, Alert.AlertType.INFORMATION, title, message);
    }

    /**
     * Shows a confirmation dialog with OK/Cancel (or Yes/No, depending
     * on platform) and returns {@code true} when the user accepts.
     *
     * @param owner   owner window (may be {@code null})
     * @param title   dialog title (in Portuguese)
     * @param header  header text (may be {@code null} or blank)
     * @param message message text (may be {@code null} or blank)
     * @return {@code true} if user confirmed the action
     */
    public static boolean showConfirmation(Window owner,
                                           String title,
                                           String header,
                                           String message) {

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        if (owner != null) {
            alert.initOwner(owner);
        }

        String safeTitle = isBlank(title) ? "Confirmação" : title;
        String safeHeader = isBlank(header) ? null : header;
        String safeMessage = isBlank(message) ? "" : message;

        alert.setTitle(safeTitle);
        alert.setHeaderText(safeHeader);
        alert.setContentText(safeMessage);

        // Ensure the dialog grows to fit the content
        alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);

        return alert.showAndWait()
                .filter(btn -> btn == ButtonType.OK || btn == ButtonType.YES)
                .isPresent();
    }

    /* =========================================================
     *                      PRIVATE HELPERS
     * ========================================================= */

    /**
     * Internal helper to create and show an alert with sane defaults.
     *
     * @param owner   owner window (may be {@code null})
     * @param type    alert type
     * @param title   dialog title/header (in Portuguese)
     * @param message content message (in Portuguese)
     */
    private static void showAlert(Window owner,
                                  Alert.AlertType type,
                                  String title,
                                  String message) {

        Alert alert = new Alert(type);
        if (owner != null) {
            alert.initOwner(owner);
        }

        String defaultTitle;
        if (type == Alert.AlertType.ERROR) {
            defaultTitle = "Erro";
        } else if (type == Alert.AlertType.INFORMATION) {
            defaultTitle = "Informação";
        } else {
            defaultTitle = "Mensagem";
        }

        String safeTitle = isBlank(title) ? defaultTitle : title;

        String safeMessage;
        if (isBlank(message)) {
            safeMessage = "Ocorreu um erro inesperado.";
        } else {
            safeMessage = message;
        }

        alert.setTitle(safeTitle);
        alert.setHeaderText(safeTitle);
        alert.setContentText(safeMessage);

        alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);

        alert.showAndWait();
    }

    /**
     * Returns {@code true} if the given string is {@code null} or blank.
     */
    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
