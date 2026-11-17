package ste.wakemeapp.settings;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.BDDAssertions.then;

/** How long before a meeting the user is notified: one of a few choices (settings/US-000015). */
class LeadTimeTest {

    private final MemoryPreferences node = new MemoryPreferences();
    private final Settings settings = new Settings(node);

    @Test
    void the_choices_are_1_2_5_10_and_15_minutes() {
        then(LeadTime.CHOICES).containsExactly(1, 2, 5, 10, 15);
    }

    @Test
    void the_default_is_5_minutes() {
        then(LeadTime.read(settings)).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void a_saved_choice_is_read_back() {
        LeadTime.save(settings, 10);

        then(LeadTime.read(settings)).isEqualTo(Duration.ofMinutes(10));
        then(node.get(LeadTime.KEY, null)).isEqualTo("10");
    }

    @Test
    void a_stored_value_that_is_not_a_choice_is_ignored() {
        for (String stored : new String[] {"12", "0", "-1", "1440", "soon"}) {
            node.put(LeadTime.KEY, stored);

            then(LeadTime.read(settings)).isEqualTo(Duration.ofMinutes(5));
        }
    }

    @Test
    void only_the_choices_are_valid() {
        then(LeadTime.isValid("5")).isTrue();
        then(LeadTime.isValid(" 15 ")).isTrue();
        then(LeadTime.isValid("3")).isFalse();
        then(LeadTime.isValid("")).isFalse();
    }
}
