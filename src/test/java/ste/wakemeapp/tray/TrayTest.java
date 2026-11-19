package ste.wakemeapp.tray;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.BDDAssertions.then;

class TrayTest {

    /** Records what the tray would show. */
    private static final class FakeView implements TrayView {
        final List<String> events = new ArrayList<>();

        @Override public void show() { events.add("show"); }
        @Override public void setAlert(boolean alert) { events.add("alert=" + alert); }
        @Override public void setTooltip(String tooltip) { events.add("tooltip=" + tooltip); }
        @Override public void setLastUpdate(String text) { events.add("lastUpdate=" + text); }
        @Override public void close() { events.add("close"); }
    }

    private final FakeView view = new FakeView();

    @Test
    void should_show_the_exclamation_mark_and_the_tooltip_when_no_calendar_is_configured() {
        new Tray(view, () -> true).start(false);

        then(view.events).containsExactly("alert=true", "tooltip=WakeMeApp - no calendar configured", "show");
    }

    @Test
    void should_show_the_normal_icon_when_a_calendar_is_configured() {
        new Tray(view, () -> true).start(true);

        then(view.events).containsExactly("alert=false", "tooltip=WakeMeApp", "show");
    }

    @Test
    void should_clear_the_mark_when_a_calendar_gets_configured() {
        Tray tray = new Tray(view, () -> true);
        tray.start(false);
        view.events.clear();

        tray.setConfigured(true);

        then(view.events).containsExactly("alert=false", "tooltip=WakeMeApp");
    }

    @Test
    void should_log_that_the_tray_is_not_available_and_keep_going() {
        List<String> logged = new ArrayList<>();
        Logger logger = Logger.getLogger(Tray.class.getName());
        Handler handler = new Handler() {
            @Override public void publish(LogRecord record) { logged.add(record.getMessage()); }
            @Override public void flush() { }
            @Override public void close() { }
        };
        logger.addHandler(handler);
        try {
            Tray tray = new Tray(view, () -> false);
            tray.start(false);
            tray.setConfigured(true);
        } finally {
            logger.removeHandler(handler);
        }

        then(view.events).isEmpty();
        then(logged).anyMatch(m -> m.contains("tray is not available"));
    }

    @Test
    void should_remove_the_icon_when_closed() {
        Tray tray = new Tray(view, () -> true);
        tray.start(true);
        view.events.clear();

        tray.close();

        then(view.events).containsExactly("close");
    }

    @Test
    void should_not_touch_the_view_when_closed_without_a_tray() {
        new Tray(view, () -> false).close();

        then(view.events).isEmpty();
    }

    private static final java.time.Clock NOW = java.time.Clock.fixed(
        java.time.Instant.parse("2026-11-19T16:00:00Z"), java.time.ZoneId.of("Europe/Rome"));

    private Tray tray() {
        return new Tray(view, () -> true, NOW, java.util.Locale.UK);
    }

    @Test
    void should_show_the_last_update_when_started_and_when_it_changes() {
        Tray tray = tray();
        tray.start(true);

        tray.setLastUpdate(java.util.Optional.of(java.time.Instant.parse("2026-11-19T15:49:00Z")));

        then(view.events).containsExactly("alert=false", "tooltip=WakeMeApp", "lastUpdate=Last update: \u2014", "show",
            "lastUpdate=Last update: 16:49");
    }

    @Test
    void should_show_a_last_update_given_before_the_start_when_it_starts() {
        Tray tray = tray();
        tray.setLastUpdate(java.util.Optional.of(java.time.Instant.parse("2026-11-18T15:49:00Z")));

        tray.start(true);

        then(view.events).containsExactly("alert=false", "tooltip=WakeMeApp", "lastUpdate=Last update: 18 Nov, 16:49", "show");
    }

    @Test
    void should_go_back_to_the_dash_when_the_last_update_is_cleared() {
        Tray tray = tray();
        tray.start(true);
        view.events.clear();

        tray.setLastUpdate(java.util.Optional.empty());

        then(view.events).containsExactly("lastUpdate=Last update: \u2014");
    }

    @Test
    void should_not_touch_the_view_for_a_last_update_when_there_is_no_tray() {
        Tray tray = new Tray(view, () -> false, NOW, java.util.Locale.UK);
        tray.start(true);

        tray.setLastUpdate(java.util.Optional.of(java.time.Instant.parse("2026-11-19T15:49:00Z")));

        then(view.events).isEmpty();
    }
}
