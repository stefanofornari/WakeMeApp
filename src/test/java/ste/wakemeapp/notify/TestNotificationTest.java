package ste.wakemeapp.notify;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import static org.assertj.core.api.BDDAssertions.then;

/** The fake meeting of the Test Notification button, shown through the real overlay (settings/US-000018). */
@ExtendWith(ApplicationExtension.class)
class TestNotificationTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-13T09:00:00Z"), ZoneId.of("UTC"));

    private TestNotification test;

    @Start
    private void start(Stage stage) {
        test = new TestNotification();
    }

    @Test
    void should_be_a_meeting_that_starts_in_5_minutes_with_a_harmless_join_link() {
        MeetingNotification meeting = TestNotification.meeting(CLOCK);

        then(meeting.title()).isEqualTo("Test Meeting");
        then(meeting.startTime()).isEqualTo(LocalDateTime.of(2026, 11, 13, 9, 5));
        then(meeting.joinUrl()).isEqualTo("https://example.com");
        then(meeting.snoozeCount()).isZero();
        then(meeting.eventId()).isNull();   // not a calendar event: nothing can match it, dismiss it or cancel it
    }

    @Test
    void should_show_the_overlay(FxRobot robot) {
        robot.interact(test::show);

        then(test.manager().getOverlayCount()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void should_close_by_itself_after_10_seconds_without_interaction(FxRobot robot) {
        then(TestNotification.AUTO_CLOSE).isEqualTo(Duration.ofSeconds(10));
        test.manager().setAutoClose(Duration.ofSeconds(1));   // not to wait 10 seconds
        robot.interact(test::show);
        then(test.manager().getOverlayCount()).isGreaterThanOrEqualTo(1);

        robot.sleep(3000);

        then(test.manager().getOverlayCount()).isZero();
    }
}
