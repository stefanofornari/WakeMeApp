package ste.wakemeapp.settings;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.BDDAssertions.then;

/** What --print-config shows: every setting, its effective value and where the value comes from. */
class ConfigReportTest {

    private static final String CLIENT_ID = "11111111-2222-3333-4444-555555555555";
    private static final String TENANT_ID = "99999999-8888-7777-6666-555555555555";

    private final MemoryPreferences node = new MemoryPreferences();
    private final Settings stored = new Settings(node);

    private static String lineOf(List<String> lines, String key) {
        return lines.stream().filter(l -> l.startsWith(key + " ")).findFirst().orElse("");
    }

    @Test
    void each_setting_shows_its_value_and_source() {
        stored.putString("graph.tenant", TENANT_ID);

        List<String> lines = ConfigReport.lines(CLIENT_ID, null, stored);

        then(lineOf(lines, "graph.clientId")).contains(CLIENT_ID).contains("(command line)");
        then(lineOf(lines, "graph.tenant")).contains(TENANT_ID).contains("(stored)");
    }

    @Test
    void a_setting_nobody_gave_shows_its_default() {
        List<String> lines = ConfigReport.lines(null, null, stored);

        then(lineOf(lines, "graph.tenant")).contains("organizations").contains("(default)");
        then(lineOf(lines, "graph.clientId")).contains("(not set)").contains("(default)");
    }

    @Test
    void the_command_line_wins_over_the_stored_value() {
        stored.putString("graph.tenant", "organizations");

        List<String> lines = ConfigReport.lines(null, TENANT_ID, stored);

        then(lineOf(lines, "graph.tenant")).contains(TENANT_ID).contains("(command line)");
    }

    @Test
    void a_stored_value_that_is_not_valid_is_shown_as_ignored() {
        node.put("graph.tenant", "common");

        List<String> lines = ConfigReport.lines(null, null, stored);

        then(lineOf(lines, "graph.tenant")).contains("organizations").contains("(default)")
            .contains("stored value 'common' ignored");
    }

    @Test
    void unknown_stored_keys_are_listed() {
        node.put("something.else", "42");

        List<String> lines = ConfigReport.lines(null, null, stored);

        then(lineOf(lines, "something.else")).contains("42").contains("(stored, unknown key)");
    }

    @Test
    void known_keys_are_not_listed_twice() {
        stored.putString("graph.tenant", TENANT_ID);

        List<String> lines = ConfigReport.lines(null, null, stored);

        then(lines.stream().filter(l -> l.startsWith("graph.tenant ")).count()).isEqualTo(1L);
        then(lines).hasSize(7);
    }

    @Test
    void the_lead_time_shows_its_default_and_its_stored_value() {
        then(lineOf(ConfigReport.lines(null, null, stored), "notify.leadTimeMinutes")).contains("5").contains("(default)");

        stored.putInt("notify.leadTimeMinutes", 10);

        then(lineOf(ConfigReport.lines(null, null, stored), "notify.leadTimeMinutes")).contains("10").contains("(stored)");
    }

    @Test
    void a_stored_lead_time_out_of_range_is_shown_as_ignored() {
        node.put("notify.leadTimeMinutes", "12");

        then(lineOf(ConfigReport.lines(null, null, stored), "notify.leadTimeMinutes"))
            .contains("(default)").contains("stored value '12' ignored");
    }

    @Test
    void the_lead_time_is_not_listed_as_an_unknown_key() {
        stored.putInt("notify.leadTimeMinutes", 10);

        then(ConfigReport.lines(null, null, stored).stream().filter(l -> l.contains("unknown key")).count()).isZero();
    }

    @Test
    void the_poll_interval_shows_its_default_and_its_stored_value() {
        then(lineOf(ConfigReport.lines(null, null, stored), "poll.intervalMinutes")).contains("5").contains("(default)");

        stored.putInt("poll.intervalMinutes", 15);

        then(lineOf(ConfigReport.lines(null, null, stored), "poll.intervalMinutes")).contains("15").contains("(stored)");
        then(ConfigReport.lines(null, null, stored).stream().filter(l -> l.contains("unknown key")).count()).isZero();
    }

    @Test
    void the_sound_and_its_volume_show_their_defaults_and_stored_values() {
        List<String> lines = ConfigReport.lines(null, null, stored);
        then(lineOf(lines, "notify.sound")).contains("chime").contains("(default)");
        then(lineOf(lines, "notify.soundVolume")).contains("80").contains("(default)");

        stored.putString("notify.sound", "ping");
        stored.putInt("notify.soundVolume", 35);

        lines = ConfigReport.lines(null, null, stored);
        then(lineOf(lines, "notify.sound")).contains("ping").contains("(stored)");
        then(lineOf(lines, "notify.soundVolume")).contains("35").contains("(stored)");
        then(lines.stream().filter(l -> l.contains("unknown key")).count()).isZero();
    }

    @Test
    void a_stored_sound_that_is_not_bundled_or_a_volume_out_of_range_is_shown_as_ignored() {
        node.put("notify.sound", "dubstep");
        node.put("notify.soundVolume", "101");

        List<String> lines = ConfigReport.lines(null, null, stored);

        then(lineOf(lines, "notify.sound")).contains("chime").contains("stored value 'dubstep' ignored");
        then(lineOf(lines, "notify.soundVolume")).contains("80").contains("stored value '101' ignored");
    }

    @Test
    void a_custom_sound_shows_its_file() {
        stored.putString("notify.sound", "custom");
        stored.putString("notify.soundFile", "/home/ste/ding.wav");

        List<String> lines = ConfigReport.lines(null, null, stored);

        then(lineOf(lines, "notify.sound")).contains("custom").contains("(stored)");
        then(lineOf(lines, "notify.soundFile")).contains("/home/ste/ding.wav").contains("(stored)");
        then(lines.stream().filter(l -> l.contains("unknown key")).count()).isZero();
    }

    @Test
    void silent_is_a_valid_sound() {
        stored.putString("notify.sound", "none");

        then(lineOf(ConfigReport.lines(null, null, stored), "notify.sound")).contains("none").contains("(stored)");
    }
}
