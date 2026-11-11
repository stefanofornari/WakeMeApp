package ste.wakemeapp.auth;

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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.BDDAssertions.then;

/** The token file: private to the user, never half written. */
class FileTokenStoreTest {

    @TempDir
    Path dir;

    private final List<LogRecord> logged = new ArrayList<>();
    private final Logger log = Logger.getLogger(FileTokenStore.class.getName());
    private final Handler capture = new Handler() {
        @Override public void publish(LogRecord record) { logged.add(record); }
        @Override public void flush() { }
        @Override public void close() { }
    };

    @BeforeEach
    void captureLog() {
        log.addHandler(capture);
        log.setLevel(Level.ALL);
    }

    @AfterEach
    void releaseLog() {
        log.removeHandler(capture);
    }

    @Test
    void what_is_saved_is_loaded_even_in_a_folder_that_does_not_exist_yet() throws IOException {
        FileTokenStore store = new FileTokenStore(dir.resolve("a/b/signin.json"));

        store.save("{\"cache\":1}");

        then(store.load()).hasValue("{\"cache\":1}");
        then(new FileTokenStore(dir.resolve("a/b/signin.json")).load()).hasValue("{\"cache\":1}");
    }

    @Test
    void a_later_save_replaces_the_earlier_one() throws IOException {
        FileTokenStore store = new FileTokenStore(dir.resolve("signin.json"));

        store.save("one");
        store.save("two");

        then(store.load()).hasValue("two");
        then(Files.list(dir).count()).isEqualTo(1L);   // no temporary file left behind
    }

    @Test
    void there_is_nothing_to_load_without_a_file() throws IOException {
        then(new FileTokenStore(dir.resolve("signin.json")).load()).isEmpty();
    }

    @Test
    void the_file_can_be_read_and_written_by_its_owner_only() throws IOException {
        if (!FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            return;   // no POSIX permissions on this platform
        }
        FileTokenStore store = new FileTokenStore(dir.resolve("signin.json"));

        store.save("secret");
        store.save("secret again");

        then(PosixFilePermissions.toString(Files.getPosixFilePermissions(dir.resolve("signin.json"))))
            .isEqualTo("rw-------");
    }

    @Test
    void a_crash_during_a_save_keeps_the_old_file() throws IOException {
        Path file = dir.resolve("signin.json");
        new FileTokenStore(file).save("old");
        FileTokenStore crashing = new FileTokenStore(file) {
            @Override
            void write(Path target, String text) throws IOException {
                Files.writeString(target, text.substring(0, 2));   // half of it
                throw new IOException("disk full");
            }
        };

        try {
            crashing.save("new content");
            throw new AssertionError("the save was expected to fail");
        } catch (IOException expected) {
            then(expected.getMessage()).contains("disk full");
        }

        then(new FileTokenStore(file).load()).hasValue("old");
        then(Files.list(dir).count()).isEqualTo(1L);   // the half written file is removed
    }

    @Test
    void a_file_that_cannot_be_read_is_treated_as_empty_with_a_warning() throws IOException {
        Path notAFile = Files.createDirectory(dir.resolve("signin.json"));

        then(new FileTokenStore(notAFile).load()).isEmpty();

        then(logged).hasSize(1);
        then(logged.get(0).getLevel()).isEqualTo(Level.WARNING);
        then(logged.get(0).getMessage()).contains("saved sign-in").contains("ignored");
    }

    @Test
    void clear_removes_the_file_and_is_harmless_when_there_is_none() throws IOException {
        FileTokenStore store = new FileTokenStore(dir.resolve("signin.json"));
        store.clear();
        store.save("x");

        store.clear();

        then(store.load()).isEmpty();
        then(Files.exists(dir.resolve("signin.json"))).isFalse();
    }
}
