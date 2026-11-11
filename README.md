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
5. Open **Authentication** and, under **Advanced settings**, set **Allow public client flows** to **Yes**, then
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

## Command line options

| Option | Meaning |
|---|---|
| `--calendar <file\|address>` | The Outlook calendar address (`https://graph.microsoft.com/v1.0/me/calendarView`), or a CSV file of meetings to try the application without an account. |
| `--client-id <uuid>` | The application (client) id of the registration. |
| `--tenant <id>` | The tenant id, or `organizations` (default). |
| `--poll-interval <minutes>` | How often the calendar is read (default 5). |
| `--window <minutes>` | How far ahead each reading looks (default 15; at least poll interval + 5). |
| `--debug` | Logs the detail needed to investigate a problem. |
| `--print-config` | Prints every setting, its value and where it comes from, then exits. |
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

The sign-in (`signin.json`) and the dismissed meetings (`dismissed.json`) are in the application data folder,
not in the preferences: `~/Library/Application Support/WakeMeApp` on macOS, `%LOCALAPPDATA%\WakeMeApp` on
Windows, `~/.local/share/WakeMeApp` on Linux.

## When something does not work

The application writes what it does to the terminal; add `--debug` for the detail. The messages say what
failed and whether it will try again. The usual ones:

* *"Waiting for the sign-in to complete"*: nobody entered the code yet.
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
