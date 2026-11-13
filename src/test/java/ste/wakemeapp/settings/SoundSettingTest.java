package ste.wakemeapp.settings;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.BDDAssertions.then;

/** Which bundled sound plays and how loud (notify/US-000008). */
class SoundSettingTest {

    private final MemoryPreferences node = new MemoryPreferences();
    private final Settings settings = new Settings(node);

    @Test
    void the_bundled_sounds_are_chime_pop_ping_and_bell() {
        then(SoundSetting.BUNDLED).containsExactly("chime", "pop", "ping", "bell");
    }

    @Test
    void the_default_sound_is_the_chime_at_80_percent() {
        then(SoundSetting.sound(settings)).isEqualTo("chime");
        then(SoundSetting.volume(settings)).isEqualTo(80);
    }

    @Test
    void a_saved_sound_and_volume_are_read_back() {
        settings.putString(SoundSetting.SOUND_KEY, "ping");
        settings.putInt(SoundSetting.VOLUME_KEY, 35);

        then(SoundSetting.sound(settings)).isEqualTo("ping");
        then(SoundSetting.volume(settings)).isEqualTo(35);
    }

    @Test
    void the_volume_limits_are_valid() {
        settings.putInt(SoundSetting.VOLUME_KEY, 0);
        then(SoundSetting.volume(settings)).isZero();

        settings.putInt(SoundSetting.VOLUME_KEY, 100);
        then(SoundSetting.volume(settings)).isEqualTo(100);
    }

    @Test
    void a_volume_out_of_range_or_not_a_number_is_ignored() {
        for (String stored : new String[] {"101", "-1", "loud"}) {
            node.put(SoundSetting.VOLUME_KEY, stored);

            then(SoundSetting.volume(settings)).isEqualTo(80);
        }
    }

    @Test
    void a_sound_that_is_not_bundled_is_ignored() {
        node.put(SoundSetting.SOUND_KEY, "dubstep");

        then(SoundSetting.sound(settings)).isEqualTo("chime");
    }

    @Test
    void every_bundled_sound_is_in_the_jar() {
        for (String name : SoundSetting.BUNDLED) {
            then(SoundSetting.class.getResource(SoundSetting.resourceOf(name))).as(name).isNotNull();
        }
    }
}
