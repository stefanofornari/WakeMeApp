package ste.wakemeapp.tray;

import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;
import java.util.Optional;

/** The text of the last-update item of the tray menu. */
final class LastUpdateText {

    static final String PREFIX = "Last update: ";
    static final String NONE = PREFIX + "\u2014";

    private LastUpdateText() {
    }

    /**
     * "Last update: 16:49" if the instant is today (the clock's day and zone), "Last update: 18 Nov, 16:49" if
     * it is another day, "Last update: \u2014" if there is none. The formats are those of {@code locale}.
     */
    static String of(Optional<Instant> last, Clock clock, Locale locale) {
        if (last.isEmpty()) {
            return NONE;
        }
        ZonedDateTime at = last.get().atZone(clock.getZone());
        DateTimeFormatter time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale);
        if (at.toLocalDate().equals(ZonedDateTime.now(clock).toLocalDate())) {
            return PREFIX + time.format(at);
        }
        return PREFIX + DateTimeFormatter.ofLocalizedPattern("MMMd").withLocale(locale).format(at) + ", " + time.format(at);
    }
}
