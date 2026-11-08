# Notes for logging/US-000034..37 — Logging

## Decisions

- `ste.wakemeapp.log.LogSetup.configure(folder, debug)` builds the JUL configuration in memory (the folder is only known at startup) and hands it to `LogManager.readConfiguration`. Handlers: `FileHandler` (pattern `<folder>/wakemeapp.%g.log`, 1 000 000 bytes, 3 files, append, UTF-8) and `ConsoleHandler` (stderr), both with `LineFormatter` and level ALL; the level is decided by the loggers: root INFO, `ste.wakemeapp` INFO or FINE with `--debug`.
- The level of the `ste.wakemeapp` logger is also set in code and a strong reference to it is kept: `LogManager` holds loggers weakly, and a configuration read after a logger was created would otherwise not reach it reliably.
- `LineFormatter`: `2026-11-08 09:12:03 INFO  message`, level names ERROR/WARN/INFO/DEBUG, stack trace on the following lines.
- The folder is `ProjectDirectories.from("", "", "WakeMeApp").dataLocalDir` + `logs` (directories-jvm has no log directory). Qualifier and organisation are empty, so the folder is named after the application on every platform.
- A folder that cannot be created or written is reported on stderr and logging continues on the console only; `configure` returns whether the file is in use.
- `CLILauncher` configures logging right after the arguments are parsed (until then there is nothing to log with), so the `--debug` flag decides the level.
- The stdout messages of `CLILauncher` became log calls: "Showing notification ..." at INFO, the meeting's times and join URL, the raw and processed arguments and the keyboard hints at DEBUG, "Polling ..." and "Scheduled ..." at INFO. Also logged: meeting scheduled/rescheduled/cancelled (poller), snooze chosen (overlay manager), notification dismissed and closed automatically (overlay), a failing handler or poll (existing SEVERE and WARNING calls).
- Not changed: `OverlayLauncher`, a manual development launcher that is not part of the application, still prints with `System.out`.

## Verification status

`LogSetup`, `LineFormatter` and the poller's log messages were run with a plain Java program (no Maven): all the scenarios of `LogSetupTest` and the poller log tests pass. The changes to `CLILauncher`, `NotificationCommand` and the overlay classes need JavaFX and picocli and have not been run. The stories stay `WIP` until `mvn test` and a manual run.
