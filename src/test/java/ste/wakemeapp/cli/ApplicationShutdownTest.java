package ste.wakemeapp.cli;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import ste.wakemeapp.notify.MeetingNotification;
import ste.wakemeapp.notify.OverlayManager;
import ste.wakemeapp.scheduler.NotificationScheduler;
import static org.assertj.core.api.BDDAssertions.then;

@ExtendWith(ApplicationExtension.class)
class ApplicationShutdownTest {

    @Start
    private void start(Stage stage) {
        // initialises the JavaFX toolkit
    }

    @Test
    void should_close_overlay_cancel_pending_notification_and_exit_with_zero(FxRobot robot) {
        List<MeetingNotification> fired = new ArrayList<>();
        NotificationScheduler scheduler = new NotificationScheduler(fired::add);
        OverlayManager overlays = new OverlayManager(scheduler);
        MeetingNotification shown = new MeetingNotification("Shown", LocalDateTime.now().plusMinutes(10), null);
        robot.interact(() -> overlays.showNotification(shown));
        then(overlays.getOverlayCount()).isPositive();
        scheduler.scheduleAtExactTime(new MeetingNotification("Pending", LocalDateTime.now().plusNanos(300_000_000), null));

        List<Integer> exits = new ArrayList<>();
        ApplicationShutdown shutdown = new ApplicationShutdown(exits::add);
        shutdown.onShutdown(scheduler::close);
        shutdown.onShutdown(overlays::hideAll);

        robot.interact(shutdown::run);
        robot.sleep(500);

        then(overlays.getOverlayCount()).isZero();
        then(fired).isEmpty();
        then(exits).containsExactly(0);
    }

    @Test
    void should_exit_once_even_when_asked_twice_and_when_a_cleanup_fails() {
        AtomicInteger cleanups = new AtomicInteger();
        List<Integer> exits = new ArrayList<>();
        ApplicationShutdown shutdown = new ApplicationShutdown(exits::add);
        shutdown.onShutdown(() -> { throw new IllegalStateException("boom"); });
        shutdown.onShutdown(cleanups::incrementAndGet);

        shutdown.run();
        shutdown.run();

        then(cleanups).hasValue(1);
        then(exits).containsExactly(0);
    }
}
