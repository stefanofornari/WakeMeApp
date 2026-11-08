package ste.wakemeapp.log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.BDDAssertions.then;

class LogSetupTest {

    private static final Logger LOG = Logger.getLogger("ste.wakemeapp.test");

    @TempDir
    Path dir;

    @AfterEach
    void restore_default_logging() throws IOException {
        LogManager.getLogManager().readConfiguration(); // closes our handlers, back to the JDK default
    }

    private String logFile(Path folder) throws IOException {
        return Files.readString(folder.resolve("wakemeapp.0.log"));
    }

    @Test
    void should_write_one_line_per_message_to_the_log_file() throws IOException {
        then(LogSetup.configure(dir, false)).isTrue();

        LOG.info("Polling meetings.csv every 5 min");

        then(logFile(dir)).matches("(?s)\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2} INFO  Polling meetings.csv every 5 min\n");
    }

    @Test
    void should_create_the_log_folder_when_missing() throws IOException {
        Path logs = dir.resolve("a").resolve("logs");

        LogSetup.configure(logs, false);
        LOG.info("first message");

        then(logFile(logs)).contains("first message");
    }

    @Test
    void should_follow_a_logged_exception_with_its_stack_trace() throws IOException {
        LogSetup.configure(dir, false);

        LOG.log(java.util.logging.Level.SEVERE, "Cannot show the notification", new IllegalStateException("boom"));

        then(logFile(dir)).contains(" ERROR Cannot show the notification\n").contains("IllegalStateException: boom").contains("\tat ");
    }

    @Test
    void should_log_at_info_by_default() throws IOException {
        LogSetup.configure(dir, false);

        LOG.fine("a detail");
        LOG.info("a flow");

        then(logFile(dir)).contains("a flow").doesNotContain("a detail");
    }

    @Test
    void should_add_the_debug_messages_when_asked() throws IOException {
        LogSetup.configure(dir, true);

        LOG.fine("a detail");

        then(logFile(dir)).contains(" DEBUG a detail");
    }

    @Test
    void should_keep_the_total_size_of_the_log_bounded() throws IOException {
        LogSetup.configure(dir, false);
        String line = "x".repeat(3000);

        for (int i = 0; i < 1500; i++) { // 4.5 MB
            LOG.info(line);
        }

        try (Stream<Path> files = Files.list(dir)) {
            List<Path> logs = files.filter(p -> p.getFileName().toString().matches("wakemeapp\\.\\d+\\.log")).toList();
            then(logs).hasSizeLessThanOrEqualTo(3);
            for (Path log : logs) {
                then(Files.size(log)).isLessThanOrEqualTo(1_100_000L);
            }
        }
    }

    @Test
    void should_also_write_to_the_console() throws IOException {
        PrintStream original = System.err;
        ByteArrayOutputStream console = new ByteArrayOutputStream();
        System.setErr(new PrintStream(console, true));
        try {
            LogSetup.configure(dir, false);
            LOG.info("Polling meetings.csv");
            LOG.fine("a detail");
        } finally {
            System.setErr(original);
        }

        then(console.toString()).contains(" INFO  Polling meetings.csv\n").doesNotContain("a detail");
    }

    @Test
    void should_start_anyway_when_the_log_folder_cannot_be_used() throws IOException {
        Path file = Files.writeString(dir.resolve("blocked"), "not a folder");
        PrintStream original = System.err;
        ByteArrayOutputStream console = new ByteArrayOutputStream();
        System.setErr(new PrintStream(console, true));
        boolean fileLogging;
        try {
            fileLogging = LogSetup.configure(file.resolve("logs"), false);
            LOG.info("still logging");
        } finally {
            System.setErr(original);
        }

        then(fileLogging).isFalse();
        then(console.toString()).contains("Cannot use the log folder").contains("still logging");
    }

    @Test
    void should_roll_three_files_of_one_megabyte_in_the_given_folder() {
        Properties configuration = LogSetup.configuration(dir, false);

        then(configuration.getProperty("java.util.logging.FileHandler.limit")).isEqualTo("1000000");
        then(configuration.getProperty("java.util.logging.FileHandler.count")).isEqualTo("3");
        then(configuration.getProperty("java.util.logging.FileHandler.pattern"))
            .isEqualTo(dir.toString().replace('\\', '/').replace("%", "%%") + "/wakemeapp.%g.log");
    }

    @Test
    void should_escape_percent_signs_of_the_folder_for_the_file_pattern() {
        Properties configuration = LogSetup.configuration(Path.of("/tmp/100%/logs"), false);

        then(configuration.getProperty("java.util.logging.FileHandler.pattern")).isEqualTo("/tmp/100%%/logs/wakemeapp.%g.log");
    }

    @Test
    void should_take_the_static_settings_from_the_logging_properties_resource() throws IOException {
        Properties resource = new Properties();
        try (java.io.InputStream in = LogSetup.class.getResourceAsStream("logging.properties")) {
            then(in).as("the logging.properties resource").isNotNull();
            resource.load(in);
        }

        then(resource.getProperty("handlers")).contains("FileHandler").contains("ConsoleHandler");
        then(resource.getProperty("java.util.logging.FileHandler.limit")).isEqualTo("1000000");
        then(resource.getProperty("java.util.logging.FileHandler.count")).isEqualTo("3");
        then(resource.getProperty("java.util.logging.FileHandler.formatter")).isEqualTo(LineFormatter.class.getName());
        then(resource.getProperty("java.util.logging.ConsoleHandler.formatter")).isEqualTo(LineFormatter.class.getName());
        then(resource.getProperty("ste.wakemeapp.level")).isEqualTo("INFO");
        // the folder is only known at startup, so the pattern is not in the file
        then(resource.getProperty("java.util.logging.FileHandler.pattern")).isNull();
    }

    @Test
    void should_only_add_the_folder_and_the_debug_level_to_the_resource() {
        Properties configuration = LogSetup.configuration(dir, true);

        then(configuration.getProperty("ste.wakemeapp.level")).isEqualTo("FINE");
        then(configuration.getProperty("java.util.logging.FileHandler.pattern")).startsWith(dir.toString().replace('\\', '/'));
        then(configuration.getProperty("java.util.logging.FileHandler.count")).isEqualTo("3");
    }

    @Test
    void should_leave_the_file_handler_out_when_there_is_no_usable_folder() {
        Properties configuration = LogSetup.configuration(null, false);

        then(configuration.getProperty("handlers")).isEqualTo("java.util.logging.ConsoleHandler");
        then(configuration.getProperty("java.util.logging.FileHandler.pattern")).isNull();
    }
}
