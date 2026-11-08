package ste.wakemeapp.log;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 * One line per message: {@code 2026-11-08 09:12:03 INFO  Polling meetings.csv}. Levels are named
 * ERROR, WARN, INFO and DEBUG (not SEVERE, WARNING and FINE); an exception adds its stack trace
 * on the following lines. Instantiated by name from the JUL configuration, hence public.
 */
public final class LineFormatter extends Formatter {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public String format(LogRecord record) {
        StringBuilder line = new StringBuilder()
            .append(TIME.withZone(ZoneId.systemDefault()).format(record.getInstant()))
            .append(' ').append(levelName(record.getLevel()))
            .append(' ').append(formatMessage(record))
            .append('\n');
        if (record.getThrown() != null) {
            StringWriter trace = new StringWriter();
            record.getThrown().printStackTrace(new PrintWriter(trace));
            line.append(trace);
        }
        return line.toString();
    }

    /** The level name, padded to 5 characters. */
    private static String levelName(Level level) {
        int value = level.intValue();
        if (value >= Level.SEVERE.intValue()) {
            return "ERROR";
        }
        if (value >= Level.WARNING.intValue()) {
            return "WARN ";
        }
        return value >= Level.INFO.intValue() ? "INFO " : "DEBUG";
    }
}
