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
import static org.assertj.core.api.BDDAssertions.thenThrownBy;

/** Command line, stored value, default: which one wins for the Microsoft client id and tenant. */
class ConnectionSettingsTest {

    private static final String CLIENT_ID = "11111111-2222-3333-4444-555555555555";
    private static final String OTHER_CLIENT_ID = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";
    private static final String TENANT_ID = "99999999-8888-7777-6666-555555555555";

    private final MemoryPreferences node = new MemoryPreferences();
    private final Settings stored = new Settings(node);
    private final List<LogRecord> logged = new ArrayList<>();
    private final Logger log = Logger.getLogger(ConnectionSettings.class.getName());
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
    void the_command_line_overrules_the_stored_value_and_is_not_saved() {
        stored.putString("graph.tenant", "organizations");

        ConnectionSettings connection = ConnectionSettings.resolve(null, TENANT_ID, stored);

        then(connection.tenant()).isEqualTo(TENANT_ID);
        then(stored.getString("graph.tenant", null)).isEqualTo("organizations");
    }

    @Test
    void the_command_line_client_id_overrules_the_stored_one() {
        stored.putString("graph.clientId", CLIENT_ID);

        then(ConnectionSettings.resolve(OTHER_CLIENT_ID, null, stored).clientId()).isEqualTo(OTHER_CLIENT_ID);
        then(stored.getString("graph.clientId", null)).isEqualTo(CLIENT_ID);
    }

    @Test
    void the_stored_values_are_used_without_options() {
        stored.putString("graph.clientId", CLIENT_ID);
        stored.putString("graph.tenant", TENANT_ID);

        ConnectionSettings connection = ConnectionSettings.resolve(null, null, stored);

        then(connection.clientId()).isEqualTo(CLIENT_ID);
        then(connection.tenant()).isEqualTo(TENANT_ID);
    }

    @Test
    void the_defaults_are_used_when_nothing_is_given_or_stored() {
        ConnectionSettings connection = ConnectionSettings.resolve(null, null, stored);

        then(connection.clientId()).isNull();
        then(connection.tenant()).isEqualTo("organizations");
    }

    @Test
    void organizations_is_a_valid_tenant() {
        then(ConnectionSettings.resolve(null, "organizations", stored).tenant()).isEqualTo("organizations");
    }

    @Test
    void an_invalid_client_id_on_the_command_line_names_the_option() {
        thenThrownBy(() -> ConnectionSettings.validate("not-a-uuid", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("--client-id must be a UUID");
        thenThrownBy(() -> ConnectionSettings.resolve("not-a-uuid", null, stored))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void an_invalid_tenant_on_the_command_line_names_the_option() {
        thenThrownBy(() -> ConnectionSettings.validate(null, "common"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("--tenant must be a UUID or \"organizations\"");
    }

    @Test
    void a_missing_option_is_valid() {
        ConnectionSettings.validate(null, null);
        ConnectionSettings.validate(CLIENT_ID, TENANT_ID);
    }

    @Test
    void an_invalid_stored_value_is_ignored_with_a_warning() {
        node.put("graph.clientId", "not-a-uuid");
        node.put("graph.tenant", "common");

        ConnectionSettings connection = ConnectionSettings.resolve(null, null, stored);

        then(connection.clientId()).isNull();
        then(connection.tenant()).isEqualTo("organizations");
        then(logged).hasSize(2);
        then(logged.get(0).getLevel()).isEqualTo(Level.WARNING);
        then(logged.get(0).getMessage()).contains("graph.clientId").contains("not-a-uuid");
        then(logged.get(1).getMessage()).contains("graph.tenant").contains("common");
    }

    @Test
    void a_client_id_is_required_to_sign_in() {
        thenThrownBy(() -> ConnectionSettings.resolve(null, null, stored).requireClientId())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("A client id is needed")
            .hasMessageContaining("--client-id");
        then(ConnectionSettings.resolve(CLIENT_ID, null, stored).requireClientId()).isEqualTo(CLIENT_ID);
    }
}
