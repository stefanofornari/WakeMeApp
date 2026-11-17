# Notes — settings/US-000056

* Styled FXML like the overlay: `settings.fxml`, `settings.css` (same dark card, lime accent) and `SettingsController` under `ste.wakemeapp.settings`; `SettingsDialog` loads them and owns the `Stage`. Inter is registered from the overlay's bundled fonts, the system font is the fallback.
* One dialog: `show()` opens it with what is saved, or only brings the open one to the front (edits kept); closing, Cancel and Escape hide it and the next `show()` reloads what is saved. Modality NONE, not always-on-top, so the overlay (always on top) still covers it.
* `--settings` is a picocli flag; it is not a meeting option, so `isTrayOnly()` stays true with it. With `--print-config` the print wins and the application exits.
* `Tray.create(stage, onSettings, onQuit)`: the "Settings…" item is enabled and runs `onSettings` on the FX thread. `CLILauncher.showSettings()` creates the dialog lazily.
* Not verified here: nothing that needs JavaFX or TestFX could be compiled or run on the machine used for the work (`SettingsDialogTest`, `NotificationCommandTest`, `CLILauncher`); only `mvn test` and a look on a desktop verify them. The tray item and the look of the dialog are to be checked by eye.

* The window has no decoration (`StageStyle.UNDECORATED`), is not resizable and is moved by dragging its background or labels (`ui/WindowDrag`; text fields, sliders and lists keep their own drags). A 1 px border in the CSS keeps its edge visible on a dark desktop. It closes with Cancel/Close or Escape. Not verified here (needs `mvn test` and a look): `WindowDragTest`, the dialog tests.
