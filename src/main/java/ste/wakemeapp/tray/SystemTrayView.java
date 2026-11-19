package ste.wakemeapp.tray;

import dorkbox.systemTray.MenuItem;
import dorkbox.systemTray.Separator;
import dorkbox.systemTray.SystemTray;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import javafx.application.Platform;

/**
 * The native tray icon, through dorkbox SystemTray (AppIndicator, GtkStatusIcon or Swing on Linux,
 * the native icon on Windows and macOS). Menu: "Settings…", "About WakeMeApp", "Quit", a separator and the read-only "Last update: …". The menu actions run on the
 * library's own thread, so they hand over to the JavaFX Application Thread where needed.
 */
final class SystemTrayView implements TrayView {

    private static final Logger LOG = Logger.getLogger(SystemTrayView.class.getName());

    private final URL normal = resource("tray.png");
    private final URL alert = resource("tray-alert.png");
    private final Runnable onSettings;
    private final Runnable onAbout;
    private final Runnable onQuit;
    // every call to the library runs here, in order, never on the JavaFX Application Thread: on macOS the library
    // asks the AppKit thread (the one JavaFX runs on) to do its work and waits, so calling it from there deadlocks
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "wakemeapp-tray");
        thread.setDaemon(true);
        return thread;
    });
    private SystemTray tray;   // only used on the executor's thread
    private MenuItem lastUpdate;   // likewise; read-only

    private SystemTrayView(Runnable onSettings, Runnable onAbout, Runnable onQuit) {
        this.onSettings = onSettings;
        this.onAbout = onAbout;
        this.onQuit = onQuit;
        executor.execute(this::open);
    }

    static SystemTrayView create(Runnable onSettings, Runnable onAbout, Runnable onQuit) {
        return new SystemTrayView(onSettings, onAbout, onQuit);
    }

    private void open() {
        try {
            tray = SystemTray.get();
        } catch (RuntimeException | LinkageError x) {
            LOG.warning("The system tray cannot be started: " + x);
        }
        if (tray == null) {
            LOG.warning("The system tray is not available on this desktop: no tray icon is shown");
            return;
        }
        Platform.runLater(MacDock::hideIcon);
        tray.getMenu().add(new MenuItem("Settings…", e -> Platform.runLater(onSettings)));
        tray.getMenu().add(new MenuItem("About WakeMeApp", e -> Platform.runLater(onAbout)));
        tray.getMenu().add(new MenuItem("Quit", e -> onQuit.run()));
        tray.getMenu().add(new Separator());
        lastUpdate = new MenuItem(LastUpdateText.NONE);
        lastUpdate.setEnabled(false);
        tray.getMenu().add(lastUpdate);
    }

    @Override
    public void show() {
        run(() -> tray.setEnabled(true));   // the image was set by setAlert
    }

    @Override
    public void setAlert(boolean alert) {
        run(() -> tray.setImage(alert ? this.alert : normal));
    }

    @Override
    public void setTooltip(String tooltip) {
        run(() -> tray.setTooltip(tooltip));
    }

    @Override
    public void setLastUpdate(String text) {
        run(() -> {
            if (lastUpdate != null) {
                lastUpdate.setText(text);
            }
        });
    }

    @Override
    public void close() {
        run(() -> tray.shutdown());
        executor.shutdown();
    }

    private void run(Runnable action) {
        executor.execute(() -> {
            if (tray != null) {
                try {
                    action.run();
                } catch (RuntimeException x) {
                    LOG.warning("The tray icon cannot be updated: " + x);
                }
            }
        });
    }

    private static URL resource(String name) {
        URL url = SystemTrayView.class.getResource(name);
        if (url == null) {
            throw new IllegalStateException("Missing the tray icon " + name);
        }
        return url;
    }
}
