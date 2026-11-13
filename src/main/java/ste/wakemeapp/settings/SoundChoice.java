package ste.wakemeapp.settings;

/** What plays with a notification: a bundled sound, nothing, or a file of the user's. */
public record SoundChoice(Kind kind, String value) {

    public enum Kind { BUNDLED, NONE, CUSTOM }

    /** One of {@link SoundSetting#BUNDLED}. */
    public static SoundChoice bundled(String name) {
        return new SoundChoice(Kind.BUNDLED, name);
    }

    /** Silent notifications: the overlay only. */
    public static SoundChoice none() {
        return new SoundChoice(Kind.NONE, null);
    }

    /** A WAV or AIFF file, by its absolute path. */
    public static SoundChoice custom(String path) {
        return new SoundChoice(Kind.CUSTOM, path);
    }
}
