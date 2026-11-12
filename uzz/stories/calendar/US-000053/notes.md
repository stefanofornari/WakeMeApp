# Notes: calendar/US-000053

## Where the address could have leaked, and what was done
* Messages and errors of the source: made with the host only from the start (US-000051); no exception cause is kept, since a network error message may carry the address. `IcsFeedSecretTest` polls through `CalendarPoller` in five situations (works, 404, 503, not a calendar, network failure) and checks everything logged at every level, stack traces included.
* Debug log of the arguments (`Raw args`, `Processed args`) printed the address: `CLILauncher.redact` now cuts the value of `--calendar` to `https://host/…`, in the two forms (`--calendar x`, `--calendar=x`), quoted or not, `webcal` shown as `https`.
* Rejected links (`http://`) and invalid addresses: the message names the option and says why, without the address.
* Not saved: the app never writes the address (settings, `dismissed.json`). `--print-config` shows settings only, so it cannot show it.

## Not covered
* The address stays in the shell history and in the process list of the computer while the app runs: the README says so. Reading it from a file or the settings is a possible future story (out of scope for now).
* The meeting links found in events (Teams and others) are shown to the user on purpose and logged only at debug level.
