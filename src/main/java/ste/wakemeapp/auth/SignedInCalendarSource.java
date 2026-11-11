package ste.wakemeapp.auth;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.BooleanSupplier;
import ste.wakemeapp.calendar.CalendarAccessException;
import ste.wakemeapp.calendar.CalendarEvent;
import ste.wakemeapp.calendar.CalendarSource;

/**
 * A calendar that is read only once the user has signed in. Until then every poll fails with a
 * temporary calendar error ("waiting for the sign-in"), which the poller logs as a WARNING and
 * retries at the next poll.
 */
public class SignedInCalendarSource implements CalendarSource {

    private final CalendarSource calendar;
    private final BooleanSupplier signedIn;

    public SignedInCalendarSource(CalendarSource calendar, BooleanSupplier signedIn) {
        this.calendar = calendar;
        this.signedIn = signedIn;
    }

    @Override
    public List<CalendarEvent> meetingsStartingBetween(LocalDateTime from, LocalDateTime to) throws IOException {
        if (!signedIn.getAsBoolean()) {
            throw new CalendarAccessException("Waiting for the sign-in to complete: the calendar is not read yet", true);
        }
        try {
            return calendar.meetingsStartingBetween(from, to);
        } catch (SignInException x) {
            // the access token supplier could not renew the sign-in: a calendar error of the same kind
            throw new CalendarAccessException(x.getMessage(), x, x.isTemporary());
        }
    }
}
