package ste.wakemeapp.log;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.BDDAssertions.then;

/**
 * The logging configuration is the one given to the JVM with
 * {@code -Djava.util.logging.config.file=.../logging.properties} (see the pom); the only thing left
 * to code is the level.
 */
class LogSetupTest {

    private static final Logger LOG = Logger.getLogger("ste.wakemeapp.test");

    @TempDir
    Path dir;

    @BeforeEach
    void log_under_the_temporary_folder() throws IOException {
        System.setProperty("app.data.dir", dir.toString());
        LogManager.getLogManager().readConfiguration(); // the handlers expand the folder when created
    }

    @AfterEach
    void restore_default_logging() throws IOException {
        System.clearProperty("app.data.dir");
        LogManager.getLogManager().readConfiguration();
    }

    private String logFile() throws IOException {
        return Files.readString(dir.resolve("logs").resolve("wakemeapp.0.log"));
    }

    @Test
    void should_have_the_jvm_configuration_describe_the_handlers() {
        LogManager manager = LogManager.getLogManager();

        then(manager.getProperty("handlers")).contains("java.util.logging.ConsoleHandler").contains(AppFileHandler.class.getName());
        then(manager.getProperty(AppFileHandler.class.getName() + ".pattern")).isEqualTo("${app.data.dir}/logs/wakemeapp.%g.log");
        then(manager.getProperty(AppFileHandler.class.getName() + ".limit")).isEqualTo("1000000");
        then(manager.getProperty(AppFileHandler.class.getName() + ".count")).isEqualTo("3");
        then(manager.getProperty(AppFileHandler.class.getName() + ".formatter")).isEqualTo(LineFormatter.class.getName());
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
