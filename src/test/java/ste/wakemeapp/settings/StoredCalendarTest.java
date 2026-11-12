package ste.wakemeapp.settings;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.BDDAssertions.then;

/** The calendar the user saved: kept in a file only its owner can read, never in Preferences. */
class StoredCalendarTest {

    private static final String LINK = "https://outlook.office365.com/owa/calendar/abc123/secret456/calendar.ics";

    @TempDir
    Path folder;

    private StoredCalendar stored() {
        return new StoredCalendar(folder.resolve("calendar.txt"));
    }

    @Test
    void nothing_is_stored_at_first() {
        then(stored().value()).isEmpty();
    }

    @Test
    void a_saved_value_is_read_back_by_another_instance() throws IOException {
        stored().save(LINK);

        then(stored().value()).hasValue(LINK);
    }

    @Test
    void a_value_saved_over_another_replaces_it() throws IOException {
        StoredCalendar calendar = stored();
        calendar.save(LINK);
        calendar.save("meetings.csv");

        then(stored().value()).hasValue("meetings.csv");
    }

    @Test
    void spaces_around_the_value_are_dropped() throws IOException {
        stored().save("  " + LINK + " \n");

        then(stored().value()).hasValue(LINK);
    }

    @Test
    void saving_nothing_removes_the_value() throws IOException {
        StoredCalendar calendar = stored();
        calendar.save(LINK);

        calendar.save("   ");

        then(calendar.value()).isEmpty();
        then(folder.resolve("calendar.txt")).doesNotExist();
    }

    @Test
    void the_file_can_be_read_by_its_owner_only() throws IOException {
        if (!FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            return;   // no POSIX permissions on this platform
        }
        stored().save(LINK);

        then(PosixFilePermissions.toString(Files.getPosixFilePermissions(folder.resolve("calendar.txt"))))
            .isEqualTo("rw-------");
    }

    @Test
    void a_file_that_cannot_be_read_counts_as_not_set_and_the_warning_does_not_hold_the_value() throws IOException {
        Files.createDirectory(folder.resolve("calendar.txt"));   // a directory where the file should be
        List<String> warnings = new ArrayList<>();
        Logger log = Logger.getLogger("ste.wakemeapp.auth.FileTokenStore");
        Handler handler = new Handler() {
            @Override public void publish(LogRecord r) {
                if (r.getLevel().intValue() >= Level.WARNING.intValue()) {
                    warnings.add(r.getMessage());
                }
            }
            @Override public void flush() { }
            @Override public void close() { }
        };
        log.addHandler(handler);
        try {
            then(stored().value()).isEmpty();
        } finally {
            log.removeHandler(handler);
        }

        then(warnings).hasSize(1);
        then(warnings.get(0)).contains("saved calendar").contains("calendar.txt").doesNotContain("secret456");
    }

    @Test
    void the_default_file_is_in_the_data_folder_of_the_application() {
        then(StoredCalendar.defaultFile().getFileName().toString()).isEqualTo("calendar.txt");
        then(StoredCalendar.defaultFile().toString().toLowerCase()).contains("wakemeapp");
    }
}
