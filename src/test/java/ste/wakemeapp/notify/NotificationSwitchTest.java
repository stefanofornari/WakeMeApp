package ste.wakemeapp.notify;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import ste.wakemeapp.settings.MemoryPreferences;
import ste.wakemeapp.settings.Settings;
import static org.assertj.core.api.BDDAssertions.then;

/** Switching the meeting notifications off and on, for all meetings at once (settings/US-000017). */
class NotificationSwitchTest {

    private final MemoryPreferences node = new MemoryPreferences();
    private final Settings settings = new Settings(node);
    private final MeetingNotification meeting = new MeetingNotification("Standup", LocalDateTime.of(2026, 11, 13, 10, 0), null);

    @Test
    void notifications_are_on_by_default() {
        then(NotificationSwitch.of(settings).isOn()).isTrue();
    }

    @Test
    void the_state_is_saved_and_read_back_by_another_switch() {
        NotificationSwitch.of(settings).set(false);

        then(NotificationSwitch.of(settings).isOn()).isFalse();
        then(node.get(NotificationSwitch.KEY, null)).isEqualTo("false");

        NotificationSwitch.of(settings).set(true);
        then(NotificationSwitch.of(settings).isOn()).isTrue();
    }

    @Test
    void a_stored_value_that_is_not_true_or_false_counts_as_on() {
        node.put(NotificationSwitch.KEY, "maybe");

        then(NotificationSwitch.of(settings).isOn()).isTrue();
    }

    @Test
    void an_on_switch_lets_a_notification_through() {
        List<MeetingNotification> shown = new ArrayList<>();

        NotificationSwitch.of(settings).guard(shown::add).accept(meeting);

        then(shown).containsExactly(meeting);
    }

    @Test
    void an_off_switch_holds_a_notification_back_and_says_so() {
        List<MeetingNotification> shown = new ArrayList<>();
        List<String> logged = new ArrayList<>();
        Logger log = Logger.getLogger(NotificationSwitch.class.getName());
        Handler handler = new Handler() {
            @Override public void publish(LogRecord r) { logged.add(r.getLevel() + " " + r.getMessage()); }
            @Override public void flush() { }
            @Override public void close() { }
        };
        NotificationSwitch off = NotificationSwitch.of(settings);
        off.set(false);
        log.addHandler(handler);
        try {
            off.guard(shown::add).accept(meeting);
        } finally {
            log.removeHandler(handler);
        }

        then(shown).isEmpty();
        then(logged).hasSize(1);
        then(logged.get(0)).contains("INFO").contains("Notification suppressed (globally disabled)");
    }

    @Test
    void the_guard_follows_the_switch_without_being_created_again() {
        List<MeetingNotification> shown = new ArrayList<>();
        NotificationSwitch notifications = NotificationSwitch.of(settings);
        var guard = notifications.guard(shown::add);

        notifications.set(false);
        guard.accept(meeting);
        notifications.set(true);
        guard.accept(meeting);

        then(shown).hasSize(1);
    }

    @Test
    void the_log_does_not_name_the_meeting() {
        List<String> logged = new ArrayList<>();
        Logger log = Logger.getLogger(NotificationSwitch.class.getName());
        Handler handler = new Handler() {
            @Override public void publish(LogRecord r) { logged.add(r.getMessage()); }
            @Override public void flush() { }
            @Override public void close() { }
        };
        NotificationSwitch off = NotificationSwitch.of(settings);
        off.set(false);
        log.addHandler(handler);
        try {
            off.guard(m -> { }).accept(meeting);
        } finally {
            log.removeHandler(handler);
        }

        then(logged.get(0)).doesNotContain("Standup");
    }
}
