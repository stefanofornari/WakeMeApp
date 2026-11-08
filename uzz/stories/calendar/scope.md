# Project Name: WakeMeUp — Calendar Synchronisation
**Version:** 0.1.0

## Overall Description
Keeps the notification scheduler aligned with a calendar that changes over time. A poller
periodically asks a calendar source for the meetings starting in the next *window* of time
(policy: "meetings in the next X minutes", not "the first meeting" nor "up to X meetings")
and brings the scheduler in line with the result: new meetings are scheduled, moved meetings
are rescheduled, cancelled or no-longer-eligible meetings are unscheduled, unchanged meetings
are left alone. Meetings are identified by the calendar event id, so a change of start time
reschedules the same notification instead of creating a new one.

The window must be at least *poll interval + lead time* so that every meeting that may need a
notification before the next poll is already known to the scheduler (defaults: poll interval
5 min, lead time 5 min, window 15 min).

This scope covers the synchronisation logic and a calendar-source abstraction only; the
real Microsoft Graph source is delivered separately (auth and calendar domains).

## In-Scope Capabilities
* **Calendar source abstraction**:
  - [ ] A calendar-source interface that returns the meetings starting within a given time window (event id, title, start, end, join URL, organizer, response status, all-day flag)
  - [ ] An in-memory implementation of the interface, usable to drive the poller in tests
  - [ ] A CSV-file implementation of the interface, re-read at every poll, so that the integration with a calendar (including changes over time) can be simulated by editing a file
  - [ ] A command-line option `--calendar` that starts the poller on such a file instead of notifying for a single meeting
* **Poller**:
  - [ ] Polls the calendar source every poll interval (default 5 min) on a background thread, never on the JavaFX Application Thread
  - [ ] Asks for the meetings starting in the next window (default 15 min)
  - [ ] Poll interval and window can be changed from the command line (defaults apply when omitted)
  - [ ] A command line where the window is shorter than poll interval + lead time is rejected with an error
  - [ ] A failed poll is logged and the next poll runs at the normal interval; already scheduled notifications keep firing
* **Eligibility filter**:
  - [ ] Include accepted (committed) and tentative meetings, and meetings the user organises
  - [ ] Exclude all-day events
  - [ ] Exclude declined meetings
  - [ ] Exclude meetings the user has not answered
* **Scheduler reconciliation**:
  - [ ] A meeting not yet known is scheduled
  - [ ] A meeting whose start time changed has its pending notification rescheduled (same event id, same notification) and its snooze count reset to zero
  - [ ] A meeting that disappeared from the result, or is no longer eligible, has its pending notification cancelled
  - [ ] An unchanged meeting is not rescheduled and does not notify twice
  - [ ] Snoozes already scheduled are not dropped by a poll

## Out-of-Scope / Future Enhancements
* Microsoft Graph implementation of the calendar source (pagination, throttling, timezone conversion stay with calendar/US-000003)
* All-day events (to be tracked as a future feature)
* Rule-based filtering (organizer, keywords, categories, calendar selection)
* Push updates (Graph change notifications / webhooks) instead of polling
* Configuring poll interval and window through the settings UI (settings domain)
* Persisting pending notifications across restarts (scheduler/US-000026)
* Catching up on notifications missed during sleep (scheduler/US-000027)

## Technical Context & Infrastructure
* **Development Toolchain**: Maven + JavaFX 25 + Java 25
* **Target Systems/Platforms**: macOS (primary), Windows/Linux (future)
* **Project Reference Docs**:
  - Coding Standard: `../../project/coding_standard.md`
  - Development Framework: `../../project/development_framework.md`
