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

    private static final String LINK = "https://outlook.office365.com/owa/calendar/abc123/secret456/calendar.ics";

    @Test
    void should_hide_the_path_of_a_feed_address_in_the_arguments_that_are_logged() {
        List<String> shown = CLILauncher.redact(List.of("--calendar", LINK, "--debug"));

        then(shown).containsExactly("--calendar", "https://outlook.office365.com/\u2026", "--debug");
    }

    @Test
    void should_hide_the_path_of_a_feed_address_given_with_an_equals_sign_or_as_webcal() {
        then(CLILauncher.redact(List.of("--calendar=" + LINK)))
            .containsExactly("--calendar=https://outlook.office365.com/\u2026");
        then(CLILauncher.redact(List.of("--calendar", "webcal://outlook.office365.com/x/y/c.ics")))
            .containsExactly("--calendar", "https://outlook.office365.com/\u2026");
        then(CLILauncher.redact(List.of("--calendar=\"" + LINK + "\"")))
            .containsExactly("--calendar=https://outlook.office365.com/\u2026");
    }

    @Test
    void should_hide_a_feed_address_that_is_not_https_without_showing_it() {
        then(CLILauncher.redact(List.of("--calendar", "http://outlook.office365.com/x/secret456/c.ics")))
            .containsExactly("--calendar", "http://outlook.office365.com/\u2026");
    }

    @Test
    void should_leave_alone_the_arguments_that_are_not_a_feed_address() {
        List<String> args = List.of("--calendar", "meetings.csv", "--client-id", "abc", "-t", "Standup",
            "--calendar=https://graph.microsoft.com/v1.0/me/calendarView");

        then(CLILauncher.redact(args)).containsExactlyElementsOf(args);
    }

    @Test
    void should_hide_the_link_when_the_option_value_is_in_the_next_argument_after_other_options() {
        then(CLILauncher.redact(List.of("--debug", "--calendar", LINK, "--poll-interval", "5")))
            .containsExactly("--debug", "--calendar", "https://outlook.office365.com/\u2026", "--poll-interval", "5");
    }
}
