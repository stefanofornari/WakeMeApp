package ste.wakemeapp.notify;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import ste.wakemeapp.scheduler.NotificationScheduler;

/**
 * A fake meeting shown through the real overlay, to check that the display (and later the sound) work.
 * It has a scheduler and an overlay manager of its own, so a snooze brings it back and nothing of it
 * touches the calendar being polled or the application's own scheduler. It does not look at the
 * switch of the notifications. Use on the JavaFX Application Thread.
 */
public final class TestNotification {

    /** How long the overlay stays up without interaction. */
    static final Duration AUTO_CLOSE = Duration.ofSeconds(10);

    static final String JOIN_URL = "https://example.com";

    private final NotificationScheduler scheduler;
    private final OverlayManager manager;

    public TestNotification() {
        // a snooze fires again through the same overlay
        scheduler = new NotificationScheduler(NotificationScheduler.onFxThread(this::show));
        manager = new OverlayManager(scheduler);
        manager.setAutoClose(AUTO_CLOSE);
    }

    /** The meeting: "Test Meeting", starting in 5 minutes, with a harmless join link, not from a calendar. */
    static MeetingNotification meeting(Clock clock) {
        return new MeetingNotification("Test Meeting", LocalDateTime.now(clock).plusMinutes(5), JOIN_URL,
            "WakeMeApp", "A test of the notification: nothing in your calendar.");
    }

    /** Shows the test meeting now. */
    public void show() {
        show(meeting(Clock.systemDefaultZone()));
    }

    private void show(MeetingNotification notification) {
        manager.showNotification(notification);
    }

    /** Closes what is showing and cancels a snooze that is pending. */
    public void close() {
        manager.hideAll();
        scheduler.close();
    }

    OverlayManager manager() {
        return manager;
    }
}
