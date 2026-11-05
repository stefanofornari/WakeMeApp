# Notes for scheduler/US-000024 — Schedule a Notification from the Command Line

## Decisions

- The CLI process stays alive and schedules in its own JVM (confirmed with the user). Handing the meeting to a running background instance is a separate, later story.
- `CLILauncher` decides in one place: `--now` shows the overlay straight away; otherwise it hands the meeting to `NotificationScheduler`, whose callback is `showOverlay` on the JavaFX thread. The wait-until-dismissed-then-exit logic stays inside `showOverlay`, so it only starts once the overlay has actually appeared.
- The shutdown hook is registered before scheduling (it used to be registered in `showOverlay`), so Ctrl+C before the notification fires also closes the scheduler.
- The message printed after scheduling lives in a package-private static method (`scheduleMessage`) so it can be unit tested without starting JavaFX. Partial minutes round up.
- A start date inside the lead time (or in the past) is shown immediately: this is the scheduler's behaviour for a trigger time that has already passed (scheduler/US-000023).

## Effects on other stories

- notify/US-000022 showed the overlay immediately. That is now the behaviour with `--now`; without it the notification is scheduled. US-000022 was left untouched and its scenarios need `--now` to hold.

## Verification status

`NotificationCommandTest` (the `--now`/`-n` flag) and `CLILauncherTest` (`scheduleMessage`) were added but could not be run in the authoring environment (no access to Maven Central); the message logic was checked by running the same method and inputs as a plain Java program. The end-to-end flow (schedule, wait, fire, dismiss, exit 0, Ctrl+C) needs a JavaFX environment and has not been run. Status stays `WIP` until it has.
