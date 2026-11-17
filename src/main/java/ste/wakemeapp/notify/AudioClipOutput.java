package ste.wakemeapp.notify;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import javafx.scene.media.AudioClip;

/** Plays through JavaFX {@link AudioClip}: WAV and AIFF, no delay at start, volume of its own. */
public final class AudioClipOutput implements SoundOutput {

    private final Map<URI, AudioClip> clips = new HashMap<>();
    private AudioClip playing;

    @Override
    public synchronized void prepare(URI sound) {
        clip(sound);
    }

    @Override
    public synchronized void play(URI sound, double volume) {
        AudioClip clip = clip(sound);
        clip.setVolume(volume);
        clip.play();
        playing = clip;
    }

    @Override
    public synchronized void stop() {
        if (playing != null) {
            playing.stop();
            playing = null;
        }
    }

    @Override
    public void beep() {
        java.awt.Toolkit.getDefaultToolkit().beep();
    }

    private AudioClip clip(URI sound) {
        return clips.computeIfAbsent(sound, uri -> new AudioClip(uri.toString()));
    }
}
