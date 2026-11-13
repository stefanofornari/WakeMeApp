package ste.wakemeapp.settings;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Duration;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.text.Font;
import javafx.stage.Modality;
import javafx.stage.Stage;

/**
 * The settings dialog: one window, opened from the tray or with --settings. Showing it again brings
 * the open one to the front. It does not block the rest of the application, so a meeting reminder
 * still appears while it is open. Call every method on the JavaFX Application Thread.
 */
public final class SettingsDialog {

    private final Stage stage = new Stage();
    private final SettingsController controller;

    /**
     * @param stored       where the calendar is kept
     * @param settings     where the other settings are kept
     * @param pollInterval how often the calendar is polled
     * @param listener     told what was saved
     */
    public SettingsDialog(StoredCalendar stored, Settings settings, Duration pollInterval, SettingsListener listener) {
        loadFont("Inter-Regular.ttf");
        loadFont("Inter-Bold.ttf");
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ste/wakemeapp/settings/settings.fxml"));
            Parent root = loader.load();
            controller = loader.getController();
            Scene scene = new Scene(root);
            scene.getStylesheets().add(getClass().getResource("/ste/wakemeapp/settings/settings.css").toExternalForm());
            scene.setOnKeyPressed(event -> {
                if (event.getCode() == KeyCode.ESCAPE) {
                    stage.hide();
                }
            });
            stage.setScene(scene);
        } catch (IOException x) {
            throw new UncheckedIOException("Cannot load the settings dialog", x);
        }
        stage.setTitle("WakeMeApp Settings");
        stage.initModality(Modality.NONE);
        stage.setResizable(false);
        controller.bind(stored, settings, pollInterval, listener, stage::hide);
    }

    /** Opens the dialog with what is saved, or brings the open one to the front (its edits are kept). */
    public void show() {
        if (!stage.isShowing()) {
            controller.load();
            stage.show();
        }
        stage.toFront();
        stage.requestFocus();
    }

    public boolean isShowing() {
        return stage.isShowing();
    }

    Stage getStage() {
        return stage;
    }

    SettingsController getController() {
        return controller;
    }

    private static void loadFont(String name) {
        try (InputStream in = SettingsDialog.class.getResourceAsStream("/ste/wakemeapp/notify/fonts/" + name)) {
            if (in != null) {
                Font.loadFont(in, 13);
            }
        } catch (IOException ignored) {
            // the system font is used instead
        }
    }
}
