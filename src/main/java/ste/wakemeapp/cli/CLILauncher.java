package ste.wakemeapp.cli;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import picocli.CommandLine;
import ste.wakemeapp.notify.MeetingNotification;
import ste.wakemeapp.notify.OverlayManager;
import java.util.List;

public class CLILauncher extends Application {

    private static CommandLine commandLine;
    private static NotificationCommand parsedCommand;
    private OverlayManager overlayManager;

    @Override
    public void init() {
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
            MeetingNotification notification = buildNotification();
            showOverlay(notification);
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

    private void showOverlay(MeetingNotification notification) {
        overlayManager = new OverlayManager();
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

        // Add shutdown hook to clean up
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Platform.runLater(() -> {
                if (overlayManager != null) {
                    overlayManager.hideAll();
                }
            });
        }));

        // Wait for all overlays to be dismissed
        new Thread(() -> {
            try {
                while (overlayManager != null && overlayManager.getOverlayCount() > 0) {
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