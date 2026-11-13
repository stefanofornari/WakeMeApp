package ste.wakemeapp.notify;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.logging.Logger;
import ste.wakemeapp.settings.Settings;
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
            output.prepare(uriOf(SoundSetting.sound(settings)));
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
        int volume = SoundSetting.volume(settings);
        if (volume == 0) {
            return;   // silent on purpose: no beep either
        }
        lastPlayed = now;
        try {
            output.play(uriOf(SoundSetting.sound(settings)), volume / 100.0);
        } catch (RuntimeException x) {
            LOG.warning("The notification sound cannot be played, using the system beep: " + x.getMessage());
            output.beep();
        }
    }

    /** Stops the sound, for example when the user dismisses or snoozes the notification. */
    public void stop() {
        output.stop();
    }

    private static URI uriOf(String name) {
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
