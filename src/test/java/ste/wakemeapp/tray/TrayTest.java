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
}
