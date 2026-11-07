package ste.wakemeapp.notify;

import javafx.animation.FadeTransition;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.io.IOException;
import java.io.InputStream;
import java.util.function.IntConsumer;

public final class OverlayWindow {

    private final Stage stage;
    private final FadeTransition fadeIn;
    private final FadeTransition fadeOut;
    private final OverlayController controller;
    private MeetingNotification notification;
    private javafx.animation.Timeline countdownTimeline;
    private java.time.Duration autoClose = java.time.Duration.ofSeconds(90);
    private java.time.Instant startedAt;
    private IntConsumer onSnooze;

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
            loadFonts();
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
        ft.setOnFinished(e -> {
            controller.stopAnimations();
            stage.hide();
        });
        return ft;
    }

    public void setNotification(MeetingNotification notification) {
        this.notification = notification;
        controller.setNotification(notification);
        startCountdown();
    }

    public MeetingNotification getNotification() {
        return notification;
    }

    /**
     * Sets how long the overlay stays up before closing by itself.
     * Takes effect for the next {@link #setNotification(MeetingNotification)}.
     */
    public void setAutoClose(java.time.Duration autoClose) {
        this.autoClose = autoClose;
    }

    private void startCountdown() {
        if (notification == null) return;

        if (countdownTimeline != null) {
            countdownTimeline.stop();
        }

        startedAt = java.time.Instant.now();
        tick();

        countdownTimeline = new javafx.animation.Timeline(
            new javafx.animation.KeyFrame(Duration.seconds(1), e -> tick())
        );
        countdownTimeline.setCycleCount(javafx.animation.Animation.INDEFINITE);
        countdownTimeline.play();
    }

    /** Based on the system clock rather than accumulated ticks, so it does not drift. */
    private void tick() {
        java.time.Duration remaining = autoClose.minus(java.time.Duration.between(startedAt, java.time.Instant.now()));
        controller.updateTimers(remaining, autoClose);
        if (remaining.isZero() || remaining.isNegative()) {
            hide();
        }
    }

    /**
     * Registers a listener called when the user snoozes the notification.
     * The int parameter is the snooze duration in minutes, or -1 for "until meeting starts".
     * Scheduling the re-notification is up to the listener.
     */
    public void setOnSnooze(IntConsumer onSnooze) {
        this.onSnooze = onSnooze;
        if (controller != null) {
            controller.setOnSnoozeSelected(onSnooze);
        }
    }

    /** Closes the overlay on behalf of the user and tells the snooze listener, if any. */
    public void snooze(int minutes) {
        if (onSnooze != null) {
            onSnooze.accept(minutes);
        }
        hide();
    }

    private static final String FONT_DIR = "/ste/wakemeapp/notify/fonts/";
    private static final String[] FONT_FILES = {
        "Inter-Regular.ttf", "Inter-Bold.ttf", "BricolageGrotesque-Bold.ttf"
    };
    private static boolean fontsLoaded;

    /** Registers the bundled (SIL OFL) fonts once; the CSS falls back to system fonts if this fails. */
    private static synchronized void loadFonts() {
        if (fontsLoaded) {
            return;
        }
        fontsLoaded = true;
        for (String file : FONT_FILES) {
            try (InputStream in = OverlayWindow.class.getResourceAsStream(FONT_DIR + file)) {
                if (in != null) {
                    Font.loadFont(in, 12);
                }
            } catch (IOException e) {
                // keep going: the CSS font-family list has system fallbacks
            }
        }
    }

    public void show() {
        stage.show();
        fadeIn.playFromStart();
    }

    public void hide() {
        if (countdownTimeline != null) {
            countdownTimeline.stop();
        }
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