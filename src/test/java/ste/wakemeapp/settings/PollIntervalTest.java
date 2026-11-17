package ste.wakemeapp.settings;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.BDDAssertions.then;

class PollIntervalTest {

    private final Settings settings = new Settings(new MemoryPreferences());

    @Test
    void should_poll_every_5_minutes_unless_chosen_otherwise() {
        then(PollInterval.read(settings)).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void should_read_back_a_saved_choice() {
        PollInterval.save(settings, 30);

        then(PollInterval.read(settings)).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    void should_ignore_a_stored_value_that_is_not_a_choice() {
        settings.putInt(PollInterval.KEY, 7);

        then(PollInterval.read(settings)).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void should_offer_1_2_5_10_15_and_30_minutes() {
        then(PollInterval.CHOICES).containsExactly(1, 2, 5, 10, 15, 30);
        then(PollInterval.isValid(" 15 ")).isTrue();
        then(PollInterval.isValid("3")).isFalse();
        then(PollInterval.isValid("")).isFalse();
    }
}
