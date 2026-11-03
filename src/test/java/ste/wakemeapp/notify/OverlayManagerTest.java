package ste.wakemeapp.notify;

import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import static org.assertj.core.api.BDDAssertions.then;

@ExtendWith(ApplicationExtension.class)
class OverlayManagerTest {

    private OverlayManager manager;

    @Start
    private void start(Stage stage) {
        // Initialize JavaFX toolkit - runs on FX thread
        manager = new OverlayManager();
    }

    @Test
    void should_create_overlay_per_screen(FxRobot robot) {
        MeetingNotification notification = new MeetingNotification(
            "Test Meeting",
            java.time.LocalDateTime.now().plusMinutes(5),
            "https://teams.example.com"
        );

        robot.interact(() -> manager.showNotification(notification));

        then(manager.getOverlayCount()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void should_hide_all_overlays(FxRobot robot) {
        MeetingNotification notification = new MeetingNotification(
            "Test Meeting",
            java.time.LocalDateTime.now().plusMinutes(5),
            "https://teams.example.com"
        );

        robot.interact(() -> manager.showNotification(notification));
        int initialCount = manager.getOverlayCount();

        robot.interact(() -> manager.hideAll());

        then(manager.getOverlayCount()).isEqualTo(0);
    }

    @Test
    void should_configure_overlay_for_each_screen_bounds(FxRobot robot) {
        MeetingNotification notification = new MeetingNotification(
            "Test Meeting",
            java.time.LocalDateTime.now().plusMinutes(5),
            "https://teams.example.com"
        );

        robot.interact(() -> manager.showNotification(notification));

        for (OverlayWindow overlay : manager.getOverlays()) {
            Stage stage = overlay.getStage();
            then(stage.getWidth()).isGreaterThan(0);
            then(stage.getHeight()).isGreaterThan(0);
        }
    }
}