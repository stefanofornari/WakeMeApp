package ste.wakemeapp.notify;

import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.net.URL;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ResourceBundle;

public class OverlayController implements Initializable {

    private static final DateTimeFormatter HOUR_MINUTE = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter HOUR_MINUTE_SECOND = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter MINUTE_SECOND = DateTimeFormatter.ofPattern("mm:ss");

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

    @FXML
    private Label snoozeStatusLabel;

    @FXML
    private Label meetingCountdownLabel;

    private OverlayWindow overlayWindow;
    private FadeTransition pulse;
    private Timeline clock;
    private ContextMenu snoozeMenu;
    private java.util.function.IntConsumer onSnoozeSelected;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Gentle pulse on the status dot to draw the eye without being loud
        pulse = new FadeTransition(Duration.millis(900), statusDot);
        pulse.setFromValue(1.0);
        pulse.setToValue(0.3);
        pulse.setAutoReverse(true);
        pulse.setCycleCount(Animation.INDEFINITE);
        pulse.play();

        // The clock runs on its own timer so it keeps ticking whatever the auto-close timer does
        refreshClock();
        clock = new Timeline(new KeyFrame(Duration.seconds(1), e -> refreshClock()));
        clock.setCycleCount(Animation.INDEFINITE);
        clock.play();

        // Create snooze context menu
        createSnoozeMenu();
    }

    private void createSnoozeMenu() {
        snoozeMenu = new ContextMenu();
        snoozeMenu.getStyleClass().add("overlay-snooze-menu");

        MenuItem item1Min = new MenuItem("Snooze 1 min");
        item1Min.setOnAction(e -> handleSnoozeSelection(1));

        MenuItem item5Min = new MenuItem("Snooze 5 min");
        item5Min.setOnAction(e -> handleSnoozeSelection(5));

        MenuItem item10Min = new MenuItem("Snooze 10 min");
        item10Min.setOnAction(e -> handleSnoozeSelection(10));

        MenuItem itemUntilMeeting = new MenuItem("Until meeting starts");
        itemUntilMeeting.setOnAction(e -> handleSnoozeSelection(-1)); // -1 means "until meeting starts"

        snoozeMenu.getItems().addAll(item1Min, item5Min, item10Min, itemUntilMeeting);

        // Show menu when snooze button is clicked
        snoozeButton.setOnAction(e -> {
            if (snoozeMenu != null) {
                snoozeMenu.show(snoozeButton, javafx.geometry.Side.BOTTOM, 0, 0);
            }
        });
    }

    private void handleSnoozeSelection(int minutes) {
        if (overlayWindow != null) {
            overlayWindow.snooze(minutes);
        }
    }

    private void refreshClock() {
        clockLabel.setText(HOUR_MINUTE_SECOND.format(LocalTime.now()));
    }

    /** Stops the endless animations; called once the overlay is gone so nothing keeps running. */
    void stopAnimations() {
        pulse.stop();
        clock.stop();
    }

    public void setOverlayWindow(OverlayWindow overlayWindow) {
        this.overlayWindow = overlayWindow;
    }

    public void setOnSnoozeSelected(java.util.function.IntConsumer onSnoozeSelected) {
        this.onSnoozeSelected = onSnoozeSelected;
    }

    public void setNotification(MeetingNotification notification) {
        titleLabel.setText(notification.title());
        java.time.LocalDateTime end = notification.endTime();
        // After a snooze startTime is the re-notification time: always show the real meeting time
        LocalDateTime meetingStart = notification.originalStartTime() != null
            ? notification.originalStartTime() : notification.startTime();
        timeLabel.setText(
            HOUR_MINUTE.format(meetingStart) + " – "
            + (end != null ? HOUR_MINUTE.format(end) : END_TIME_PLACEHOLDER)
        );
        String duration = end != null
            ? formatDuration(java.time.Duration.between(meetingStart, end))
            : "";
        durationLabel.setText(duration);
        durationLabel.setVisible(!duration.isEmpty());
        durationLabel.setManaged(!duration.isEmpty());

        boolean hasOrganizer = notification.organizer() != null && !notification.organizer().isBlank();
        organizerLabel.setText(hasOrganizer ? notification.organizer() : "");
        organizerBox.setVisible(hasOrganizer);
        organizerBox.setManaged(hasOrganizer);

        // Update snooze status
        updateSnoozeStatus(notification);

        // Update meeting countdown
        updateMeetingCountdown(notification);
    }

    private void updateSnoozeStatus(MeetingNotification notification) {
        if (notification.snoozeCount() > 0) {
            snoozeStatusLabel.setText(notification.snoozeStatusText());
            snoozeStatusLabel.setVisible(true);
            snoozeStatusLabel.setManaged(true);
        } else {
            snoozeStatusLabel.setVisible(false);
            snoozeStatusLabel.setManaged(false);
        }

        // Disable snooze button if max snoozes reached
        boolean canSnooze = notification.canSnooze();
        snoozeButton.setDisable(!canSnooze);
        snoozeButton.setVisible(canSnooze);
        snoozeButton.setManaged(canSnooze);
    }

    private void updateMeetingCountdown(MeetingNotification notification) {
        LocalDateTime meetingStart = notification.originalStartTime();
        if (meetingStart != null) {
            java.time.Duration remaining = java.time.Duration.between(LocalDateTime.now(), meetingStart);
            if (!remaining.isNegative() && !remaining.isZero()) {
                meetingCountdownLabel.setText("Meeting in " + formatCountdown(remaining));
                meetingCountdownLabel.setVisible(true);
                meetingCountdownLabel.setManaged(true);
            } else {
                meetingCountdownLabel.setVisible(false);
                meetingCountdownLabel.setManaged(false);
            }
        } else {
            meetingCountdownLabel.setVisible(false);
            meetingCountdownLabel.setManaged(false);
        }
    }

    private String formatCountdown(java.time.Duration duration) {
        long totalSeconds = duration.getSeconds();
        if (totalSeconds <= 0) {
            return "00:00";
        }
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        if (minutes >= 60) {
            long hours = minutes / 60;
            long mins = minutes % 60;
            return String.format("%dh %02dm", hours, mins);
        }
        return String.format("%02d:%02d", minutes, seconds);
    }

    /**
     * Refreshes the auto-close countdown.
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

        // Also update meeting countdown
        if (overlayWindow != null && overlayWindow.getNotification() != null) {
            updateMeetingCountdown(overlayWindow.getNotification());
        }
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

    public Label getSnoozeStatusLabel() {
        return snoozeStatusLabel;
    }

    public Label getMeetingCountdownLabel() {
        return meetingCountdownLabel;
    }

    public ContextMenu getSnoozeMenu() {
        return snoozeMenu;
    }
}
