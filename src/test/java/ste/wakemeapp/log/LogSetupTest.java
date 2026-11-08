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
        ByteArrayOutputStream console = captureConsole();
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
        ByteArrayOutputStream console = captureConsole();
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


    /** The console handler binds to System.err when the JUL configuration is read: read it again after redirecting it. */
    private ByteArrayOutputStream captureConsole() throws IOException {
        ByteArrayOutputStream console = new ByteArrayOutputStream();
        System.setErr(new PrintStream(console, true));
        LogManager.getLogManager().readConfiguration();
        return console;
    }

    @Test
    void should_rely_on_the_logging_properties_given_to_the_jvm() {
        LogManager manager = LogManager.getLogManager();

        // -Djava.util.logging.config.file=.../logging.properties (see the surefire argLine in the pom)
        then(manager.getProperty("java.util.logging.FileHandler.limit")).isEqualTo("1000000");
        then(manager.getProperty("java.util.logging.FileHandler.count")).isEqualTo("3");
        then(manager.getProperty("java.util.logging.FileHandler.formatter")).isEqualTo(LineFormatter.class.getName());
        // the file handler is added at startup, when the folder is known: JUL must not create its own
        then(manager.getProperty("handlers")).contains("ConsoleHandler").doesNotContain("FileHandler");
    }

    @Test
    void should_keep_the_cap_and_the_line_format_without_the_configuration_file() throws IOException {
        LogManager.getLogManager().readConfiguration(
            new java.io.ByteArrayInputStream("handlers = java.util.logging.ConsoleHandler\n".getBytes()));
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
        then(logFile(dir)).startsWith("20").contains(" INFO  xxx");
    }

    @Test
    void should_escape_percent_signs_of_the_folder_for_the_file_pattern() {
        then(LogSetup.filePattern(Path.of("/tmp/100%/logs"))).isEqualTo("/tmp/100%%/logs/wakemeapp.%g.log");
    }
}
