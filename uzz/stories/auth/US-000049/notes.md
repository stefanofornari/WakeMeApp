# Notes: auth/US-000049

## What is tested and what is not
The window logic (the code in a read-only field, the message, "Copy code", "Open in the browser", close on completion, one window reused for a new code, closing by hand not cancelling the sign-in) is tested with a fake page. The real `WebViewPage` is a six line wrapper around `WebView`; headless tests would only test JavaFX.

## Spike still to do, with a real account
Run the application with a real client id and a Graph address (`--calendar https://graph.microsoft.com/v1.0/me/calendarView`) and check in the window that:
1. the Microsoft sign-in page renders and accepts the code in a JavaFX WebView;
2. the page does not refuse to run in an embedded browser (some tenants do);
3. after the sign-in the window closes by itself.
If it does not work, the "Open in the browser" button is the fallback, and the story becomes "open the system browser" with the WebView dropped. Write the outcome here.

## Decisions
* The WebView is created with the default user data folder: no cookies are kept between runs.
* `VerificationPresenter.completed()` (default: does nothing) tells the presenter that the sign-in ended, successfully or not, so the window also closes after a failed sign-in and after the sign-in that auth/US-000042 starts again.
* `javafx-web` is added to the pom with the other JavaFX modules (same version).
