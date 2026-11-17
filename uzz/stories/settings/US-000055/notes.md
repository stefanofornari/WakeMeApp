# Notes — settings/US-000055

* `StoredCalendar` (settings package) wraps `FileTokenStore`, which already does the owner-only temporary file and atomic move; it got a second constructor with a label so the warning reads "saved calendar", not "saved sign-in". File: `calendar.txt` next to `signin.json`.
* Nothing writes the file yet: the dialog (settings/US-000057) calls `StoredCalendar.save`. Until then it can only be created by hand (README says so). The tray (settings/US-000054) and polling start on save are wired in US-000057.
* Precedence is in `NotificationCommand.calendarValue()`: `--calendar`, else the stored value. A single meeting on the command line (`--title`, `--start-date`, ...) does not use the stored calendar; it is its own request. `isTrayOnly()` is now "no meeting options and no calendar from either source", so a stored calendar starts the tray as configured and polls, with no change in `CLILauncher`.
* A stored value that is not valid (plain `http://` link, a directory) is ignored with a warning and counts as not configured: the user cannot fix it from the command line. A `--calendar` value that is not valid is still an error. The warning carries the validation message, which never contains the link.
* `--print-config` reads the raw stored value (so it shows even an invalid one) and shows an ICS link as host only, source `(stored)`.
* Tests of `NotificationCommand` inject an empty `StoredCalendar` in a temporary folder, so they never read the developer's own `calendar.txt`. Tests that don't, and give meeting options, don't read it either.
* Not verified here: the picocli tests (`NotificationCommandTest`) need `mvn test`; `StoredCalendarTest` was run against the shim.
* calendar/US-000053 note 2 amended: this file is the one exception to "the link is never written".
