package ste.wakemeapp.settings;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * When the calendar was last read successfully, kept between runs (shown in the tray menu). It is state, not a
 * setting: the settings dialog does not show it. It belongs to the calendar that was read, so it is cleared when
 * the calendar changes.
 */
public final class LastUpdate {

    public static final String KEY = "calendar.last_update";

    private static final Logger LOG = Logger.getLogger(LastUpdate.class.getName());

    private LastUpdate() {
    }

    /** The stored instant; empty if there is none, or if the stored value is not an instant (a WARNING says so). */
    public static Optional<Instant> read(Settings stored) {
        String value = stored.getString(KEY, "");
        if (value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Instant.parse(value.strip()));
        } catch (DateTimeParseException x) {
            LOG.warning("The setting " + KEY + " is not a date and time; ignored");
            return Optional.empty();
        }
    }

    /** Stores {@code instant}, to the second. */
    public static void save(Settings stored, Instant instant) {
        stored.putString(KEY, instant.truncatedTo(ChronoUnit.SECONDS).toString());
    }

    public static void clear(Settings stored) {
        stored.remove(KEY);
    }

    static boolean isValid(String text) {
        try {
            Instant.parse(text.strip());
            return true;
        } catch (DateTimeParseException x) {
            return false;
        }
    }
}
