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
        then(OverlayController.countdownText(Duration.ZERO)).isEqualTo("Meeting started - Join now");
        then(OverlayController.countdownText(Duration.ofSeconds(-5))).isEqualTo("Meeting started - Join now");
    }

    @Test
    void should_be_calm_while_more_than_five_minutes_remain() {
        then(OverlayController.countdownState(Duration.ofMinutes(15))).isEqualTo(OverlayController.CountdownState.PLENTY);
        then(OverlayController.countdownState(Duration.ofMinutes(6))).isEqualTo(OverlayController.CountdownState.PLENTY);
        then(OverlayController.countdownState(Duration.ofMinutes(5))).isEqualTo(OverlayController.CountdownState.PLENTY);
    }

    @Test
    void should_warn_from_4_59_down_to_one_minute() {
        then(OverlayController.countdownState(Duration.ofSeconds(299))).isEqualTo(OverlayController.CountdownState.SOON);
        then(OverlayController.countdownState(Duration.ofSeconds(60))).isEqualTo(OverlayController.CountdownState.SOON);
    }

    @Test
    void should_alert_in_the_last_minute() {
        then(OverlayController.countdownState(Duration.ofSeconds(59))).isEqualTo(OverlayController.CountdownState.IMMINENT);
        then(OverlayController.countdownState(Duration.ofSeconds(1))).isEqualTo(OverlayController.CountdownState.IMMINENT);
    }

    @Test
    void should_be_started_when_no_time_is_left() {
        then(OverlayController.countdownState(Duration.ZERO)).isEqualTo(OverlayController.CountdownState.STARTED);
        then(OverlayController.countdownState(Duration.ofSeconds(-30))).isEqualTo(OverlayController.CountdownState.STARTED);
    }

    @Test
    void should_use_the_displayed_second_for_the_thresholds() {
        // 4:59.5 is shown as 00:05:00, so it is still in the first range
        then(OverlayController.countdownState(Duration.ofMillis(299_500))).isEqualTo(OverlayController.CountdownState.PLENTY);
    }
}
