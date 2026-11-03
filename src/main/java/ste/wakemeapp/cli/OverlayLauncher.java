package ste.wakemeapp.cli;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import java.util.List;
import ste.wakemeapp.notify.MeetingNotification;
import ste.wakemeapp.notify.OverlayManager;

public class OverlayLauncher extends Application {

    @Override
    public void start(Stage primaryStage) {
        // Use JavaFX getParameters() for proper argument handling
        // javafx-maven-plugin keeps the quotes around arguments containing spaces
        List<String> params = getParameters().getRaw().stream()
            .map(a -> a.length() >= 2 && a.startsWith("\"") && a.endsWith("\"")
                ? a.substring(1, a.length() - 1) : a)
            .toList();

        // Check if any non-empty arguments provided
        if (params.isEmpty() || params.stream().allMatch(String::isBlank)) {
            System.err.println("Error: No arguments provided.");
            System.err.println("Usage: OverlayLauncher <meeting-title> <minutes-from-now> [join-url]");
            System.err.println("Example: OverlayLauncher \"Team Standup\" 5 \"https://teams.example.com/join\"");
            primaryStage.close();
            Platform.exit();
            System.exit(1);
            return;
        }

        // Hide the primary stage
        primaryStage.hide();

        String title = params.get(0);
        int minutesFromNow = 5;
        String joinUrl = "https://teams.microsoft.com/l/meetup-join/...";

        if (params.size() > 1) {
            try {
                minutesFromNow = Integer.parseInt(params.get(1));
            } catch (NumberFormatException e) {
                System.err.println("Invalid minutes value: " + params.get(1) + ", using default 5");
            }
        }
        if (params.size() > 2) {
            joinUrl = params.get(2);
        }

        MeetingNotification notification = new MeetingNotification(
            title,
            java.time.LocalDateTime.now().plusMinutes(minutesFromNow),
            joinUrl,
            "Test Organizer",
            "Test meeting description"
        );

        System.out.println("Showing overlay for: " + title);
        System.out.println("Starting in: " + minutesFromNow + " minutes");
        System.out.println("Join URL: " + joinUrl);
        System.out.println("Press ESC on overlay to dismiss, or Ctrl+C here to exit");

        OverlayManager manager = new OverlayManager();
        manager.showNotification(notification);

        // Add shutdown hook to clean up
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Platform.runLater(() -> manager.hideAll());
        }));

        // Wait for all overlays to be dismissed
        new Thread(() -> {
            while (manager.getOverlayCount() > 0) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            Platform.exit();
            System.exit(0);
        }).start();
    }

    public static void main(String[] args) {
        launch(args);
    }
}