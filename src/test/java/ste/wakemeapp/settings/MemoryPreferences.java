package ste.wakemeapp.settings;

import java.util.HashMap;
import java.util.Map;
import java.util.prefs.AbstractPreferences;
import java.util.prefs.BackingStoreException;

/** A Preferences node that lives in memory, so that tests never touch the real user settings. */
class MemoryPreferences extends AbstractPreferences {

    private final Map<String, String> values = new HashMap<>();
    int flushes;
    boolean failFlush;

    MemoryPreferences() {
        super(null, "");
    }

    @Override protected void putSpi(String key, String value) { values.put(key, value); }
    @Override protected String getSpi(String key) { return values.get(key); }
    @Override protected void removeSpi(String key) { values.remove(key); }
    @Override protected void removeNodeSpi() { }
    @Override protected String[] keysSpi() { return values.keySet().toArray(new String[0]); }
    @Override protected String[] childrenNamesSpi() { return new String[0]; }
    @Override protected AbstractPreferences childSpi(String name) { return new MemoryPreferences(this, name); }
    @Override protected void syncSpi() { }

    @Override
    protected void flushSpi() throws BackingStoreException {
        flushes++;
        if (failFlush) {
            throw new BackingStoreException("store not writable");
        }
    }

    private MemoryPreferences(AbstractPreferences parent, String name) {
        super(parent, name);
    }
}
