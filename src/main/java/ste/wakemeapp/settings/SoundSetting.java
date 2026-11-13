package ste.wakemeapp.settings;

import java.util.List;
import java.util.logging.Logger;

/** Which sound plays with a notification and how loud (0 to 100 %); the sounds come with the application. */
public final class SoundSetting {

    public static final String SOUND_KEY = "notify.sound";
    public static final String VOLUME_KEY = "notify.soundVolume";
    public static final List<String> BUNDLED = List.of("chime", "pop", "ping", "bell");
    public static final String DEFAULT_SOUND = "chime";
    public static final int DEFAULT_VOLUME = 80;

    private static final Logger LOG = Logger.getLogger(SoundSetting.class.getName());

    private SoundSetting() {
    }

    /** The name of the chosen bundled sound; one that is not bundled is ignored with a WARNING. */
    public static String sound(Settings stored) {
        String name = stored.getString(SOUND_KEY, DEFAULT_SOUND);
        if (!BUNDLED.contains(name)) {
            LOG.warning("The setting " + SOUND_KEY + " is '" + name + "', which is not one of " + BUNDLED
                + "; using " + DEFAULT_SOUND);
            return DEFAULT_SOUND;
        }
        return name;
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
