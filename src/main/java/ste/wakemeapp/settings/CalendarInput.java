package ste.wakemeapp.settings;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import ste.wakemeapp.calendar.CalendarAddress;
import ste.wakemeapp.calendar.IcsAddress;

/**
 * What the calendar field of the settings dialog accepts: recognises the kind of a value like
 * {@code --calendar} does, checks it, and says how it may be shown. The messages never contain a link,
 * since a link is a credential.
 */
public final class CalendarInput {

    static final String ACCEPTED = "Enter an https:// link to a published calendar (ICS), "
        + "an https://graph.microsoft.com/\u2026 address or the path of a CSV file";

    private CalendarInput() {
    }

    /** The kind of the value; empty for a blank one and for one that is none of the three. */
    public static Optional<CalendarKind> kindOf(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        if (IcsAddress.isFeed(value)) {
            return Optional.of(CalendarKind.ICS_LINK);
        }
        if (CalendarAddress.isAddress(value)) {
            return Optional.of(CalendarKind.GRAPH_ADDRESS);
        }
        return looksLikeAPath(value.strip()) ? Optional.of(CalendarKind.CSV_FILE) : Optional.empty();
    }

    /** @throws IllegalArgumentException with a message for the user if the value cannot be used */
    public static void validate(String value) {
        CalendarKind kind = kindOf(value).orElseThrow(() -> new IllegalArgumentException(ACCEPTED));
        String v = value.strip();
        switch (kind) {
            case ICS_LINK -> {
                String lower = v.toLowerCase(Locale.ROOT);
                if (!lower.startsWith("https://") && !lower.startsWith("webcal://")) {
                    throw new IllegalArgumentException("The link must start with https:// or webcal://");
                }
                try {
                    IcsAddress.parse(v);
                } catch (IllegalArgumentException x) {
                    throw new IllegalArgumentException("The link is not valid");
                }
            }
            case GRAPH_ADDRESS -> {
                try {
                    CalendarAddress.parse(v);
                } catch (IllegalArgumentException x) {
                    throw new IllegalArgumentException(x.getMessage()
                        .replace("--calendar address", "The address").replace("--calendar is not", "The address is not"));
                }
            }
            case CSV_FILE -> {
                try {
                    Path file = Path.of(v);
                    if (Files.isRegularFile(file) && Files.isReadable(file)) {
                        return;
                    }
                } catch (InvalidPathException x) {
                    // falls through to the message
                }
                throw new IllegalArgumentException("The file cannot be read: " + v);
            }
        }
    }

    /** The value as it may be shown: a link only by its host (https://host/\u2026), the rest as it is. */
    public static String shown(String value) {
        if (value == null) {
            return "";
        }
        return IcsAddress.isFeed(value) ? IcsAddress.redact(value) : value;
    }

    private static boolean looksLikeAPath(String v) {
        return v.contains("/") || v.contains("\\") || v.startsWith("~") || v.startsWith(".")
            || v.toLowerCase(Locale.ROOT).endsWith(".csv");
    }
}
