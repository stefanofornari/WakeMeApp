# Notes for calendar/US-000029 — Notify Only for Relevant Meetings

## Decisions

- The rule is `CalendarEvent.isEligible()`; `CalendarPoller` applies it to what the source returns, so every source (CSV, in-memory, Graph later) is filtered the same way and sources stay unfiltered.
- A `null` response status is not handled: sources must provide one (the CSV source defaults an empty status to accepted).
- Meetings with no join link are not filtered out: not part of this story.
- A meeting that stops being eligible after it was scheduled (accepted, then declined) is not cancelled here: that is scheduler/US-000030.

## Verification status

Tests run with Maven and passing; behaviour confirmed by a manual run (2026-11-08).
