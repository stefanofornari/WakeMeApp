package ste.wakemeapp.cli;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import picocli.CommandLine;
import ste.wakemeapp.calendar.CalendarPoller;
import ste.wakemeapp.calendar.CsvCalendarSource;
import ste.wakemeapp.notify.MeetingNotification;
import ste.wakemeapp.notify.OverlayManager;
import ste.wakemeapp.scheduler.NotificationScheduler;
import ste.wakemeapp.scheduler.ScheduledNotification;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class CLILauncher extends Application {

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
        commandLine = new CommandLine(new NotificationCommand());
    }

    @Override
    public void start(Stage primaryStage) {
        
        // Hide the primary stage
        primaryStage.hide();

        List<String> rawArgs = getParameters().getRaw();
        System.out.println("Raw args: " + rawArgs);
        String[] args = normalizeArgs(rawArgs);
        System.out.println("Processed args: " + java.util.Arrays.toString(args));
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

        parsedCommand = commandLine.getCommand();

        try {
            registerShutdownHook();
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
            System.err.println("Error: " + e.getMessage());
            Platform.exit();
            System.exit(1);
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

        System.out.println(scheduleMessage(notification.title(), scheduled.triggerTime(), LocalDateTime.now()));
        System.out.println("Press Ctrl+C to cancel");
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
        System.out.println("Polling " + calendarFile + " every " + parsedCommand.getPollInterval().toMinutes()
            + " min for meetings starting in the next " + parsedCommand.getWindow().toMinutes() + " min");
        System.out.println("Press Ctrl+C to stop");
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

        System.out.println("Showing overlay for: " + notification.title());
        System.out.println("Starting at: " + notification.startTime());
        if (notification.endTime() != null) {
            System.out.println("Ending at: " + notification.endTime());
        }
        if (notification.joinUrl() != null) {
            System.out.println("Join URL: " + notification.joinUrl());
        }
        System.out.println("Press ESC on overlay to dismiss, or Ctrl+C here to exit");

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