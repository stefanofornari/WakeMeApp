package ste.wakemeapp.calendar;

import java.io.IOException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import ste.wakemeapp.notify.MeetingNotification;
import static org.assertj.core.api.BDDAssertions.then;

class InMemoryCalendarSourceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 7, 9, 0);

    private static CalendarEvent event(String id, LocalDateTime start) {
        return new CalendarEvent(id, id, start, start.plusHours(1), "https://teams.example.com/" + id,
            "Ann", null, ResponseStatus.ACCEPTED, false);
    }

    @Test
    void should_return_only_the_events_starting_inside_the_window() throws IOException {
        InMemoryCalendarSource source = new InMemoryCalendarSource();
        source.add(event("before", NOW.minusMinutes(1)));
        source.add(event("at-start", NOW));
        source.add(event("inside", NOW.plusMinutes(12)));
        source.add(event("at-end", NOW.plusMinutes(15)));

        then(source.meetingsStartingBetween(NOW, NOW.plusMinutes(15)))
            .extracting(CalendarEvent::id).containsExactlyInAnyOrder("at-start", "inside");
    }

    @Test
    void should_reflect_changes_made_between_two_queries() throws IOException {
        InMemoryCalendarSource source = new InMemoryCalendarSource();
        source.add(event("a", NOW.plusMinutes(5)));
        source.add(event("b", NOW.plusMinutes(6)));
        source.remove("a");
        source.add(event("b", NOW.plusMinutes(8))); // same id replaces the previous version

        then(source.meetingsStartingBetween(NOW, NOW.plusMinutes(15)))
            .extracting(CalendarEvent::id, CalendarEvent::start)
            .containsExactly(org.assertj.core.groups.Tuple.tuple("b", NOW.plusMinutes(8)));
    }

    @Test
    void should_turn_an_event_into_the_notification_data() {
        CalendarEvent event = new CalendarEvent("id1", "Standup", NOW, NOW.plusMinutes(30),
            "https://teams.example.com/join", "Ann", "Daily", ResponseStatus.TENTATIVE, false);

        MeetingNotification notification = event.toNotification();

        then(notification.eventId()).isEqualTo("id1");
        then(notification.title()).isEqualTo("Standup");
        then(notification.startTime()).isEqualTo(NOW);
        then(notification.endTime()).isEqualTo(NOW.plusMinutes(30));
        then(notification.joinUrl()).isEqualTo("https://teams.example.com/join");
        then(notification.organizer()).isEqualTo("Ann");
        then(notification.description()).isEqualTo("Daily");
    }
}
