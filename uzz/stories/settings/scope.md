# Project Name: WakeMeApp — Configuration (settings domain)
**Version:** 0.1.0

## Overall Description
How the application gets, keeps and shows its configuration.

* **Where settings live**: `java.util.prefs.Preferences`, user root, node `/ste/wakemeapp`. No properties file for now.
* **Precedence**: command line option, then stored preference, then built-in default. A command line option applies to that run only and is never saved: it overrules what is stored.
* **First run**: the tray icon shows an exclamation mark until a calendar is configured (see Running without arguments). The Microsoft client ID and tenant (neither is a secret) are stored by the dialog (settings/US-000014) and can be overruled for a run with `--client-id` and `--tenant`. Until the UI exists they must be passed on every run; there is no command to store them.
* **Not in Preferences**: the sign-in token cache is a file in the application data directory, owner-only (see auth/US-000002); it is too large and too sensitive for Preferences.
* **Inspecting values**: the stored values must be inspectable on every platform: with `--print-config` (same everywhere) and with the native tools, which are documented (macOS: `defaults read com.apple.java.util.prefs`; Windows: `reg query HKCU\Software\JavaSoft\Prefs\ste\wakemeapp`; Linux: XML under `~/.java/.userPrefs/ste/wakemeapp/`). The macOS location and command are to be verified on a Mac while writing the documentation.
* **Tests** use an injected Preferences node, never the real user root.
* **Running without arguments**: the application starts in the tray / menu bar and shows no window. If a calendar is configured it polls it. If not (no stored calendar and no `--calendar`), the tray icon carries an exclamation mark, its tooltip says that no calendar is configured, and nothing is polled; the dialog does not open by itself. The mark goes away as soon as a calendar is saved, without a restart. Arguments still work as before: `--calendar` for one run, `--print-config` prints and exits.
* **The settings dialog** (JavaFX) is where a user sets everything without the command line. It opens: (1) from the tray / menu bar item "Settings…"; (2) at launch with `--settings`. Only one dialog at a time: opening it again brings the open one to the front. Saving applies without a restart; closing without saving changes nothing. The tray item is in scope here because nothing else opens the dialog once the application runs.
* **The calendar setting** is one text field, whatever the kind. The kind is detected from the value exactly as `--calendar` does it (ICS link, Microsoft Graph address, CSV file path, in this order; calendar/scope.md), and the dialog shows which kind it recognised. Validation per kind: an ICS link must be `https://` or `webcal://` (never plain `http://`); a Graph address must be a graph.microsoft.com address; a CSV path must be an existing readable file. A value that fits none is refused with a message naming the three accepted forms, not echoing a link.
* **Microsoft account** (client ID, tenant; settings/US-000014) is a separate section of the dialog. It is needed only when the calendar is a Graph address and the dialog says so; for an ICS link or a CSV file it is optional and ignored.
* **Where the calendar value is kept**: in an owner-only file in the application data directory (same place and same rules as the sign-in token cache, auth/US-000002), not in Preferences, because an ICS link is a credential and Preferences is plain, easy to read and included in backups. All three kinds go in the same file, so the user has one place to look. Decision: no encryption and no passphrase ("seed"). The file is protected by its owner-only permissions only, which does not protect against someone who can read the user's files or a backup; the README says so and says how to revoke an ICS link. Asking the user for a passphrase was rejected because it would have to be typed at every start and breaks launch at login; a key the app derives or stores by itself would only look like protection.
* **Showing the calendar value**: the dialog, `--print-config`, logs and messages show an ICS link as `https://host/…` (calendar/US-000053). The dialog field is masked after saving; editing it means typing the value again, there is no "reveal". A Graph address and a CSV path are shown as they are.
* **Precedence** for the calendar: `--calendar` (this run only, never saved), then the stored value, then none. With none, the application does not poll and opens the dialog.

## In-Scope Capabilities
* **Storage**:
  - [ ] Typed settings read/write on the node `/ste/wakemeapp` with defaults, flushed after every write
  - [ ] Precedence command line > stored > default, applied to every setting (settings/US-000045, US-000046)
* **Settings dialog**:
  - [ ] Started without arguments the application lives in the tray / menu bar; with no calendar configured the tray icon shows an exclamation mark and a tooltip saying so, and clears when a calendar is saved
  - [ ] Tray / menu bar item "Settings…" opens the dialog; `--settings` opens it at launch (settings/US-000014 and following)
  - [ ] One dialog at a time; Save applies without a restart; Cancel changes nothing
  - [ ] One calendar field: the kind (ICS link, Graph address, CSV file) is detected as for `--calendar`, validated per kind, and shown to the user
  - [ ] The stored calendar value is kept in an owner-only file, never in Preferences, never logged; an ICS link is shown as host only and masked after saving
  - [ ] Microsoft account section (client ID, tenant) with validation, used only for a Graph address
  - [ ] Lead time, sound, enable/disable and test notification, as in settings/US-000015 to US-000018
* **Overrides**:
  - [ ] `--client-id` and `--tenant` overrule the stored values for one run and are never saved
* **Inspection**:
  - [ ] `--print-config` prints every effective setting and its source (command line, stored, default), the calendar included with an ICS link shown as host only
  - [ ] Documentation of how to inspect stored values with the native tools of macOS, Windows and Linux (settings/US-000044)

## Out-of-Scope / Future Enhancements
* Properties/config file for hand-editing or IT deployment: deferred; Preferences is not hand-editable, `--print-config` and the native tools cover inspection
* Encryption of the stored calendar value or tokens, a user passphrase, and system password managers (Keychain, DPAPI, libsecret): deferred; revisit if the owner-only file is judged not enough (macOS Keychain would be the first candidate)
* Revealing or copying the saved ICS link from the dialog
* More than one calendar at the same time: separate scope (dedup, per-source errors)
* Testing the calendar from the dialog (download and report how many meetings were found): nice to have, not in this cycle
* Import and export of settings
* Built-in client ID shipped with the application: deferred, needs a decision on a shared app registration
* Migrating or exporting settings between machines

## Technical Context & Infrastructure
* **Development Toolchain**: Maven + JavaFX 25 + Java 25; `java.prefs` module only for the settings storage
* **Tray integration (technical note)**: use FXTrayIcon (https://github.com/dustinkredmond/FXTrayIcon) for the tray / menu bar icon, its menu ("Settings…", quit) and the exclamation-mark variant of the icon. It is the one new dependency of this domain; the choice and its behaviour on macOS (where the app must not show a Dock icon) are to be verified on a Mac when the tray story is implemented.
* **Target Systems/Platforms**: macOS (primary), Windows/Linux (future)
* **Project Reference Docs**:
  - Coding Standard: `../../project/coding_standard.md`
  - Development Framework: `../../project/development_framework.md`
