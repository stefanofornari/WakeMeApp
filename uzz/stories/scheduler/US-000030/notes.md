# Notes for scheduler/US-000030 — Keep Scheduled Notifications in Sync with Calendar Changes

## Decisions

- `MeetingNotification` got a last component `eventId` (null for a meeting not coming from a calendar). It travels with the notification through `withSnooze`, which is how a snooze can be tied back to its meeting without the poller knowing about snoozes.
- `NotificationScheduler` keeps the pending handles per event id and offers `cancelAll(eventId)`: it cancels the notification and any snooze of that event, and nothing else. Handles that already fired are pruned on the next registration for the same event.
- `CalendarPoller` remembers, per event id, the start it scheduled and the handle, and reconciles on every poll: unknown → schedule; start changed → `cancelAll` then schedule again (the new notification starts at snooze count 0, which also replaces a pending snooze); declined/unanswered/all-day or moved out of the window or deleted → `cancelAll`; unchanged → nothing.
- A meeting that is not returned by a poll is cancelled only if its remembered start is still in the future. Once it has started it has left the window (windows begin at "now"), so absence says nothing; the entry is just forgotten and a pending snooze (which can ring after the start) is left alone.
- A failing poll skips reconciliation entirely, so nothing is cancelled because the calendar was unreachable.
- Only the start time is compared. A changed title, link or end time of a meeting already scheduled is not applied until it is rescheduled for another reason: not covered by the scenarios.

## Verification status

The scenarios of the new tests (`CalendarPollerTest`, `NotificationSchedulerTest`, `MeetingNotificationTest`) were checked with a plain Java program against a stub of `javafx.application.Platform`: all passed. The tests themselves have not been run with Maven, and the manual check (edit the CSV while the app runs) has not been done. Status stays `WIP` until both.
