# Notes — settings/US-000058

* Package `ste.wakemeapp.about`: `AppInfo` (name, description, project link, licence, and the version), `AboutDialog` + `AboutController` + `about.fxml`/`about.css` (same flat dark look as the settings dialog, styled FXML). The dialog is not modal and has a Close button (also Escape); it needs no settings.
* The version is the Maven project version: `about/app.properties` holds `version=${project.version}` and is the only resource the pom filters (the other resources are copied untouched, so no `${...}` or `@...@` in them is ever replaced). If the file is missing, empty or still has the placeholder (an IDE that skips Maven's filtering), the dialog shows "unknown".
* The project link is a hyperlink that opens the browser through `getHostServices().showDocument`; the opener is injected, so the test sees the call without a browser.
* Tray menu order: "Settings…", "About WakeMeApp", "Quit". The menu action runs on the tray library's thread and hands over to the FX thread, as "Settings…" does.
* Not verified here: the dialog (TestFX), the tray item and the pom filtering need `mvn test` and a look on a desktop; `AppInfo` was run.
