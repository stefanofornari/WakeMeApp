package ste.wakemeapp.settings;

import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * The user's settings, kept in Java Preferences (see uzz/stories/settings/scope.md). Reads give the
 * default for a missing or unusable value, writes are flushed at once; a failure is logged and never
 * stops the application.
 */
public class Settings {

    /** The node of the application in the user preferences. */
    public static final String NODE = "/ste/wakemeapp";

    private static final Logger LOG = Logger.getLogger(Settings.class.getName());

    private final Preferences node;

    /** @param node where the settings are kept; tests pass a node of their own */
    public Settings(Preferences node) {
        this.node = node;
    }

    /** The settings of the user running the application. */
    public static Settings user() {
        return new Settings(Preferences.userRoot().node(NODE));
    }

    public String getString(String key, String defaultValue) {
        return node.get(key, defaultValue);
    }

    public int getInt(String key, int defaultValue) {
        String value = node.get(key, null);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException x) {
            LOG.warning("The setting " + key + " is '" + value + "', which is not a number; using " + defaultValue);
            return defaultValue;
        }
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        String value = node.get(key, null);
        if (value == null) {
            return defaultValue;
        }
        if ("true".equalsIgnoreCase(value.trim())) {
            return true;
        }
        if ("false".equalsIgnoreCase(value.trim())) {
            return false;
        }
        LOG.warning("The setting " + key + " is '" + value + "', which is not true or false; using " + defaultValue);
        return defaultValue;
    }

    /** Forgets the stored value of a key. */
    public void remove(String key) {
        node.remove(key);
    }

    /** The keys that have a stored value; empty (with a WARNING) if the store cannot be read. */
    public Set<String> keys() {
        try {
            return new TreeSet<>(Set.of(node.keys()));
        } catch (BackingStoreException | RuntimeException x) {
            LOG.warning("The stored settings cannot be listed: " + x.getMessage());
            return Set.of();
        }
    }

    public void putString(String key, String value) {
        node.put(key, value);
        flush(key);
    }

    public void putInt(String key, int value) {
        node.put(key, Integer.toString(value));
        flush(key);
    }

    public void putBoolean(String key, boolean value) {
        node.put(key, Boolean.toString(value));
        flush(key);
    }

    private void flush(String key) {
        try {
            node.flush();
        } catch (BackingStoreException x) {
            LOG.warning("The setting " + key + " was not saved: " + x.getMessage());
            LOG.log(Level.FINE, "Details of the failure to save the settings", x);
        }
    }
}
