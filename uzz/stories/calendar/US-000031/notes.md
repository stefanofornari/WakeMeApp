# Notes for calendar/US-000031 — Configure Poll Interval and Window from the Command Line

## Decisions

- `--poll-interval` and `--window` are whole minutes, defaulting to the `CalendarPoller` defaults (5 and 15), so the defaults live in one place.
- The rule "window >= poll interval + lead time" is `CalendarPoller.requireValidWindow`, a static method taking the lead time as an argument; the command passes `NotificationScheduler.DEFAULT_LEAD_TIME`. When the lead time becomes configurable (settings/US-000015) only the caller changes. The poller's constructor does not enforce it.
- The values are checked in `NotificationCommand.call()` on every run, not only with `--events-file`: the defaults always satisfy the rule, so nothing changes without the options, and a combination such as `--poll-interval 11` (window default 15) is rejected too.
- Rejection reuses the existing error path of the command (message on stderr, exit code 1); nothing is polled or scheduled.

## Verification status

`requireValidWindow` was checked with a plain Java program (accepted: 10/15, 2/12, 5/15; rejected: 10/12, 5/9, 11/15). The picocli options, `NotificationCommandTest` and the use of the values in `CLILauncher.pollCalendar` could not be compiled or run here (no picocli or JavaFX). Status stays `WIP` until `mvn test` has run.
