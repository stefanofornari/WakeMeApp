package ste.wakemeapp.settings;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;

/** A sound file the user picked: it has to be a WAV or AIFF that can be played (settings/US-000016). */
class SoundFileTest {

    @TempDir
    Path folder;

    private Path audio(String name, AudioFileFormat.Type type) throws IOException {
        AudioFormat format = new AudioFormat(44100f, 16, 1, true, false);
        byte[] silence = new byte[4410 * 2];
        AudioInputStream in = new AudioInputStream(new ByteArrayInputStream(silence), format, 4410);
        Path file = folder.resolve(name);
        AudioSystem.write(in, type, file.toFile());
        return file;
    }

    @Test
    void a_wav_file_is_accepted() throws IOException {
        SoundFile.validate(audio("ding.wav", AudioFileFormat.Type.WAVE).toString());
    }

    @Test
    void an_aiff_file_is_accepted() throws IOException {
        SoundFile.validate(audio("ding.aiff", AudioFileFormat.Type.AIFF).toString());
    }

    @Test
    void a_text_file_is_refused() throws IOException {
        Path text = Files.writeString(folder.resolve("notes.txt"), "not audio");

        thenThrownBy(() -> SoundFile.validate(text.toString()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Selected file is not a valid audio format (WAV or AIFF)");
    }

    @Test
    void a_text_file_with_a_wav_name_is_refused() throws IOException {
        Path fake = Files.writeString(folder.resolve("fake.wav"), "not audio");

        thenThrownBy(() -> SoundFile.validate(fake.toString()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Selected file is not a valid audio format (WAV or AIFF)");
    }

    @Test
    void a_missing_file_is_refused() {
        thenThrownBy(() -> SoundFile.validate(folder.resolve("missing.wav").toString()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("cannot be read");
    }

    @Test
    void a_file_is_usable_when_it_is_valid() throws IOException {
        then(SoundFile.isUsable(audio("ding.wav", AudioFileFormat.Type.WAVE).toString())).isTrue();
        then(SoundFile.isUsable(folder.resolve("missing.wav").toString())).isFalse();
    }
}
