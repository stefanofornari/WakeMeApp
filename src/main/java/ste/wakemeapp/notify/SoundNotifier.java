package ste.wakemeapp.notify;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.logging.Logger;
import ste.wakemeapp.settings.Settings;
import ste.wakemeapp.settings.SoundChoice;
import ste.wakemeapp.settings.SoundFile;
import ste.wakemeapp.settings.SoundSetting;

/**
 * Plays the sound of a notification, as set: the chosen sound at the chosen volume, once. Notifications
 * less than a second apart share one sound. If the sound cannot be played the system beep is used and the
 * reason is logged (never shown to the user). Settings are read at each notification, so a change in the
 * settings dialog counts at once.
 */
public final class SoundNotifier {

    private static final Logger LOG = Logger.getLogger(SoundNotifier.class.getName());
    private static final Duration SHARED_SOUND = Duration.ofSeconds(1);

    private final Settings settings;
    private final SoundOutput output;
    private final Clock clock;
    private Instant lastPlayed;

    public SoundNotifier(Settings settings, SoundOutput output, Clock clock) {
        this.settings = settings;
        this.output = output;
        this.clock = clock;
    }

    /** Loads the chosen sound, so that the first notification has no delay. A failure is only logged. */
    public void prepare() {
        try {
            URI sound = uriOf(SoundSetting.choice(settings));
            if (sound != null) {
                output.prepare(sound);
            }
        } catch (RuntimeException x) {
            LOG.warning("The notification sound cannot be loaded: " + x.getMessage());
        }
    }

    /** Plays the sound for a notification that appears now. */
    public synchronized void play() {
        Instant now = clock.instant();
        if (lastPlayed != null && Duration.between(lastPlayed, now).compareTo(SHARED_SOUND) < 0) {
            LOG.fine("A sound was just played: this notification shares it");
            return;
        }
        if (start(SoundSetting.choice(settings), SoundSetting.volume(settings))) {
            lastPlayed = now;
        }
    }

    /**
     * Plays a sound to be heard, as the settings dialog does: the given choice at the given volume,
     * whatever is stored, and not held back by a sound just played.
     */
    public synchronized void preview(SoundChoice choice, int volumePercent) {
        output.stop();
        start(choice, volumePercent);
    }

    /** @return whether a sound was started (or the beep was used) */
    private boolean start(SoundChoice choice, int volumePercent) {
        if (volumePercent == 0 || choice.kind() == SoundChoice.Kind.NONE) {
            return false;   // silent on purpose: no beep either
        }
        try {
            output.play(uriOf(choice), volumePercent / 100.0);
        } catch (RuntimeException x) {
            LOG.warning("The notification sound cannot be played, using the system beep: " + x.getMessage());
            output.beep();
        }
        return true;
    }

    /** Stops the sound, for example when the user dismisses or snoozes the notification. */
    public void stop() {
        output.stop();
    }

    /** The sound to play; a custom file that is gone or unusable is replaced by the default, with a warning. */
    private static URI uriOf(SoundChoice choice) {
        return switch (choice.kind()) {
            case NONE -> null;
            case BUNDLED -> bundledUri(choice.value());
            case CUSTOM -> {
                if (SoundFile.isUsable(choice.value())) {
                    yield java.nio.file.Path.of(choice.value()).toUri();
                }
                LOG.warning("The custom sound " + choice.value() + " cannot be used: the default sound plays instead");
                yield bundledUri(SoundSetting.DEFAULT_SOUND);
            }
        };
    }

    private static URI bundledUri(String name) {
        var resource = SoundNotifier.class.getResource(SoundSetting.resourceOf(name));
        if (resource == null) {
            throw new IllegalStateException("the sound '" + name + "' is not in the application");
        }
        try {
            return resource.toURI();
        } catch (java.net.URISyntaxException x) {
            throw new IllegalStateException(x);
        }
    }
}
