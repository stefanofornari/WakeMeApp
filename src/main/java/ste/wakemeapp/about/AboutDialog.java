package ste.wakemeapp.about;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.function.Consumer;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.text.Font;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import ste.wakemeapp.ui.WindowDrag;

/**
 * The About dialog: one window, opened from the tray. Showing it again brings the open one to the
 * front. It does not block the rest of the application, so a meeting reminder still appears while it
 * is open, and closing it does not end the application. Call every method on the JavaFX Application Thread.
 */
public final class AboutDialog {

    private final Stage stage = new Stage();
    private final AboutController controller;

    /**
     * @param version the version to show ({@link AppInfo#version()})
     * @param opener  opens a link in the browser
     */
    public AboutDialog(String version, Consumer<String> opener) {
        loadFont("Inter-Regular.ttf");
        loadFont("Inter-Bold.ttf");
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ste/wakemeapp/about/about.fxml"));
            Parent root = loader.load();
            controller = loader.getController();
            Scene scene = new Scene(root);
            scene.getStylesheets().add(getClass().getResource("/ste/wakemeapp/about/about.css").toExternalForm());
            scene.setOnKeyPressed(event -> {
                if (event.getCode() == KeyCode.ESCAPE) {
                    stage.hide();
                }
            });
            stage.setScene(scene);
        } catch (IOException x) {
            throw new UncheckedIOException("Cannot load the About dialog", x);
        }
        stage.setTitle("About " + AppInfo.NAME);
        stage.initStyle(StageStyle.UNDECORATED);   // no title bar: it is moved by dragging the window
        stage.initModality(Modality.NONE);
        stage.setResizable(false);
        WindowDrag.enable(stage.getScene().getRoot(), stage);
        controller.bind(version, opener, stage::hide);
    }

    /** Opens the dialog, or brings the open one to the front. */
    public void show() {
        if (!stage.isShowing()) {
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

    AboutController getController() {
        return controller;
    }

    private static void loadFont(String name) {
        try (InputStream in = AboutDialog.class.getResourceAsStream("/ste/wakemeapp/notify/fonts/" + name)) {
            if (in != null) {
                Font.loadFont(in, 13);
            }
        } catch (IOException ignored) {
            // the system font is used
        }
    }
}
