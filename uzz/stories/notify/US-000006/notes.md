# Notes for notify/US-000006 — Countdown Timer

## Decisions

- The countdown lives in the status pill (no separate label); the old "Meeting in mm:ss" label of notify/US-000009 was removed.
- It is driven by the one-second timeline that also updates the clock, and computed from the system clock (`Duration.between(now, originalStartTime)`), so it does not drift. After a snooze it still counts down to the original start.
- `CountdownState` (PLENTY, SOON, IMMINENT, STARTED) is chosen by `countdownState(Duration)` on the seconds shown (rounded up): so 4:59.5, displayed as 5:00, is still green. The state is applied as a style class to both the pill text and the status dot; the colours are in `overlay.css` (green is the theme's lime, so the calm state matches the rest of the design).
- After the start a separate 500 ms timeline toggles the style class `overlay-flash` (white text over the red state); it stops with the other animations when the overlay goes (`stopAnimations`) and when the state leaves STARTED.
- Not done: a sound or other alert at the thresholds (a different story).

## Verification status

Tests written first in `OverlayControllerTest` (state thresholds) and `OverlayWindowTest` (style classes, flashing, change of colour while open). Not run: JavaFX is not available in the authoring environment. Status stays `WIP` until `mvn test` and a look at the colours have passed.
