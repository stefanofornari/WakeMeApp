package ste.wakemeapp.scheduler;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.application.Platform;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import ste.wakemeapp.notify.MeetingNotification;
import static org.assertj.core.api.BDDAssertions.then;

@ExtendWith(ApplicationExtension.class)
class NotificationSchedulerFxThreadTest {

    private NotificationScheduler scheduler;

    @Start
    private void start(Stage stage) {
        // starts the JavaFX toolkit
    }

    @AfterEach
    void close_scheduler() {
        scheduler.close();
    }

    @Test
    void should_run_the_action_on_the_javafx_application_thread() throws Exception {
        AtomicBoolean onFxThread = new AtomicBoolean();
        CountDownLatch latch = new CountDownLatch(1);
        scheduler = new NotificationScheduler(
            NotificationScheduler.onFxThread(n -> {
                onFxThread.set(Platform.isFxApplicationThread());
                latch.countDown();
            }),
            Duration.ZERO,
            Clock.systemDefaultZone()
        );

        scheduler.schedule(new MeetingNotification(
            "Standup", LocalDateTime.now().plus(Duration.ofMillis(200)), "https://example.com/join"
        ));

        then(latch.await(2, TimeUnit.SECONDS)).isTrue();
        then(onFxThread.get()).isTrue();
    }
}
