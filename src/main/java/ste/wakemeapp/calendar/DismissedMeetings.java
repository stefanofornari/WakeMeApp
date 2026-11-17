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

package ste.wakemeapp.calendar;

import dev.dirs.ProjectDirectories;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.json.JSONObject;

/**
 * The meeting occurrences the user dismissed: an event id together with the start it had. Keying on
 * the start makes a dismissal apply to that occurrence only (a recurring series has a new start the
 * next time) and makes it lapse by itself when the meeting is moved.
 * <p>
 * With a file the dismissals survive a restart; entries whose meeting started more than a day ago
 * are dropped when the file is read. A file that cannot be read or written is logged and the
 * dismissals stay in memory: not being able to remember must never stop the app.
 */
public final class DismissedMeetings {

    private static final Logger LOG = Logger.getLogger(DismissedMeetings.class.getName());
    private static final java.time.Duration KEEP = java.time.Duration.ofDays(1);

    private final Path file;
    private final Map<String, LocalDateTime> dismissed = new ConcurrentHashMap<>();

    /** Keeps the dismissals in memory only. */
    public DismissedMeetings() {
        this.file = null;
    }

    public DismissedMeetings(Path file) {
        this.file = file;
        load();
    }

    /** The file of the application in its data folder, next to the saved sign-in. */
    public static Path defaultFile() {
        return Path.of(ProjectDirectories.from("", "", "WakeMeApp").dataLocalDir, "dismissed.json");
    }

    public boolean isDismissed(String eventId, LocalDateTime start) {
        return start.equals(dismissed.get(eventId));
    }

    public synchronized void dismiss(String eventId, LocalDateTime start) {
        dismissed.put(eventId, start);   // replaces what was dismissed for an older time of the event
        save();
    }

    public synchronized void undo(String eventId, LocalDateTime start) {
        if (dismissed.remove(eventId, start)) {
            save();
        }
    }

    /** The calendar reports the event at {@code start}: a dismissal of another time no longer holds. */
    public synchronized void meetingMoved(String eventId, LocalDateTime start) {
        LocalDateTime before = dismissed.get(eventId);
        if (before != null && !before.equals(start) && dismissed.remove(eventId, before)) {
            save();
        }
    }

    public int size() {
        return dismissed.size();
    }

    private void load() {
        if (!Files.exists(file)) {
            return;
        }
        try {
            JSONObject json = new JSONObject(Files.readString(file, StandardCharsets.UTF_8));
            LocalDateTime limit = LocalDateTime.now().minus(KEEP);
            for (String id : json.keySet()) {
                LocalDateTime start = LocalDateTime.parse(json.getString(id));
                if (!start.isBefore(limit)) {
                    dismissed.put(id, start);
                }
            }
        } catch (IOException | RuntimeException e) {
            dismissed.clear();
            LOG.warning("The dismissed meetings in " + file + " cannot be read and are ignored: " + e.getMessage());
        }
    }

    private void save() {
        if (file == null) {
            return;
        }
        JSONObject json = new JSONObject();
        dismissed.forEach((id, start) -> json.put(id, start.toString()));
        try {
            Path folder = file.toAbsolutePath().getParent();
            Files.createDirectories(folder);
            Path temporary = Files.createTempFile(folder, file.getFileName().toString(), ".tmp");
            try {
                Files.writeString(temporary, json.toString(), StandardCharsets.UTF_8);
                try {
                    Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException x) {
                    Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException | RuntimeException e) {
            LOG.warning("The dismissed meetings cannot be saved to " + file + ", they will be forgotten when the app stops: " + e.getMessage());
        }
    }
}
