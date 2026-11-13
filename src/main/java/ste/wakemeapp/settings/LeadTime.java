package ste.wakemeapp.settings;

import java.time.Duration;
import java.util.Optional;
import java.util.logging.Logger;

/** How many minutes before a meeting the user is notified: 1 to 1440, 5 unless the user chose otherwise. */
public final class LeadTime {

    public static final String KEY = "notify.leadTimeMinutes";
    public static final int DEFAULT_MINUTES = 5;
    public static final int MIN_MINUTES = 1;
    public static final int MAX_MINUTES = 1440;

    private static final Logger LOG = Logger.getLogger(LeadTime.class.getName());

    private LeadTime() {
    }

    /** The stored lead time; a stored value out of range or not a number is ignored with a WARNING. */
    public static Duration read(Settings stored) {
        int minutes = stored.getInt(KEY, DEFAULT_MINUTES);
        if (!isValid(minutes)) {
            LOG.warning("The setting " + KEY + " is '" + minutes + "', which is not between " + MIN_MINUTES
                + " and " + MAX_MINUTES + "; using " + DEFAULT_MINUTES);
            minutes = DEFAULT_MINUTES;
        }
        return Duration.ofMinutes(minutes);
    }

    public static void save(Settings stored, int minutes) {
        stored.putInt(KEY, minutes);
    }

    static boolean isValid(int minutes) {
        return minutes >= MIN_MINUTES && minutes <= MAX_MINUTES;
    }

    static boolean isValid(String text) {
        try {
            return isValid(Integer.parseInt(text.trim()));
        } catch (NumberFormatException x) {
            return false;
        }
    }

    /**
     * @return the minutes typed by the user
     * @throws IllegalArgumentException with a message for the user if it is not a number between 1 and 1440
     */
    public static int parse(String text) {
        int minutes;
        try {
            minutes = Integer.parseInt(text == null ? "" : text.trim());
        } catch (NumberFormatException x) {
            throw new IllegalArgumentException("Lead time must be a whole number of minutes");
        }
        if (minutes < MIN_MINUTES) {
            throw new IllegalArgumentException("Lead time must be at least " + MIN_MINUTES + " minute");
        }
        if (minutes > MAX_MINUTES) {
            throw new IllegalArgumentException(
                "Lead time cannot exceed " + MAX_MINUTES + " minutes (24 hours)");
        }
        return minutes;
    }

    /** A warning, not an error: with a poll interval longer than the lead time a notification may be late. */
    public static Optional<String> pollWarning(int leadMinutes, Duration pollInterval) {
        if (pollInterval.toMinutes() > leadMinutes) {
            return Optional.of("Poll interval (" + pollInterval.toMinutes() + " min) exceeds lead time ("
                + leadMinutes + " min). Notifications may be delayed.");
        }
        return Optional.empty();
    }
}
