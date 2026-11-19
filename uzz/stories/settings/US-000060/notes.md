# Notes — settings/US-000060

* "Last update" is the last *successful* read: `CalendarPoller.onSuccessfulPoll` fires after a poll that read the calendar (even with no meetings), never after a failure. A throwing callback is logged and does not stop polling.
* Stored in `calendar.last_update` as a UTC ISO-8601 instant, truncated to the second. A blank or unparseable value counts as "none" (warning in the log). It is state, so `--print-config` does not report it as an unknown key and the dialog does not show it.
* Split of duties when the calendar changes: `SettingsController` clears the stored key (testable without the launcher); `CLILauncher.calendarChanged` resets the tray text.
* The day is decided in the clock's zone, the text in the locale (`LastUpdateText`).
* Not verified here: the dorkbox 4.4 calls (`Separator`, `MenuItem.setEnabled/setText`), TestFX/JavaFX tests (`SettingsDialogTest`, `TrayTest`) and the menu order on each platform; run `mvn test` and look at the menu.
