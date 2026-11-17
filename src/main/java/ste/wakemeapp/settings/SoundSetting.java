package ste.wakemeapp.settings;

import java.util.List;
import java.util.logging.Logger;

/** Which sound plays with a notification and how loud (0 to 100 %); the sounds come with the application. */
public final class SoundSetting {

    public static final String SOUND_KEY = "notify.sound";
    public static final String FILE_KEY = "notify.soundFile";
    public static final String VOLUME_KEY = "notify.soundVolume";
    public static final String NONE = "none";
    public static final String CUSTOM = "custom";
    public static final List<String> BUNDLED = List.of("chime", "pop", "ping", "bell");
    public static final String DEFAULT_SOUND = "chime";
    public static final int DEFAULT_VOLUME = 80;

    private static final Logger LOG = Logger.getLogger(SoundSetting.class.getName());

    private SoundSetting() {
    }

    /** What plays: the stored choice; one that is not valid is ignored with a WARNING (the default plays). */
    public static SoundChoice choice(Settings stored) {
        String value = stored.getString(SOUND_KEY, DEFAULT_SOUND);
        if (value.equals(NONE)) {
            return SoundChoice.none();
        }
        if (value.equals(CUSTOM)) {
            String file = stored.getString(FILE_KEY, "");
            if (!file.isBlank()) {
                return SoundChoice.custom(file);
            }
            LOG.warning("The setting " + SOUND_KEY + " is 'custom' but " + FILE_KEY + " is not set; using "
                + DEFAULT_SOUND);
            return SoundChoice.bundled(DEFAULT_SOUND);
        }
        if (BUNDLED.contains(value)) {
            return SoundChoice.bundled(value);
        }
        LOG.warning("The setting " + SOUND_KEY + " is '" + value + "', which is not one of " + BUNDLED + ", '"
            + NONE + "' or '" + CUSTOM + "'; using " + DEFAULT_SOUND);
        return SoundChoice.bundled(DEFAULT_SOUND);
    }

    /** Saves the choice and the volume; the file of an earlier custom choice is forgotten. */
    public static void save(Settings stored, SoundChoice choice, int volume) {
        switch (choice.kind()) {
            case BUNDLED -> {
                stored.putString(SOUND_KEY, choice.value());
                stored.remove(FILE_KEY);
            }
            case NONE -> {
                stored.putString(SOUND_KEY, NONE);
                stored.remove(FILE_KEY);
            }
            case CUSTOM -> {
                stored.putString(SOUND_KEY, CUSTOM);
                stored.putString(FILE_KEY, choice.value());
            }
        }
        stored.putInt(VOLUME_KEY, volume);
    }

    static boolean isValidChoice(String value) {
        return BUNDLED.contains(value) || value.equals(NONE) || value.equals(CUSTOM);
    }

    /** The volume in percent; one outside 0 to 100 is ignored with a WARNING. */
    public static int volume(Settings stored) {
        int percent = stored.getInt(VOLUME_KEY, DEFAULT_VOLUME);
        if (!isValidVolume(percent)) {
            LOG.warning("The setting " + VOLUME_KEY + " is '" + percent + "', which is not between 0 and 100; using "
                + DEFAULT_VOLUME);
            return DEFAULT_VOLUME;
        }
        return percent;
    }

    static boolean isValidVolume(int percent) {
        return percent >= 0 && percent <= 100;
    }

    static boolean isValidVolume(String text) {
        try {
            return isValidVolume(Integer.parseInt(text.trim()));
        } catch (NumberFormatException x) {
            return false;
        }
    }

    /** The classpath resource of a bundled sound. */
    public static String resourceOf(String name) {
        return "/ste/wakemeapp/notify/sounds/" + name + ".wav";
    }
}
