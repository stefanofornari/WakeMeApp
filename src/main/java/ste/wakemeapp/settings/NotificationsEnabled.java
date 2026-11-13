package ste.wakemeapp.settings;

/** The stored state of the global switch of the meeting notifications: on unless the user switched them off. */
public final class NotificationsEnabled {

    public static final String KEY = "notify.enabled";

    private NotificationsEnabled() {
    }

    public static boolean read(Settings stored) {
        return stored.getBoolean(KEY, true);
    }

    public static void save(Settings stored, boolean enabled) {
        stored.putBoolean(KEY, enabled);
    }
}
