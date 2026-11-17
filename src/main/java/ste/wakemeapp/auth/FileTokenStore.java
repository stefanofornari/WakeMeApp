/*
 * Copyright 2026 the original author or authors from the WakeMeApp project (https://github.com/stefanofornari/WakeMeApp)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ste.wakemeapp.auth;

import dev.dirs.ProjectDirectories;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Keeps the sign-in in a file that only its owner can read and write (where the platform has POSIX
 * permissions). The text is stored in the clear: encryption and system password managers are out of
 * scope for now. A save goes to a temporary file that is moved over the old one, so a crash never
 * leaves a half written file.
 */
public class FileTokenStore implements TokenStore {

    private static final Logger LOG = Logger.getLogger(FileTokenStore.class.getName());
    private static final boolean POSIX = FileSystems.getDefault().supportedFileAttributeViews().contains("posix");

    private final Path file;
    private final String what;

    public FileTokenStore(Path file) {
        this(file, "sign-in");
    }

    /** @param what what the file holds, for the messages ("sign-in", "calendar") */
    public FileTokenStore(Path file, String what) {
        this.file = file;
        this.what = what;
    }

    /** The file of the application in its data folder, next to the logs. */
    public static Path defaultFile() {
        return Path.of(ProjectDirectories.from("", "", "WakeMeApp").dataLocalDir, "signin.json");
    }

    @Override
    public Optional<String> load() {
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException x) {
            LOG.warning("The saved " + what + " in " + file + " cannot be read and is ignored: " + x.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public synchronized void save(String text) throws IOException {
        Path folder = file.toAbsolutePath().getParent();
        Files.createDirectories(folder);
        Path temporary = Files.createTempFile(folder, file.getFileName().toString(), ".tmp");
        try {
            if (POSIX) {
                Files.setPosixFilePermissions(temporary, PosixFilePermissions.fromString("rw-------"));
            }
            write(temporary, text);
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException x) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);   // only left if something failed
        }
    }

    /** Writes the text to the (temporary) file; a test replaces it to simulate a failure. */
    void write(Path target, String text) throws IOException {
        Files.writeString(target, text, StandardCharsets.UTF_8);
    }

    @Override
    public synchronized void clear() throws IOException {
        Files.deleteIfExists(file);
    }
}
