package ste.wakemeapp.notify;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.BDDAssertions.then;

class MeetingNotificationTest {

    @Test
    void should_have_no_event_id_when_not_coming_from_a_calendar() {
        then(new MeetingNotification("Standup", LocalDateTime.now(), "https://example.com").eventId()).isNull();
    }

    @Test
    void should_keep_the_event_id_and_original_start_across_a_snooze() {
        LocalDateTime start = LocalDateTime.of(2026, 11, 8, 10, 0);
        MeetingNotification meeting = new MeetingNotification("Standup", start, null, null, null, null, 0, 3, start, "e1");

        MeetingNotification snoozed = meeting.withSnooze(5, start.minusMinutes(2));

        then(snoozed.eventId()).isEqualTo("e1");
        then(snoozed.originalStartTime()).isEqualTo(start);
        then(snoozed.snoozeCount()).isEqualTo(1);
    }
}
