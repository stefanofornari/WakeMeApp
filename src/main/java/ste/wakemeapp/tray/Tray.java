package ste.wakemeapp.tray;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.logging.Logger;

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
    private final Clock clock;
    private final Locale locale;
    private boolean started;
    private Optional<Instant> lastUpdate = Optional.empty();

    Tray(TrayView view, BooleanSupplier supported) {
        this(view, supported, Clock.systemDefaultZone(), Locale.getDefault());
    }

    Tray(TrayView view, BooleanSupplier supported, Clock clock, Locale locale) {
        this.view = view;
        this.supported = supported;
        this.clock = clock;
        this.locale = locale;
    }

    /**
     * Creates the tray for this platform; the menu has "Settings…" and "About WakeMeApp", which run {@code onSettings} and
     * {@code onAbout} on the JavaFX Application Thread, and "Quit", which runs {@code onQuit}. Call on the JavaFX
     * Application Thread.
     */
    public static Tray create(Runnable onSettings, Runnable onAbout, Runnable onQuit) {
        return new Tray(SystemTrayView.create(onSettings, onAbout, onQuit), () -> true);
    }

    /** Removes the icon (the application is ending). */
    public void close() {
        if (started) {
            started = false;
            view.close();
        }
    }

    /** Shows the icon in the state that matches {@code configured}. */
    public void start(boolean configured) {
        if (!supported.getAsBoolean()) {
            LOG.warning("The system tray is not available on this desktop: no tray icon is shown");
            return;
        }
        started = true;
        apply(configured);
        view.setLastUpdate(LastUpdateText.of(lastUpdate, clock, locale));
        view.show();
    }

    /** Called when a calendar gets saved (or removed): updates the icon without a restart. */
    public void setConfigured(boolean configured) {
        if (started) {
            apply(configured);
        }
    }

    /**
     * Shows when the calendar was last read successfully, in the last item of the menu: "Last update: 16:49" today,
     * with the date if it was another day, a dash if there is none. May be called before {@link #start}.
     */
    public void setLastUpdate(Optional<Instant> instant) {
        lastUpdate = instant;
        if (started) {
            view.setLastUpdate(LastUpdateText.of(instant, clock, locale));
        }
    }

    private void apply(boolean configured) {
        view.setAlert(!configured);
        view.setTooltip(configured ? TOOLTIP : TOOLTIP_NOT_CONFIGURED);
    }
}
