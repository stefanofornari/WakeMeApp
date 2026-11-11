package ste.wakemeapp.cli;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import picocli.CommandLine;
import ste.wakemeapp.auth.DeviceCodeSignIn;
import ste.wakemeapp.auth.FileTokenStore;
import ste.wakemeapp.auth.SignedInCalendarSource;
import ste.wakemeapp.calendar.CalendarPoller;
import ste.wakemeapp.calendar.CalendarSource;
import ste.wakemeapp.calendar.CsvCalendarSource;
import ste.wakemeapp.calendar.GraphCalendarSource;
import ste.wakemeapp.log.LogSetup;
import ste.wakemeapp.notify.MeetingNotification;
import ste.wakemeapp.notify.OverlayManager;
import ste.wakemeapp.scheduler.NotificationScheduler;
import ste.wakemeapp.scheduler.ScheduledNotification;
import ste.wakemeapp.settings.ConnectionSettings;
import ste.wakemeapp.settings.Settings;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CLILauncher extends Application {

    private static final Logger LOG = Logger.getLogger(CLILauncher.class.getName());

    private static CommandLine commandLine;
    private static NotificationCommand parsedCommand;
    private OverlayManager overlayManager;
    private NotificationScheduler scheduler;
    private CalendarPoller poller;
    private boolean calendarMode;
    private final AtomicInteger pendingNotifications = new AtomicInteger(0);

    @Override
    public void init() {
        // The primary stage is never shown: without this JavaFX would shut down as soon as the
        // overlay closes, and a snoozed notification could no longer be shown.
        Platform.setImplicitExit(false);
        commandLine = NotificationCommand.newCommandLine();
    }

    @Override
    public void start(Stage primaryStage) {
        
        // Hide the primary stage
        primaryStage.hide();

        List<String> rawArgs = getParameters().getRaw();
        String[] args = normalizeArgs(rawArgs);
        int exitCode = commandLine.execute(args);

        if (exitCode != 0) {
            Platform.exit();
            System.exit(exitCode);
            return;
        }

        // --help / --version: picocli has already printed the text and no meeting was parsed
        if (commandLine.isUsageHelpRequested() || commandLine.isVersionHelpRequested()) {
            Platform.exit();
            System.exit(0);
            return;
        }

        // --print-config: the settings were printed, nothing else starts
        if (commandLine.<NotificationCommand>getCommand().isPrintConfig()) {
            Platform.exit();
            System.exit(0);
            return;
        }

        parsedCommand = commandLine.getCommand();
        configureLogging();
        LOG.fine(() -> "Raw args: " + rawArgs);
        LOG.fine(() -> "Processed args: " + java.util.Arrays.toString(args));

        try {
            registerShutdownHook();
            if (parsedCommand.getCalendarAddress() != null) {
                pollGraphCalendar(parsedCommand.getCalendarAddress());
                return;
            }
            if (parsedCommand.getCalendarFile() != null) {
                pollCalendar(parsedCommand.getCalendarFile());
                return;
            }
            MeetingNotification notification = buildNotification();
            if (parsedCommand.isNow()) {
                showOverlay(notification);
            } else {
                schedule(notification);
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "WakeMeApp cannot start: " + e.getMessage(), e);
            Platform.exit();
            System.exit(1);
        }
    }

    private void configureLogging() {
        try {
            LogSetup.configure(parsedCommand.isDebug());
        } catch (RuntimeException e) {
            System.err.println("Cannot set up logging: " + e);
        }
        if (parsedCommand.isDebug()) {
            LOG.info("Debug logging is on");
        }
    }

    /**
     * Removes the quotes javafx-maven-plugin leaves in the arguments: it splits
     * {@code -Djavafx.args='--title="My Meeting"'} into the single token
     * {@code --title="My Meeting"}, quotes included, which picocli would take literally.
     * Handles {@code --opt="value"} and a whole argument wrapped in quotes.
     */
    static String[] normalizeArgs(List<String> rawArgs) {
        return rawArgs.stream()
            .map(a -> a.replaceFirst("^(-{1,2}[\\w-]+)=\"(.*)\"$", "$1=$2"))
            .map(a -> a.length() >= 2 && a.startsWith("\"") && a.endsWith("\"")
                ? a.substring(1, a.length() - 1) : a)
            .toArray(String[]::new);
    }

    private MeetingNotification buildNotification() {
        String title = parsedCommand.getTitle();
        java.time.LocalDateTime startDate = parsedCommand.getStartDate();
        java.time.LocalDateTime endDate = parsedCommand.getEndDate();
        String organizer = parsedCommand.getOrganizer();
        String url = parsedCommand.getUrl();
        String description = parsedCommand.getDescription();

        if (endDate != null) {
            return new MeetingNotification(title, startDate, url, organizer, description, endDate);
        } else if (organizer != null || description != null || url != null) {
            return new MeetingNotification(title, startDate, url, organizer, description);
        } else if (url != null) {
            return new MeetingNotification(title, startDate, url);
        } else {
            return new MeetingNotification(title, startDate, url);
        }
    }

    /**
     * Schedules the notification for its start minus the lead time. This process stays alive
     * (the JavaFX runtime keeps running) and shows the overlay, then exits once it is dismissed.
     */
    private void schedule(MeetingNotification notification) {
        scheduler = new NotificationScheduler(NotificationScheduler.onFxThread(this::onNotificationTriggered));
        pendingNotifications.incrementAndGet();
        ScheduledNotification scheduled = scheduler.schedule(notification);

        LOG.info(scheduleMessage(notification.title(), scheduled.triggerTime(), LocalDateTime.now()));
        LOG.info("Press Ctrl+C to cancel");
    }

    /**
     * Simulates a calendar: polls the CSV file and schedules what it finds. The process runs until
     * it is stopped, since meetings may appear in the file at any time.
     */
    private void pollCalendar(Path calendarFile) {
        calendarMode = true;
        scheduler = new NotificationScheduler(NotificationScheduler.onFxThread(this::onNotificationTriggered));
        poller = new CalendarPoller(new CsvCalendarSource(calendarFile), scheduler,
            parsedCommand.getPollInterval(), parsedCommand.getWindow(), Clock.systemDefaultZone());
        poller.start();
        LOG.info(() -> "Polling " + calendarFile + " every " + parsedCommand.getPollInterval().toMinutes()
            + " min for meetings starting in the next " + parsedCommand.getWindow().toMinutes() + " min");
        LOG.info("Press Ctrl+C to stop");
    }

    /**
     * Signs the user in (the code is in the log) and polls the Outlook calendar at {@code address};
     * until the sign-in is done the polls only log that they are waiting.
     */
    private void pollGraphCalendar(java.net.URI address) {
        ConnectionSettings connection = ConnectionSettings.resolve(
            parsedCommand.getClientId(), parsedCommand.getTenant(), Settings.user());
        DeviceCodeSignIn signIn = new DeviceCodeSignIn(
            connection.requireClientId(), connection.tenant(), null, new FileTokenStore(FileTokenStore.defaultFile()));
        // a sign-in saved by an earlier run is used if Microsoft still accepts it, otherwise the user gets a code;
        // a failure is logged, once, with the reason, by the sign-in itself
        signIn.resume().whenComplete((resumed, failure) -> {
            if (failure != null || !resumed) {
                signIn.signIn().thenRun(() -> LOG.info("Signed in"));
            }
        });
        CalendarSource calendar = new SignedInCalendarSource(
            new GraphCalendarSource(java.net.http.HttpClient.newBuilder(), signIn.accessTokenSupplier(), address),
            signIn::isSignedIn);
        calendarMode = true;
        scheduler = new NotificationScheduler(NotificationScheduler.onFxThread(this::onNotificationTriggered));
        poller = new CalendarPoller(calendar, scheduler,
            parsedCommand.getPollInterval(), parsedCommand.getWindow(), Clock.systemDefaultZone());
        poller.start();
        LOG.info(() -> "Polling " + address + " every " + parsedCommand.getPollInterval().toMinutes()
            + " min for meetings starting in the next " + parsedCommand.getWindow().toMinutes() + " min");
        LOG.info("Press Ctrl+C to stop");
    }

    /** What to tell the user right after scheduling, so a scheduled run is not mistaken for a hang. */
    static String scheduleMessage(String title, LocalDateTime triggerTime, LocalDateTime now) {
        long seconds = Duration.between(now, triggerTime).getSeconds();
        if (seconds <= 0) {
            return "Showing \"" + title + "\" now";
        }
        long minutes = (seconds + 59) / 60;
        return "Scheduled \"" + title + "\" for " + DateTimeFormatter.ofPattern("HH:mm").format(triggerTime)
            + " (in " + minutes + " min)";
    }

    private void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (poller != null) {
                poller.close();
            }
            if (scheduler != null) {
                scheduler.close();
            }
            try {
                Platform.runLater(() -> {
                    if (overlayManager != null) {
                        overlayManager.hideAll();
                    }
                });
            } catch (IllegalStateException e) {
                // JavaFX already shut down: nothing left to hide
            }
        }));
    }

    /** Called when a scheduled notification fires (initial or snooze). */
    private void onNotificationTriggered(MeetingNotification notification) {
        if (!calendarMode) {
            pendingNotifications.decrementAndGet();
        }
        showOverlay(notification);
    }

    private void showOverlay(MeetingNotification notification) {
        if (scheduler == null) {
            // --now: still needs a scheduler so that a snooze can bring the overlay back
            scheduler = new NotificationScheduler(NotificationScheduler.onFxThread(this::onNotificationTriggered));
        }
        overlayManager = new OverlayManager(scheduler);
        // Track snoozes scheduled from this overlay
        overlayManager.setOnSnoozeScheduled(() -> pendingNotifications.incrementAndGet());
        overlayManager.showNotification(notification);

        LOG.info(() -> "Showing notification \"" + notification.title() + "\"");
        LOG.fine(() -> "Meeting starts at " + notification.originalStartTime()
            + (notification.endTime() != null ? ", ends at " + notification.endTime() : "")
            + (notification.joinUrl() != null ? ", join URL " + notification.joinUrl() : ""));
        LOG.fine("Press ESC on the overlay to dismiss it, or Ctrl+C in the terminal to exit");

        if (calendarMode) {
            return; // keeps running: the next meeting may come from the calendar
        }

        // Wait for all overlays to be dismissed AND no pending notifications
        new Thread(() -> {
            try {
                while (overlayManager != null && 
                       (overlayManager.getOverlayCount() > 0 || pendingNotifications.get() > 0)) {
                    Thread.sleep(500);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                Platform.exit();
                System.exit(0);
            }
        }).start();
    }

    public static void main(String[] args) {
        launch(args);
    }
}