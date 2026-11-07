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
    void should_print_all_options_and_succeed_when_help_is_requested_without_other_options() {
        java.io.StringWriter out = new java.io.StringWriter();
        picocli.CommandLine commandLine = new picocli.CommandLine(new NotificationCommand());
        commandLine.setOut(new java.io.PrintWriter(out));

        int exitCode = commandLine.execute("--help");

        then(exitCode).isZero();
        then(commandLine.isUsageHelpRequested()).isTrue();
        then(out.toString()).contains(
            "--title", "--start-date", "--end-date", "--organizer", "--url", "--description"
        );
    }

    private static final java.time.LocalDateTime NOW = java.time.LocalDateTime.of(2026, 11, 4, 9, 51);

    @Test
    void should_say_when_the_notification_will_fire() {
        String message = CLILauncher.scheduleMessage("Team Standup", NOW.plusMinutes(4), NOW);

        then(message).isEqualTo("Scheduled \"Team Standup\" for 09:55 (in 4 min)");
    }

    @Test
    void should_round_a_partial_minute_up() {
        String message = CLILauncher.scheduleMessage("Team Standup", NOW.plusMinutes(4).plusSeconds(10), NOW);

        then(message).isEqualTo("Scheduled \"Team Standup\" for 09:55 (in 5 min)");
    }

    @Test
    void should_say_the_notification_is_shown_now_when_its_time_has_passed() {
        String message = CLILauncher.scheduleMessage("Team Standup", NOW.minusMinutes(3), NOW);

        then(message).isEqualTo("Showing \"Team Standup\" now");
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

    /**
     * The launcher never shows its primary stage, so once the overlay closes no window is left and
     * JavaFX would shut the toolkit down (implicit exit): a snoozed notification, due later,
     * could then no longer be shown.
     */
    @Test
    void should_keep_the_toolkit_running_after_the_last_window_closes() {
        javafx.application.Platform.setImplicitExit(true); // the JavaFX default

        new CLILauncher().init();

        then(javafx.application.Platform.isImplicitExit()).isFalse();
    }
}
