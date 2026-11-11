# Notes: notify/US-000007

## Coverage of the scenarios
* Opens the link / deep link: `JoinMeetingTest` (only http, https and msteams are handed to the system; anything else is refused).
* Clipboard fallback with toast, overlay stays open: `OverlayWindowTest`.
* Button hidden for a meeting without a link or with a link that is not a meeting link: `OverlayWindowTest`.
* Hover and pressed states: in `overlay.css`; not testable headless, to be checked by eye.

## Decisions
* Closing after a successful join is `OverlayController.setCloseOnJoin`, on by default; no settings UI for it yet.
* Opening the system browser is what `Desktop.browse` does: which browser is the platform default.
