package ste.wakemeapp.cli;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.BDDAssertions.then;

class CLILauncherTest {

    @Test
    void should_strip_quotes_around_option_values() {
        String[] args = CLILauncher.normalizeArgs(List.of(
            "--title=\"Meeting Title\"",
            "--start-date=\"2026-11-04T12:00\"",
            "--url=\"https://teams.url\""
        ));

        then(args).containsExactly(
            "--title=Meeting Title",
            "--start-date=2026-11-04T12:00",
            "--url=https://teams.url"
        );
    }

    @Test
    void should_strip_quotes_wrapping_a_whole_argument() {
        String[] args = CLILauncher.normalizeArgs(List.of("--title", "\"Meeting Title\""));

        then(args).containsExactly("--title", "Meeting Title");
    }

    @Test
    void should_leave_unquoted_arguments_untouched() {
        String[] args = CLILauncher.normalizeArgs(List.of("-t", "Standup", "--end-date=2026-11-04T13:00"));

        then(args).containsExactly("-t", "Standup", "--end-date=2026-11-04T13:00");
    }

    @Test
    void should_produce_arguments_the_notification_command_accepts() {
        String[] args = CLILauncher.normalizeArgs(List.of(
            "--title=\"Meeting Title\"", "--start-date=\"2026-11-04T12:00\"", "--url=\"https://teams.url\""
        ));
        NotificationCommand command = new NotificationCommand();

        int exitCode = new picocli.CommandLine(command).execute(args);

        then(exitCode).isZero();
        then(command.getTitle()).isEqualTo("Meeting Title");
        then(command.getStartDate()).isEqualTo(java.time.LocalDateTime.of(2026, 11, 4, 12, 0));
        then(command.getUrl()).isEqualTo("https://teams.url");
    }
}
