package ste.wakemeapp.settings;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.BDDAssertions.then;

/** The typed settings on a Preferences node; the node is in memory, never the real one. */
class SettingsTest {

    private final MemoryPreferences node = new MemoryPreferences();
    private final Settings settings = new Settings(node);
    private final List<LogRecord> logged = new ArrayList<>();
    private final Logger log = Logger.getLogger(Settings.class.getName());
    private final Handler capture = new Handler() {
        @Override public void publish(LogRecord record) { logged.add(record); }
        @Override public void flush() { }
        @Override public void close() { }
    };

    @BeforeEach
    void captureLog() {
        log.addHandler(capture);
        log.setLevel(Level.ALL);
    }

    @AfterEach
    void releaseLog() {
        log.removeHandler(capture);
    }

    @Test
    void the_user_node_is_ste_wakemeapp() {
        then(Settings.NODE).isEqualTo("/ste/wakemeapp");
    }

    @Test
    void a_stored_int_is_read_back_by_a_new_settings_on_the_same_node() {
        settings.putInt("notify.leadMinutes", 10);

        then(new Settings(node).getInt("notify.leadMinutes", 5)).isEqualTo(10);
    }

    @Test
    void a_later_value_replaces_an_earlier_one() {
        settings.putInt("notify.leadMinutes", 10);
        settings.putInt("notify.leadMinutes", 3);

        then(settings.getInt("notify.leadMinutes", 5)).isEqualTo(3);
    }

    @Test
    void a_missing_value_gives_the_default() {
        then(settings.getInt("notify.leadMinutes", 5)).isEqualTo(5);
        then(settings.getString("graph.tenant", "organizations")).isEqualTo("organizations");
        then(settings.getBoolean("notify.enabled", true)).isTrue();
        then(logged).isEmpty();
    }

    @Test
    void strings_and_booleans_are_read_back() {
        settings.putString("graph.tenant", "contoso.onmicrosoft.com");
        settings.putBoolean("notify.enabled", false);

        then(settings.getString("graph.tenant", "organizations")).isEqualTo("contoso.onmicrosoft.com");
        then(settings.getBoolean("notify.enabled", true)).isFalse();
    }

    @Test
    void an_int_setting_holding_text_gives_the_default_and_a_warning_naming_the_key() {
        node.put("notify.leadMinutes", "abc");

        then(settings.getInt("notify.leadMinutes", 5)).isEqualTo(5);
        then(logged).hasSize(1);
        then(logged.get(0).getLevel()).isEqualTo(Level.WARNING);
        then(logged.get(0).getMessage()).contains("notify.leadMinutes").contains("abc");
    }

    @Test
    void a_boolean_setting_holding_text_gives_the_default_and_a_warning_naming_the_key() {
        node.put("notify.enabled", "maybe");

        then(settings.getBoolean("notify.enabled", true)).isTrue();
        then(logged).hasSize(1);
        then(logged.get(0).getMessage()).contains("notify.enabled").contains("maybe");
    }

    @Test
    void every_write_is_flushed() {
        settings.putInt("notify.leadMinutes", 10);
        then(node.flushes).isEqualTo(1);

        settings.putString("graph.tenant", "organizations");
        then(node.flushes).isEqualTo(2);

        settings.putBoolean("notify.enabled", true);
        then(node.flushes).isEqualTo(3);
    }

    @Test
    void a_store_that_cannot_be_written_does_not_stop_the_application() {
        node.failFlush = true;

        settings.putInt("notify.leadMinutes", 10);

        List<LogRecord> warnings = logged.stream().filter(r -> r.getLevel() == Level.WARNING).toList();
        then(warnings).hasSize(1);
        then(warnings.get(0).getMessage()).contains("notify.leadMinutes").contains("not saved");
        then(settings.getInt("notify.leadMinutes", 5)).isEqualTo(10);   // still known in this run
    }
}
