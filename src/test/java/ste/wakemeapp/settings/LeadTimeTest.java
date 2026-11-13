package ste.wakemeapp.settings;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;

/** How long before a meeting the user is notified (settings/US-000015). */
class LeadTimeTest {

    private final MemoryPreferences node = new MemoryPreferences();
    private final Settings settings = new Settings(node);

    @Test
    void the_default_is_5_minutes() {
        then(LeadTime.read(settings)).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void a_saved_value_is_read_back() {
        LeadTime.save(settings, 10);

        then(LeadTime.read(settings)).isEqualTo(Duration.ofMinutes(10));
        then(node.get(LeadTime.KEY, null)).isEqualTo("10");
    }

    @Test
    void the_limits_are_accepted() {
        then(LeadTime.parse("1")).isEqualTo(1);
        then(LeadTime.parse(" 1440 ")).isEqualTo(1440);
    }

    @Test
    void zero_and_negative_are_refused() {
        thenThrownBy(() -> LeadTime.parse("0")).isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Lead time must be at least 1 minute");
        thenThrownBy(() -> LeadTime.parse("-3")).isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Lead time must be at least 1 minute");
    }

    @Test
    void more_than_a_day_is_refused() {
        thenThrownBy(() -> LeadTime.parse("2000")).isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Lead time cannot exceed 1440 minutes (24 hours)");
    }

    @Test
    void something_that_is_not_a_number_is_refused() {
        thenThrownBy(() -> LeadTime.parse("soon")).isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Lead time must be a whole number of minutes");
        thenThrownBy(() -> LeadTime.parse("")).isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Lead time must be a whole number of minutes");
    }

    @Test
    void a_stored_value_out_of_range_is_ignored() {
        node.put(LeadTime.KEY, "5000");
        then(LeadTime.read(settings)).isEqualTo(Duration.ofMinutes(5));

        node.put(LeadTime.KEY, "0");
        then(LeadTime.read(settings)).isEqualTo(Duration.ofMinutes(5));

        node.put(LeadTime.KEY, "soon");
        then(LeadTime.read(settings)).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void a_poll_interval_longer_than_the_lead_time_gets_a_warning() {
        then(LeadTime.pollWarning(5, Duration.ofMinutes(10))).hasValue(
            "Poll interval (10 min) exceeds lead time (5 min). Notifications may be delayed.");
    }

    @Test
    void a_poll_interval_not_longer_than_the_lead_time_gets_none() {
        then(LeadTime.pollWarning(5, Duration.ofMinutes(5))).isEmpty();
        then(LeadTime.pollWarning(30, Duration.ofMinutes(5))).isEmpty();
    }
}
