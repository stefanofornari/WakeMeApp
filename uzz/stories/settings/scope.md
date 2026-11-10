# Project Name: WakeMeApp — Configuration (settings domain)
**Version:** 0.1.0

## Overall Description
How the application gets, keeps and shows its configuration.

* **Where settings live**: `java.util.prefs.Preferences`, user root, node `/ste/wakemeapp`. No properties file for now.
* **Precedence**: command line option, then stored preference, then built-in default. A command line option applies to that run only and is never saved: it overrules what is stored.
* **First run**: the Microsoft client ID and tenant (neither is a secret) are stored by the settings UI (settings/US-000014) and can be overruled for a run with `--client-id` and `--tenant`. Until the UI exists they must be passed on every run; there is no command to store them.
* **Not in Preferences**: the sign-in token cache is a file in the application data directory, owner-only (see auth/US-000002); it is too large and too sensitive for Preferences.
* **Inspecting values**: the stored values must be inspectable on every platform: with `--print-config` (same everywhere) and with the native tools, which are documented (macOS: `defaults read com.apple.java.util.prefs`; Windows: `reg query HKCU\Software\JavaSoft\Prefs\ste\wakemeapp`; Linux: XML under `~/.java/.userPrefs/ste/wakemeapp/`). The macOS location and command are to be verified on a Mac while writing the documentation.
* **Tests** use an injected Preferences node, never the real user root.

## In-Scope Capabilities
* **Storage**:
  - [ ] Typed settings read/write on the node `/ste/wakemeapp` with defaults, flushed after every write
  - [ ] Precedence command line > stored > default, applied to every setting (settings/US-000045, US-000046)
* **Overrides**:
  - [ ] `--client-id` and `--tenant` overrule the stored values for one run and are never saved
* **Inspection**:
  - [ ] `--print-config` prints every effective setting and its source (command line, stored, default)
  - [ ] Documentation of how to inspect stored values with the native tools of macOS, Windows and Linux (settings/US-000044)

## Out-of-Scope / Future Enhancements
* Properties/config file for hand-editing or IT deployment: deferred; Preferences is not hand-editable, `--print-config` and the native tools cover inspection
* First-run settings dialog: deferred to the settings UI stories (settings/US-000014 to US-000018)
* Encryption of settings or tokens, and system password managers (Keychain, DPAPI, libsecret): deferred, see the main scope
* Built-in client ID shipped with the application: deferred, needs a decision on a shared app registration
* Migrating or exporting settings between machines

## Technical Context & Infrastructure
* **Development Toolchain**: Maven + JavaFX 25 + Java 25; `java.prefs` module only, no new dependency
* **Target Systems/Platforms**: macOS (primary), Windows/Linux (future)
* **Project Reference Docs**:
  - Coding Standard: `../../project/coding_standard.md`
  - Development Framework: `../../project/development_framework.md`
