package ste.wakemeapp.log;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.BDDAssertions.then;

class LineFormatterTest {

    private static final Instant AT = Instant.parse("2026-11-08T08:12:03Z");

    private final LineFormatter formatter = new LineFormatter();

    private static String time() {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault()).format(AT);
    }

    private static LogRecord record(Level level, String message) {
        LogRecord record = new LogRecord(level, message);
        record.setInstant(AT);
        return record;
    }

    @Test
    void should_write_timestamp_level_and_message_on_one_line() {
        then(formatter.format(record(Level.INFO, "Polling meetings.csv"))).isEqualTo(time() + " INFO  Polling meetings.csv\n");
    }

    @Test
    void should_name_the_levels_error_warn_info_and_debug() {
        then(formatter.format(record(Level.SEVERE, "m"))).contains(" ERROR m");
        then(formatter.format(record(Level.WARNING, "m"))).contains(" WARN  m");
        then(formatter.format(record(Level.INFO, "m"))).contains(" INFO  m");
        then(formatter.format(record(Level.FINE, "m"))).contains(" DEBUG m");
        then(formatter.format(record(Level.FINEST, "m"))).contains(" DEBUG m");
    }

    @Test
    void should_fill_in_the_message_parameters() {
        LogRecord record = record(Level.INFO, "Scheduled {0} for {1}");
        record.setParameters(new Object[] {"Standup", "09:25"});

        then(formatter.format(record)).endsWith(" INFO  Scheduled Standup for 09:25\n");
    }

    @Test
    void should_follow_the_message_with_the_stack_trace_of_an_exception() {
        LogRecord record = record(Level.SEVERE, "Cannot show the notification");
        record.setThrown(new IllegalStateException("boom"));

        String line = formatter.format(record);

        then(line).startsWith(time() + " ERROR Cannot show the notification\n");
        then(line).contains("\njava.lang.IllegalStateException: boom\n").contains("\tat ");
    }
}
