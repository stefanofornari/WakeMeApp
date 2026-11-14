# Where WakeMeApp keeps its settings, and how to look at them

WakeMeApp stores what you set in its settings dialog in the **Java user preferences**, node `/ste/wakemeapp`.
Where that is depends on the operating system. The files that are not preferences (the sign-in, the saved
calendar, the dismissed meetings) are in the application data folder, described at the end.

The quickest way, the same on every platform, is the application itself. It prints every setting, its value and
where the value comes from (command line, stored or default), then exits:

```
mvn javafx:run -Djavafx.args="--print-config"
```

A saved calendar link is shown as `https://host/…/calendar.ics`: the secret part is never printed.

Quit WakeMeApp (tray menu → **Quit**) before changing or deleting stored values by hand: a running application
keeps what it read and writes it back when you save in the dialog.

## The keys

| Key | Meaning | Default |
|---|---|---|
| `graph.clientId` | Application (client) id of the Microsoft Graph app registration | none |
| `graph.tenant` | Tenant id, or `organizations` | `organizations` |
| `poll.intervalMinutes` | How often the calendar is read: 1, 2, 5, 10, 15 or 30 | 5 |
| `notify.leadTimeMinutes` | Minutes before a meeting starts that the reminder appears: 1, 2, 5, 10 or 15 | 5 |
| `notify.sound` | `chime`, `pop`, `ping`, `bell`, `none` or `custom` | `chime` |
| `notify.soundFile` | Absolute path of the custom sound (WAV or AIFF), used when `notify.sound` is `custom` | none |
| `notify.soundVolume` | Volume of the sound, 0 to 100 (0 is silent) | 80 |

A key that is missing is simply at its default. A stored value that is not one of the allowed ones is ignored
with a warning in the log, and the default is used. A key that is not in this table is reported by
`--print-config` as unknown.

## Linux

The preferences are an XML file per node:

```
~/.java/.userPrefs/ste/wakemeapp/prefs.xml
```

Read them:

```
cat ~/.java/.userPrefs/ste/wakemeapp/prefs.xml
```

```xml
<map MAP_XML_VERSION="1.0">
  <entry key="notify.sound" value="bell"/>
  <entry key="poll.intervalMinutes" value="15"/>
</map>
```

Reset to the defaults (with the application not running):

```
rm -r ~/.java/.userPrefs/ste/wakemeapp
```

*Checked on Linux with the Java Preferences API: the file is where it says, `cat` shows the stored keys, and
after the `rm` every key is back at its default.*

## macOS

The preferences are in the property list `~/Library/Preferences/com.apple.java.util.prefs.plist`. Read them with
`defaults`, not by opening the file: macOS caches preferences and the file may be behind.

```
defaults read com.apple.java.util.prefs
```

The values of WakeMeApp are under the entry for the node `/ste/wakemeapp/`. Reset only those, with the
application not running:

```
defaults delete com.apple.java.util.prefs "/ste/wakemeapp/"
```

Do not run `defaults delete com.apple.java.util.prefs` without the node: it removes the preferences of every
Java application of your user.

*Not checked yet on a Mac: the exact shape of the output of `defaults read` and the reset command are to be
confirmed there.*

## Windows

The preferences are in the registry:

```
HKEY_CURRENT_USER\Software\JavaSoft\Prefs\ste\wakemeapp
```

Read them:

```
reg query "HKCU\Software\JavaSoft\Prefs\ste\wakemeapp"
```

or open the key in regedit. The registry does not tell capital letters from small ones, so Java writes a `/`
before every capital letter of a name: `notify.leadTimeMinutes` is shown as `notify.lead/Time/Minutes`.

Reset to the defaults (with the application not running):

```
reg delete "HKCU\Software\JavaSoft\Prefs\ste\wakemeapp" /f
```

*Not checked yet on Windows: the commands and the `/` before capital letters are to be confirmed there.*

## What is not in the preferences

These files are in the application data folder: `~/Library/Application Support/WakeMeApp` on macOS,
`%LOCALAPPDATA%\WakeMeApp` on Windows, `~/.local/share/WakeMeApp` on Linux.

| File | What it holds |
|---|---|
| `signin.json` | The Microsoft sign-in (token cache). It is **not** in the preferences. Readable only by you; delete it to sign out. |
| `calendar.txt` | The calendar saved by the settings dialog: an ICS link, a Graph address or a CSV path. Readable only by you, **not encrypted**: an ICS link is a secret, unpublish it if it leaks. Delete it to forget the calendar. |
| `dismissed.json` | The meetings you dismissed, so they are not announced again. |
