package ste.wakemeapp.cli;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import picocli.CommandLine;
import ste.wakemeapp.auth.DeviceCodeSignIn;
import ste.wakemeapp.auth.FileTokenStore;
import ste.wakemeapp.auth.WebViewPresenter;
import ste.wakemeapp.auth.SignedInCalendarSource;
import ste.wakemeapp.calendar.CalendarAddress;
import ste.wakemeapp.calendar.CalendarPoller;
import ste.wakemeapp.calendar.CalendarSource;
import ste.wakemeapp.calendar.CsvCalendarSource;
import ste.wakemeapp.calendar.DismissedMeetings;
import ste.wakemeapp.calendar.GraphCalendarSource;
import ste.wakemeapp.calendar.IcsAddress;
import ste.wakemeapp.calendar.IcsCalendarSource;
import ste.wakemeapp.log.LogSetup;
import ste.wakemeapp.notify.MeetingNotification;
import ste.wakemeapp.notify.NotificationSwitch;
import ste.wakemeapp.notify.OverlayManager;
import ste.wakemeapp.notify.TestNotification;
import ste.wakemeapp.scheduler.NotificationScheduler;
import ste.wakemeapp.scheduler.ScheduledNotification;
import ste.wakemeapp.settings.ConnectionSettings;
import ste.wakemeapp.settings.CalendarInput;
import ste.wakemeapp.settings.LeadTime;
import ste.wakemeapp.settings.Settings;
import ste.wakemeapp.settings.SettingsDialog;
import ste.wakemeapp.settings.SettingsListener;
import ste.wakemeapp.settings.StoredCalendar;
import ste.wakemeapp.tray.Tray;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;
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
    private Tray tray;
    private SettingsDialog settingsDialog;
    private final NotificationSwitch notifications = NotificationSwitch.of(Settings.user());
    private TestNotification testNotification;
    private Runnable repoll;   // starts polling the current calendar again, with the settings as they are now
    private boolean calendarMode;
    private final ApplicationShutdown shutdown = new ApplicationShutdown(code -> {
        Platform.exit();
        System.exit(code);
    });
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
        LOG.fine(() -> "Raw args: " + redact(rawArgs));
        LOG.fine(() -> "Processed args: " + redact(java.util.Arrays.asList(args)));

        shutdown.onShutdown(() -> {
            if (poller != null) {
                poller.close();
            }
            if (scheduler != null) {
                scheduler.close();
            }
            if (overlayManager != null) {
                overlayManager.hideAll();
            }
            if (testNotification != null) {
                testNotification.close();
            }
        });
        try {
            registerShutdownHook();
            boolean configured = hasCalendar();
            if (configured || parsedCommand.isTrayOnly()) {
                tray = Tray.create(primaryStage, this::showSettings, this::shutdown);
                tray.start(configured);
            }
            if (parsedCommand.isSettings()) {
                showSettings();
            }
            if (parsedCommand.isTrayOnly()) {
                LOG.info("Running in the tray; no calendar is configured, nothing is polled");
                return;
            }
            if (parsedCommand.getCalendarFeed() != null) {
                pollIcsFeed(parsedCommand.getCalendarFeed());
                return;
            }
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

    /** Opens the settings dialog, or brings the open one to the front. FX thread only. */
    private void showSettings() {
        if (settingsDialog == null) {
            settingsDialog = new SettingsDialog(StoredCalendar.user(), Settings.user(), parsedCommand.getPollInterval(),
                new SettingsListener() {
                    @Override public void calendarSaved(String value) {
                        calendarChanged(value);
                    }

                    @Override public void leadTimeSaved(Duration leadTime) {
                        leadTimeChanged(leadTime);
                    }

                    @Override public void testNotificationRequested() {
                        if (testNotification == null) {
                            testNotification = new TestNotification();
                        }
                        testNotification.show();
                        LOG.info("Showing the test notification");
                    }

                    @Override public void notificationsSwitched(boolean on) {
                        notifications.follow(on);
                        LOG.info(on ? "Meeting notifications are on" : "Meeting notifications are off");
                    }
                });
        }
        settingsDialog.show();
    }

    /**
     * The user saved another calendar (or removed it, {@code value} null): stops polling the old one,
     * polls the new one and shows in the tray whether a calendar is configured. FX thread only.
     */
    private void calendarChanged(String value) {
        stopPolling();
        if (value == null) {
            if (tray != null) {
                tray.setConfigured(false);
            }
            LOG.info("No calendar is configured: nothing is polled");
            return;
        }
        try {
            switch (CalendarInput.kindOf(value).orElseThrow()) {
                case ICS_LINK -> pollIcsFeed(IcsAddress.parse(value));
                case GRAPH_ADDRESS -> pollGraphCalendar(CalendarAddress.parse(value));
                case CSV_FILE -> pollCalendar(Path.of(value.strip()));
            }
            if (tray != null) {
                tray.setConfigured(true);
            }
        } catch (RuntimeException x) {
            // the message of the exceptions of the calendar sources never holds a link
            LOG.severe("The new calendar cannot be polled: " + x.getMessage());
            if (tray != null) {
                tray.setConfigured(false);
            }
        }
    }

    /**
     * The lead time was saved: the calendar being polled is polled again with it, so that what is already
     * scheduled is scheduled again for the new time. FX thread only.
     */
    private void leadTimeChanged(Duration leadTime) {
        LOG.info("Lead time is now " + leadTime.toMinutes() + " min");
        Runnable again = repoll;
        if (again == null) {
            return;   // no calendar is being polled
        }
        stopPolling();
        try {
            again.run();
        } catch (RuntimeException x) {
            LOG.severe("The calendar cannot be polled again: " + x.getMessage());
            if (tray != null) {
                tray.setConfigured(false);
            }
        }
    }

    private NotificationScheduler newScheduler() {
        // a single meeting given on the command line is asked for explicitly: only a calendar can be switched off
        Consumer<MeetingNotification> trigger = calendarMode
            ? notifications.guard(this::onNotificationTriggered) : this::onNotificationTriggered;
        return new NotificationScheduler(NotificationScheduler.onFxThread(trigger),
            LeadTime.read(Settings.user()), Clock.systemDefaultZone());
    }

    /** The window to poll with: the one asked for, longer if the lead time needs it. */
    private Duration window() {
        return CalendarPoller.effectiveWindow(parsedCommand.getWindow(), parsedCommand.getPollInterval(),
            LeadTime.read(Settings.user()));
    }

    private void stopPolling() {
        repoll = null;
        if (poller != null) {
            poller.close();
            poller = null;
        }
        if (scheduler != null) {
            scheduler.close();
            scheduler = null;
        }
    }

    private boolean hasCalendar() {
        return parsedCommand.getCalendarFeed() != null || parsedCommand.getCalendarAddress() != null
            || parsedCommand.getCalendarFile() != null;
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

    /**
     * The arguments as they may be logged: the address of an ICS feed is a secret, so it is cut to its
     * host. The other arguments are unchanged.
     */
    static List<String> redact(List<String> args) {
        List<String> shown = new java.util.ArrayList<>();
        for (int i = 0; i < args.size(); i++) {
            String arg = args.get(i);
            if (arg.equals("--calendar") && i + 1 < args.size()) {
                shown.add(arg);
                shown.add(redactFeed(args.get(++i)));
            } else if (arg.startsWith("--calendar=")) {
                String value = arg.substring("--calendar=".length());
                String unquoted = (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\""))
                    ? value.substring(1, value.length() - 1) : value;
                shown.add(IcsAddress.isFeed(unquoted) ? "--calendar=" + IcsAddress.redact(unquoted) : arg);
            } else {
                shown.add(arg);
            }
        }
        return shown;
    }

    private static String redactFeed(String value) {
        String unquoted = (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\""))
            ? value.substring(1, value.length() - 1) : value;
        return IcsAddress.isFeed(unquoted) ? IcsAddress.redact(unquoted) : value;
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
        scheduler = newScheduler();
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
        repoll = () -> pollCalendar(calendarFile);
        calendarMode = true;
        scheduler = newScheduler();
        poller = new CalendarPoller(new CsvCalendarSource(calendarFile), scheduler,
            parsedCommand.getPollInterval(), window(), Clock.systemDefaultZone(),
            new DismissedMeetings(DismissedMeetings.defaultFile()));
        poller.start();
        LOG.info(() -> "Polling " + calendarFile + " every " + parsedCommand.getPollInterval().toMinutes()
            + " min for meetings starting in the next " + window().toMinutes() + " min");
        LOG.info("Press Ctrl+C to stop");
    }

    /**
     * Polls a published calendar (ICS feed). No sign-in: the link is the credential, so only its
     * host is ever shown.
     */
    private void pollIcsFeed(java.net.URI feed) {
        CalendarSource calendar = new IcsCalendarSource(
            java.net.http.HttpClient.newBuilder().followRedirects(java.net.http.HttpClient.Redirect.NORMAL), feed);
        repoll = () -> pollIcsFeed(feed);
        calendarMode = true;
        scheduler = newScheduler();
        poller = new CalendarPoller(calendar, scheduler,
            parsedCommand.getPollInterval(), window(), Clock.systemDefaultZone(),
            new DismissedMeetings(DismissedMeetings.defaultFile()));
        poller.start();
        LOG.info(() -> "Polling the calendar feed on " + IcsAddress.hostOf(feed) + " every "
            + parsedCommand.getPollInterval().toMinutes() + " min for meetings starting in the next "
            + window().toMinutes() + " min");
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
            connection.requireClientId(), connection.tenant(), new WebViewPresenter(getHostServices()::showDocument),
            new FileTokenStore(FileTokenStore.defaultFile()));
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
        repoll = () -> pollGraphCalendar(address);
        calendarMode = true;
        scheduler = newScheduler();
        poller = new CalendarPoller(calendar, scheduler,
            parsedCommand.getPollInterval(), window(), Clock.systemDefaultZone(),
            new DismissedMeetings(DismissedMeetings.defaultFile()));
        poller.start();
        LOG.info(() -> "Polling " + address + " every " + parsedCommand.getPollInterval().toMinutes()
            + " min for meetings starting in the next " + window().toMinutes() + " min");
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

    /**
     * Ends the application: closes the overlays, cancels what is pending and exits with code 0.
     * Callable from any thread (the tray menu will call it); the cleanup runs on the FX thread.
     */
    void shutdown() {
        if (Platform.isFxApplicationThread()) {
            shutdown.run();
        } else {
            Platform.runLater(shutdown::run);
        }
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
            scheduler = newScheduler();
        }
        overlayManager = new OverlayManager(scheduler);
        // Track snoozes scheduled from this overlay
        overlayManager.setOnSnoozeScheduled(() -> pendingNotifications.incrementAndGet());
        if (poller != null) {
            overlayManager.setOnDismissed(poller::dismiss);
            overlayManager.setOnDismissUndone(poller::undoDismiss);
        }
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