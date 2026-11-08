package ste.wakemeapp.log;

import dev.dirs.ProjectDirectories;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.logging.LogManager;

/**
 * Sets up logging, once at startup, through the JUL configuration in the {@code logging.properties}
 * resource: a rolled file in the application data directory plus the console, one line per
 * message. The size of the log is bounded (see the resource).
 */
public final class LogSetup {

    private static final String FILE_HANDLER = "java.util.logging.FileHandler";
    private static final String CONSOLE_HANDLER = "java.util.logging.ConsoleHandler";
    private static final String RESOURCE = "logging.properties";
    private static final String APPLICATION_LOGGER = "ste.wakemeapp";

    /**
     * The parent of all the application's loggers. A strong reference is kept (the LogManager only
     * holds loggers weakly) and its level is set in code too: the JUL configuration is applied only
     * to loggers that exist when it is read or that are created afterwards, and loggers of classes
     * loaded before the setup would otherwise miss a level set by it.
     */
    private static final java.util.logging.Logger APPLICATION = java.util.logging.Logger.getLogger(APPLICATION_LOGGER);

    private LogSetup() {
    }

    /** The {@code logs} folder in the application data directory of this platform (machine-local). */
    public static Path defaultLogDirectory() {
        return Path.of(ProjectDirectories.from("", "", "WakeMeApp").dataLocalDir, "logs");
    }

    /**
     * Applies the logging configuration.
     *
     * @param debug whether the application's DEBUG messages are logged too (default: INFO and above)
     * @return whether the log file can be used; when it cannot, the reason is told on the console
     *         and logging goes to the console only
     */
    public static boolean configure(Path logDirectory, boolean debug) {
        boolean fileLogging = true;
        try {
            Files.createDirectories(logDirectory);
            if (!Files.isWritable(logDirectory)) {
                throw new IOException("the folder is not writable");
            }
        } catch (IOException | RuntimeException e) {
            fileLogging = false;
            System.err.println("Cannot use the log folder " + logDirectory + " (" + e.getMessage()
                + "), logging to the console only");
        }
        try {
            apply(configuration(fileLogging ? logDirectory : null, debug));
            APPLICATION.setLevel(debug ? java.util.logging.Level.FINE : java.util.logging.Level.INFO);
        } catch (IOException e) {
            System.err.println("Cannot configure logging: " + e.getMessage());
            return false;
        }
        return fileLogging;
    }

    /**
     * The JUL configuration: the {@code logging.properties} resource plus what is only known at
     * startup (the folder of the files and, with {@code --debug}, the application's level).
     *
     * @param logDirectory where the files go; {@code null} for the console only
     */
    static Properties configuration(Path logDirectory, boolean debug) {
        Properties configuration = new Properties();
        try (InputStream in = LogSetup.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing resource " + RESOURCE);
            }
            configuration.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        if (logDirectory == null) {
            configuration.setProperty("handlers", CONSOLE_HANDLER);
        } else {
            configuration.setProperty(FILE_HANDLER + ".pattern", filePattern(logDirectory));
        }
        if (debug) {
            configuration.setProperty(APPLICATION_LOGGER + ".level", "FINE");
        }
        return configuration;
    }

    /** JUL file patterns use '/' on every platform and '%' as the escape character. */
    private static String filePattern(Path logDirectory) {
        return logDirectory.toString().replace('\\', '/').replace("%", "%%") + "/wakemeapp.%g.log";
    }

    private static void apply(Properties configuration) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        configuration.store(bytes, null);
        LogManager.getLogManager().readConfiguration(new ByteArrayInputStream(bytes.toByteArray()));
    }
}
