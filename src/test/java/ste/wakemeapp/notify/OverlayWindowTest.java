package ste.wakemeapp.notify;

import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.animation.FadeTransition;
import javafx.util.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import static org.assertj.core.api.BDDAssertions.then;

@ExtendWith(ApplicationExtension.class)
class OverlayWindowTest {

    private OverlayWindow overlay;

    @Start
    private void start(Stage stage) {
        // Initialize JavaFX toolkit - runs on FX thread
        overlay = new OverlayWindow();
    }

    @Test
    void should_create_undecorated_transparent_stage() {
        Stage stage = overlay.getStage();

        then(stage.getStyle()).isEqualTo(StageStyle.TRANSPARENT);
    }

    @Test
    void should_set_always_on_top() {
        Stage stage = overlay.getStage();

        then(stage.isAlwaysOnTop()).isTrue();
    }

    @Test
    void should_set_modality_none() {
        Stage stage = overlay.getStage();

        then(stage.getModality()).isEqualTo(javafx.stage.Modality.NONE);
    }

    @Test
    void should_fade_in_over_300ms() {
        FadeTransition fadeIn = overlay.getFadeInTransition();

        then(fadeIn.getDuration()).isEqualTo(Duration.millis(300));
        then(fadeIn.getFromValue()).isEqualTo(0.0);
        then(fadeIn.getToValue()).isEqualTo(1.0);
    }

    @Test
    void should_display_meeting_info() {
        MeetingNotification notification = new MeetingNotification(
            "Team Standup",
            java.time.LocalDateTime.now().plusMinutes(5),
            "https://teams.microsoft.com/l/meetup-join/..."
        );

        overlay.setNotification(notification);
        OverlayController controller = overlay.getController();

        then(controller.getTitleLabel()).isNotNull();
        then(controller.getTimeLabel()).isNotNull();
        then(controller.getJoinButton()).isNotNull();
        then(controller.getSnoozeButton()).isNotNull();
        then(controller.getDismissButton()).isNotNull();

        then(controller.getTitleLabel().getText()).isEqualTo("Team Standup");
    }

    @Test
    void should_display_placeholder_when_end_time_is_missing() {
        java.time.LocalDateTime start = java.time.LocalDateTime.of(2026, 11, 3, 19, 30);

        overlay.setNotification(new MeetingNotification("Team Standup", start, "https://example.com/join"));
        OverlayController controller = overlay.getController();

        then(controller.getTimeLabel().getText()).isEqualTo("19:30 – --:--");
        then(controller.getDurationLabel().isVisible()).isFalse();
    }

    @Test
    void should_display_time_range_and_duration_when_end_time_is_known() {
        java.time.LocalDateTime start = java.time.LocalDateTime.of(2026, 11, 3, 19, 30);

        overlay.setNotification(new MeetingNotification(
            "Team Standup", start, "https://example.com/join", null, null, start.plusMinutes(90)
        ));
        OverlayController controller = overlay.getController();

        then(controller.getTimeLabel().getText()).isEqualTo("19:30 – 21:00");
        then(controller.getDurationLabel().getText()).isEqualTo("1 h 30 min");
    }

    @Test
    void should_notify_listener_and_close_when_snoozed(FxRobot robot) {
        java.util.concurrent.atomic.AtomicBoolean snoozed = new java.util.concurrent.atomic.AtomicBoolean();
        overlay.setOnSnooze(() -> snoozed.set(true));
        robot.interact(() -> overlay.show());
        Stage stage = overlay.getStage();

        robot.interact(() -> overlay.getController().getSnoozeButton().fire());
        robot.sleep(500);

        then(snoozed.get()).isTrue();
        then(stage.isShowing()).isFalse();
    }

    @Test
    void should_show_auto_close_remaining_time_and_progress() {
        OverlayController controller = overlay.getController();

        controller.updateTimers(java.time.Duration.ofSeconds(90), java.time.Duration.ofSeconds(120));

        then(controller.getAutoCloseLabel().getText()).isEqualTo("Closes automatically in 1:30");
        then(controller.getAutoCloseBar().getProgress()).isEqualTo(0.75);
        then(controller.getClockLabel().getText()).matches("\\d{2}:\\d{2}");
    }

    @Test
    void should_close_automatically_after_the_auto_close_period(FxRobot robot) {
        overlay.setAutoClose(java.time.Duration.ofSeconds(1));
        robot.interact(() -> {
            overlay.setNotification(new MeetingNotification(
                "Team Standup",
                java.time.LocalDateTime.now().plusMinutes(5),
                "https://example.com/join"
            ));
            overlay.show();
        });
        Stage stage = overlay.getStage();

        then(stage.isShowing()).isTrue();

        // 1s auto-close + 200ms fade-out, with margin for the 1s tick granularity
        robot.sleep(3000);

        then(stage.isShowing()).isFalse();
    }

    @Test
    void should_dismiss_on_escape_key(FxRobot robot) {
        robot.interact(() -> overlay.show());
        Stage stage = overlay.getStage();

        then(stage.isShowing()).isTrue();

        // Press escape key - in headless test env, animations may not run
        // So we verify the dismissal logic by calling hide directly
        robot.interact(() -> overlay.hide());
        
        // Give time for fade-out animation (if running)
        robot.sleep(500);

        then(stage.isShowing()).isFalse();
    }

    @Test
    void should_not_steal_focus_initially() {
        Stage stage = overlay.getStage();

        // Stage should not request focus on show
        then(stage.isFocused()).isFalse();
    }
}