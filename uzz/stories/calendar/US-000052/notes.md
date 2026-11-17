# Notes: calendar/US-000052

## Decisions
* ical4j 4.4.0 expands the rules (`calculateRecurrenceSet`), applies EXDATE and RDATE and knows the Windows zone names. A first version with a hand-written expansion was replaced by it.
* Id of an occurrence: `UID#<original start in the zone of the series, yyyy-MM-ddTHH:mm>`. A changed occurrence (`RECURRENCE-ID`) is read by its own times, so it can move into the window from outside it, and keeps the id of the original. Without the series in the feed it stands alone with the same id. The changes of an occurrence are handled here, not by ical4j's `ComponentGroup`, which only keeps a replacement that starts at the original time.
* Each event is parsed on its own (`CalendarBuilder` on a one event calendar): a calendar with an unknown time zone fails as a whole in ical4j, and one bad event must not hide the others. About 2000 events take 0.2 s.
* The `VTIMEZONE` blocks of the feed are left out. Every one parsed makes ical4j register new zones in the Java runtime, which can never be removed: one poll every 5 minutes would add thousands a week (measured: about 6 per poll). Outlook writes names ical4j already resolves. A zone defined only in the feed (a custom name) is not understood: that event is skipped with a warning naming the zone.
* `src/main/resources/tz.alias` corrects the names where ical4j's table is not the Windows zone: "Pacific Standard Time" is Canada/Pacific there, and British Columbia stopped changing the clocks in 2026, so every US Pacific meeting would be an hour off in winter. Same for Central and GMT.
* ical4j is built with SLF4J 2 and the project keeps the 1.7 bridge: ical4j only uses the plain logging calls (checked in its source), so the 1.7 API is enough. `slf4j-api` is declared explicitly so Maven does not pick the 2.x.

## How it was checked
The library was built from its 4.4.0 source (Maven Central cannot be reached from the build machine, so without its optional modules) and the tests run against it. The first `mvn test` with the real jar is the proof that the dependency resolves.

## To check with a real feed
* Which `RRULE` forms Outlook writes, and the zone names in the feed (`--debug` shows what is skipped).
* `RDATE` is read by ical4j; Outlook does not write it for meetings.
