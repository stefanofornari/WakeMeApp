# Notes: calendar/US-000051

## Decisions
* The iCalendar reader is written here (about 150 lines: unfolding, parameters, escapes, VEVENT). ical4j could not be fetched or its API checked from the build machine, and plain events need little; US-000052 decides whether recurrence needs the library.
* `--calendar` tells the three sources from the value: `IcsAddress.isFeed` is checked first (webcal, or http(s) on any host except graph.microsoft.com), then Graph, then a file. A wrong host is therefore a feed, not an error: the test of `InvalidArgumentsTest` for "another host" now uses an http graph address.
* The feed does not say which attendee is the user, so the story's "log the user's status" became "log the statuses the feed has, without names": enough to learn whether switching to the stricter rule is possible.
* All-day events are returned flagged, as Graph does, and the eligibility filter drops them.
* `WindowsZones` covers the common Windows zone names only, since Outlook writes them in `TZID`; an unknown zone skips the event with a warning.
* Messages contain the host only and no cause of the original exception, since a network error message may contain the address. The rest of the secrecy is calendar/US-000053.
* Redirects are followed by the client built in `CLILauncher` (not in the source), so the source can be tested with the stub.

## To check with a real feed
* Whether the published calendar lists the meetings the user was invited to or only the ones organised.
* Which time zone names the feed uses, and whether the Teams link is in `X-MICROSOFT-SKYPETEAMSMEETINGURL`, the location or the description.
* What `--debug` says about the attendee status.
