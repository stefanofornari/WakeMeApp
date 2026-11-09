package ste.wakemeapp.log;

import java.util.logging.LogRecord;
import java.util.logging.StreamHandler;

/**
 * Writes the log to {@code System.out}. The JUL {@code ConsoleHandler} always writes to
 * {@code System.err}, where runners and shells present every line as a warning, even a plain INFO
 * message. Level, filter, formatter and encoding come from the JUL configuration under this class's
 * name, like for any handler.
 */
public class StdoutHandler extends StreamHandler {

    public StdoutHandler() {
        super();
        setOutputStream(System.out);
    }

    /** Messages are shown as they come, not when a buffer is full. */
    @Override
    public synchronized void publish(LogRecord record) {
        super.publish(record);
        flush();
    }

    /** Stdout is not ours to close. */
    @Override
    public synchronized void close() {
        flush();
    }
}
