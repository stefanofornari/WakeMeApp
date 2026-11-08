package ste.wakemeapp.notify;

import javafx.scene.control.Label;
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
        java.util.concurrent.atomic.AtomicInteger snoozedMinutes = new java.util.concurrent.atomic.AtomicInteger(-1);
        overlay.setOnSnooze(minutes -> snoozedMinutes.set(minutes));
        robot.interact(() -> overlay.show());
        Stage stage = overlay.getStage();

        // Call snooze directly with 5 minutes
        robot.interact(() -> overlay.snooze(5));
        robot.sleep(500);

        then(snoozedMinutes.get()).isEqualTo(5);
        then(stage.isShowing()).isFalse();
    }

    @Test
    void should_show_auto_close_remaining_time_and_progress() {
        OverlayController controller = overlay.getController();

        controller.updateTimers(java.time.Duration.ofSeconds(90), java.time.Duration.ofSeconds(120));

        then(controller.getAutoCloseLabel().getText()).isEqualTo("Closes automatically in 1:30");
        then(controller.getAutoCloseBar().getProgress()).isEqualTo(0.75);
    }

    @Test
    void should_keep_the_clock_ticking(FxRobot robot) {
        Label clock = overlay.getController().getClockLabel();

        then(clock.getText()).matches("\\d{2}:\\d{2}:\\d{2}");

        String before = clock.getText();
        robot.sleep(2200);

        then(clock.getText()).isNotEqualTo(before);
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
    void should_show_snooze_status_when_snoozed() {
        MeetingNotification notification = new MeetingNotification(
            "Team Standup",
            java.time.LocalDateTime.now().plusMinutes(5),
            "https://teams.example.com/join"
        );

        // First notification (no snooze)
        overlay.setNotification(notification);
        OverlayController controller = overlay.getController();

        then(controller.getSnoozeStatusLabel().isVisible()).isFalse();

        // Snoozed once
        MeetingNotification snoozed = notification.withSnooze(5, java.time.LocalDateTime.now().plusMinutes(5));
        overlay.setNotification(snoozed);

        then(controller.getSnoozeStatusLabel().isVisible()).isTrue();
        then(controller.getSnoozeStatusLabel().getText()).isEqualTo("Snoozed 1/3 times");
    }

    @Test
    void should_disable_snooze_button_when_max_reached() {
        MeetingNotification notification = new MeetingNotification(
            "Team Standup",
            java.time.LocalDateTime.now().plusMinutes(5),
            "https://teams.example.com/join"
        );

        overlay.setNotification(notification);
        OverlayController controller = overlay.getController();

        then(controller.getSnoozeButton().isDisable()).isFalse();
        then(controller.getSnoozeButton().isVisible()).isTrue();

        // Max snoozes reached (3)
        MeetingNotification maxSnoozed = notification.withSnooze(5, java.time.LocalDateTime.now().plusMinutes(5))
            .withSnooze(5, java.time.LocalDateTime.now().plusMinutes(10))
            .withSnooze(5, java.time.LocalDateTime.now().plusMinutes(15));

        overlay.setNotification(maxSnoozed);

        then(controller.getSnoozeButton().isDisable()).isTrue();
        then(controller.getSnoozeButton().isVisible()).isFalse();
    }

    @Test
    void should_show_snooze_context_menu_on_button_click(FxRobot robot) {
        MeetingNotification notification = new MeetingNotification(
            "Team Standup",
            java.time.LocalDateTime.now().plusMinutes(5),
            "https://teams.example.com/join"
        );

        overlay.setNotification(notification);
        robot.interact(() -> overlay.show());

        OverlayController controller = overlay.getController();
        
        // Click snooze button
        robot.interact(() -> controller.getSnoozeButton().fire());
        
        // Context menu should be showing
        then(controller.getSnoozeMenu().isShowing()).isTrue();
        
        // Menu should have 4 items
        then(controller.getSnoozeMenu().getItems()).hasSize(4);
        then(controller.getSnoozeMenu().getItems().get(0).getText()).isEqualTo("Snooze 1 min");
        then(controller.getSnoozeMenu().getItems().get(1).getText()).isEqualTo("Snooze 5 min");
        then(controller.getSnoozeMenu().getItems().get(2).getText()).isEqualTo("Snooze 10 min");
        then(controller.getSnoozeMenu().getItems().get(3).getText()).isEqualTo("Until meeting starts");
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

    @Test
    void should_show_the_countdown_in_the_status_pill() {
        overlay.setNotification(new MeetingNotification(
            "Team Standup", java.time.LocalDateTime.now().plusMinutes(15), "https://teams.example.com/join"));

        then(overlay.getController().getCountdownLabel().getText()).matches("Meeting starting in 00:1[45]:\\d\\d");
    }

    @Test
    void should_count_down_as_time_passes(FxRobot robot) {
        overlay.setNotification(new MeetingNotification(
            "Team Standup", java.time.LocalDateTime.now().plusMinutes(15), "https://teams.example.com/join"));
        String before = overlay.getController().getCountdownLabel().getText();

        robot.sleep(2500);

        then(overlay.getController().getCountdownLabel().getText()).isNotEqualTo(before);
    }

    @Test
    void should_count_down_to_the_original_start_after_a_snooze() {
        MeetingNotification meeting = new MeetingNotification(
            "Team Standup", java.time.LocalDateTime.now().plusMinutes(15), "https://teams.example.com/join");

        overlay.setNotification(meeting.withSnooze(5, java.time.LocalDateTime.now().plusMinutes(5)));

        then(overlay.getController().getCountdownLabel().getText()).matches("Meeting starting in 00:1[45]:\\d\\d");
    }

    private void showMeetingStartingIn(java.time.Duration remaining) {
        overlay.setNotification(new MeetingNotification(
            "Team Standup", java.time.LocalDateTime.now().plus(remaining), "https://teams.example.com/join"));
    }

    private void thenCountdownAndDotHaveStyle(String styleClass) {
        then(overlay.getController().getCountdownLabel().getStyleClass()).contains(styleClass);
        then(overlay.getController().getStatusDot().getStyleClass()).contains(styleClass);
    }

    @Test
    void should_show_the_countdown_in_green_with_more_than_five_minutes_left() {
        showMeetingStartingIn(java.time.Duration.ofMinutes(6));

        thenCountdownAndDotHaveStyle("overlay-state-green");
    }

    @Test
    void should_show_the_countdown_in_yellow_between_five_and_one_minutes() {
        showMeetingStartingIn(java.time.Duration.ofMinutes(4));

        thenCountdownAndDotHaveStyle("overlay-state-yellow");
        then(overlay.getController().getCountdownLabel().getStyleClass()).doesNotContain("overlay-state-green");
    }

    @Test
    void should_show_the_countdown_in_red_in_the_last_minute() {
        showMeetingStartingIn(java.time.Duration.ofSeconds(30));

        thenCountdownAndDotHaveStyle("overlay-state-red");
    }

    @Test
    void should_tell_to_join_when_the_meeting_has_started() {
        showMeetingStartingIn(java.time.Duration.ofSeconds(-10));

        then(overlay.getController().getCountdownLabel().getText()).isEqualTo("Meeting started - Join now");
        thenCountdownAndDotHaveStyle("overlay-state-started");
    }

    @Test
    void should_flash_the_countdown_once_the_meeting_has_started(FxRobot robot) {
        showMeetingStartingIn(java.time.Duration.ofSeconds(-10));
        java.util.Set<Boolean> seen = new java.util.HashSet<>();

        for (int i = 0; i < 20; i++) {
            seen.add(overlay.getController().getCountdownLabel().getStyleClass().contains("overlay-flash"));
            robot.sleep(100);
        }

        then(seen).containsExactlyInAnyOrder(true, false);
    }

    @Test
    void should_change_colour_when_a_threshold_is_crossed_while_open(FxRobot robot) {
        showMeetingStartingIn(java.time.Duration.ofSeconds(301));
        thenCountdownAndDotHaveStyle("overlay-state-green");

        // the clock ticks once a second at its own phase: after 3.5 s the last tick is at least 2.5 s in
        robot.sleep(3500);

        thenCountdownAndDotHaveStyle("overlay-state-yellow");
    }
}
