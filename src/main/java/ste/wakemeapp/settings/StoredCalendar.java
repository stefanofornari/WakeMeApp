package ste.wakemeapp.settings;

import dev.dirs.ProjectDirectories;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import ste.wakemeapp.auth.FileTokenStore;

/**
 * The calendar the user saved (an ICS link, a Microsoft Graph address or a CSV path), kept in one
 * file that only its owner can read, in the application data folder. It is not in Preferences: an
 * ICS link is a credential. The text is stored in the clear, see uzz/stories/settings/scope.md.
 */
public class StoredCalendar {

    private final FileTokenStore file;

    public StoredCalendar(Path path) {
        this.file = new FileTokenStore(path, "calendar");
    }

    /** The stored calendar of the user. */
    public static StoredCalendar user() {
        return new StoredCalendar(defaultFile());
    }

    public static Path defaultFile() {
        return Path.of(ProjectDirectories.from("", "", "WakeMeApp").dataLocalDir, "calendar.txt");
    }

    /** The saved value, or empty if there is none or the file cannot be read (a warning names the file). */
    public Optional<String> value() {
        return file.load().map(String::strip).filter(v -> !v.isEmpty());
    }

    /** Saves the value; a blank one removes what is stored. */
    public void save(String value) throws IOException {
        String text = value == null ? "" : value.strip();
        if (text.isEmpty()) {
            file.clear();
        } else {
            file.save(text);
        }
    }
}
