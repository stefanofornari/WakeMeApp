package ste.wakemeapp.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;
import ste.wakemeapp.settings.MemoryPreferences;
import ste.wakemeapp.settings.Settings;
import ste.wakemeapp.settings.StoredCalendar;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.BDDAssertions.then;

class NotificationCommandTest {

    private static final String LINK = "https://outlook.office365.com/owa/calendar/abc123/secret456/calendar.ics";

    private final StoredCalendar storedCalendar = emptyStore();
    private final CommandLine commandLine = new CommandLine(commandWith(storedCalendar));

    /** A stored calendar in a folder of its own, so that a test never reads the user's one. */
    private static StoredCalendar emptyStore() {
        try {
            return new StoredCalendar(Files.createTempDirectory("calendar").resolve("calendar.txt"));
        } catch (java.io.IOException x) {
            throw new IllegalStateException(x);
        }
    }

    private static NotificationCommand commandWith(StoredCalendar stored) {
        NotificationCommand command = new NotificationCommand();
        command.setStoredCalendar(stored);
        return command;
    }

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
    void should_run_in_the_tray_when_started_without_arguments() {
        int exitCode = commandLine.execute();

        then(exitCode).isZero();
        then(commandLine.<NotificationCommand>getCommand().isTrayOnly()).isTrue();
    }

    @Test
    void should_run_in_the_tray_with_only_the_debug_option() {
        commandLine.execute("--debug");

        then(commandLine.<NotificationCommand>getCommand().isTrayOnly()).isTrue();
    }

    @Test
    void should_not_run_in_the_tray_only_when_a_meeting_or_a_calendar_is_given() {
        commandLine.execute("--title", "Team Standup", "--start-date", "2026-11-03T10:00:00");
        then(commandLine.<NotificationCommand>getCommand().isTrayOnly()).isFalse();

        CommandLine other = new CommandLine(new NotificationCommand());
        other.execute("--calendar", "meetings.csv");
        then(other.<NotificationCommand>getCommand().isTrayOnly()).isFalse();
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
        java.io.StringWriter err = new java.io.StringWriter();
        commandLine.setErr(new java.io.PrintWriter(err, true));

        int exitCode = executeWithCalendarFile("--poll-interval", "10", "--window", "12");

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

    @Test
    void should_not_log_debug_detail_by_default() {
        executeWithCalendarFile();

        then(command().isDebug()).isFalse();
    }

    @Test
    void should_log_debug_detail_with_the_debug_option() {
        executeWithCalendarFile("--debug");

        then(command().isDebug()).isTrue();
    }

    @Test
    void should_list_the_debug_option_in_the_help() {
        then(commandLine.getUsageMessage()).contains("--debug");
    }

    @Test
    void should_keep_the_client_id_and_the_tenant_given_on_the_command_line() {
        int exitCode = commandLine.execute("--calendar", "meetings.csv",
            "--client-id", "11111111-2222-3333-4444-555555555555", "--tenant", "organizations");

        then(exitCode).isZero();
        NotificationCommand cmd = commandLine.getCommand();
        then(cmd.getClientId()).isEqualTo("11111111-2222-3333-4444-555555555555");
        then(cmd.getTenant()).isEqualTo("organizations");
    }

    @Test
    void should_leave_the_client_id_and_the_tenant_null_when_not_given() {
        commandLine.execute("--calendar", "meetings.csv");

        NotificationCommand cmd = commandLine.getCommand();
        then(cmd.getClientId()).isNull();
        then(cmd.getTenant()).isNull();
    }

    @Test
    void should_print_the_configuration_and_nothing_else_with_print_config() {
        MemoryPreferences node = new MemoryPreferences();
        node.put("graph.tenant", "99999999-8888-7777-6666-555555555555");
        NotificationCommand command = commandWith(emptyStore());
        command.setSettings(new Settings(node));
        CommandLine line = new CommandLine(command);
        java.io.StringWriter out = new java.io.StringWriter();
        line.setOut(new java.io.PrintWriter(out, true));

        int exitCode = line.execute("--print-config", "--client-id", "11111111-2222-3333-4444-555555555555");

        then(exitCode).isZero();
        then(command.isPrintConfig()).isTrue();
        then(out.toString()).contains("graph.clientId").contains("(command line)")
            .contains("99999999-8888-7777-6666-555555555555").contains("(stored)");
    }

    @Test
    void should_neither_print_nor_store_the_address_of_a_feed() throws Exception {
        MemoryPreferences node = new MemoryPreferences();
        NotificationCommand command = commandWith(emptyStore());
        command.setSettings(new Settings(node));
        CommandLine line = new CommandLine(command);
        java.io.StringWriter out = new java.io.StringWriter();
        line.setOut(new java.io.PrintWriter(out, true));

        int exitCode = line.execute("--print-config", "--calendar",
            "https://outlook.office365.com/owa/calendar/abc123/secret456/calendar.ics");

        then(exitCode).isZero();
        then(out.toString()).doesNotContain("abc123").doesNotContain("secret456")
            .doesNotContain("calendar.ics");
        then(node.keys()).isEmpty();
    }

    @Test
    void should_print_the_calendar_of_a_feed_as_its_host_only() throws Exception {
        NotificationCommand command = commandWith(emptyStore());
        command.setSettings(new Settings(new MemoryPreferences()));
        CommandLine line = new CommandLine(command);
        java.io.StringWriter out = new java.io.StringWriter();
        line.setOut(new java.io.PrintWriter(out, true));

        line.execute("--print-config", "--calendar",
            "webcal://outlook.office365.com/owa/calendar/abc123/secret456/calendar.ics");

        then(out.toString()).contains("calendar").contains("https://outlook.office365.com/\u2026")
            .contains("(command line)");
    }

    @Test
    void should_print_a_calendar_file_as_given_and_a_missing_calendar_as_not_set() throws Exception {
        NotificationCommand command = commandWith(emptyStore());
        command.setSettings(new Settings(new MemoryPreferences()));
        CommandLine line = new CommandLine(command);
        java.io.StringWriter out = new java.io.StringWriter();
        line.setOut(new java.io.PrintWriter(out, true));
        line.execute("--print-config", "--calendar", "meetings.csv");
        then(out.toString()).contains("meetings.csv");

        NotificationCommand none = commandWith(emptyStore());
        none.setSettings(new Settings(new MemoryPreferences()));
        CommandLine line2 = new CommandLine(none);
        java.io.StringWriter out2 = new java.io.StringWriter();
        line2.setOut(new java.io.PrintWriter(out2, true));
        line2.execute("--print-config");
        then(out2.toString()).contains("(not set)");
    }

    @Test
    void should_not_print_the_configuration_by_default() {
        commandLine.execute("--calendar", "meetings.csv");

        then(commandLine.<NotificationCommand>getCommand().isPrintConfig()).isFalse();
    }

    @Test
    void should_take_a_graph_address_as_the_calendar() {
        int exitCode = commandLine.execute("--calendar", "https://graph.microsoft.com/v1.0/me/calendars/AAMk");

        then(exitCode).isZero();
        NotificationCommand cmd = commandLine.getCommand();
        then(cmd.getCalendarAddress()).isEqualTo(
            java.net.URI.create("https://graph.microsoft.com/v1.0/me/calendars/AAMk/calendarView"));
        then(cmd.getCalendarFile()).isNull();
    }

    @Test
    void should_take_an_ics_address_as_the_calendar_feed() {
        int exitCode = commandLine.execute("--calendar", "https://outlook.office365.com/owa/calendar/a/b/calendar.ics");

        then(exitCode).isZero();
        NotificationCommand cmd = commandLine.getCommand();
        then(cmd.getCalendarFeed()).isEqualTo(
            java.net.URI.create("https://outlook.office365.com/owa/calendar/a/b/calendar.ics"));
        then(cmd.getCalendarAddress()).isNull();
        then(cmd.getCalendarFile()).isNull();
    }

    @Test
    void should_read_webcal_as_https() {
        commandLine.execute("--calendar", "webcal://outlook.office365.com/c.ics");

        then(commandLine.<NotificationCommand>getCommand().getCalendarFeed()).isEqualTo(
            java.net.URI.create("https://outlook.office365.com/c.ics"));
    }

    @Test
    void should_have_no_feed_for_graph_or_a_file() {
        commandLine.execute("--calendar", "https://graph.microsoft.com/v1.0/me/calendarView");
        then(commandLine.<NotificationCommand>getCommand().getCalendarFeed()).isNull();

        commandLine.execute("--calendar", "meetings.csv");
        then(commandLine.<NotificationCommand>getCommand().getCalendarFeed()).isNull();
    }

    @Test
    void should_take_a_path_as_a_calendar_file_and_no_address() {
        commandLine.execute("--calendar", "meetings.csv");

        NotificationCommand cmd = commandLine.getCommand();
        then(cmd.getCalendarFile()).isEqualTo(Path.of("meetings.csv"));
        then(cmd.getCalendarAddress()).isNull();
    }

    @Test
    void should_use_the_stored_calendar_when_none_is_given() throws Exception {
        storedCalendar.save(LINK);

        int exitCode = commandLine.execute();

        then(exitCode).isZero();
        NotificationCommand cmd = commandLine.getCommand();
        then(cmd.getCalendarFeed()).isEqualTo(java.net.URI.create(LINK));
        then(cmd.isTrayOnly()).isFalse();
    }

    @Test
    void should_use_a_stored_csv_file_or_graph_address_like_the_command_line_does() throws Exception {
        storedCalendar.save("meetings.csv");
        commandLine.execute("--debug");
        then(commandLine.<NotificationCommand>getCommand().getCalendarFile()).isEqualTo(Path.of("meetings.csv"));

        StoredCalendar graph = emptyStore();
        graph.save("https://graph.microsoft.com/v1.0/me/calendars/AAMk");
        CommandLine other = new CommandLine(commandWith(graph));
        other.execute();
        then(other.<NotificationCommand>getCommand().getCalendarAddress()).isEqualTo(
            java.net.URI.create("https://graph.microsoft.com/v1.0/me/calendars/AAMk/calendarView"));
    }

    @Test
    void should_prefer_the_command_line_to_the_stored_calendar_and_not_save_it() throws Exception {
        storedCalendar.save(LINK);

        commandLine.execute("--calendar", "other.csv");

        NotificationCommand cmd = commandLine.getCommand();
        then(cmd.getCalendarFile()).isEqualTo(Path.of("other.csv"));
        then(cmd.getCalendarFeed()).isNull();
        then(storedCalendar.value()).hasValue(LINK);
    }

    @Test
    void should_never_save_the_calendar_given_on_the_command_line() {
        commandLine.execute("--calendar", LINK);

        then(storedCalendar.value()).isEmpty();
    }

    @Test
    void should_not_use_the_stored_calendar_for_a_single_meeting() throws Exception {
        storedCalendar.save(LINK);

        int exitCode = commandLine.execute("--title", "Team Standup", "--start-date", "2026-11-03T10:00:00");

        then(exitCode).isZero();
        NotificationCommand cmd = commandLine.getCommand();
        then(cmd.getCalendarFeed()).isNull();
        then(cmd.getTitle()).isEqualTo("Team Standup");
    }

    @Test
    void should_ignore_a_stored_calendar_that_is_not_valid() throws Exception {
        storedCalendar.save("http://outlook.office365.com/owa/calendar/abc123/secret456/calendar.ics");

        int exitCode = commandLine.execute();

        then(exitCode).isZero();
        NotificationCommand cmd = commandLine.getCommand();
        then(cmd.getCalendarFeed()).isNull();
        then(cmd.isTrayOnly()).isTrue();
    }

    @Test
    void should_print_the_stored_calendar_masked_with_its_source() throws Exception {
        storedCalendar.save(LINK);
        NotificationCommand command = commandWith(storedCalendar);
        command.setSettings(new Settings(new MemoryPreferences()));
        CommandLine line = new CommandLine(command);
        java.io.StringWriter out = new java.io.StringWriter();
        line.setOut(new java.io.PrintWriter(out, true));

        line.execute("--print-config");

        then(out.toString()).contains("https://outlook.office365.com/\u2026").contains("(stored)")
            .doesNotContain("abc123").doesNotContain("secret456").doesNotContain("calendar.ics");
    }

    @Test
    void should_print_a_command_line_calendar_even_when_one_is_stored() throws Exception {
        storedCalendar.save(LINK);
        NotificationCommand command = commandWith(storedCalendar);
        command.setSettings(new Settings(new MemoryPreferences()));
        CommandLine line = new CommandLine(command);
        java.io.StringWriter out = new java.io.StringWriter();
        line.setOut(new java.io.PrintWriter(out, true));

        line.execute("--print-config", "--calendar", "other.csv");

        then(out.toString()).contains("other.csv").contains("(command line)").doesNotContain("outlook");
    }

    @Test
    void should_open_the_settings_dialog_only_when_asked() {
        commandLine.execute();
        then(commandLine.<NotificationCommand>getCommand().isSettings()).isFalse();

        CommandLine asked = new CommandLine(commandWith(emptyStore()));
        int exitCode = asked.execute("--settings");

        then(exitCode).isZero();
        then(asked.<NotificationCommand>getCommand().isSettings()).isTrue();
    }

    @Test
    void should_stay_in_the_tray_with_only_the_settings_option() {
        commandLine.execute("--settings");

        then(commandLine.<NotificationCommand>getCommand().isTrayOnly()).isTrue();
    }
}
