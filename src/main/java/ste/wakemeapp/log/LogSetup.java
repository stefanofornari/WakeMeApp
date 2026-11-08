package ste.wakemeapp.log;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The only thing of the logging that is decided in code. Everything else (handlers, formatter, the
 * log file and its cap) is in {@code logging.properties}, which JUL reads itself when the JVM is
 * started with {@code -Djava.util.logging.config.file=.../logging.properties}.
 */
public final class LogSetup {

    /**
     * The parent of all the application's loggers. A strong reference is kept: the LogManager only
     * holds loggers weakly, and a level set on a logger that is garbage collected is lost.
     */
    private static final Logger APPLICATION = Logger.getLogger("ste.wakemeapp");

    private LogSetup() {
    }

    /** @param debug whether the application's DEBUG messages are logged too (default: INFO and above) */
    public static void configure(boolean debug) {
        APPLICATION.setLevel(debug ? Level.FINE : Level.INFO);
    }
}
