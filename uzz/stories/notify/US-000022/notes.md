# Notes for notify/US-000022 — Command-Line Trigger for Meeting Notification

## Implementation Summary

Implemented command-line trigger for meeting notifications using picocli 4.7.6.

## Files Created

1. **src/main/java/ste/wakemeapp/cli/NotificationCommand.java** - picocli command definition with all options
2. **src/main/java/ste/wakemeapp/cli/CLILauncher.java** - Main entry point that parses args and triggers notification
3. **src/test/java/ste/wakemeapp/cli/NotificationCommandTest.java** - Unit tests for argument parsing

## Technical Implementation

### NotificationCommand (picocli)
- Annotated with `@Command(name = "notify", description = "Trigger a meeting notification")`
- Required options:
  - `@Option(names = {"-t", "--title"}, description = "Meeting title", required = true)`
  - `@Option(names = {"-s", "--start-date"}, description = "Meeting start date/time (ISO-8601)", required = true)`
- Optional options:
  - `@Option(names = {"-e", "--end-date"}, description = "Meeting end date/time (ISO-8601)")`
  - `@Option(names = {"-o", "--organizer"}, description = "Meeting organizer")`
  - `@Option(names = {"-u", "--url"}, description = "Join URL (Teams, Zoom, etc.)")`
  - `@Option(names = {"-d", "--description"}, description = "Meeting description")`
- Implements `Callable<Integer>` to return exit code

### CLILauncher
- Extends `Application` for JavaFX integration
- Parses command line using `new CommandLine(new NotificationCommand())`
- On successful parse, creates `MeetingNotification` record
- Triggers overlay via `OverlayManager.showNotification(notification)` on JavaFX thread
- Handles validation errors gracefully with clear error messages
- Waits for overlays to be dismissed before exiting

## Dependencies Added
- picocli 4.7.6 (added to pom.xml)

## Acceptance Criteria Verification

| Scenario | Test Coverage | Manual Verification |
|----------|---------------|---------------------|
| Required args only | `NotificationCommandTest.should_parse_required_args` | ✅ Verified |
| All optional args | `NotificationCommandTest.should_parse_all_optional_args` | ✅ Verified |
| Missing --title | `NotificationCommandTest.should_fail_on_missing_title` | ✅ Verified |
| Missing --start-date | `NotificationCommandTest.should_fail_on_missing_start_date` | ✅ Verified |
| Invalid date format | `NotificationCommandTest.should_fail_on_invalid_date_format` | ✅ Verified |
| Help flag | `NotificationCommandTest.should_show_help` | ✅ Verified |
| Short options | `NotificationCommandTest.should_parse_short_options` | N/A |

## Test Results
- All 23 tests pass (13 OverlayWindowTest + 3 OverlayManagerTest + 7 NotificationCommandTest)

## Future Enhancements
- Config file support (--config)
- Multiple notifications in one command
- Dry-run mode (--dry-run)