# WakeMeApp

WakeMeApp reads your Outlook calendar and, a few minutes before a meeting starts, covers the screen with a
card you cannot miss: the title, the time, a countdown and a **Join Meeting** button. You can snooze it or
dismiss it. It is for people who miss meetings because a small notification went by unseen.

> **Status**: work in progress. It runs from the command line; the menu bar icon, the settings window, the
> sound and the installers are not there yet. The sign-in has been tested against a simulated Microsoft
> server, not yet with a real account on every platform.

## What you can do

* **See a meeting coming**: the reminder opens 5 minutes before the start, on every screen, always on top.
  It shows the title, the time, how long the meeting lasts, who organises it and how long is left before the
  reminder closes by itself (90 seconds).
* **Join in one click**: *Join Meeting* opens the Teams (or other online meeting) link. If the browser cannot
  be opened, the link is copied to the clipboard and a message tells you so.
* **Snooze**: 1 minute, 5 minutes, 10 minutes or until the meeting starts, up to 3 times per meeting.
* **Dismiss**: *Dismiss* closes the reminder for good for that occurrence of the meeting. A small
  "Dismissed. Undo?" message stays for 5 seconds and brings the reminder back if you click *Undo*. A dismissed
  meeting stays dismissed after a restart; the next occurrence of a recurring meeting is notified as usual,
  and so is a meeting that was moved.
* **Close without deciding**: *Esc*, a click on the background, or waiting 90 seconds closes the reminder
  without dismissing the meeting (a snoozed meeting still comes back).
* **Calendar rules**: meetings you accepted, tentatively accepted or organise are notified; declined,
  unanswered and all-day events are not. A meeting that is cancelled or moved is followed at the next poll.

## Requirements

* Java 21 or later and Maven (the application is run with the JavaFX Maven plugin for now).
* A Microsoft work or school account with an Outlook calendar.
* A Microsoft **client id** (an "application registration"). WakeMeApp does not ship one: you create your own,
  once, as described below. It is free and takes a few minutes.

## Getting a client id

WakeMeApp asks Microsoft for permission to read your calendar, and Microsoft requires every program that does
so to be registered. You register WakeMeApp in your own organisation, so no data goes through anybody else.

1. Open the [Microsoft Entra admin center](https://entra.microsoft.com) (or the
   [Azure portal](https://portal.azure.com) and its Microsoft Entra ID page) and sign in with your work or
   school account. If you have access to several tenants, pick the right one with the settings icon at the top.
2. Go to **Entra ID → App registrations** and choose **New registration**.
3. Give it a name (for example *WakeMeApp*). Under **Supported account types** choose
   **Accounts in this organizational directory only** (single tenant), then **Register**.
   You need at least the *Application Developer* role in the tenant. If you do not have it, ask your IT
   department to register the application for you, or to give you the role.
4. On the **Overview** page copy the **Application (client) ID** and the **Directory (tenant) ID**.
5. Open **Authentication/Settings** and, under **Advanced settings**, set **Allow public client flows** to **Yes**, then
   **Save**. This is what lets a program without a secret sign you in with a code. No redirect URI is needed,
   and you do **not** create a client secret under *Certificates & secrets*: a desktop program cannot keep a
   secret, WakeMeApp never asks for one, and the sign-in does not use it.
6. Open **API permissions → Add a permission → Microsoft Graph → Delegated permissions** and add
   **Calendars.Read** and **User.Read** (the second is usually there already). WakeMeApp only reads: it never
   changes your calendar.
7. Some organisations do not let users approve permissions themselves. If the sign-in later says that an
   administrator must approve the application, ask your administrator to use **Grant admin consent** on this
   page (or send them the client id).

If you use a **personal Microsoft account** (Outlook.com, Hotmail) this is not supported yet: the application
is meant for work and school accounts.

## Starting the application

From the project folder, with your client id and the full address of your calendar:

```
mvn javafx:run -Djavafx.args="--calendar https://graph.microsoft.com/v1.0/me/calendarView --client-id <client id> --tenant <tenant id>"
```

`--tenant` is the **Directory (tenant) ID** of step 4. If you registered the application as multi-tenant you
can leave it out and WakeMeApp uses `organizations`.

### In the tray

Started without arguments, the application shows no window: it lives in the tray / menu bar. The icon has an
exclamation mark, and the tooltip says "WakeMeApp - no calendar configured", while no calendar is configured
(set one in the settings dialog, below, or start with `--calendar`). With `--calendar` the application works as
described below and shows the icon too. The menu has **Settings…**, which opens the settings dialog, and **Quit**,
which cancels the pending reminders and ends the application. On a desktop without a system tray the log says so and
the application keeps running; stop it with Ctrl+C.

### The settings dialog

Choose **Settings…** in the tray menu, or start the application with `--settings`, to open the dialog (only one
at a time; a meeting reminder still appears while it is open). Type your calendar in the one field: the link of a
published calendar (ICS), the address of an Outlook calendar on Microsoft Graph, or the path of a CSV file. The
dialog tells which kind it recognised and refuses what cannot work: a link that does not start with `https://` or
`webcal://`, a file that cannot be read, anything else. **Save** stores it, starts polling it at once and clears the
exclamation mark of the tray icon, with no restart; **Cancel** changes nothing. A saved link is shown as
`https://host/…` when you open the dialog again: type the whole link to change it. Emptying the field and saving
removes the calendar. **Notify me … before a meeting starts** sets the lead time: 1, 2, 5, 10 or 15 minutes (default 5). It
applies at once, also to the meetings already scheduled. Lead times shorter than the poll interval
(`--poll-interval`, 5 by default) are greyed out, since the reminder could be late; hover the list to see why, or
start with a shorter poll interval. The window the calendar is read with grows to poll interval + lead time when
`--window` is shorter. A **sound** plays with every reminder (the bundled chime at 80 % volume, once; reminders less than a second apart share it; dismissing or snoozing stops it; if it cannot be played the system beep is used and the log says why). **Show meeting reminders** switches all reminders off or on, for example during focused work: it applies at
once and is kept across restarts. While it is off the calendar is still polled but nothing is shown, and the log says
"Notification suppressed (globally disabled)"; a snoozed meeting is held back too. A meeting given with `--title`
is not affected. **Test Notification** shows a test meeting ("Test Meeting", starting in 5 minutes, joining
`https://example.com`) exactly like a real reminder, with countdown, join, snooze and dismiss; it closes by itself
after 10 seconds without interaction, works whatever the other settings are (also with the reminders switched
off), saves nothing and leaves the dialog open. A Microsoft Graph address also needs a client id and a tenant (`--client-id`, `--tenant`; the
dialog will ask for them in a later version).

### The first time: signing in

1. WakeMeApp opens a small window with a short **code** and a Microsoft address.
2. The window can show the Microsoft page itself; if it does not work for your account, press **Open in the
   browser** and enter the code there. **Copy code** puts the code on the clipboard.
3. Sign in with your work account and approve the permissions. The window closes by itself.

While you have not signed in, the application only logs that it is waiting. The code is valid for a few
minutes; if it expires, a new one is requested and shown.

### The next times

The sign-in is saved in a file called `signin.json` in the application data folder (readable only by you on
macOS and Linux), so you are not asked again until Microsoft asks for a new sign-in (typically after weeks of
not using the application, or when your password changes). The file is not encrypted: do not copy it around.
To sign out, stop the application and delete the file.

## Using an ICS link instead (no Microsoft sign-in)

Some organisations let only managed devices sign in to Microsoft 365 (the sign-in ends with *"your admin
requires the device to be managed"*, error **530033**, whatever browser you use). WakeMeApp can then read
your calendar from a **published calendar link**, which needs no sign-in and no client id:

1. In Outlook on the web open **Settings → Calendar → Shared calendars → Publish a calendar**.
2. Choose the calendar and **Can view all details**, then **Publish**.
3. Copy the **ICS** link and start the application with it:

```
mvn javafx:run -Djavafx.args="--calendar https://outlook.office365.com/owa/calendar/.../calendar.ics"
```

The link is read again at every poll. Things to know:

* If your organisation does not allow publishing, the option is missing: ask your administrator.
* **Treat the link as a password**: anybody who has it can read your whole calendar. WakeMeApp only
  shows the host of the link in its messages. The link given on the command line is never saved, but it stays in
  your shell history and is visible in the list of processes of your computer. To revoke it, unpublish the
  calendar in the same place.
* A feed does not say whether you accepted a meeting, so every meeting is notified except cancelled and
  all-day ones.
* Recurring meetings are notified one occurrence at a time, also when one was moved or removed. A meeting whose rule or time zone cannot be read is skipped, with a warning, and the others are still notified.
* The link may be out of date by a few minutes: a meeting moved a moment ago may be notified late.

### The saved calendar

The calendar you save (an ICS link, a Microsoft Graph address or a CSV path) is kept in one file,
`calendar.txt`, in the application data folder. When you start the application without `--calendar` and
without a single meeting (`--title`, `--start-date`…), that calendar is used. The file is readable only by you
(on Windows it is protected by your user account only) and is **not encrypted**: whoever can read your files or a
backup of them can read the link, so unpublish it (Outlook on the web: **Publish a calendar → Unpublish**) if
it leaks. A saved value that is not valid (for example a link starting with `http://`) is ignored with a
warning in the log. `--print-config` shows it as `https://host/…`.
The settings dialog writes this file; it can also be written by hand.

## Command line options

| Option | Meaning |
|---|---|
| `--calendar <file\|address>` | For this run only (never saved; it wins over the saved calendar). The Outlook calendar address (`https://graph.microsoft.com/v1.0/me/calendarView`), the link of a published calendar (ICS), or a CSV file of meetings to try the application without an account. |
| `--client-id <uuid>` | The application (client) id of the registration. |
| `--tenant <id>` | The tenant id, or `organizations` (default). |
| `--poll-interval <minutes>` | How often the calendar is read (default 5). |
| `--window <minutes>` | How far ahead each reading looks (default 15; at least poll interval + 5). |
| `--debug` | Logs the detail needed to investigate a problem. |
| `--settings` | Opens the settings dialog at launch. |
| `--print-config` | Prints every setting, its value and where it comes from, then exits. The `--calendar` value is included; an ICS link is shown as its host only. |
| `-t`, `-s`, `-e`, `-o`, `-u`, `-d` | Title, start, end, organiser, join link and description of a single meeting, to try the reminder without a calendar (`--title`, `--start-date`, …). |
| `-n`, `--now` | Shows the reminder immediately instead of before the start time. |

Try the reminder without any account:

```
mvn javafx:run -Djavafx.args="--title Standup --start-date 2026-11-12T10:00 --url https://teams.microsoft.com/l/meetup-join/x --now"
```

## Settings

`--client-id` and `--tenant` are not saved: they are used for that run only. What the application stores, and
that it reads when an option is missing, lives in the Java user preferences, node `/ste/wakemeapp`:

| Key | Meaning | Default |
|---|---|---|
| `graph.clientId` | Application (client) id | none |
| `graph.tenant` | Tenant id or `organizations` | `organizations` |
| `notify.sound` | The notification sound: `chime`, `pop`, `ping` or `bell` | `chime` |
| `notify.soundVolume` | Volume of the notification sound, 0 to 100 (0 is silent) | 80 |
| `notify.enabled` | Whether meeting reminders are shown (`true`/`false`; set in the settings dialog) | `true` |
| `notify.leadTimeMinutes` | Minutes before a meeting starts that the reminder appears (1, 2, 5, 10 or 15; set in the settings dialog) | 5 |

The order is: command line, then stored value, then default. To see what is in effect, and where each value
comes from, on any platform:

```
mvn javafx:run -Djavafx.args="--print-config"
```

Where the stored values are, if you want to look at them directly:

* **Linux**: `~/.java/.userPrefs/ste/wakemeapp/prefs.xml`
* **Windows**: registry key `HKCU\Software\JavaSoft\Prefs\ste\wakemeapp` (`reg query` or regedit)
* **macOS**: `defaults read com.apple.java.util.prefs` (the file is
  `~/Library/Preferences/com.apple.java.util.prefs.plist`, but read it through `defaults`, as macOS caches it)

The sign-in (`signin.json`), the saved calendar (`calendar.txt`) and the dismissed meetings (`dismissed.json`) are in the application data folder,
not in the preferences: `~/Library/Application Support/WakeMeApp` on macOS, `%LOCALAPPDATA%\WakeMeApp` on
Windows, `~/.local/share/WakeMeApp` on Linux.

## When something does not work

The application writes what it does to the terminal; add `--debug` for the detail. The messages say what
failed and whether it will try again. The usual ones:

* *"Waiting for the sign-in to complete"*: nobody entered the code yet.
* *"your admin requires the device to be managed"* or *530033*: the organisation allows only managed devices; use an ICS link (above).
* *"The calendar link no longer works"*: the link was unpublished or changed; publish the calendar again.
* *"administrator must approve"* or *AADSTS65001*: step 7 above.
* *"public client"* or *AADSTS7000218*: **Allow public client flows** is not set to **Yes** (step 5).
* *"Cannot read the calendar … 401"*: the sign-in is no longer valid; delete `signin.json` and start again.
* The reminder never appears: check that the meeting starts within the next window (default 15 minutes), that
  you accepted it, and that it is not all-day.

## Building and testing

```
mvn test
```

The user stories and their notes are under [`uzz/stories`](uzz/stories/index.md); the scope is in
[`uzz/stories/scope.md`](uzz/stories/scope.md).

## Licence

Apache License 2.0.
