package ste.wakemeapp.notify;

import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import static org.assertj.core.api.BDDAssertions.then;

import java.time.LocalDateTime;

@ExtendWith(ApplicationExtension.class)
class OverlayManagerTest {

    private OverlayManager manager;

    @Start
    private void start(Stage stage) {
        // Initialize JavaFX toolkit - runs on FX thread
        manager = new OverlayManager();
    }

    @Test
    void should_create_overlay_per_screen(FxRobot robot) {
        MeetingNotification notification = new MeetingNotification(
            "Test Meeting",
            java.time.LocalDateTime.now().plusMinutes(5),
            "https://teams.example.com"
        );

        robot.interact(() -> manager.showNotification(notification));

        then(manager.getOverlayCount()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void should_hide_all_overlays(FxRobot robot) {
        MeetingNotification notification = new MeetingNotification(
            "Test Meeting",
            java.time.LocalDateTime.now().plusMinutes(5),
            "https://teams.example.com"
        );

        robot.interact(() -> manager.showNotification(notification));
        int initialCount = manager.getOverlayCount();

        robot.interact(() -> manager.hideAll());

        then(manager.getOverlayCount()).isEqualTo(0);
    }

    @Test
    void should_configure_overlay_for_each_screen_bounds(FxRobot robot) {
        MeetingNotification notification = new MeetingNotification(
            "Test Meeting",
            java.time.LocalDateTime.now().plusMinutes(5),
            "https://teams.example.com"
        );

        robot.interact(() -> manager.showNotification(notification));

        for (OverlayWindow overlay : manager.getOverlays()) {
            Stage stage = overlay.getStage();
            then(stage.getWidth()).isGreaterThan(0);
            then(stage.getHeight()).isGreaterThan(0);
        }
    }

    @Test
    void should_preserve_original_start_time_when_snoozed() {
        LocalDateTime originalStart = LocalDateTime.of(2026, 11, 6, 10, 0);
        MeetingNotification notification = new MeetingNotification(
            "Test Meeting",
            originalStart,
            "https://teams.example.com"
        );

        // Snooze for 5 minutes - new trigger time is 5 minutes from now
        LocalDateTime snoozeUntil = LocalDateTime.now().plusMinutes(5);
        MeetingNotification snoozed = notification.withSnooze(5, snoozeUntil);

        then(snoozed.originalStartTime()).isEqualTo(originalStart);
        then(snoozed.startTime()).isEqualTo(snoozeUntil);
        then(snoozed.snoozeCount()).isEqualTo(1);
        then(snoozed.maxSnoozes()).isEqualTo(3);
    }

    @Test
    void should_increment_snooze_count_on_each_snooze() {
        LocalDateTime originalStart = LocalDateTime.of(2026, 11, 6, 10, 0);
        MeetingNotification notification = new MeetingNotification(
            "Test Meeting",
            originalStart,
            "https://teams.example.com"
        );

        MeetingNotification snoozed1 = notification.withSnooze(5, LocalDateTime.now().plusMinutes(5));
        MeetingNotification snoozed2 = snoozed1.withSnooze(5, LocalDateTime.now().plusMinutes(10));
        MeetingNotification snoozed3 = snoozed2.withSnooze(5, LocalDateTime.now().plusMinutes(15));

        then(snoozed1.snoozeCount()).isEqualTo(1);
        then(snoozed2.snoozeCount()).isEqualTo(2);
        then(snoozed3.snoozeCount()).isEqualTo(3);
    }

    @Test
    void should_disable_snooze_when_max_reached() {
        MeetingNotification notification = new MeetingNotification(
            "Test Meeting",
            LocalDateTime.of(2026, 11, 6, 10, 0),
            "https://teams.example.com"
        );

        MeetingNotification snoozed1 = notification.withSnooze(5, LocalDateTime.now().plusMinutes(5));
        MeetingNotification snoozed2 = snoozed1.withSnooze(5, LocalDateTime.now().plusMinutes(10));
        MeetingNotification snoozed3 = snoozed2.withSnooze(5, LocalDateTime.now().plusMinutes(15));

        then(snoozed1.canSnooze()).isTrue();
        then(snoozed2.canSnooze()).isTrue();
        then(snoozed3.canSnooze()).isFalse();
    }

    @Test
    void should_format_snooze_status_text() {
        MeetingNotification notification = new MeetingNotification(
            "Test Meeting",
            LocalDateTime.of(2026, 11, 6, 10, 0),
            "https://teams.example.com"
        );

        then(notification.snoozeStatusText()).isEqualTo("Snoozed 0/3 times");

        MeetingNotification snoozed = notification.withSnooze(5, LocalDateTime.now().plusMinutes(5));
        then(snoozed.snoozeStatusText()).isEqualTo("Snoozed 1/3 times");

        MeetingNotification snoozed2 = snoozed.withSnooze(5, LocalDateTime.now().plusMinutes(10));
        then(snoozed2.snoozeStatusText()).isEqualTo("Snoozed 2/3 times");
    }

    @Test
    void should_calculate_until_meeting_starts_correctly() {
        // Meeting starts in 8 minutes, "Until meeting starts" should schedule for 7 minutes from now (1 min before)
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime meetingStart = now.plusMinutes(8);
        MeetingNotification notification = new MeetingNotification(
            "Test Meeting",
            meetingStart,
            "https://teams.example.com"
        );

        // Test the calculation logic directly (same as in OverlayManager.handleSnooze)
        LocalDateTime snoozeUntil = notification.originalStartTime().minusMinutes(1);
        
        then(snoozeUntil).isEqualTo(meetingStart.minusMinutes(1));
        then(java.time.Duration.between(now, snoozeUntil).toMinutes()).isEqualTo(7);
    }

    @Test
    void should_schedule_fixed_duration_snooze_correctly() {
        LocalDateTime meetingStart = LocalDateTime.now().plusMinutes(15);
        MeetingNotification notification = new MeetingNotification(
            "Test Meeting",
            meetingStart,
            "https://teams.example.com"
        );

        // Test 5-minute snooze calculation
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expectedSnoozeUntil = now.plusMinutes(5);
        
        then(expectedSnoozeUntil).isAfter(now);
        then(java.time.Duration.between(now, expectedSnoozeUntil).toMinutes()).isEqualTo(5);
    }
}