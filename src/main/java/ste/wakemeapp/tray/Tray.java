package ste.wakemeapp.tray;

import java.util.function.BooleanSupplier;
import java.util.logging.Logger;
import javafx.stage.Stage;

/**
 * The application's tray / menu bar icon: normal when a calendar is configured, with an
 * exclamation mark and an explaining tooltip when not. Where the platform has no tray the
 * application only says so in the log and keeps running.
 */
public final class Tray {

    static final String TOOLTIP = "WakeMeApp";
    static final String TOOLTIP_NOT_CONFIGURED = "WakeMeApp - no calendar configured";

    private static final Logger LOG = Logger.getLogger(Tray.class.getName());

    private final TrayView view;
    private final BooleanSupplier supported;
    private boolean started;

    Tray(TrayView view, BooleanSupplier supported) {
        this.view = view;
        this.supported = supported;
    }

    /**
     * Creates the tray for this platform; the menu has "Settings…", which runs {@code onSettings} on
     * the JavaFX Application Thread, and "Quit", which runs {@code onQuit}. Call on the JavaFX
     * Application Thread.
     */
    public static Tray create(Stage stage, Runnable onSettings, Runnable onQuit) {
        return new Tray(FxTrayView.supported() ? new FxTrayView(stage, onSettings, onQuit) : null, FxTrayView::supported);
    }

    /** Shows the icon in the state that matches {@code configured}. */
    public void start(boolean configured) {
        if (!supported.getAsBoolean()) {
            LOG.warning("The system tray is not available on this desktop: no tray icon is shown");
            return;
        }
        started = true;
        apply(configured);
        view.show();
    }

    /** Called when a calendar gets saved (or removed): updates the icon without a restart. */
    public void setConfigured(boolean configured) {
        if (started) {
            apply(configured);
        }
    }

    private void apply(boolean configured) {
        view.setAlert(!configured);
        view.setTooltip(configured ? TOOLTIP : TOOLTIP_NOT_CONFIGURED);
    }
}
