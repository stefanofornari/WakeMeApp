# Notes for logging/US-000034..37 — Logging

## Decisions

- JUL reads the configuration itself: the JVM is started with `-Djava.util.logging.config.file=<path to logging.properties>` (property `logging.config` in the pom, used by the surefire `argLine` and the `javafx:run` options; the packaging stories must pass it too, e.g. jpackage `--java-options` with the file next to the application). The file is `src/main/resources/ste/wakemeapp/log/logging.properties`: console handler, `LineFormatter`, levels (root and `ste.wakemeapp` INFO) and the settings of LazyJava's `ste.lazyjava.logging.FileLogHandler` (application `WakeMeApp`, pattern `${dataDir}/logs/wakemeapp.%g.log`, limit 1 000 000 bytes, count 3, append, UTF-8, `LineFormatter`).
- `ste.wakemeapp.log.LogSetup.configure(debug)` only sets the level of `ste.wakemeapp` (FINE with `--debug`); handlers, formatter, file location and cap are all in `logging.properties`. The file handler is `FileLogHandler` from `lazyjava-core` (US-000010 to US-000016 of lazy-java), which expands `${dataDir}` through directories-jvm before the file is opened and creates the folder; it replaced the in-app `AppFileHandler` spike. If the file cannot be created, JUL reports it and the console handler keeps working; an unresolved placeholder is left in the path and reported with a warning.
- The level of the `ste.wakemeapp` logger is also set in code and a strong reference to it is kept: `LogManager` holds loggers weakly, and a configuration read after a logger was created would otherwise not reach it reliably.
- `LineFormatter`: `2026-11-08 09:12:03 INFO  message`, level names ERROR/WARN/INFO/DEBUG, stack trace on the following lines.
- The folder is `${dataDir}/logs` for the application `WakeMeApp` (directories-jvm `ProjectDirectories.from("", "", "WakeMeApp").dataLocalDir`, no log directory exists there). Qualifier and organisation are empty, so the folder is named after the application on every platform.
- A folder that cannot be created or written is reported on stderr and logging continues on the console only; `configure` returns whether the file is in use.
- `CLILauncher` configures logging right after the arguments are parsed (until then there is nothing to log with), so the `--debug` flag decides the level.
- The stdout messages of `CLILauncher` became log calls: "Showing notification ..." at INFO, the meeting's times and join URL, the raw and processed arguments and the keyboard hints at DEBUG, "Polling ..." and "Scheduled ..." at INFO. Also logged: meeting scheduled/rescheduled/cancelled (poller), snooze chosen (overlay manager), notification dismissed and closed automatically (overlay), a failing handler or poll (existing SEVERE and WARNING calls).
- Not changed: `OverlayLauncher`, a manual development launcher that is not part of the application, still prints with `System.out`.

## Verification status

`LogSetup`, `LineFormatter` and the poller's log messages were run with a plain Java program (no Maven), the `LogSetupTest` scenarios with the real `FileLogHandler` of lazy-java: they pass. The changes to `CLILauncher`, `NotificationCommand` and the overlay classes need JavaFX and picocli and have not been run. The stories stay `WIP` until `mvn test` and a manual run.

- The pom has `lazyjava.version` set to the placeholder `SET-ME`: put the first `lazyjava-core` release that contains `FileLogHandler`.
