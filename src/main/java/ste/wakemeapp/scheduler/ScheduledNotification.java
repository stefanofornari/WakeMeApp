package ste.wakemeapp.scheduler;

import java.time.LocalDateTime;
import java.util.concurrent.ScheduledFuture;
import ste.wakemeapp.notify.MeetingNotification;

/** Handle for a notification registered with a {@link NotificationScheduler}. */
public final class ScheduledNotification {

    private final MeetingNotification notification;
    private final LocalDateTime triggerTime;
    private final ScheduledFuture<?> future;

    ScheduledNotification(MeetingNotification notification, LocalDateTime triggerTime, ScheduledFuture<?> future) {
        this.notification = notification;
        this.triggerTime = triggerTime;
        this.future = future;
    }

    public MeetingNotification notification() {
        return notification;
    }

    /** When the notification is due: the meeting start minus the lead time. */
    public LocalDateTime triggerTime() {
        return triggerTime;
    }

    /** Prevents the notification from firing if it has not fired yet. */
    public void cancel() {
        future.cancel(false);
    }
}
