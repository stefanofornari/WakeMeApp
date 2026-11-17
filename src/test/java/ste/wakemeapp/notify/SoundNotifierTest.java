package ste.wakemeapp.notify;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ste.wakemeapp.settings.MemoryPreferences;
import ste.wakemeapp.settings.Settings;
import ste.wakemeapp.settings.SoundChoice;
import ste.wakemeapp.settings.SoundSetting;
import static org.assertj.core.api.BDDAssertions.then;

/** The sound that plays with a meeting notification (notify/US-000008). */
class SoundNotifierTest {

    /** Records what would be played instead of playing it. */
    private static final class FakeOutput implements SoundOutput {
        final List<String> played = new ArrayList<>();
        final List<Double> volumes = new ArrayList<>();
        final List<String> prepared = new ArrayList<>();
        int beeps;
        int stops;
        RuntimeException failure;

        @Override public void play(URI sound, double volume) {
            if (failure != null) {
                throw failure;
            }
            played.add(sound.toString());
            volumes.add(volume);
        }
        @Override public void prepare(URI sound) { prepared.add(sound.toString()); }
        @Override public void stop() { stops++; }
        @Override public void beep() { beeps++; }
    }

    private static final class MovableClock extends Clock {
        Instant now = Instant.parse("2026-11-13T09:00:00Z");
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    @TempDir
    Path folder;

    private final Settings settings = new Settings(new MemoryPreferences());
    private final FakeOutput output = new FakeOutput();
    private final MovableClock clock = new MovableClock();
    private final SoundNotifier notifier = new SoundNotifier(settings, output, clock);

    @Test
    void the_default_sound_plays_once_at_the_default_volume() {
        notifier.play();

        then(output.played).hasSize(1);
        then(output.played.get(0)).endsWith("/sounds/chime.wav");
        then(output.volumes).containsExactly(0.8);
        then(output.beeps).isZero();
    }

    @Test
    void the_chosen_bundled_sound_plays() {
        settings.putString(SoundSetting.SOUND_KEY, "bell");

        notifier.play();

        then(output.played.get(0)).endsWith("/sounds/bell.wav");
    }

    @Test
    void the_volume_is_the_setting_as_a_fraction() {
        settings.putInt(SoundSetting.VOLUME_KEY, 50);

        notifier.play();

        then(output.volumes).containsExactly(0.5);
    }

    @Test
    void a_volume_of_zero_plays_nothing_and_does_not_beep() {
        settings.putInt(SoundSetting.VOLUME_KEY, 0);

        notifier.play();

        then(output.played).isEmpty();
        then(output.beeps).isZero();
    }

    @Test
    void a_sound_that_cannot_be_played_gives_the_system_beep_and_a_logged_warning() {
        output.failure = new IllegalStateException("no audio device");
        List<String> warnings = new ArrayList<>();
        Logger log = Logger.getLogger(SoundNotifier.class.getName());
        Handler handler = new Handler() {
            @Override public void publish(LogRecord r) {
                if (r.getLevel().intValue() >= Level.WARNING.intValue()) {
                    warnings.add(r.getMessage());
                }
            }
            @Override public void flush() { }
            @Override public void close() { }
        };
        log.addHandler(handler);
        try {
            notifier.play();
        } finally {
            log.removeHandler(handler);
        }

        then(output.beeps).isEqualTo(1);
        then(warnings).hasSize(1);
        then(warnings.get(0)).contains("system beep").contains("no audio device");
    }

    @Test
    void two_notifications_within_a_second_play_one_sound() {
        notifier.play();
        clock.now = clock.now.plusMillis(900);

        notifier.play();

        then(output.played).hasSize(1);
    }

    @Test
    void a_notification_a_second_later_plays_again() {
        notifier.play();
        clock.now = clock.now.plus(Duration.ofSeconds(1));

        notifier.play();

        then(output.played).hasSize(2);
    }

    @Test
    void stopping_stops_the_output() {
        notifier.stop();

        then(output.stops).isEqualTo(1);
    }

    @Test
    void preparing_loads_the_chosen_sound_without_playing_it() {
        notifier.prepare();

        then(output.played).isEmpty();
        then(output.prepared).hasSize(1);
    }

    private Path wav() throws IOException {
        AudioFormat format = new AudioFormat(44100f, 16, 1, true, false);
        AudioInputStream in = new AudioInputStream(new ByteArrayInputStream(new byte[8820]), format, 4410);
        Path file = folder.resolve("ding.wav");
        AudioSystem.write(in, AudioFileFormat.Type.WAVE, file.toFile());
        return file;
    }

    private List<String> warningsWhile(Runnable action) {
        List<String> warnings = new ArrayList<>();
        Logger log = Logger.getLogger(SoundNotifier.class.getName());
        Handler handler = new Handler() {
            @Override public void publish(LogRecord r) {
                if (r.getLevel().intValue() >= Level.WARNING.intValue()) {
                    warnings.add(r.getMessage());
                }
            }
            @Override public void flush() { }
            @Override public void close() { }
        };
        log.addHandler(handler);
        try {
            action.run();
        } finally {
            log.removeHandler(handler);
        }
        return warnings;
    }

    @Test
    void silent_plays_nothing_and_does_not_beep() {
        SoundSetting.save(settings, SoundChoice.none(), 80);

        notifier.play();

        then(output.played).isEmpty();
        then(output.beeps).isZero();
    }

    @Test
    void a_custom_file_plays() throws IOException {
        Path file = wav();
        SoundSetting.save(settings, SoundChoice.custom(file.toString()), 80);

        notifier.play();

        then(output.played).containsExactly(file.toUri().toString());
    }

    @Test
    void a_custom_file_that_is_gone_plays_the_default_sound_and_logs_a_warning() {
        SoundSetting.save(settings, SoundChoice.custom(folder.resolve("gone.wav").toString()), 80);

        List<String> warnings = warningsWhile(notifier::play);

        then(output.played).hasSize(1);
        then(output.played.get(0)).endsWith("/sounds/chime.wav");
        then(output.beeps).isZero();
        then(warnings).hasSize(1);
        then(warnings.get(0)).contains("custom sound").contains("gone.wav");
    }

    @Test
    void a_preview_plays_the_given_choice_at_the_given_volume_whatever_the_settings() {
        SoundSetting.save(settings, SoundChoice.none(), 10);

        notifier.preview(SoundChoice.bundled("pop"), 30);

        then(output.played).hasSize(1);
        then(output.played.get(0)).endsWith("/sounds/pop.wav");
        then(output.volumes).containsExactly(0.3);
    }

    @Test
    void a_preview_is_not_held_back_by_the_sound_just_played() {
        notifier.play();

        notifier.preview(SoundChoice.bundled("ping"), 80);

        then(output.played).hasSize(2);
    }

    @Test
    void a_preview_of_silent_or_volume_zero_plays_nothing() {
        notifier.preview(SoundChoice.none(), 80);
        notifier.preview(SoundChoice.bundled("pop"), 0);

        then(output.played).isEmpty();
    }

    @Test
    void a_preview_stops_the_sound_that_is_playing_first() {
        notifier.preview(SoundChoice.bundled("pop"), 80);

        then(output.stops).isEqualTo(1);
    }
}
