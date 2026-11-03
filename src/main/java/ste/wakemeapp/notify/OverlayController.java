package ste.wakemeapp.notify;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.net.URL;
import java.util.ResourceBundle;

public class OverlayController implements Initializable {

    @FXML
    private StackPane root;

    @FXML
    private StackPane background;

    @FXML
    private VBox contentCard;

    @FXML
    private Label titleLabel;

    @FXML
    private Label countdownLabel;

    @FXML
    private Button joinButton;

    @FXML
    private Button snoozeButton;

    @FXML
    private Button dismissButton;

    private OverlayWindow overlayWindow;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Prevent content card from stretching vertically in StackPane
        contentCard.maxHeightProperty().bind(contentCard.prefHeightProperty());
    }

    public void setOverlayWindow(OverlayWindow overlayWindow) {
        this.overlayWindow = overlayWindow;
    }

    public void setNotification(MeetingNotification notification) {
        titleLabel.setText(notification.title());
        updateCountdown(notification.startTime());
    }

    public void updateCountdown(java.time.LocalDateTime startTime) {
        java.time.Duration duration = java.time.Duration.between(
            java.time.LocalDateTime.now(),
            startTime
        );

        if (duration.isNegative()) {
            countdownLabel.setText("00:00");
        } else {
            long minutes = duration.toMinutes();
            long seconds = duration.minusMinutes(minutes).getSeconds();
            countdownLabel.setText(String.format("%02d:%02d", minutes, seconds));
        }
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
        // TODO: Implement snooze
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

    public Label getCountdownLabel() {
        return countdownLabel;
    }

    public Button getJoinButton() {
        return joinButton;
    }

    public Button getSnoozeButton() {
        return snoozeButton;
    }

    public Button getDismissButton() {
        return dismissButton;
    }
}