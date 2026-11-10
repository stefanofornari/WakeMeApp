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
        then(lines).hasSize(2);
    }
}
