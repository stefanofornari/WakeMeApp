package ste.wakemeapp.scheduler;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
    /** What is still pending for each calendar event, so that it can be cancelled by event id. */
    private final Map<String, List<ScheduledNotification>> pendingByEvent = new ConcurrentHashMap<>();

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

        return register(new ScheduledNotification(
            notification,
            triggerTime,
            executor.schedule(() -> fire(notification), delayMillis, TimeUnit.MILLISECONDS)
        ));
    }

    /**
     * Registers a notification that fires exactly at its {@code startTime}, with no lead time
     * (used for snoozes). If that time has already passed it fires immediately.
     */
    public ScheduledNotification scheduleAtExactTime(MeetingNotification notification) {
        LocalDateTime triggerTime = notification.startTime();
        long delayMillis = Math.max(0, Duration.between(LocalDateTime.now(clock), triggerTime).toMillis());

        return register(new ScheduledNotification(
            notification,
            triggerTime,
            executor.schedule(() -> fire(notification), delayMillis, TimeUnit.MILLISECONDS)
        ));
    }

    /**
     * Cancels everything not yet fired for a calendar event: the notification and any snooze of it.
     * Notifications of other events, or without an event id, are left alone.
     */
    public void cancelAll(String eventId) {
        List<ScheduledNotification> pending = pendingByEvent.remove(eventId);
        if (pending != null) {
            pending.forEach(ScheduledNotification::cancel);
        }
    }

    private ScheduledNotification register(ScheduledNotification scheduled) {
        String eventId = scheduled.notification().eventId();
        if (eventId != null) {
            pendingByEvent.compute(eventId, (id, pending) -> {
                List<ScheduledNotification> list = pending == null ? new ArrayList<>() : pending;
                list.removeIf(ScheduledNotification::isDone); // drop what already fired
                list.add(scheduled);
                return list;
            });
        }
        return scheduled;
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
