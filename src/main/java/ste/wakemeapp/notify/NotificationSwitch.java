package ste.wakemeapp.notify;

import java.util.function.Consumer;
import java.util.logging.Logger;
import ste.wakemeapp.settings.NotificationsEnabled;
import ste.wakemeapp.settings.Settings;

/**
 * Whether meeting notifications are on, for all meetings at once. The state is stored and survives a
 * restart; changing it takes effect at once, from any thread. The meetings keep being polled while it
 * is off, only what would be shown is held back.
 */
public final class NotificationSwitch {

    public static final String KEY = NotificationsEnabled.KEY;

    private static final Logger LOG = Logger.getLogger(NotificationSwitch.class.getName());

    private final Settings settings;
    private volatile boolean on;

    private NotificationSwitch(Settings settings) {
        this.settings = settings;
        this.on = NotificationsEnabled.read(settings);
    }

    /** The switch in the state that is stored (on if nothing valid is). */
    public static NotificationSwitch of(Settings settings) {
        return new NotificationSwitch(settings);
    }

    public boolean isOn() {
        return on;
    }

    /** Switches on or off and stores the state. */
    public void set(boolean on) {
        this.on = on;
        NotificationsEnabled.save(settings, on);
    }

    /** Takes over a state that is already stored (the dialog saved it), without writing it again. */
    public void follow(boolean on) {
        this.on = on;
    }

    /** {@code action} wrapped so that it only runs while the switch is on; a notification held back is logged. */
    public Consumer<MeetingNotification> guard(Consumer<MeetingNotification> action) {
        return notification -> {
            if (on) {
                action.accept(notification);
            } else {
                LOG.info("Notification suppressed (globally disabled)");
            }
        };
    }
}
