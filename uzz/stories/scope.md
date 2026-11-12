# Project Name: WakeMeUp
**Version:** 0.0.0

## Overall Description
A JavaFX desktop application that connects to Microsoft Graph to monitor Outlook/Teams
calendars and delivers attention-grabbing meeting notifications via full-screen
overlay with sound. 
The MVP focuses on a simple "notify for all upcoming meetings" approach with 
global lead-time configuration.

## In-Scope Capabilities
* **Microsoft Graph Integration**
  - [ ] Device Code Flow authentication (user logs in via browser on first run)
  - [ ] Token storage/refresh (local file protected by owner-only permissions)
  - [ ] Poll `/me/calendar/events` for upcoming meetings (configurable interval, 
        default 5 min)
  - [ ] Parse meeting details: title, start/end time, join URL, organizer,
        description
* **Notification Engine**
  - [ ] Full-screen overlay window (always-on-top, semi-transparent background)
  - [ ] Countdown timer to meeting start
  - [ ] One-click "Join" button opening Teams meeting URL
  - [ ] Configurable sound notification (bundled WAV + system fallback)
  - [ ] Snooze options: 1 min, 5 min, 10 min, "Until meeting starts"
  - [ ] Dismiss action
* **Scheduling & Background Operation**
  - [ ] Background scheduler checks for meetings within lead-time window
  - [ ] System tray / menu bar icon (macOS) with show/hide window; started without arguments the app lives only in the tray, with an exclamation mark when not configured (settings/scope.md)
  - [ ] Auto-start at login (via platform-specific launcher)
* **Settings UI (JavaFX)**
  - [ ] Microsoft Graph configuration (Tenant ID, Client ID)
  - [ ] Default lead time (minutes before meeting)
  - [ ] Sound selection (bundled sounds + custom file)
  - [ ] Enable/disable notifications
  - [ ] Test notification button
* **Platform Packaging & Integration (macOS MVP)**
  - [ ] macOS: jpackage .dmg + Launch Agent plist + NSStatusItem menu bar integration
  - [ ] macOS: Sleep/wake detection for notification rescheduling
  - [ ] Windows: jpackage .msi + Registry Run key + SystemTray integration
  - [ ] Linux: jpackage .deb/.AppImage + systemd user service / autostart .desktop

## Detailed Scopes
* Calendar synchronisation (polling policy, eligibility, scheduler reconciliation, ICS feed): `calendar/scope.md`
* Logging (levels, rolled log file in the platform directory, `--debug`): `logging/scope.md`
* Configuration (Preferences storage, the settings dialog and how it opens, where the calendar link is kept, `--print-config`, inspection docs): `settings/scope.md`

## Out-of-Scope / Future Enhancements
* Short form for the Outlook calendar (`graph:me`) and shared calendars of other users (`Calendars.Read.Shared`): deferred; `--calendar` takes the full Graph address for now
* Keychain, DPAPI, libsecret or any system password manager for the stored sign-in, and encryption of the token file: deferred; for now the file is protected only by owner-only permissions. The same holds for the stored calendar value (settings/scope.md)
* Rule-based filtering (organizer, keywords, categories, calendar selection)
* Multiple notification rules with different conditions
* Apple Calendar (EventKit) / CalDAV support
* System native notifications (banners) - only full-screen overlay in MVP
* Push notifications via Microsoft Graph change notifications (webhook)
* Meeting categorization / color coding
* History / logs of past notifications
* Notion/Slack/other integrations

## Technical Context & Infrastructure
* **Development Toolchain**: Maven + JavaFX 25 + Java 25
* **Target Systems/Platforms**: macOS (primary), Windows/Linux (future)
* **Tray integration**: FXTrayIcon (https://github.com/dustinkredmond/FXTrayIcon); see settings/scope.md
* **Architecture Decision**: Long-lived background process managed by Launch Agent (macOS) / Registry Run (Windows) / systemd user service (Linux). NOT launchd periodic scheduling — the JVM stays running to maintain in-memory state (snoozes, pending notifications) and avoid startup latency.
* **Project Reference Docs**:
  - Coding Standard: `../project/coding_standard.md`
  - Development Framework: `../project/development_framework.md`