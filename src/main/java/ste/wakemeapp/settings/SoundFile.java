package ste.wakemeapp.settings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

/** A sound file of the user: a WAV or AIFF that exists, can be read and is really audio. */
public final class SoundFile {

    private SoundFile() {
    }

    /** @throws IllegalArgumentException with a message for the user if the file cannot be used */
    public static void validate(String path) {
        Path file;
        try {
            file = Path.of(path);
        } catch (InvalidPathException x) {
            throw new IllegalArgumentException("The sound file cannot be read: " + path);
        }
        if (!Files.isRegularFile(file) || !Files.isReadable(file)) {
            throw new IllegalArgumentException("The sound file cannot be read: " + path);
        }
        try {
            AudioFileFormat format = AudioSystem.getAudioFileFormat(file.toFile());
            String type = format.getType().toString().toUpperCase(java.util.Locale.ROOT);
            if (!type.startsWith("WAV") && !type.startsWith("AIF")) {
                throw new UnsupportedAudioFileException(type);
            }
        } catch (UnsupportedAudioFileException | IOException x) {
            throw new IllegalArgumentException("Selected file is not a valid audio format (WAV or AIFF)");
        }
    }

    public static boolean isUsable(String path) {
        try {
            validate(path);
            return true;
        } catch (IllegalArgumentException x) {
            return false;
        }
    }
}
