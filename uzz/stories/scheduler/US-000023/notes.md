# Notes for scheduler/US-000023 — Notification Scheduler Core

## Decisions

- `NotificationScheduler` is JavaFX-free apart from `onFxThread(...)`: callbacks run on the scheduler's own daemon thread and the caller decides whether to hop to the FX thread. This keeps the timing logic testable without the toolkit and lets the CLI story reuse it.
- `schedule(...)` returns a `ScheduledNotification` handle (trigger time + `cancel()`) rather than an id, because `MeetingNotification` has no identifier yet. Calendar polling (calendar/US-000003) will need one to detect changed or cancelled meetings; add it there.
- A handler exception is logged and swallowed so a failing notification cannot stop later ones.
- The scheduler thread is a daemon: keeping the JVM alive is the job of the background lifecycle story (scheduler/US-000025), not of the scheduler.

## Verification status

The JUnit tests (`NotificationSchedulerTest`, `NotificationSchedulerFxThreadTest`) could not be run in the authoring environment, which had no access to Maven Central. The scheduling logic was checked by compiling it against stubs and running the same scenarios as a plain Java program, all passing. Status stays `WIP` until `mvn test` has been run.
