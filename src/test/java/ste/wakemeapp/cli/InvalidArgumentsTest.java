package ste.wakemeapp.cli;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;
import static org.assertj.core.api.BDDAssertions.then;

/** What the user sees when the command line is wrong: the error and the whole help, no exception. */
class InvalidArgumentsTest {

    @TempDir
    Path dir;

    private final StringWriter err = new StringWriter();
    private final StringWriter out = new StringWriter();
    private final CommandLine commandLine = NotificationCommand.newCommandLine();

    private int run(String... args) {
        commandLine.setErr(new PrintWriter(err, true));
        commandLine.setOut(new PrintWriter(out, true));
        return commandLine.execute(args);
    }

    /** A line only the full help has: each option with its description, not just the synopsis. */
    private void thenFullHelpIsShown() {
        then(err.toString()).contains("Usage: notify")
            .contains("--poll-interval")
            .contains("Meeting title")
            .contains("How far ahead each poll looks");
    }

    @Test
    void should_show_the_error_and_the_full_help_for_an_unknown_option() {
        int exitCode = run("--colour", "red");

        then(exitCode).isNotEqualTo(0);
        then(err.toString()).contains("Unknown option").contains("'--colour'");
        thenFullHelpIsShown();
    }

    @Test
    void should_show_the_full_help_when_a_required_argument_is_missing() {
        int exitCode = run("--title", "Standup");

        then(exitCode).isNotEqualTo(0);
        then(err.toString()).contains("--start-date");
        thenFullHelpIsShown();
    }

    @Test
    void should_show_the_full_help_for_an_invalid_value() {
        int exitCode = run("--calendar", dir.resolve("meetings.csv").toString(), "--poll-interval", "abc");

        then(exitCode).isNotEqualTo(0);
        then(err.toString()).contains("--poll-interval").contains("abc");
        thenFullHelpIsShown();
    }

    @Test
    void should_show_the_full_help_for_an_unparsable_date() {
        int exitCode = run("--title", "Standup", "--start-date", "tomorrow");

        then(exitCode).isNotEqualTo(0);
        then(err.toString()).contains("Invalid --start-date");
        thenFullHelpIsShown();
    }

    @Test
    void should_show_the_full_help_when_the_window_is_too_short() {
        int exitCode = run("--calendar", dir.resolve("meetings.csv").toString(),
            "--poll-interval", "10", "--window", "12");

        then(exitCode).isNotEqualTo(0);
        then(err.toString()).contains("The window must be at least 15 minutes");
        thenFullHelpIsShown();
    }

    @Test
    void should_use_the_same_exit_code_for_every_kind_of_invalid_argument() {
        int unknown = run("--colour", "red");
        int missing = run("--title", "Standup");
        int rule = run("--calendar", dir.resolve("m.csv").toString(), "--window", "1");

        then(missing).isEqualTo(unknown);
        then(rule).isEqualTo(unknown);
    }

    @Test
    void should_not_treat_a_help_request_as_an_error() {
        int exitCode = run("--help");

        then(exitCode).isEqualTo(0);
        then(err.toString()).isEmpty();
        then(out.toString()).contains("Usage: notify").contains("Meeting title");
    }

    @Test
    void should_stop_and_name_the_option_for_an_invalid_client_id() {
        int exitCode = run("--calendar", dir.resolve("meetings.csv").toString(), "--client-id", "not-a-uuid");

        then(exitCode).isNotEqualTo(0);
        then(err.toString()).contains("--client-id must be a UUID");
        thenFullHelpIsShown();
    }

    @Test
    void should_stop_and_name_the_option_for_an_invalid_tenant() {
        int exitCode = run("--calendar", dir.resolve("meetings.csv").toString(), "--tenant", "common");

        then(exitCode).isNotEqualTo(0);
        then(err.toString()).contains("--tenant must be a UUID or \"organizations\"");
    }

    @Test
    void should_refuse_a_calendar_address_on_another_host() {
        int exitCode = run("--calendar", "https://example.com/v1.0/me/calendarView");

        then(exitCode).isNotEqualTo(0);
        then(err.toString()).contains("--calendar address must be on graph.microsoft.com over https");
        thenFullHelpIsShown();
    }
}
