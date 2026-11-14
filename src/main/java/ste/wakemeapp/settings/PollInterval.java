package ste.wakemeapp.settings;

import java.time.Duration;
import java.util.List;
import java.util.logging.Logger;

/** How often the calendar is polled: one of a few choices, 5 minutes unless chosen otherwise. */
public final class PollInterval {

    public static final String KEY = "poll.intervalMinutes";
    public static final List<Integer> CHOICES = List.of(1, 2, 5, 10, 15, 30);
    public static final int DEFAULT_MINUTES = 5;

    private static final Logger LOG = Logger.getLogger(PollInterval.class.getName());

    private PollInterval() {
    }

    /** The stored interval; a stored value that is not one of the choices is ignored with a WARNING. */
    public static Duration read(Settings stored) {
        int minutes = stored.getInt(KEY, DEFAULT_MINUTES);
        if (!CHOICES.contains(minutes)) {
            LOG.warning("The setting " + KEY + " is '" + minutes + "', which is not one of " + CHOICES
                + "; using " + DEFAULT_MINUTES);
            minutes = DEFAULT_MINUTES;
        }
        return Duration.ofMinutes(minutes);
    }

    public static void save(Settings stored, int minutes) {
        stored.putInt(KEY, minutes);
    }

    static boolean isValid(String text) {
        try {
            return CHOICES.contains(Integer.parseInt(text.trim()));
        } catch (NumberFormatException x) {
            return false;
        }
    }
}
