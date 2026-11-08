# Notes for calendar/US-000032 — Simulate the Calendar from a CSV File

## Decisions

- `CsvCalendarSource` reads and parses the whole file on every query: files are small and it is what makes edits visible to the next poll. All rows are parsed, in or out of the window, so a malformed line is reported whichever meeting it describes.
- `id`, `title` and `start` are required (the id is what scheduler/US-000030 will use to recognise a moved meeting). Optional columns may be missing from the header or empty in a row.
- Quoted fields are single-line: a field cannot contain a line break.
- Errors name the file and the 1-based line number counted over the whole file, comments and blank lines included, so it matches what an editor shows.
- `--title` and `--start-date` are no longer picocli `required`: picocli cannot express "required unless `--events-file`", so the check moved to `NotificationCommand.call()`, which exits with 1 and an error message as before.
- `CLILauncher.pollCalendar` reuses the shutdown hook and scheduler of scheduler/US-000024. In calendar mode the process never exits by itself (the wait-until-dismissed loop is skipped) and the pending counter is not used.
- Poll interval and window are the defaults (5 and 15 minutes); with those a change to the file may take up to 5 minutes to be seen. calendar/US-000031 makes them configurable.

- A missing events file is an empty calendar, not an error (changed after the first manual test): the source warns once per "missing episode" and the poller picks the file up at its next poll, so with the default interval that is up to 5 minutes after it appears. Only a directory is rejected at startup.

## Verification status

`CsvCalendarSource` was checked with a plain Java program running the scenarios of `CsvCalendarSourceTest`: all passed. `NotificationCommandTest` (new `--events-file` cases) and the `CLILauncher` wiring could not be compiled or run here (no picocli or JavaFX available). The end-to-end flow (start on a file, wait, overlay, keep running, Ctrl+C) needs a JavaFX environment. Status stays `WIP` until `mvn test` and a manual run have passed.
