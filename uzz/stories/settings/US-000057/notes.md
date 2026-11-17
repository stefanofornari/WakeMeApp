# Notes — settings/US-000057

* The logic that does not need JavaFX is in `CalendarInput` (kind, validation, masking) and `CalendarKind`, tested on its own (`CalendarInputTest`, run). The controller only wires it to the field.
* Kind detection is the one of `--calendar` (ICS link, Graph address, CSV path). A value is a CSV path when it looks like one (has a separator, starts with `~` or `.`, or ends in `.csv`); anything else has no kind and is refused naming the three accepted forms, without being echoed. A missing file is a different message ("The file cannot be read: path").
* The messages of the dialog never contain a link: plain `http://` gets "must start with https:// or webcal://"; a link that does not parse gets "The link is not valid". Graph errors are the ones of `CalendarAddress` with "--calendar" replaced by "The address" (a Graph address is not a secret).
* The saved link is shown masked. The text of the field is compared with how the saved value was shown: untouched, Save just closes (so the saved link survives); changed, the whole text is the new value. There is no reveal.
* Save: validate, `StoredCalendar.save`, then `CLILauncher.calendarChanged(value)` stops the poller and scheduler, starts the one for the new kind and sets the tray mark. An empty field removes the calendar (`calendarChanged(null)`). A calendar given with `--calendar` for this run is replaced as soon as one is saved. A poll that cannot start is logged (message only) and the tray shows "not configured".
* The Microsoft account section (client id, tenant) is not here (settings/US-000014): for a Graph address the dialog only shows a hint, and the sign-in still uses `--client-id` / `--tenant` or the stored values.
* Choosing a CSV file with a picker is not in this story.
* A single meeting scheduled from the command line is cancelled too if a calendar is saved (it shares the scheduler); only reachable with `--settings` plus `--title`.
* Not verified here: `SettingsDialogTest` (TestFX) and `CLILauncher.calendarChanged` need `mvn test` and a desktop.
