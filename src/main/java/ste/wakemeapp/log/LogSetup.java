package ste.wakemeapp.log;

import dev.dirs.ProjectDirectories;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * Adjusts at startup what the JUL configuration cannot know. JUL reads the configuration itself
 * ({@code -Djava.util.logging.config.file=.../logging.properties}: console handler, formatter,
 * levels, and the settings of the file handler); this class adds the file handler, whose
 * location is the application data directory of the platform, and applies {@code --debug}.
 * <p>
 * The file is rolled and capped by the {@code java.util.logging.FileHandler} settings of the
 * configuration. Should they be missing, the same limits apply (see {@link #DEFAULT_LIMIT}), so
 * the log can never grow indefinitely.
 */
public final class LogSetup {

    static final int DEFAULT_LIMIT = 1_000_000;
    static final int DEFAULT_COUNT = 3;

    private static final String FILE_HANDLER = "java.util.logging.FileHandler";

    /**
     * The parent of all the application's loggers. A strong reference is kept: the LogManager only
     * holds loggers weakly, and a level set on a logger that is garbage collected is lost.
     */
    private static final Logger APPLICATION = Logger.getLogger("ste.wakemeapp");

    private LogSetup() {
    }

    /** The {@code logs} folder in the application data directory of this platform (machine-local). */
    public static Path defaultLogDirectory() {
        return Path.of(ProjectDirectories.from("", "", "WakeMeApp").dataLocalDir, "logs");
    }

    /**
     * @param debug whether the application's DEBUG messages are logged too (default: INFO and above)
     * @return whether the log file can be used; when it cannot, the reason is told on the console
     *         and logging goes to the console only
     */
    public static boolean configure(Path logDirectory, boolean debug) {
        boolean fileLogging = addFileHandler(logDirectory);
        APPLICATION.setLevel(debug ? Level.FINE : Level.INFO);
        return fileLogging;
    }

    private static boolean addFileHandler(Path logDirectory) {
        try {
            Files.createDirectories(logDirectory);
            if (!Files.isWritable(logDirectory)) {
                throw new IOException("the folder is not writable");
            }
            FileHandler handler = new FileHandler(filePattern(logDirectory),
                intProperty("limit", DEFAULT_LIMIT), intProperty("count", DEFAULT_COUNT), true);
            // the 4-argument constructor takes level, formatter and encoding from the configuration too: defaults if absent
            if (property("formatter") == null) {
                handler.setFormatter(new LineFormatter());
            }
            if (property("encoding") == null) {
                handler.setEncoding("UTF-8");
            }
            Logger.getLogger("").addHandler(handler);
            return true;
        } catch (IOException | RuntimeException e) {
            System.err.println("Cannot use the log folder " + logDirectory + " (" + e.getMessage()
                + "), logging to the console only");
            return false;
        }
    }

    /** JUL file patterns use '/' on every platform and '%' as the escape character. */
    static String filePattern(Path logDirectory) {
        return logDirectory.toString().replace('\\', '/').replace("%", "%%") + "/wakemeapp.%g.log";
    }

    private static String property(String name) {
        return LogManager.getLogManager().getProperty(FILE_HANDLER + "." + name);
    }

    private static int intProperty(String name, int defaultValue) {
        String value = property(name);
        try {
            return value == null ? defaultValue : Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
