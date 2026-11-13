package ste.wakemeapp.settings;

import java.time.Duration;

/** What the application does when the user saves a setting in the dialog. Called on the JavaFX Application Thread. */
public interface SettingsListener {

    /** The calendar was saved ({@code value}), or removed (null). The lead time, if it changed too, is already saved. */
    void calendarSaved(String value);

    /** The lead time was saved and the calendar was not changed. */
    void leadTimeSaved(Duration leadTime);

    /** The notifications were switched on ({@code true}) or off; the state is already saved. */
    void notificationsSwitched(boolean on);

    /** The user asked for a test notification; it works whatever the other settings are. */
    void testNotificationRequested();

    /** The user asked to hear {@code choice} at {@code volumePercent}, whatever is saved. */
    void previewSound(SoundChoice choice, int volumePercent);

    /** The sound or its volume was saved. */
    void soundSaved();
}
