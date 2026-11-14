package ste.wakemeapp.tray;

import dorkbox.systemTray.MenuItem;
import dorkbox.systemTray.SystemTray;
import java.net.URL;
import java.util.logging.Logger;
import javafx.application.Platform;

/**
 * The native tray icon, through dorkbox SystemTray (AppIndicator, GtkStatusIcon or Swing on Linux,
 * the native icon on Windows and macOS). Menu: "Settings…" and "Quit". The menu actions run on the
 * library's own thread, so they hand over to the JavaFX Application Thread where needed.
 */
final class SystemTrayView implements TrayView {

    private static final Logger LOG = Logger.getLogger(SystemTrayView.class.getName());

    private final SystemTray tray;
    private final URL normal = resource("tray.png");
    private final URL alert = resource("tray-alert.png");

    private SystemTrayView(SystemTray tray, Runnable onSettings, Runnable onQuit) {
        this.tray = tray;
        tray.getMenu().add(new MenuItem("Settings…", e -> Platform.runLater(onSettings)));
        tray.getMenu().add(new MenuItem("Quit", e -> onQuit.run()));
    }

    /** The view, or null where this desktop has no system tray. */
    static SystemTrayView create(Runnable onSettings, Runnable onQuit) {
        try {
            SystemTray tray = SystemTray.get();
            return tray == null ? null : new SystemTrayView(tray, onSettings, onQuit);
        } catch (RuntimeException | LinkageError x) {
            LOG.warning("The system tray cannot be started: " + x);
            return null;
        }
    }

    @Override
    public void show() {
        tray.setEnabled(true);   // the image was set by setAlert
    }

    @Override
    public void setAlert(boolean alert) {
        tray.setImage(alert ? this.alert : normal);
    }

    @Override
    public void setTooltip(String tooltip) {
        tray.setTooltip(tooltip);
    }

    @Override
    public void close() {
        tray.shutdown();
    }

    private static URL resource(String name) {
        URL url = SystemTrayView.class.getResource(name);
        if (url == null) {
            throw new IllegalStateException("Missing the tray icon " + name);
        }
        return url;
    }
}
