package ste.wakemeapp.calendar;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

/** Where meetings come from: a remote calendar, or a test double. */
@FunctionalInterface
public interface CalendarSource {

    /**
     * @return the events starting at or after {@code from} and before {@code to}, in local time
     * @throws IOException when the calendar cannot be read; the caller decides when to retry
     */
    List<CalendarEvent> meetingsStartingBetween(LocalDateTime from, LocalDateTime to) throws IOException;
}
