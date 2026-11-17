# Notes — calendar/US-000003

1. `ste.wakemeapp.calendar.GraphCalendarSource` implements `CalendarSource`. It takes an `HttpClient.Builder` (tests give an xtest `HttpClientStubber`, see `uzz/library/HOWTO StubHttpClient.md`) and a `Supplier<String>` for the access token, asked at every query. Authentication is not part of this story.
2. It uses the **calendar view** (`/me/calendarView?startDateTime&endDateTime`), not `/me/calendar/events` with `$filter`: only the view expands the occurrences of recurring meetings, which is most stand-ups. The view also returns events that started earlier and are still running, so the source keeps those whose start is in `[from, to)`.
3. Requests carry `Prefer: outlook.timezone="UTC"`; the window is sent in UTC and the answers are converted to the local zone (the zone is injectable for tests).
4. Cancelled meetings (`isCancelled`) are skipped: for the user they are gone, so the poller cancels their notification. Nothing else is filtered; eligibility stays in `CalendarEvent.isEligible()`.
5. Response mapping: organizer, accepted, tentativelyAccepted, declined; `none`, `notResponded` and anything else is NOT_RESPONDED. Join URL from `onlineMeeting.joinUrl`, else `onlineMeetingUrl`; organizer is the name, else the address; description is `bodyPreview`.
6. 429: wait `Retry-After` (1 s if missing), doubling at every further 429, at most 5 attempts; a wait above one minute is not taken: the call throws and the poller tries again at its next poll. Other non-2xx answers, a network error, an unreadable answer or an event without id or start throw `CalendarAccessException` (an `IOException` that tells whether the failure is temporary, see logging/US-000039), with no partial result (a malformed single event fails the whole poll, as for the CSV before US-000038).
7. JSON is read with JSON-java from json.org (new dependency, `org.json:json` 20250107); xtest 3.14.0 is a new test dependency.
8. Not wired to the command line yet: that needs the configuration (settings/US-000014) and the authentication.

Verified with a plain Java program using minimal stand-ins for xtest's HTTP stubs and for org.json (Maven Central is not reachable from the authoring environment): the 23 tests of `GraphCalendarSourceTest` pass. To be run with `mvn test`; the story stays WIP until then.
