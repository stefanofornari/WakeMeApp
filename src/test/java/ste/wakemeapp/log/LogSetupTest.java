package ste.wakemeapp.log;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.BDDAssertions.then;

/**
 * The logging configuration is {@code logging.properties}, read by JUL itself when the JVM is
 * started with {@code -Djava.util.logging.config.file=...} (see the pom); the file handler is
 * LazyJava's {@code FileLogHandler}. The only thing left to code is the level.
 * <p>
 * The tests that write the log read the production file too, with the system data folder replaced
 * by a temporary one.
 */
class LogSetupTest {

    private static final String RESOURCE = "/ste/wakemeapp/log/logging.properties";
    private static final String FILE_HANDLER = "ste.lazyjava.logging.FileLogHandler";
    private static final Logger LOG = Logger.getLogger("ste.wakemeapp.test");

    @TempDir
    Path dir;

    private static String production() throws IOException {
        try (InputStream in = LogSetupTest.class.getResourceAsStream(RESOURCE)) {
            return new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
        }
    }

    @BeforeEach
    void log_under_the_temporary_folder() throws IOException {
        String folder = dir.toString().replace('\\', '/');
        LogManager.getLogManager().readConfiguration(new ByteArrayInputStream(
            production().replace("${dataDir}", folder).getBytes(StandardCharsets.ISO_8859_1)
        ));
    }

    @AfterEach
    void restore_default_logging() throws IOException {
        LogManager.getLogManager().readConfiguration();
    }

    private String logFile() throws IOException {
        return Files.readString(dir.resolve("logs").resolve("wakemeapp.0.log"));
    }

    @Test
    void should_use_the_lazyjava_file_log_handler_configured_by_the_properties_file() throws IOException {
        Properties config = new Properties();
        config.load(new ByteArrayInputStream(production().getBytes(StandardCharsets.ISO_8859_1)));

        then(config.getProperty("handlers")).contains("java.util.logging.ConsoleHandler").contains(FILE_HANDLER);
        then(config.getProperty(FILE_HANDLER + ".application")).isEqualTo("WakeMeApp");
        then(config.getProperty(FILE_HANDLER + ".pattern")).isEqualTo("${dataDir}/logs/wakemeapp.%g.log");
        then(config.getProperty(FILE_HANDLER + ".limit")).isEqualTo("1000000");
        then(config.getProperty(FILE_HANDLER + ".count")).isEqualTo("3");
        then(config.getProperty(FILE_HANDLER + ".formatter")).isEqualTo(LineFormatter.class.getName());
    }

    @Test
    void should_write_to_the_log_folder_of_the_application_data_directory() throws IOException {
        LOG.info("Polling meetings.csv every 5 min");

        then(logFile()).contains(" INFO  Polling meetings.csv every 5 min\n");
    }

    @Test
    void should_log_at_info_by_default() throws IOException {
        LogSetup.configure(false);

        LOG.fine("a detail");
        LOG.info("a flow");

        then(logFile()).contains("a flow").doesNotContain("a detail");
    }

    @Test
    void should_add_the_debug_messages_when_asked() throws IOException {
        LogSetup.configure(true);

        LOG.fine("a detail");

        then(logFile()).contains(" DEBUG a detail");
        then(Logger.getLogger("ste.wakemeapp").getLevel()).isEqualTo(Level.FINE);
    }
}
