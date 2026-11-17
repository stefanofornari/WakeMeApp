# Notes for calendar/US-000028 — Schedule Upcoming Meetings from a Polled Calendar Source

## Decisions

- New package `ste.wakemeapp.calendar`: `CalendarSource` (single-method interface, so tests can use a lambda), `InMemoryCalendarSource` (the double that stands in for the remote calendar; events can be added, replaced by id and removed while a poller runs), `CalendarEvent` (event id, times, join URL, organizer, response status, all-day flag; `toNotification()` for the overlay data) and `CalendarPoller`.
- `CalendarEvent` and `ResponseStatus` carry the fields calendar/US-000029 needs (response status, all-day) so that story does not have to change the model. Nothing filters on them yet: in this story every returned meeting is scheduled.
- The window is `[from, to)`: a meeting starting exactly at `to` is picked up by the next poll.
- The poller keeps a map event id → `ScheduledNotification` and schedules an id once. This is the minimum needed because the window (15 min) is larger than the poll interval (5 min), so one meeting is returned by up to three polls. The map is also where scheduler/US-000030 will reschedule and cancel.
- A failing poll (any exception from the source) is logged at WARNING and swallowed; `scheduleAtFixedRate` would otherwise stop all later polls.
- Polling uses `scheduleAtFixedRate` with delay 0: the first poll runs immediately on `start()`.
- The poller does not own the scheduler: `close()` stops polling only, notifications already scheduled stay.

## Not done here

- The poller is not wired into the application yet. Starting it from the launcher needs a calendar source to plug in and the options of calendar/US-000031.
- Poll interval and window are constructor arguments with the defaults of the story; command-line options are calendar/US-000031.

## Effects on other stories

- calendar/US-000003 was reduced to the Microsoft Graph implementation of `CalendarSource`; scheduler/US-000011 was canceled. Stories that still list scheduler/US-000011 as a dependency (US-12, US-13, US-15, US-17, US-19, US-20, US-21) were not touched.

## Verification status

Tests run with Maven and passing; behaviour confirmed by a manual run (2026-11-08).
