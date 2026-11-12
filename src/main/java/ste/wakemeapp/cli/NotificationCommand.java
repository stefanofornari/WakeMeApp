package ste.wakemeapp.cli;

import picocli.CommandLine;
import ste.wakemeapp.calendar.CalendarAddress;
import ste.wakemeapp.calendar.CalendarPoller;
import ste.wakemeapp.calendar.IcsAddress;
import ste.wakemeapp.scheduler.NotificationScheduler;
import ste.wakemeapp.settings.ConfigReport;
import ste.wakemeapp.settings.ConnectionSettings;
import ste.wakemeapp.settings.Settings;
import ste.wakemeapp.settings.StoredCalendar;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Spec;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Callable;
import java.util.logging.Logger;

@Command(name = "notify", description = "Trigger a meeting notification",
         mixinStandardHelpOptions = true, version = "1.0.0")
public class NotificationCommand implements Callable<Integer> {

    @Option(names = {"-t", "--title"}, description = "Meeting title (required unless --calendar is given)")
    private String title;

    @Option(names = {"-s", "--start-date"}, description = "Meeting start date/time (ISO-8601) (required unless --calendar is given)")
    private String startDateStr;

    @Option(names = {"-e", "--end-date"}, description = "Meeting end date/time (ISO-8601)")
    private String endDateStr;

    @Option(names = {"-o", "--organizer"}, description = "Meeting organizer")
    private String organizer;

    @Option(names = {"-u", "--url"}, description = "Join URL (Teams, Zoom, etc.)")
    private String url;

    @Option(names = {"-d", "--description"}, description = "Meeting description")
    private String description;

    @Option(names = {"-n", "--now"},
            description = "Show the notification immediately instead of scheduling it before the start date")
    private boolean now;

    @Option(names = "--calendar",
            paramLabel = "<file|address>",
            description = "CSV file of meetings, the address of an Outlook calendar on Microsoft Graph "
                + "(e.g. https://graph.microsoft.com/v1.0/me/calendarView), or the link of a published "
                + "calendar (ICS), to poll like a calendar, instead of a single meeting")
    private String calendar;

    @Option(names = "--poll-interval", paramLabel = "<minutes>",
            description = "How often the calendar file is polled (default: 5)")
    private int pollIntervalMinutes = (int) CalendarPoller.DEFAULT_POLL_INTERVAL.toMinutes();

    @Option(names = "--window", paramLabel = "<minutes>",
            description = "How far ahead each poll looks for meetings; at least poll interval + lead time (default: 15)")
    private int windowMinutes = (int) CalendarPoller.DEFAULT_WINDOW.toMinutes();

    @Option(names = "--client-id", paramLabel = "<uuid>",
            description = "Microsoft application (client) id for this run; overrules the stored one, is not saved")
    private String clientId;

    @Option(names = "--tenant", paramLabel = "<id>",
            description = "Microsoft tenant id or \"organizations\" for this run; overrules the stored one, is not saved")
    private String tenant;

    @Option(names = "--print-config",
            description = "Print every setting with its value and where it comes from, then exit")
    private boolean printConfig;

    @Option(names = "--settings",
            description = "Open the settings dialog at launch (it can always be opened from the tray menu)")
    private boolean settingsDialog;

    @Option(names = "--debug", description = "Log the detail needed to investigate a problem (default: INFO and above)")
    private boolean debug;

    @Spec
    private CommandSpec spec;

    private static final Logger LOG = Logger.getLogger(NotificationCommand.class.getName());

    private Settings settings;
    private StoredCalendar storedCalendar;
    private boolean storedRead;
    private String stored;

    private LocalDateTime startDate;
    private LocalDateTime endDate;

    @Override
    public Integer call() {
        try {
            validatePolling();
            ConnectionSettings.validate(clientId, tenant);
            if (printConfig) {
                PrintWriter out = spec.commandLine().getOut();
                ConfigReport.lines(clientId, tenant, settings()).forEach(out::println);
                out.println(calendarLine());
                out.flush();
                return 0;
            }
            if (calendar != null) {
                validateCalendar(calendar);
                return 0;
            }
            if (calendarValue() != null) {
                return 0;   // the stored calendar, already checked (and ignored if not valid)
            }
            if (isTrayOnly()) {
                return 0;
            }
            if (title == null || startDateStr == null) {
                throw new IllegalArgumentException("--title and --start-date are required (or use --calendar)");
            }
            validateAndParseDates();
            return 0;
        } catch (IllegalArgumentException e) {
            // handled like a parsing error: the message and the full help (see newCommandLine)
            throw new ParameterException(spec.commandLine(), e.getMessage());
        }
    }

    /**
     * A command line that answers invalid arguments with the error followed by the full help on
     * stderr, and one exit code, instead of picocli's short usage hint.
     */
    public static CommandLine newCommandLine() {
        CommandLine commandLine = new CommandLine(new NotificationCommand());
        commandLine.setParameterExceptionHandler((exception, args) -> {
            CommandLine failed = exception.getCommandLine();
            PrintWriter err = failed.getErr();
            err.println(exception.getMessage());
            failed.usage(err);
            err.flush();
            return failed.getCommandSpec().exitCodeOnInvalidInput();
        });
        return commandLine;
    }

    private void validatePolling() {
        if (pollIntervalMinutes <= 0) {
            throw new IllegalArgumentException("--poll-interval must be a positive number of minutes");
        }
        if (windowMinutes <= 0) {
            throw new IllegalArgumentException("--window must be a positive number of minutes");
        }
        CalendarPoller.requireValidWindow(getPollInterval(), getWindow(), NotificationScheduler.DEFAULT_LEAD_TIME);
    }

    private void validateAndParseDates() {
        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

        try {
            startDate = LocalDateTime.parse(startDateStr, formatter);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid --start-date format. Expected ISO-8601 (e.g., 2026-11-03T10:00:00)");
        }

        if (endDateStr != null && !endDateStr.isBlank()) {
            try {
                endDate = LocalDateTime.parse(endDateStr, formatter);
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid --end-date format. Expected ISO-8601 (e.g., 2026-11-03T11:00:00)");
            }
        }
    }

    public String getTitle() {
        return title;
    }

    public LocalDateTime getStartDate() {
        return startDate;
    }

    public LocalDateTime getEndDate() {
        return endDate;
    }

    public String getOrganizer() {
        return organizer;
    }

    public String getUrl() {
        return url;
    }

    public String getDescription() {
        return description;
    }

    public boolean isDebug() {
        return debug;
    }

    public Duration getPollInterval() {
        return Duration.ofMinutes(pollIntervalMinutes);
    }

    public Duration getWindow() {
        return Duration.ofMinutes(windowMinutes);
    }

    public Path getCalendarFile() {
        String value = calendarValue();
        return (value == null || IcsAddress.isFeed(value) || CalendarAddress.isAddress(value)) ? null : Path.of(value);
    }

    /** The value of --client-id, or null if not given (the stored one, if any, applies). */
    public String getClientId() {
        return clientId;
    }

    /** The value of --tenant, or null if not given (the stored one, if any, applies). */
    public String getTenant() {
        return tenant;
    }

    /** True with --settings: the settings dialog is opened at launch. */
    public boolean isSettings() {
        return settingsDialog;
    }

    /** True with --print-config: the settings were printed and the application must not start. */
    public boolean isPrintConfig() {
        return printConfig;
    }

    private Settings settings() {
        if (settings == null) {
            settings = Settings.user();
        }
        return settings;
    }

    private StoredCalendar storedCalendar() {
        if (storedCalendar == null) {
            storedCalendar = StoredCalendar.user();
        }
        return storedCalendar;
    }

    /** For tests: the stored calendar to read instead of the user's. */
    void setStoredCalendar(StoredCalendar storedCalendar) {
        this.storedCalendar = storedCalendar;
    }

    /** For tests: the settings to read instead of the user's. */
    void setSettings(Settings settings) {
        this.settings = settings;
    }

    /** The Graph calendar to poll when --calendar is an address, otherwise null. */
    public java.net.URI getCalendarAddress() {
        String value = calendarValue();
        return (value != null && !IcsAddress.isFeed(value) && CalendarAddress.isAddress(value)) ? CalendarAddress.parse(value) : null;
    }

    /** The published calendar (ICS feed) to poll when --calendar is a link to one, otherwise null. */
    public java.net.URI getCalendarFeed() {
        String value = calendarValue();
        return value != null && IcsAddress.isFeed(value) ? IcsAddress.parse(value) : null;
    }

    /**
     * True when no calendar is configured (neither {@code --calendar} nor a stored one) and no single
     * meeting was asked for (at most options like {@code --debug}): the application then lives in the tray.
     */
    public boolean isTrayOnly() {
        return noMeetingOptions() && calendarValue() == null;
    }

    private boolean noMeetingOptions() {
        return title == null && startDateStr == null && endDateStr == null
            && organizer == null && url == null && description == null && !now;
    }

    /**
     * The calendar to use: --calendar, else the one the user saved. A single meeting on the command
     * line is a request of its own and does not use the saved calendar. A saved value that is not
     * valid is ignored with a warning, since it cannot be fixed from the command line.
     */
    private String calendarValue() {
        if (calendar != null) {
            return calendar;
        }
        if (!noMeetingOptions()) {
            return null;
        }
        if (!storedRead) {
            storedRead = true;
            stored = storedCalendar().value().orElse(null);
            if (stored != null) {
                try {
                    validateCalendar(stored);
                } catch (IllegalArgumentException x) {
                    LOG.warning("The saved calendar is ignored: " + x.getMessage());
                    stored = null;
                }
            }
        }
        return stored;
    }

    private static void validateCalendar(String value) {
        if (IcsAddress.isFeed(value)) {
            IcsAddress.parse(value);   // refuses a link that is not https
        } else if (CalendarAddress.isAddress(value)) {
            CalendarAddress.parse(value);   // refuses an address that must not get the token
        } else if (Files.isDirectory(Path.of(value))) {
            // the file may be created later: only a directory can never work
            throw new IllegalArgumentException("The calendar file is a directory: " + value);
        }
    }

    /** The line of --print-config for the calendar: a feed link is cut to its host, since the path is a secret. */
    private String calendarLine() {
        String value = calendar != null ? calendar : storedCalendar().value().orElse(null);
        String source = calendar != null ? "command line" : value != null ? "stored" : "default";
        String shown = value == null ? "(not set)" : IcsAddress.isFeed(value) ? IcsAddress.redact(value) : value;
        return String.format("%-16s %s  (%s)", "calendar", shown, source);
    }

    public boolean isNow() {
        return now;
    }
}