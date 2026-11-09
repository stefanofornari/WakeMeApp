# Project Name: WakeMeUp — Logging
**Version:** 0.1.0

## Overall Description
A single, consistent way to tell what the application does and what goes wrong, replacing the
messages written with `System.out`/`System.err`. Everything goes through `java.util.logging`
(no logging framework) into a log file in the platform's application directory. The file is
rolled and capped, so it never grows indefinitely.

Levels (JUL name in brackets) mean the same everywhere:
* **ERROR** (`SEVERE`): only what the user MUST be aware of
* **WARN** (`WARNING`): something the user can understand and should care about, e.g. an event discarded because it could not be parsed
* **INFO** (`INFO`): the main flows, mostly the happy path, worded so that a user understands them (e.g. polling started, a meeting scheduled, a notification shown)
* **DEBUG** (`FINE`): detail for whoever investigates a problem (e.g. the raw command-line arguments, what each poll returned, the overlay being shown); off unless asked

## In-Scope Capabilities
* **Log policy**:
  - [ ] The levels above are documented in the development framework, with the rule that messages are written for the user, not for the developer, at INFO and above
  - [ ] Existing output of the application is reviewed and moved to the right level (e.g. "Polling ..." at INFO, "Showing overlay for ..." at DEBUG); existing log calls are reviewed against the same rules
* **Log file**:
  - [ ] The file lives in a `logs` folder inside the application data directory of the operating system (macOS, Windows, Linux), found with the directories library, not hand-written paths
  - [ ] The directory is created when missing; if it cannot be used the application still starts and logs to the console instead
  - [ ] Logging is set up through the JUL configuration (handler, pattern, limit, count, formatter, levels), applied once at startup
  - [ ] The file is rolled with a cap: a maximum size per file and a maximum number of files, so the total size is bounded
  - [ ] One line per message, with timestamp, level and message; a stack trace follows when an exception is logged
* **Console**:
  - [ ] The same messages, with the same format and level, are also written to the console
* **Level selection**:
  - [ ] No argument: level INFO
  - [ ] `--debug`: level DEBUG (`FINE`); it is the only logging option

## Out-of-Scope / Future Enhancements
* Alert dialog for ERROR messages (to be scoped later; ERROR is already reserved for what the user must know)
* Settings UI, per-class levels, or configuration file for logging
* Log viewer, log upload or any remote logging
* Parsing of the log by other tools (no structured/JSON format)
* Output required by the command line itself: `--help`, `--version` and invalid-argument messages (notify/US-000033) stay on stdout/stderr

## Technical Context & Infrastructure
* **Development Toolchain**: Maven + JavaFX 25 + Java 25
* **Target Systems/Platforms**: macOS (primary), Windows/Linux (future)
* **Logging**: `java.util.logging` only (see the development framework)
* **File handler**: `ste.lazyjava.logging.FileLogHandler` from `lazyjava-core` ([lazy-java](https://github.com/stefanofornari/lazy-java)), configured only in `logging.properties`
* **Platform directories**: [directories-jvm](https://codeberg.org/dirs/directories-jvm) `dev.dirs:directories` 26 (MPL-2.0, no dependencies listed), also the library to use for any other system folder. It has no log directory: the log folder is `logs` under `ProjectDirectories.dataLocalDir` (machine-local, not roaming on Windows)
* **Project Reference Docs**:
  - Coding Standard: `../../project/coding_standard.md`
  - Development Framework: `../../project/development_framework.md`
