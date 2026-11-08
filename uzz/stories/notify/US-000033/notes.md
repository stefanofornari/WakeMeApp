# Notes for notify/US-000033 — Show the Full Help on Invalid Command-Line Arguments

## Decisions

- `NotificationCommand.newCommandLine()` builds the `CommandLine` with a parameter-exception handler that prints the message and then the full usage to stderr, and returns picocli's exit code for invalid input (2). `CLILauncher` uses it; tests that build a plain `CommandLine` still get picocli's default handling.
- The checks done in `call()` (dates, poll interval and window, calendar path, missing title/start date) used to print "Error: ..." and return 1. They now throw `ParameterException`, which picocli routes to the same handler, so they behave like parsing errors. This also changes their exit code from 1 to 2.
- Only `IllegalArgumentException` is turned into a parameter error; anything else is a bug and keeps its stack trace.
- `--help` and `--version` are not errors and are untouched.

## Verification status

`InvalidArgumentsTest` could not be compiled or run here (picocli not available). The behaviour relies on picocli routing a `ParameterException` thrown by the business logic to the parameter-exception handler, as its documentation describes; if a test shows it printing a stack trace instead, that is the first thing to check. Status stays `WIP` until `mvn test` has run.
