package ste.wakemeapp.auth;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import ste.wakemeapp.calendar.CalendarAccessException;
import ste.wakemeapp.calendar.CalendarEvent;
import ste.wakemeapp.calendar.CalendarSource;
import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;

/** No reading of the calendar until the user has signed in. */
class SignedInCalendarSourceTest {

    private static final LocalDateTime FROM = LocalDateTime.of(2026, 11, 9, 11, 45);
    private static final LocalDateTime TO = LocalDateTime.of(2026, 11, 9, 12, 0);

    private final AtomicInteger reads = new AtomicInteger();
    private final AtomicBoolean signedIn = new AtomicBoolean();
    private final CalendarSource inner = (from, to) -> { reads.incrementAndGet(); return List.of(); };
    private final CalendarSource source = new SignedInCalendarSource(inner, signedIn::get);

    @Test
    void the_calendar_is_not_read_while_the_sign_in_is_waiting() {
        thenThrownBy(() -> source.meetingsStartingBetween(FROM, TO))
            .isInstanceOf(CalendarAccessException.class)
            .hasMessageContaining("Waiting for the sign-in")
            .satisfies(x -> then(((CalendarAccessException) x).isTemporary()).isTrue());
        then(reads.get()).isEqualTo(0);
    }

    @Test
    void the_calendar_is_read_as_soon_as_the_sign_in_is_done() throws IOException {
        thenThrownBy(() -> source.meetingsStartingBetween(FROM, TO)).isInstanceOf(CalendarAccessException.class);

        signedIn.set(true);
        List<CalendarEvent> events = source.meetingsStartingBetween(FROM, TO);

        then(events).isEmpty();
        then(reads.get()).isEqualTo(1);
    }

    @Test
    void a_failed_renewal_of_the_sign_in_is_a_calendar_access_error_with_the_same_kind() {
        signedIn.set(true);
        CalendarSource temporary = new SignedInCalendarSource((from, to) -> {
            throw new SignInException("Microsoft cannot be reached", true, null);
        }, signedIn::get);
        CalendarSource permanent = new SignedInCalendarSource((from, to) -> {
            throw new SignInException("Microsoft refused the sign-in", false, null);
        }, signedIn::get);

        thenThrownBy(() -> temporary.meetingsStartingBetween(FROM, TO))
            .isInstanceOf(CalendarAccessException.class)
            .hasMessageContaining("cannot be reached")
            .satisfies(x -> then(((CalendarAccessException) x).isTemporary()).isTrue());
        thenThrownBy(() -> permanent.meetingsStartingBetween(FROM, TO))
            .isInstanceOf(CalendarAccessException.class)
            .satisfies(x -> then(((CalendarAccessException) x).isTemporary()).isFalse());
    }
}
