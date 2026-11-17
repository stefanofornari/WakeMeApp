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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.logging.Level;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.BDDAssertions.then;

class DismissedMeetingsTest {

    private static final LocalDateTime START = LocalDateTime.now().plusMinutes(10).withNano(0);

    @TempDir
    Path folder;

    @Test
    void should_remember_a_dismissed_occurrence() {
        DismissedMeetings dismissed = new DismissedMeetings(folder.resolve("dismissed.json"));

        then(dismissed.isDismissed("standup", START)).isFalse();
        dismissed.dismiss("standup", START);

        then(dismissed.isDismissed("standup", START)).isTrue();
        then(dismissed.isDismissed("other", START)).isFalse();
    }

    @Test
    void should_not_consider_dismissed_another_occurrence_of_the_same_event() {
        DismissedMeetings dismissed = new DismissedMeetings(folder.resolve("dismissed.json"));
        dismissed.dismiss("standup", START);

        then(dismissed.isDismissed("standup", START.plusDays(7))).isFalse();
    }

    @Test
    void should_forget_the_dismissal_on_undo() {
        DismissedMeetings dismissed = new DismissedMeetings(folder.resolve("dismissed.json"));
        dismissed.dismiss("standup", START);

        dismissed.undo("standup", START);

        then(dismissed.isDismissed("standup", START)).isFalse();
    }

    @Test
    void should_be_loaded_again_after_a_restart() {
        Path file = folder.resolve("dismissed.json");
        new DismissedMeetings(file).dismiss("standup", START);

        then(new DismissedMeetings(file).isDismissed("standup", START)).isTrue();
    }

    @Test
    void should_forget_the_undone_dismissal_after_a_restart() {
        Path file = folder.resolve("dismissed.json");
        DismissedMeetings dismissed = new DismissedMeetings(file);
        dismissed.dismiss("standup", START);
        dismissed.undo("standup", START);

        then(new DismissedMeetings(file).isDismissed("standup", START)).isFalse();
    }

    @Test
    void should_replace_the_dismissal_of_an_older_time_of_the_same_event() {
        DismissedMeetings dismissed = new DismissedMeetings(folder.resolve("dismissed.json"));
        dismissed.dismiss("standup", START);

        dismissed.dismiss("standup", START.plusHours(2));

        then(dismissed.isDismissed("standup", START)).isFalse();
        then(dismissed.isDismissed("standup", START.plusHours(2))).isTrue();
    }

    @Test
    void should_clear_the_dismissal_when_the_meeting_moved() {
        DismissedMeetings dismissed = new DismissedMeetings(folder.resolve("dismissed.json"));
        dismissed.dismiss("standup", START);

        dismissed.meetingMoved("standup", START.plusHours(1));

        then(dismissed.isDismissed("standup", START)).isFalse();
        then(dismissed.isDismissed("standup", START.plusHours(1))).isFalse();
    }

    @Test
    void should_leave_the_dismissal_alone_when_the_meeting_did_not_move() {
        DismissedMeetings dismissed = new DismissedMeetings(folder.resolve("dismissed.json"));
        dismissed.dismiss("standup", START);

        dismissed.meetingMoved("standup", START);

        then(dismissed.isDismissed("standup", START)).isTrue();
    }

    @Test
    void should_drop_meetings_long_past_when_loading() {
        Path file = folder.resolve("dismissed.json");
        DismissedMeetings dismissed = new DismissedMeetings(file);
        dismissed.dismiss("old", LocalDateTime.now().minusDays(3));
        dismissed.dismiss("today", LocalDateTime.now().minusHours(1));

        DismissedMeetings reloaded = new DismissedMeetings(file);

        then(reloaded.size()).isEqualTo(1);
    }

    @Test
    void should_start_empty_and_warn_when_the_file_cannot_be_read() throws IOException {
        Path file = folder.resolve("dismissed.json");
        Files.writeString(file, "this is not json");
        java.util.List<java.util.logging.LogRecord> warnings = new java.util.ArrayList<>();
        java.util.logging.Handler handler = new java.util.logging.Handler() {
            @Override public void publish(java.util.logging.LogRecord r) { if (r.getLevel() == Level.WARNING) warnings.add(r); }
            @Override public void flush() { }
            @Override public void close() { }
        };
        java.util.logging.Logger log = java.util.logging.Logger.getLogger(DismissedMeetings.class.getName());
        log.addHandler(handler);
        try {
            then(new DismissedMeetings(file).size()).isZero();
        } finally {
            log.removeHandler(handler);
        }

        then(warnings).hasSize(1);
        then(warnings.get(0).getMessage()).contains(file.toString());
    }

    @Test
    void should_keep_the_dismissal_in_memory_when_the_file_cannot_be_written() throws IOException {
        Path blocker = folder.resolve("blocker");
        Files.writeString(blocker, "a file where a folder should be");
        DismissedMeetings dismissed = new DismissedMeetings(blocker.resolve("dismissed.json"));

        dismissed.dismiss("standup", START);

        then(dismissed.isDismissed("standup", START)).isTrue();
    }
}
