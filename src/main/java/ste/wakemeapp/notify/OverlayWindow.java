package ste.wakemeapp.notify;

import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.io.IOException;

public final class OverlayWindow {

    private final Stage stage;
    private final FadeTransition fadeIn;
    private final FadeTransition fadeOut;
    private final OverlayController controller;
    private MeetingNotification notification;
    private javafx.animation.Timeline countdownTimeline;

    public OverlayWindow() {
        this.stage = createStage();
        this.controller = loadFXML();
        setupStage();
        this.fadeIn = createFadeInTransition();
        this.fadeOut = createFadeOutTransition();
    }

    private Stage createStage() {
        Stage stage = new Stage(StageStyle.TRANSPARENT);
        stage.setAlwaysOnTop(true);
        stage.initModality(javafx.stage.Modality.NONE);
        return stage;
    }

    private OverlayController loadFXML() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ste/wakemeapp/notify/overlay.fxml"));
            Parent root = loader.load();
            OverlayController controller = loader.getController();
            controller.setOverlayWindow(this);

            // Ensure root has transparent background
            root.setStyle("-fx-background-color: transparent;");

            Scene scene = new Scene(root, Color.TRANSPARENT);
            scene.getStylesheets().add(getClass().getResource("/ste/wakemeapp/notify/overlay.css").toExternalForm());
            stage.setScene(scene);

            // Handle escape key
            scene.setOnKeyPressed(event -> {
                if (event.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                    hide();
                }
            });

            // Bind background to scene size
            controller.getBackground().prefWidthProperty().bind(scene.widthProperty());
            controller.getBackground().prefHeightProperty().bind(scene.heightProperty());

            // Set full screen bounds
            Screen screen = Screen.getPrimary();
            stage.setX(screen.getVisualBounds().getMinX());
            stage.setY(screen.getVisualBounds().getMinY());
            stage.setWidth(screen.getVisualBounds().getWidth());
            stage.setHeight(screen.getVisualBounds().getHeight());

            return controller;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load overlay FXML", e);
        }
    }

    private void setupStage() {
        // Additional setup if needed
    }

    private FadeTransition createFadeInTransition() {
        FadeTransition ft = new FadeTransition(Duration.millis(300), stage.getScene().getRoot());
        ft.setFromValue(0.0);
        ft.setToValue(1.0);
        return ft;
    }

    private FadeTransition createFadeOutTransition() {
        FadeTransition ft = new FadeTransition(Duration.millis(200), stage.getScene().getRoot());
        ft.setFromValue(1.0);
        ft.setToValue(0.0);
        ft.setOnFinished(e -> stage.hide());
        return ft;
    }

    public void setNotification(MeetingNotification notification) {
        this.notification = notification;
        controller.setNotification(notification);
        startCountdown();
    }

    private void startCountdown() {
        if (notification == null) return;

        if (countdownTimeline != null) {
            countdownTimeline.stop();
        }

        countdownTimeline = new javafx.animation.Timeline(
            new javafx.animation.KeyFrame(Duration.seconds(1), e -> {
                Platform.runLater(() -> {
                    if (controller != null && notification != null) {
                        controller.updateCountdown(notification.startTime());
                    }
                });
            })
        );
        countdownTimeline.setCycleCount(javafx.animation.Animation.INDEFINITE);
        countdownTimeline.play();
    }

    public void show() {
        stage.show();
        fadeIn.playFromStart();
    }

    public void hide() {
        fadeOut.playFromStart();
    }

    public Stage getStage() {
        return stage;
    }

    public FadeTransition getFadeInTransition() {
        return fadeIn;
    }

    public FadeTransition getFadeOutTransition() {
        return fadeOut;
    }

    public OverlayController getController() {
        return controller;
    }
}