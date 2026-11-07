package ste.wakemeapp.notify;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.BDDAssertions.then;

class OverlayControllerTest {

    @Test
    void should_format_the_countdown_as_hours_minutes_seconds() {
        then(OverlayController.countdownText(Duration.ofMinutes(15))).isEqualTo("Meeting starting in 00:15:00");
        then(OverlayController.countdownText(Duration.ofMinutes(90))).isEqualTo("Meeting starting in 01:30:00");
        then(OverlayController.countdownText(Duration.ofSeconds(59))).isEqualTo("Meeting starting in 00:00:59");
    }

    @Test
    void should_round_partial_seconds_up() {
        then(OverlayController.countdownText(Duration.ofMillis(59_500))).isEqualTo("Meeting starting in 00:01:00");
    }

    @Test
    void should_say_the_meeting_started_when_no_time_is_left() {
        then(OverlayController.countdownText(Duration.ZERO)).isEqualTo("Meeting started");
        then(OverlayController.countdownText(Duration.ofSeconds(-5))).isEqualTo("Meeting started");
    }
}
