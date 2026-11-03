package ste.wakemeapp.notify;

import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.net.URL;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ResourceBundle;

public class OverlayController implements Initializable {

    private static final DateTimeFormatter HOUR_MINUTE = DateTimeFormatter.ofPattern("HH:mm");

    /** Shown in place of the end time when the calendar did not provide one. */
    static final String END_TIME_PLACEHOLDER = "--:--";

    @FXML
    private StackPane root;

    @FXML
    private StackPane background;

    @FXML
    private VBox contentCard;

    @FXML
    private Rectangle statusDot;

    @FXML
    private Label titleLabel;

    @FXML
    private Label timeLabel;

    @FXML
    private Label durationLabel;

    @FXML
    private HBox organizerBox;

    @FXML
    private Label organizerLabel;

    @FXML
    private Button joinButton;

    @FXML
    private Button snoozeButton;

    @FXML
    private Button dismissButton;

    @FXML
    private ProgressBar autoCloseBar;

    @FXML
    private Label autoCloseLabel;

    @FXML
    private Label clockLabel;

    private OverlayWindow overlayWindow;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Gentle pulse on the status dot to draw the eye without being loud
        FadeTransition pulse = new FadeTransition(Duration.millis(900), statusDot);
        pulse.setFromValue(1.0);
        pulse.setToValue(0.3);
        pulse.setAutoReverse(true);
        pulse.setCycleCount(Animation.INDEFINITE);
        pulse.play();
    }

    public void setOverlayWindow(OverlayWindow overlayWindow) {
        this.overlayWindow = overlayWindow;
    }

    public void setNotification(MeetingNotification notification) {
        titleLabel.setText(notification.title());
        java.time.LocalDateTime end = notification.endTime();
        timeLabel.setText(
            HOUR_MINUTE.format(notification.startTime()) + " – "
            + (end != null ? HOUR_MINUTE.format(end) : END_TIME_PLACEHOLDER)
        );
        String duration = end != null
            ? formatDuration(java.time.Duration.between(notification.startTime(), end))
            : "";
        durationLabel.setText(duration);
        durationLabel.setVisible(!duration.isEmpty());
        durationLabel.setManaged(!duration.isEmpty());

        boolean hasOrganizer = notification.organizer() != null && !notification.organizer().isBlank();
        organizerLabel.setText(hasOrganizer ? notification.organizer() : "");
        organizerBox.setVisible(hasOrganizer);
        organizerBox.setManaged(hasOrganizer);
    }

    /**
     * Refreshes the auto-close countdown and the current time.
     *
     * @param remaining time left before the overlay closes by itself
     * @param total     the full auto-close period, used to size the progress bar
     */
    public void updateTimers(java.time.Duration remaining, java.time.Duration total) {
        long seconds = Math.max(0, (remaining.toMillis() + 999) / 1000);
        autoCloseLabel.setText(String.format("Closes automatically in %d:%02d", seconds / 60, seconds % 60));
        autoCloseBar.setProgress(
            total.isZero() ? 0.0 : Math.max(0.0, Math.min(1.0, (double) remaining.toMillis() / total.toMillis()))
        );
        clockLabel.setText(HOUR_MINUTE.format(LocalTime.now()));
    }

    private static String formatDuration(java.time.Duration duration) {
        long minutes = duration.toMinutes();
        if (minutes <= 0) {
            return "";
        }
        long hours = minutes / 60;
        long rest = minutes % 60;
        if (hours == 0) {
            return rest + " min";
        }
        return rest == 0 ? hours + " h" : hours + " h " + rest + " min";
    }

    @FXML
    private void handleBackgroundClick() {
        if (overlayWindow != null) {
            overlayWindow.hide();
        }
    }

    @FXML
    private void handleJoin() {
        if (overlayWindow != null) {
            // TODO: Open join URL
            overlayWindow.hide();
        }
    }

    @FXML
    private void handleSnooze() {
        if (overlayWindow != null) {
            overlayWindow.snooze();
        }
    }

    @FXML
    private void handleDismiss() {
        if (overlayWindow != null) {
            overlayWindow.hide();
        }
    }

    public StackPane getRoot() {
        return root;
    }

    public StackPane getBackground() {
        return background;
    }

    public VBox getContentCard() {
        return contentCard;
    }

    public Label getTitleLabel() {
        return titleLabel;
    }

    public Label getTimeLabel() {
        return timeLabel;
    }

    public Label getOrganizerLabel() {
        return organizerLabel;
    }

    public Button getJoinButton() {
        return joinButton;
    }

    public Label getDurationLabel() {
        return durationLabel;
    }

    public Button getSnoozeButton() {
        return snoozeButton;
    }

    public Button getDismissButton() {
        return dismissButton;
    }

    public ProgressBar getAutoCloseBar() {
        return autoCloseBar;
    }

    public Label getAutoCloseLabel() {
        return autoCloseLabel;
    }

    public Label getClockLabel() {
        return clockLabel;
    }
}
