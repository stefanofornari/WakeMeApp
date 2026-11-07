package ste.wakemeapp.calendar;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A calendar kept in memory, to stand in for a remote calendar. Events can be added, replaced
 * (same id) and removed while a poller is running, like changes made in a real calendar.
 */
public final class InMemoryCalendarSource implements CalendarSource {

    private final Map<String, CalendarEvent> events = new ConcurrentHashMap<>();

    /** Adds the event, replacing the one with the same id. */
    public void add(CalendarEvent event) {
        events.put(event.id(), event);
    }

    public void remove(String eventId) {
        events.remove(eventId);
    }

    @Override
    public List<CalendarEvent> meetingsStartingBetween(LocalDateTime from, LocalDateTime to) {
        return events.values().stream()
            .filter(e -> !e.start().isBefore(from) && e.start().isBefore(to))
            .toList();
    }
}
