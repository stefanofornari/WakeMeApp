package ste.wakemeapp.log;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;

class AppFileHandlerTest {

    private static final Logger LOG = Logger.getLogger("ste.wakemeapp.test");
    private static final String HANDLER = AppFileHandler.class.getName();

    @TempDir
    Path dir;

    @AfterEach
    void restore_default_logging() throws IOException {
        System.clearProperty("app.data.dir");
        System.clearProperty("test.log.dir");
        LogManager.getLogManager().readConfiguration(); // closes our handlers, back to the configuration given to the JVM
    }

    /** What logging.properties does, with the folder under test; {@code more} are further property lines. */
    private void configure(Path folder, String... more) throws IOException {
        System.setProperty("test.log.dir", folder.toString());
        String config = "handlers = " + HANDLER + "\n"
            + ".level = INFO\n"
            + HANDLER + ".pattern = ${test.log.dir}/wakemeapp.%g.log\n"
            + String.join("\n", more) + "\n";
        LogManager.getLogManager().readConfiguration(new ByteArrayInputStream(config.getBytes()));
    }

    private String logFile(Path folder) throws IOException {
        return Files.readString(folder.resolve("wakemeapp.0.log"));
    }

    private List<Path> logFiles(Path folder) throws IOException {
        try (Stream<Path> files = Files.list(folder)) {
            return files.filter(p -> p.getFileName().toString().matches("wakemeapp\\.\\d+\\.log")).toList();
        }
    }

    @Test
    void should_write_one_line_per_message_to_the_file_given_by_the_pattern() throws IOException {
        configure(dir);

        LOG.info("Polling meetings.csv every 5 min");

        then(logFile(dir)).matches("(?s)\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2} INFO  Polling meetings.csv every 5 min\n");
    }

    @Test
    void should_create_the_log_folder_when_missing() throws IOException {
        Path logs = dir.resolve("a").resolve("logs");
        configure(logs);

        LOG.info("first message");

        then(logFile(logs)).contains("first message");
    }

    @Test
    void should_follow_a_logged_exception_with_its_stack_trace() throws IOException {
        configure(dir);

        LOG.log(Level.SEVERE, "Cannot show the notification", new IllegalStateException("boom"));

        then(logFile(dir)).contains(" ERROR Cannot show the notification\n").contains("IllegalStateException: boom").contains("\tat ");
    }

    @Test
    void should_take_the_settings_of_its_own_class_from_the_configuration() throws IOException {
        configure(dir, HANDLER + ".formatter = java.util.logging.SimpleFormatter", HANDLER + ".level = WARNING");

        LOG.info("an info");
        LOG.warning("a warning");

        then(logFile(dir)).contains("a warning").doesNotContain("an info").contains("WARNING"); // SimpleFormatter
    }

    @Test
    void should_cap_the_size_of_the_log_as_configured() throws IOException {
        configure(dir, HANDLER + ".limit = 100000", HANDLER + ".count = 2");
        String line = "x".repeat(1000);

        for (int i = 0; i < 1000; i++) { // 1 MB
            LOG.info(line);
        }

        then(logFiles(dir)).hasSize(2);
        for (Path log : logFiles(dir)) {
            then(Files.size(log)).isLessThanOrEqualTo(110_000L);
        }
    }

    @Test
    void should_keep_the_cap_and_the_line_format_when_the_configuration_does_not_say() throws IOException {
        configure(dir); // no limit, count, formatter, encoding
        String line = "x".repeat(3000);

        for (int i = 0; i < 1500; i++) { // 4.5 MB
            LOG.info(line);
        }

        then(logFiles(dir)).hasSizeLessThanOrEqualTo(3);
        for (Path log : logFiles(dir)) {
            then(Files.size(log)).isLessThanOrEqualTo(1_100_000L);
        }
        then(logFile(dir)).startsWith("20").contains(" INFO  xxx");
    }

    @Test
    void should_keep_writing_to_the_console_when_the_log_folder_cannot_be_used() throws IOException {
        Path file = Files.writeString(dir.resolve("blocked"), "not a folder");
        PrintStream original = System.err;
        ByteArrayOutputStream console = new ByteArrayOutputStream();
        System.setErr(new PrintStream(console, true));
        try {
            System.setProperty("test.log.dir", file.resolve("logs").toString());
            LogManager.getLogManager().readConfiguration(new ByteArrayInputStream((
                "handlers = java.util.logging.ConsoleHandler, " + HANDLER + "\n"
                + "java.util.logging.ConsoleHandler.formatter = " + LineFormatter.class.getName() + "\n"
                + HANDLER + ".pattern = ${test.log.dir}/wakemeapp.%g.log\n").getBytes()));
            LOG.warning("still logging");
        } finally {
            System.setErr(original);
        }

        then(console.toString()).contains("AppFileHandler").contains("still logging");
    }

    @Test
    void should_expand_the_application_data_directory() {
        System.setProperty("app.data.dir", "/data/WakeMeApp");

        then(AppFileHandler.expand("${app.data.dir}/logs/wakemeapp.%g.log")).isEqualTo("/data/WakeMeApp/logs/wakemeapp.%g.log");
    }

    @Test
    void should_default_the_application_data_directory_to_the_one_of_the_platform() {
        then(AppFileHandler.expand("${app.data.dir}/logs/wakemeapp.%g.log"))
            .endsWith("/logs/wakemeapp.%g.log")
            .containsIgnoringCase("WakeMeApp")
            .doesNotContain("${");
    }

    @Test
    void should_expand_any_system_property() {
        System.setProperty("test.log.dir", "/somewhere");

        then(AppFileHandler.expand("${test.log.dir}/a.%g.log")).isEqualTo("/somewhere/a.%g.log");
    }

    @Test
    void should_use_forward_slashes_and_escape_the_percent_signs_of_the_values() {
        System.setProperty("test.log.dir", "C:\\Users\\100%\\logs");

        then(AppFileHandler.expand("${test.log.dir}/a.%g.log")).isEqualTo("C:/Users/100%%/logs/a.%g.log");
    }

    @Test
    void should_reject_an_unknown_placeholder() {
        thenThrownBy(() -> AppFileHandler.expand("${no.such.property}/a.%g.log"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("no.such.property");
    }
}
