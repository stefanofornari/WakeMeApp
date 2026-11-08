package ste.wakemeapp.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.BDDAssertions.then;

class NotificationCommandTest {

    private final CommandLine commandLine = new CommandLine(new NotificationCommand());

    @Test
    void should_parse_required_args() {
        String[] args = {"--title", "Team Standup", "--start-date", "2026-11-03T10:00:00"};

        int exitCode = commandLine.execute(args);

        then(exitCode).isEqualTo(0);
        NotificationCommand cmd = commandLine.getCommand();
        then(cmd.getTitle()).isEqualTo("Team Standup");
        then(cmd.getStartDate()).isEqualTo(java.time.LocalDateTime.parse("2026-11-03T10:00:00"));
    }

    @Test
    void should_not_show_immediately_by_default() {
        commandLine.execute("--title", "Team Standup", "--start-date", "2026-11-03T10:00:00");

        then(commandLine.<NotificationCommand>getCommand().isNow()).isFalse();
    }

    @Test
    void should_show_immediately_with_now_flag() {
        int exitCode = commandLine.execute("--title", "Team Standup", "--start-date", "2026-11-03T10:00:00", "--now");

        then(exitCode).isZero();
        then(commandLine.<NotificationCommand>getCommand().isNow()).isTrue();
    }

    @Test
    void should_accept_short_form_of_now_flag() {
        commandLine.execute("-t", "Team Standup", "-s", "2026-11-03T10:00:00", "-n");

        then(commandLine.<NotificationCommand>getCommand().isNow()).isTrue();
    }

    @Test
    void should_parse_all_optional_args() {
        String[] args = {
            "--title", "Team Standup",
            "--start-date", "2026-11-03T10:00:00",
            "--end-date", "2026-11-03T11:00:00",
            "--organizer", "John Doe",
            "--url", "https://teams.microsoft.com/l/meetup-join/...",
            "--description", "Weekly team sync"
        };

        int exitCode = commandLine.execute(args);

        then(exitCode).isEqualTo(0);
        NotificationCommand cmd = commandLine.getCommand();
        then(cmd.getTitle()).isEqualTo("Team Standup");
        then(cmd.getStartDate()).isEqualTo(java.time.LocalDateTime.parse("2026-11-03T10:00:00"));
        then(cmd.getEndDate()).isEqualTo(java.time.LocalDateTime.parse("2026-11-03T11:00:00"));
        then(cmd.getOrganizer()).isEqualTo("John Doe");
        then(cmd.getUrl()).isEqualTo("https://teams.microsoft.com/l/meetup-join/...");
        then(cmd.getDescription()).isEqualTo("Weekly team sync");
    }

    @Test
    void should_fail_on_missing_title() {
        String[] args = {"--start-date", "2026-11-03T10:00:00"};

        int exitCode = commandLine.execute(args);

        then(exitCode).isNotEqualTo(0);
    }

    @Test
    void should_fail_on_missing_start_date() {
        String[] args = {"--title", "Team Standup"};

        int exitCode = commandLine.execute(args);

        then(exitCode).isNotEqualTo(0);
    }

    @Test
    void should_fail_on_invalid_date_format() {
        String[] args = {"--title", "Team Standup", "--start-date", "invalid-date"};

        int exitCode = commandLine.execute(args);

        then(exitCode).isNotEqualTo(0);
    }

    @Test
    void should_show_help() {
        String[] args = {"--help"};

        int exitCode = commandLine.execute(args);

        then(exitCode).isEqualTo(0);
    }

    @Test
    void should_parse_short_options() {
        String[] args = {"-t", "Team Standup", "-s", "2026-11-03T10:00:00"};

        int exitCode = commandLine.execute(args);

        then(exitCode).isEqualTo(0);
        NotificationCommand cmd = commandLine.getCommand();
        then(cmd.getTitle()).isEqualTo("Team Standup");
        then(cmd.getStartDate()).isEqualTo(java.time.LocalDateTime.parse("2026-11-03T10:00:00"));
    }

    @TempDir
    Path dir;

    @Test
    void should_accept_an_calendar_file_without_title_and_start_date() throws Exception {
        Path file = Files.writeString(dir.resolve("meetings.csv"), "id,title,start\n");

        int exitCode = commandLine.execute("--calendar", file.toString());

        then(exitCode).isEqualTo(0);
        then(((NotificationCommand) commandLine.getCommand()).getCalendarFile()).isEqualTo(file);
    }

    @Test
    void should_accept_an_calendar_file_that_does_not_exist_yet() {
        int exitCode = commandLine.execute("--calendar", dir.resolve("missing.csv").toString());

        then(exitCode).isEqualTo(0);
    }

    @Test
    void should_reject_an_calendar_file_that_is_a_directory() {
        int exitCode = commandLine.execute("--calendar", dir.toString());

        then(exitCode).isNotEqualTo(0);
    }

    @Test
    void should_have_no_calendar_file_by_default() {
        commandLine.execute("--title", "Team Standup", "--start-date", "2026-11-03T10:00:00");

        then(((NotificationCommand) commandLine.getCommand()).getCalendarFile()).isNull();
    }

    private int executeWithCalendarFile(String... options) {
        String[] args = new String[options.length + 2];
        args[0] = "--calendar";
        args[1] = dir.resolve("meetings.csv").toString();
        System.arraycopy(options, 0, args, 2, options.length);
        return commandLine.execute(args);
    }

    private NotificationCommand command() {
        return commandLine.getCommand();
    }

    @Test
    void should_poll_every_5_minutes_for_the_next_15_by_default() {
        executeWithCalendarFile();

        then(command().getPollInterval()).isEqualTo(java.time.Duration.ofMinutes(5));
        then(command().getWindow()).isEqualTo(java.time.Duration.ofMinutes(15));
    }

    @Test
    void should_use_the_given_poll_interval_and_window() {
        int exitCode = executeWithCalendarFile("--poll-interval", "2", "--window", "12");

        then(exitCode).isEqualTo(0);
        then(command().getPollInterval()).isEqualTo(java.time.Duration.ofMinutes(2));
        then(command().getWindow()).isEqualTo(java.time.Duration.ofMinutes(12));
    }

    @Test
    void should_reject_a_window_shorter_than_poll_interval_plus_lead_time() {
        java.io.ByteArrayOutputStream err = new java.io.ByteArrayOutputStream();
        java.io.PrintStream original = System.err;
        System.setErr(new java.io.PrintStream(err));
        int exitCode;
        try {
            exitCode = executeWithCalendarFile("--poll-interval", "10", "--window", "12");
        } finally {
            System.setErr(original);
        }

        then(exitCode).isNotEqualTo(0);
        then(err.toString()).contains("at least 15 minutes");
    }

    @Test
    void should_reject_a_window_too_short_for_the_default_poll_interval() {
        then(executeWithCalendarFile("--window", "9")).isNotEqualTo(0);
    }

    @Test
    void should_reject_a_poll_interval_that_leaves_no_room_in_the_default_window() {
        then(executeWithCalendarFile("--poll-interval", "11")).isNotEqualTo(0);
    }

    @Test
    void should_reject_non_positive_values() {
        then(executeWithCalendarFile("--poll-interval", "0")).isNotEqualTo(0);
        then(executeWithCalendarFile("--window", "-3")).isNotEqualTo(0);
    }
}
