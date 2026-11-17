package ste.wakemeapp.notify;

import java.net.URI;

/** Where the sound of a notification goes: the speakers, or something that records it in a test. */
public interface SoundOutput {

    /** Loads a sound so that it can start without delay later. May do nothing. */
    void prepare(URI sound);

    /**
     * Plays the sound once, without waiting for it to end.
     *
     * @param volume 0.0 to 1.0, independent of the volume of the system
     */
    void play(URI sound, double volume);

    /** Stops what is playing. */
    void stop();

    /** The system beep, for when the sound cannot be played. */
    void beep();
}
