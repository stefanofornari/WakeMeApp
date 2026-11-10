package ste.wakemeapp.cli;

import picocli.CommandLine;
import ste.wakemeapp.calendar.CalendarPoller;
import ste.wakemeapp.scheduler.NotificationScheduler;
import ste.wakemeapp.settings.ConnectionSettings;
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
            description = "CSV file of meetings to poll like a calendar, instead of a single meeting")
    private Path calendarFile;

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

    @Option(names = "--debug", description = "Log the detail needed to investigate a problem (default: INFO and above)")
    private boolean debug;

    @Spec
    private CommandSpec spec;

    private LocalDateTime startDate;
    private LocalDateTime endDate;

    @Override
    public Integer call() {
        try {
            validatePolling();
            ConnectionSettings.validate(clientId, tenant);
            if (calendarFile != null) {
                // the file may be created later: only a directory can never work
                if (Files.isDirectory(calendarFile)) {
                    throw new IllegalArgumentException("The calendar file is a directory: " + calendarFile);
                }
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
        return calendarFile;
    }

    /** The value of --client-id, or null if not given (the stored one, if any, applies). */
    public String getClientId() {
        return clientId;
    }

    /** The value of --tenant, or null if not given (the stored one, if any, applies). */
    public String getTenant() {
        return tenant;
    }

    public boolean isNow() {
        return now;
    }
}