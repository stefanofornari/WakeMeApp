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
    private HBox organizerBox;

    @FXML
    private Label organizerLabel;

    @FXML
    private Button joinButton;

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
        timeLabel.setText(HOUR_MINUTE.format(notification.startTime()));

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
