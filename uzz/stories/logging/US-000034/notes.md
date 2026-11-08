# Notes for logging/US-000034..37 — Logging

## Decisions

- JUL reads the configuration itself: the JVM is started with `-Djava.util.logging.config.file=<path to logging.properties>` (property `logging.config` in the pom, used by the surefire `argLine` and the `javafx:run` options; the packaging stories must pass it too, e.g. jpackage `--java-options` with the file next to the application). The file is `src/main/resources/ste/wakemeapp/log/logging.properties`: console handler, `LineFormatter`, levels (root and `ste.wakemeapp` INFO) and the `java.util.logging.FileHandler.*` settings (limit 1 000 000 bytes, count 3, append, UTF-8, formatter). `FileHandler` is deliberately not in `handlers`, or JUL would create its own file in the home directory.
- `ste.wakemeapp.log.LogSetup.configure(folder, debug)` only adjusts at runtime what is not known beforehand: it creates a `FileHandler` on `<folder>/wakemeapp.%g.log` (JUL does not expand variables, nor the platform's application directory, in a configuration file pattern; a handler's pattern cannot be changed after creation) and adds it to the root logger, where it takes its limit, count, formatter and encoding from the JUL configuration; it sets the level of `ste.wakemeapp` (FINE with `--debug`). If the properties are missing it uses the same limit and count and `LineFormatter`, so the log stays capped and one line per message even when the JVM was started without the `-D`. When the folder cannot be used the file handler is not added and the console remains.
- The level of the `ste.wakemeapp` logger is also set in code and a strong reference to it is kept: `LogManager` holds loggers weakly, and a configuration read after a logger was created would otherwise not reach it reliably.
- `LineFormatter`: `2026-11-08 09:12:03 INFO  message`, level names ERROR/WARN/INFO/DEBUG, stack trace on the following lines.
- The folder is `ProjectDirectories.from("", "", "WakeMeApp").dataLocalDir` + `logs` (directories-jvm has no log directory). Qualifier and organisation are empty, so the folder is named after the application on every platform.
- A folder that cannot be created or written is reported on stderr and logging continues on the console only; `configure` returns whether the file is in use.
- `CLILauncher` configures logging right after the arguments are parsed (until then there is nothing to log with), so the `--debug` flag decides the level.
- The stdout messages of `CLILauncher` became log calls: "Showing notification ..." at INFO, the meeting's times and join URL, the raw and processed arguments and the keyboard hints at DEBUG, "Polling ..." and "Scheduled ..." at INFO. Also logged: meeting scheduled/rescheduled/cancelled (poller), snooze chosen (overlay manager), notification dismissed and closed automatically (overlay), a failing handler or poll (existing SEVERE and WARNING calls).
- Not changed: `OverlayLauncher`, a manual development launcher that is not part of the application, still prints with `System.out`.

## Verification status

`LogSetup`, `LineFormatter` and the poller's log messages were run with a plain Java program (no Maven): all the scenarios of `LogSetupTest` and the poller log tests pass. The changes to `CLILauncher`, `NotificationCommand` and the overlay classes need JavaFX and picocli and have not been run. The stories stay `WIP` until `mvn test` and a manual run.
