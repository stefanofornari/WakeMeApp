# Notes: notify/US-000010

## Design
* A dismissal is the pair event id + start of the occurrence (`DismissedMeetings`). Keying on the start gives "only this occurrence" and "a moved meeting is notified again" without extra rules: the poller calls `meetingMoved` for every event it reads.
* `CalendarPoller.dismiss` cancels everything pending for the event (snoozes included, through `NotificationScheduler.cancelAll`) and remembers what it knew, so that `undoDismiss` puts it back without scheduling twice.
* Only the Dismiss button dismisses. Escape, a click on the background and the automatic closing after 90 s only close the overlay.
* The undo is a small separate always-on-top window (`DismissToast`) at the bottom of the main screen, since the overlay itself is already gone. After Undo the overlay comes back with a new 90 s countdown.
* `dismissed.json` lives in the data folder next to `signin.json`; entries older than a day are dropped when it is read. If it cannot be read or written the app logs a warning and keeps the dismissals in memory.
* A notification without an event id (`--now`, a single scheduled meeting from the command line) has no calendar to forget it in: Dismiss just closes it.

## Not done
* The menu bar clause of the story moved to scheduler/US-000012, since the menu bar does not exist yet.
* The toast position and look are not checked by tests; see it once on a real screen.
