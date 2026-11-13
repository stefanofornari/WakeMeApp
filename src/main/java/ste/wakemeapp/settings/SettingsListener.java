package ste.wakemeapp.settings;

import java.time.Duration;

/** What the application does when the user saves a setting in the dialog. Called on the JavaFX Application Thread. */
public interface SettingsListener {

    /** The calendar was saved ({@code value}), or removed (null). The lead time, if it changed too, is already saved. */
    void calendarSaved(String value);

    /** The lead time was saved and the calendar was not changed. */
    void leadTimeSaved(Duration leadTime);
}
