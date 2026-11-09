package ste.wakemeapp.log;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.BDDAssertions.then;

/**
 * The console handler of JUL always writes to {@code System.err}, where runners and shells treat
 * everything as a problem; the application logs to {@code System.out}.
 */
class StdoutHandlerTest {

    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    @BeforeEach
    void capture_the_standard_streams() {
        System.setOut(new PrintStream(out, true));
        System.setErr(new PrintStream(err, true));
    }

    @AfterEach
    void restore_the_standard_streams() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    @Test
    void should_write_the_messages_to_stdout_and_nothing_to_stderr() {
        StdoutHandler handler = new StdoutHandler();
        handler.setFormatter(new LineFormatter());

        handler.publish(new LogRecord(Level.INFO, "Polling meetings.csv"));

        then(out.toString()).contains(" INFO  Polling meetings.csv");
        then(err.toString()).isEmpty();
    }

    @Test
    void should_write_each_message_as_soon_as_it_is_published() {
        StdoutHandler handler = new StdoutHandler();
        handler.setFormatter(new LineFormatter());

        handler.publish(new LogRecord(Level.INFO, "first"));
        then(out.toString()).contains("first");

        handler.publish(new LogRecord(Level.WARNING, "second"));
        then(out.toString()).contains("second");
    }

    @Test
    void should_use_the_level_and_the_formatter_of_its_properties() throws Exception {
        String name = StdoutHandler.class.getName();
        java.util.logging.LogManager.getLogManager().readConfiguration(new java.io.ByteArrayInputStream((
            name + ".level = WARNING\n" + name + ".formatter = " + LineFormatter.class.getName() + "\n"
        ).getBytes()));

        try {
            StdoutHandler handler = new StdoutHandler();
            handler.publish(new LogRecord(Level.INFO, "too low"));
            handler.publish(new LogRecord(Level.WARNING, "enough"));

            then(out.toString()).doesNotContain("too low").contains(" WARN  enough");
        } finally {
            java.util.logging.LogManager.getLogManager().readConfiguration();
        }
    }

    @Test
    void should_not_close_stdout_when_it_is_closed() {
        StdoutHandler handler = new StdoutHandler();
        handler.setFormatter(new LineFormatter());

        handler.close();
        System.out.println("still open");

        then(out.toString()).contains("still open");
    }
}
