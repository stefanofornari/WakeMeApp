package ste.wakemeapp.scheduler;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import ste.wakemeapp.notify.MeetingNotification;

/**
 * Fires a callback for each registered meeting at {@code start - leadTime}.
 * <p>
 * Callbacks run on the scheduler's own thread; wrap UI work with
 * {@link #onFxThread(Consumer)}. State is in memory only.
 */
public final class NotificationScheduler implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(NotificationScheduler.class.getName());

    public static final Duration DEFAULT_LEAD_TIME = Duration.ofMinutes(5);

    private final Consumer<MeetingNotification> onTrigger;
    private final Duration leadTime;
    private final Clock clock;
    private final ScheduledExecutorService executor;

    public NotificationScheduler(Consumer<MeetingNotification> onTrigger) {
        this(onTrigger, DEFAULT_LEAD_TIME, Clock.systemDefaultZone());
    }

    public NotificationScheduler(Consumer<MeetingNotification> onTrigger, Duration leadTime, Clock clock) {
        this.onTrigger = onTrigger;
        this.leadTime = leadTime;
        this.clock = clock;
        this.executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "notification-scheduler");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** Wraps {@code action} so that it runs on the JavaFX Application Thread. */
    public static Consumer<MeetingNotification> onFxThread(Consumer<MeetingNotification> action) {
        return notification -> Platform.runLater(() -> action.accept(notification));
    }

    /**
     * Registers a meeting. If its trigger time has already passed it fires immediately.
     */
    public ScheduledNotification schedule(MeetingNotification notification) {
        LocalDateTime triggerTime = notification.startTime().minus(leadTime);
        long delayMillis = Math.max(0, Duration.between(LocalDateTime.now(clock), triggerTime).toMillis());

        return new ScheduledNotification(
            notification,
            triggerTime,
            executor.schedule(() -> fire(notification), delayMillis, TimeUnit.MILLISECONDS)
        );
    }

    /** Drops everything that has not fired yet. */
    @Override
    public void close() {
        executor.shutdownNow();
    }

    private void fire(MeetingNotification notification) {
        try {
            onTrigger.accept(notification);
        } catch (RuntimeException e) {
            // an exception must not escape: it would be swallowed with the task's future
            LOG.log(Level.SEVERE, e, () -> "Notification handler failed for '" + notification.title() + "'");
        }
    }
}
