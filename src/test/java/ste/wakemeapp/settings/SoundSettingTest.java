package ste.wakemeapp.settings;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.BDDAssertions.then;

/** Which sound plays and how loud (notify/US-000008, settings/US-000016). */
class SoundSettingTest {

    private final MemoryPreferences node = new MemoryPreferences();
    private final Settings settings = new Settings(node);

    @Test
    void the_bundled_sounds_are_chime_pop_ping_and_bell() {
        then(SoundSetting.BUNDLED).containsExactly("chime", "pop", "ping", "bell");
    }

    @Test
    void the_default_is_the_chime_at_80_percent() {
        then(SoundSetting.choice(settings)).isEqualTo(SoundChoice.bundled("chime"));
        then(SoundSetting.volume(settings)).isEqualTo(80);
    }

    @Test
    void a_saved_bundled_sound_and_volume_are_read_back() {
        SoundSetting.save(settings, SoundChoice.bundled("ping"), 35);

        then(SoundSetting.choice(settings)).isEqualTo(SoundChoice.bundled("ping"));
        then(SoundSetting.volume(settings)).isEqualTo(35);
    }

    @Test
    void silent_is_saved_and_read_back() {
        SoundSetting.save(settings, SoundChoice.none(), 80);

        then(SoundSetting.choice(settings)).isEqualTo(SoundChoice.none());
        then(node.get(SoundSetting.SOUND_KEY, null)).isEqualTo("none");
    }

    @Test
    void a_custom_file_is_saved_with_its_path_and_read_back() {
        SoundSetting.save(settings, SoundChoice.custom("/home/ste/sounds/ding.wav"), 80);

        then(SoundSetting.choice(settings)).isEqualTo(SoundChoice.custom("/home/ste/sounds/ding.wav"));
        then(node.get(SoundSetting.SOUND_KEY, null)).isEqualTo("custom");
        then(node.get(SoundSetting.FILE_KEY, null)).isEqualTo("/home/ste/sounds/ding.wav");
    }

    @Test
    void choosing_a_bundled_sound_after_a_custom_one_forgets_the_file() {
        SoundSetting.save(settings, SoundChoice.custom("/home/ste/sounds/ding.wav"), 80);

        SoundSetting.save(settings, SoundChoice.bundled("bell"), 80);

        then(node.get(SoundSetting.FILE_KEY, null)).isNull();
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
    void a_sound_that_is_not_known_is_ignored() {
        node.put(SoundSetting.SOUND_KEY, "dubstep");

        then(SoundSetting.choice(settings)).isEqualTo(SoundChoice.bundled("chime"));
    }

    @Test
    void a_custom_choice_without_a_file_is_ignored() {
        node.put(SoundSetting.SOUND_KEY, "custom");

        then(SoundSetting.choice(settings)).isEqualTo(SoundChoice.bundled("chime"));
    }

    @Test
    void every_bundled_sound_is_in_the_jar() {
        for (String name : SoundSetting.BUNDLED) {
            then(SoundSetting.class.getResource(SoundSetting.resourceOf(name))).as(name).isNotNull();
        }
    }
}
