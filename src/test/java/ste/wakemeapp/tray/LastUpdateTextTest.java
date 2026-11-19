package ste.wakemeapp.tray;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.BDDAssertions.then;

class LastUpdateTextTest {

    // 19 Nov 2026, 17:00 in Rome (16:00 UTC)
    private static final Clock NOW = Clock.fixed(Instant.parse("2026-11-19T16:00:00Z"), ZoneId.of("Europe/Rome"));

    private static String text(String instant) {
        return LastUpdateText.of(Optional.of(Instant.parse(instant)), NOW, Locale.UK);
    }

    @Test
    void should_show_a_dash_when_there_is_no_last_update() {
        then(LastUpdateText.of(Optional.empty(), NOW, Locale.UK)).isEqualTo("Last update: \u2014");
    }

    @Test
    void should_show_only_the_time_when_it_was_today() {
        then(text("2026-11-19T15:49:00Z")).isEqualTo("Last update: 16:49");
    }

    @Test
    void should_show_the_date_and_the_time_when_it_was_another_day() {
        then(text("2026-11-18T15:49:00Z")).isEqualTo("Last update: 18 Nov, 16:49");
    }

    @Test
    void should_decide_the_day_in_the_zone_of_the_clock() {
        // 23:30 UTC on the 18th is 00:30 on the 19th in Rome: today
        then(text("2026-11-18T23:30:00Z")).isEqualTo("Last update: 00:30");
    }

    @Test
    void should_use_the_formats_of_the_locale() {
        then(LastUpdateText.of(Optional.of(Instant.parse("2026-11-18T15:49:00Z")), NOW, Locale.ITALY))
            .isEqualTo("Last update: 18 nov, 16:49");
    }
}
