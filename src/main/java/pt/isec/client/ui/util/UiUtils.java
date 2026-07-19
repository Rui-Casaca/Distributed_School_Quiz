package pt.isec.client.ui.util;
import javafx.application.Platform;

/**
 * Utility helper methods for JavaFX UI-related operations.
 */
public final class UiUtils {

    private UiUtils() {}

    /**
     * Ensures that the given runnable is executed on the JavaFX Application Thread.
     * <p>
     * If the current thread is already the JavaFX thread, the runnable is executed
     * immediately; otherwise it is scheduled via {@link Platform#runLater(Runnable)}.
     *
     * @param runnable action to execute on the UI thread (may be {@code null})
     */
    public static void runOnUiThread(Runnable runnable) {
        if (runnable == null) {
            return;
        }

        if (Platform.isFxApplicationThread()) {
            runnable.run();
        } else {
            Platform.runLater(runnable);
        }
    }
}
