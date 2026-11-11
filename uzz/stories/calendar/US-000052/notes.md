# Notes: calendar/US-000052

## Decisions
* No library: `Recurrence` covers the rules meetings use and refuses the rest. Refusing (first occurrence only, with a warning naming the rule part) is chosen over a guess that rings at the wrong time.
* Occurrences are worked out on the calendar of the series' zone and then converted to the local zone, so a 10:00 meeting does not move across daylight saving changes. A time that does not exist (the hour skipped in spring) is moved forward by `java.time`.
* Id of an occurrence: `UID#<original start in the zone of the series, yyyy-MM-ddTHH:mm>`. A changed occurrence (`RECURRENCE-ID`) uses the original start in its id, so the poller reschedules it instead of notifying twice. A changed occurrence is read by its own times, so it can move into the window from outside it. Without the series in the feed it stands alone with the same id.
* Open ended series are not walked from the start: the count of periods to skip is computed (unless COUNT needs counting), so a daily meeting of ten years costs nothing.
* Cancelled series are skipped; EXDATE accepts dates and date-times, in lists.
* `RDATE` (extra dates) is not read: Outlook does not write it for meetings.

## To check with a real feed
* The rule forms Outlook writes for monthly and yearly meetings (`BYDAY=2TU`, `BYSETPOS`): a `BYSETPOS` rule is warned about and gives the first occurrence only.
* The Windows zone table is partial: an unknown zone skips the event with a warning naming the zone.
