# HOWTO: JavaFX tray-only app on macOS (tray icon visible, no Dock icon)

How to ship a JavaFX/Java application as a macOS `.app` bundle, started with a double click in Finder, that shows a
system tray (menu bar) icon and does not appear in the Dock. Based on WakeMeApp (dorkbox SystemTray + JavaFX, bundle
built with `maven-assembly-plugin`, launcher is a shell script, Java is the one installed on the Mac).

## Symptoms

- `WakeMeApp.app/Contents/MacOS/WakeMeApp` run from a terminal: tray icon appears, everything works.
- Double click on the bundle (or `open WakeMeApp.app`): the app starts (a generic "java" icon in the Dock) but there
  is NO tray icon and nothing can be interacted with. No exception, nothing in the application log.
- `log show --predicate 'processID == <pid>'` shows `NSStatusItem` scene creation failing with a
  `com.apple.MenuBarAgent` XPC error (`BSServiceConnectionErrorDomain Code=3`).

## Cause 1: `exec java` in the launcher

When Finder starts a bundle, Launch Services registers the process of `CFBundleExecutable` as the application. If that
shell script ends with `exec java ...`, the registered process image is replaced by the JDK `java` binary. macOS
(MenuBarAgent) then refuses that process a status item. A terminal start is not registered with Launch Services, which
is why it works there and hides the problem.

### Fix: do not `exec`; start java as a background child and let the launcher exit

```sh
#!/bin/sh
# ... locate java / JAVA_HOME ...
"$app/bin/wakemeapp.sh" "$@" </dev/null >/dev/null 2>&1 &
exit 0
```

- Redirect stdin/stdout/stderr so the child does not hold the launcher's descriptors.
- The launcher may die; the JVM keeps running and owns the tray icon.
- Waiting on the child (and forwarding TERM/INT/HUP) also works, but is not needed.
- Consequence: nothing stops a second launch from starting a second instance, and quitting must be done from the tray
  menu (or by killing the java process).
- Confirmed alternative: `jpackage` (keeps the bundle's own executable) also works, but it bundles a runtime, which
  was not wanted here.

## Cause 2: Dock icon

`LSUIElement=true` in `Info.plist` hides the Dock icon only for the process Launch Services launched. Now that the JVM
is a plain `java` child, it shows a generic "java" Dock icon (also with a terminal start).
These do NOT help: `-Dapple.awt.UIElement=true`, and JavaFX (`libglass`) has no activation-policy setting.

### Fix: set the NSApplication activation policy to "accessory" through JNA

```java
final class MacDock {
    private static final long ACCESSORY = 1; // NSApplicationActivationPolicyAccessory

    private interface ObjC extends Library {
        Pointer objc_getClass(String name);
        Pointer sel_registerName(String name);
        Pointer objc_msgSend(Pointer receiver, Pointer selector);
        boolean objc_msgSend(Pointer receiver, Pointer selector, long argument);
    }

    static void hideIcon() {
        if (!System.getProperty("os.name", "").toLowerCase().contains("mac")) {
            return; // never touch JNA/objc elsewhere
        }
        try {
            ObjC objc = Native.load("objc", ObjC.class);
            Pointer app = objc.objc_msgSend(
                objc.objc_getClass("NSApplication"), objc.sel_registerName("sharedApplication"));
            objc.objc_msgSend(app, objc.sel_registerName("setActivationPolicy:"), ACCESSORY);
        } catch (RuntimeException | LinkageError x) {
            LOG.warning("The Dock icon cannot be removed: " + x);
        }
    }
}
```

- Call it on the JavaFX Application Thread, after the toolkit is started: `Platform.runLater(MacDock::hideIcon);`
  (WakeMeApp does it in `SystemTrayView.open()`).
- JNA is already on the classpath through the dorkbox SystemTray dependency; no extra library is needed.
- It is a no-op on Linux/Windows (guard on `os.name`), so no errors there.

## Threading note (dorkbox SystemTray on macOS)

Dorkbox uses the AWT tray on macOS. Calling it from the JavaFX/AppKit thread can deadlock. Run all tray calls on a
dedicated single-thread executor.

## Bundle checklist

- `Info.plist`: `CFBundleExecutable` = the launcher script, `LSUIElement` = `true`.
- Launcher script: no `exec` for java; background child; `exit 0`.
- Code: `MacDock.hideIcon()` after JavaFX starts.

## How to verify

- `open -n WakeMeApp.app` (not the terminal path!) then look at the menu bar and the Dock.
- `lsappinfo list` should show the bundle id with type `UIElement`.
- Logs of the app: `~/Library/Application Support/WakeMeApp/logs/`.
- Not verified: Gatekeeper/quarantine behaviour of an unsigned downloaded zip.

### Keywords

JavaFX, macOS, system tray, menu bar, NSStatusItem, Dock icon, LSUIElement, activation policy, JNA, Objective-C,
dorkbox SystemTray, app bundle, Launch Services, exec, MenuBarAgent, launcher script, jpackage
