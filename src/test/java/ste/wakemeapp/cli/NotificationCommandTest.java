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
    void should_accept_an_events_file_without_title_and_start_date() throws Exception {
        Path file = Files.writeString(dir.resolve("meetings.csv"), "id,title,start\n");

        int exitCode = commandLine.execute("--events-file", file.toString());

        then(exitCode).isEqualTo(0);
        then(((NotificationCommand) commandLine.getCommand()).getEventsFile()).isEqualTo(file);
    }

    @Test
    void should_reject_an_events_file_that_cannot_be_read() {
        int exitCode = commandLine.execute("--events-file", dir.resolve("missing.csv").toString());

        then(exitCode).isNotEqualTo(0);
    }

    @Test
    void should_have_no_events_file_by_default() {
        commandLine.execute("--title", "Team Standup", "--start-date", "2026-11-03T10:00:00");

        then(((NotificationCommand) commandLine.getCommand()).getEventsFile()).isNull();
    }
}
