# Notes — settings/US-000054

* `Tray` holds the state logic (alert mark, tooltip, "not available" log) behind the small `TrayView`; `FxTrayView` is the FXTrayIcon implementation and is not unit-tested, since it needs a real desktop. Checked by hand on macOS: started without arguments the process stays alive and logs "Running in the tray". The look of the icon and the menu are to be confirmed by eye.
* "Started without arguments" is `NotificationCommand.isTrayOnly()`: no calendar and no single-meeting option (`--title`, `--start-date`, `--end-date`, `--organizer`, `--url`, `--description`, `--now`); options like `--debug` still start the tray. `--title` alone remains an error.
* "Configured" is today `--calendar` only. The scenario "the mark clears when a calendar is saved" is covered at `Tray.setConfigured(true)`; settings/US-000055 and US-000057 must call it when they save, and start polling there (CLILauncher keeps the `tray` field for that). Polling start on save is therefore not wired yet.
* Not verified: no Dock icon on macOS (would need `-Dapple.awt.UIElement=true` before AWT starts) and Linux desktops without tray support.
* The icons are generated 64×64 PNGs (alarm clock, white, for the dark menu bar; the alert variant has a red badge with "!"), `src/main/resources/ste/wakemeapp/tray/`.
* `javafx-swing` is declared explicitly: FXTrayIcon brings version 19 transitively, the project uses `${javafx.version}`.
