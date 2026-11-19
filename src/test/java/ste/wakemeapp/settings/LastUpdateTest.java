package ste.wakemeapp.settings;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.BDDAssertions.then;

class LastUpdateTest {

    private final Settings settings = new Settings(new MemoryPreferences());

    @Test
    void should_have_no_last_update_when_nothing_is_stored() {
        then(LastUpdate.read(settings)).isEmpty();
    }

    @Test
    void should_read_back_what_was_saved_to_the_second() {
        LastUpdate.save(settings, Instant.parse("2026-11-19T15:49:12.987Z"));

        then(LastUpdate.read(settings).get()).isEqualTo(Instant.parse("2026-11-19T15:49:12Z"));
        then(settings.getString("calendar.last_update", "")).isEqualTo("2026-11-19T15:49:12Z");
    }

    @Test
    void should_forget_it_when_cleared() {
        LastUpdate.save(settings, Instant.parse("2026-11-19T15:49:12Z"));

        LastUpdate.clear(settings);

        then(LastUpdate.read(settings)).isEmpty();
        then(settings.keys()).doesNotContain("calendar.last_update");
    }

    @Test
    void should_ignore_a_stored_value_that_is_not_a_date_and_time() {
        settings.putString(LastUpdate.KEY, "yesterday");

        then(LastUpdate.read(settings)).isEmpty();
        then(LastUpdate.isValid("yesterday")).isFalse();
        then(LastUpdate.isValid("2026-11-19T15:49:12Z")).isTrue();
    }
}
