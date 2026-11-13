package ste.wakemeapp.settings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import static org.assertj.core.api.BDDAssertions.then;

/** The settings dialog: how it opens (settings/US-000056) and the calendar field (settings/US-000057). */
@ExtendWith(ApplicationExtension.class)
class SettingsDialogTest {

    private static final String LINK = "https://outlook.office365.com/owa/calendar/abc123/secret456/calendar.ics";
    private static final String GRAPH = "https://graph.microsoft.com/v1.0/me/calendars/AAMk";

    private final FxRobot robot = new FxRobot();
    private final List<String> saved = new ArrayList<>();
    private final List<Duration> leadTimes = new ArrayList<>();
    private final List<Boolean> switches = new ArrayList<>();
    private int tests;
    private final SettingsListener listener = new SettingsListener() {
        @Override public void calendarSaved(String value) { saved.add(value); }
        @Override public void leadTimeSaved(Duration leadTime) { leadTimes.add(leadTime); }
        @Override public void notificationsSwitched(boolean on) { switches.add(on); }
        @Override public void testNotificationRequested() { tests++; }
    };
    private Settings settings;
    private Duration pollInterval;
    private StoredCalendar stored;
    private SettingsDialog dialog;
    private SettingsController form;

    @Start
    private void start(Stage stage) throws IOException {
        saved.clear();
        leadTimes.clear();
        switches.clear();
        tests = 0;
        settings = new Settings(new MemoryPreferences());
        pollInterval = Duration.ofMinutes(5);
        stored = new StoredCalendar(Files.createTempDirectory("settings").resolve("calendar.txt"));
        dialog = new SettingsDialog(stored, settings, pollInterval, listener);
        form = dialog.getController();
    }

    private void open() {
        robot.interact(dialog::show);
    }

    private void type(String text) {
        robot.interact(() -> form.getCalendarField().setText(text));
    }

    private void save() {
        robot.interact(form::handleSave);
    }

    // ---- US-56

    @Test
    void should_open_a_titled_dialog_that_does_not_block_the_rest_of_the_application() {
        open();

        then(dialog.isShowing()).isTrue();
        then(dialog.getStage().getTitle()).isEqualTo("WakeMeApp Settings");
        then(dialog.getStage().getModality()).isEqualTo(Modality.NONE);
    }

    @Test
    void should_not_be_open_before_it_is_asked_for() {
        then(dialog.isShowing()).isFalse();
    }

    @Test
    void should_keep_one_dialog_and_the_edits_when_opened_again() {
        open();
        type("meetings.csv");

        open();

        then(dialog.isShowing()).isTrue();
        then(form.getCalendarField().getText()).isEqualTo("meetings.csv");
    }

    @Test
    void should_change_nothing_on_cancel() throws IOException {
        stored.save("meetings.csv");
        open();
        type(LINK);

        robot.interact(form::handleCancel);

        then(dialog.isShowing()).isFalse();
        then(stored.value()).hasValue("meetings.csv");
        then(saved).isEmpty();
    }

    @Test
    void should_show_what_is_saved_again_after_a_cancel() throws IOException {
        stored.save("meetings.csv");
        open();
        type("other.csv");
        robot.interact(form::handleCancel);

        open();

        then(form.getCalendarField().getText()).isEqualTo("meetings.csv");
    }

    // ---- US-57

    @Test
    void should_save_a_valid_link_report_it_and_close() {
        open();
        type(LINK);

        save();

        then(stored.value()).hasValue(LINK);
        then(saved).containsExactly(LINK);
        then(dialog.isShowing()).isFalse();
    }

    @Test
    void should_show_the_saved_link_masked() throws IOException {
        stored.save(LINK);

        open();

        then(form.getCalendarField().getText()).isEqualTo("https://outlook.office365.com/…");
        then(form.getKindLabel().getText()).isEqualTo("ICS link");
    }

    @Test
    void should_keep_the_saved_link_when_the_masked_text_is_saved_untouched() throws IOException {
        stored.save(LINK);
        open();

        save();

        then(stored.value()).hasValue(LINK);
        then(saved).isEmpty();
        then(dialog.isShowing()).isFalse();
    }

    @Test
    void should_show_the_kind_while_typing() {
        open();

        type(LINK);
        then(form.getKindLabel().getText()).isEqualTo("ICS link");
        type("meetings.csv");
        then(form.getKindLabel().getText()).isEqualTo("CSV file");
        type("hello");
        then(form.getKindLabel().getText()).isEmpty();
    }

    @Test
    void should_refuse_a_plain_http_link_and_store_nothing() {
        open();
        type("http://outlook.office365.com/owa/calendar/abc123/secret456/calendar.ics");

        save();

        then(form.getErrorLabel().isVisible()).isTrue();
        then(form.getErrorLabel().getText()).contains("https://").doesNotContain("secret456");
        then(form.getCalendarField().getStyleClass()).contains("settings-invalid");
        then(stored.value()).isEmpty();
        then(saved).isEmpty();
        then(dialog.isShowing()).isTrue();
    }

    @Test
    void should_refuse_a_missing_file() {
        open();
        type("/no/such/meetings.csv");

        save();

        then(form.getErrorLabel().getText()).contains("cannot be read");
        then(stored.value()).isEmpty();
    }

    @Test
    void should_refuse_a_value_of_no_known_kind_without_echoing_it() {
        open();
        type("hello");

        save();

        then(form.getErrorLabel().getText()).contains("CSV").doesNotContain("hello");
        then(stored.value()).isEmpty();
    }

    @Test
    void should_clear_the_error_when_the_user_types_again() {
        open();
        type("hello");
        save();

        type("hello2");

        then(form.getErrorLabel().isVisible()).isFalse();
        then(form.getCalendarField().getStyleClass()).doesNotContain("settings-invalid");
    }

    @Test
    void should_ask_for_the_microsoft_account_for_a_graph_address() {
        open();

        type(GRAPH);

        then(form.getKindLabel().getText()).isEqualTo("Microsoft Graph address");
        then(form.getHintLabel().isVisible()).isTrue();
        type(LINK);
        then(form.getHintLabel().isVisible()).isFalse();
    }

    @Test
    void should_save_a_graph_address() {
        open();
        type(GRAPH);

        save();

        then(stored.value()).hasValue(GRAPH);
        then(saved).containsExactly(GRAPH);
    }

    @Test
    void should_remove_the_calendar_when_the_field_is_emptied() throws IOException {
        stored.save(LINK);
        open();
        type("");

        save();

        then(stored.value()).isEmpty();
        then(saved).hasSize(1);
        then(saved.get(0)).isNull();
        then(dialog.isShowing()).isFalse();
    }

    @Test
    void should_report_nothing_when_nothing_was_saved_and_the_field_is_empty() {
        open();

        save();

        then(saved).isEmpty();
        then(dialog.isShowing()).isFalse();
    }

    @Test
    void should_say_it_cannot_save_without_the_link_when_the_file_cannot_be_written() throws IOException {
        Path blocked = Files.createTempDirectory("settings").resolve("calendar.txt");
        Files.createDirectories(blocked.resolve("inner"));   // a non-empty directory where the file should go
        stored = new StoredCalendar(blocked);
        robot.interact(() -> dialog = new SettingsDialog(stored, settings, pollInterval, listener));
        form = dialog.getController();
        open();
        type(LINK);

        save();

        then(form.getErrorLabel().getText()).startsWith("The calendar could not be saved").doesNotContain("secret456");
        then(saved).isEmpty();
        then(dialog.isShowing()).isTrue();
    }

    // ---- settings/US-000015

    private void typeLeadTime(String text) {
        robot.interact(() -> form.getLeadTimeField().setText(text));
    }

    @Test
    void should_show_the_default_lead_time() {
        open();

        then(form.getLeadTimeField().getText()).isEqualTo("5");
    }

    @Test
    void should_show_the_saved_lead_time() {
        LeadTime.save(settings, 12);

        open();

        then(form.getLeadTimeField().getText()).isEqualTo("12");
    }

    @Test
    void should_save_a_lead_time_report_it_and_close() {
        open();
        typeLeadTime("10");

        save();

        then(LeadTime.read(settings)).isEqualTo(Duration.ofMinutes(10));
        then(leadTimes).containsExactly(Duration.ofMinutes(10));
        then(saved).isEmpty();
        then(dialog.isShowing()).isFalse();
    }

    @Test
    void should_report_the_calendar_only_when_both_are_changed() {
        Path csv;
        try {
            csv = Files.writeString(Files.createTempDirectory("m").resolve("meetings.csv"), "x\n");
        } catch (IOException x) {
            throw new IllegalStateException(x);
        }
        open();
        typeLeadTime("10");
        type(csv.toString());

        save();

        then(LeadTime.read(settings)).isEqualTo(Duration.ofMinutes(10));
        then(saved).containsExactly(csv.toString());
        then(leadTimes).isEmpty();   // the new calendar is polled with the new lead time anyway
    }

    @Test
    void should_refuse_a_lead_time_below_the_minimum_and_put_back_the_minimum() {
        open();
        typeLeadTime("0");

        save();

        then(form.getLeadTimeErrorLabel().getText()).isEqualTo("Lead time must be at least 1 minute");
        then(form.getLeadTimeField().getText()).isEqualTo("1");
        then(LeadTime.read(settings)).isEqualTo(Duration.ofMinutes(5));
        then(leadTimes).isEmpty();
        then(dialog.isShowing()).isTrue();
    }

    @Test
    void should_refuse_a_lead_time_above_the_maximum_and_put_back_the_maximum() {
        open();
        typeLeadTime("2000");

        save();

        then(form.getLeadTimeErrorLabel().getText()).isEqualTo("Lead time cannot exceed 1440 minutes (24 hours)");
        then(form.getLeadTimeField().getText()).isEqualTo("1440");
        then(LeadTime.read(settings)).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void should_refuse_a_lead_time_that_is_not_a_number_and_keep_what_was_typed() {
        open();
        typeLeadTime("soon");

        save();

        then(form.getLeadTimeErrorLabel().getText()).isEqualTo("Lead time must be a whole number of minutes");
        then(form.getLeadTimeField().getText()).isEqualTo("soon");
    }

    @Test
    void should_save_nothing_when_the_calendar_is_wrong_even_if_the_lead_time_is_right() {
        open();
        typeLeadTime("10");
        type("hello");

        save();

        then(form.getErrorLabel().isVisible()).isTrue();
        then(LeadTime.read(settings)).isEqualTo(Duration.ofMinutes(5));
        then(leadTimes).isEmpty();
        then(dialog.isShowing()).isTrue();
    }

    @Test
    void should_warn_but_save_when_the_poll_interval_is_longer_than_the_lead_time() {
        pollInterval = Duration.ofMinutes(10);
        robot.interact(() -> dialog = new SettingsDialog(stored, settings, pollInterval, listener));
        form = dialog.getController();
        open();
        typeLeadTime("5");   // the default: nothing changes, so no warning
        save();
        then(dialog.isShowing()).isFalse();

        open();
        typeLeadTime("6");
        save();

        then(LeadTime.read(settings)).isEqualTo(Duration.ofMinutes(6));
        then(leadTimes).containsExactly(Duration.ofMinutes(6));
        then(form.getLeadTimeWarningLabel().getText())
            .isEqualTo("Poll interval (10 min) exceeds lead time (6 min). Notifications may be delayed.");
        then(dialog.isShowing()).isTrue();

        save();   // nothing changed since: it closes
        then(dialog.isShowing()).isFalse();
    }

    // ---- settings/US-000017

    private void tick(boolean selected) {
        robot.interact(() -> form.getNotificationsCheck().setSelected(selected));
    }

    @Test
    void should_show_the_notifications_on_by_default() {
        open();

        then(form.getNotificationsCheck().isSelected()).isTrue();
    }

    @Test
    void should_switch_the_notifications_off_save_it_and_report_it() {
        open();
        tick(false);

        save();

        then(NotificationsEnabled.read(settings)).isFalse();
        then(switches).containsExactly(false);
        then(saved).isEmpty();
        then(leadTimes).isEmpty();
        then(dialog.isShowing()).isFalse();
    }

    @Test
    void should_show_the_saved_state_when_opened_again() {
        NotificationsEnabled.save(settings, false);

        open();

        then(form.getNotificationsCheck().isSelected()).isFalse();
    }

    @Test
    void should_switch_the_notifications_back_on() {
        NotificationsEnabled.save(settings, false);
        open();
        tick(true);

        save();

        then(NotificationsEnabled.read(settings)).isTrue();
        then(switches).containsExactly(true);
    }

    @Test
    void should_report_nothing_when_the_switch_is_left_as_it_was() {
        open();
        tick(true);

        save();

        then(switches).isEmpty();
    }

    @Test
    void should_not_switch_when_cancelled() {
        open();
        tick(false);

        robot.interact(form::handleCancel);

        then(NotificationsEnabled.read(settings)).isTrue();
        then(switches).isEmpty();
    }

    @Test
    void should_not_switch_when_another_setting_is_wrong() {
        open();
        tick(false);
        typeLeadTime("0");

        save();

        then(NotificationsEnabled.read(settings)).isTrue();
        then(switches).isEmpty();
    }

    // ---- settings/US-000018

    @Test
    void should_ask_for_a_test_notification_without_closing_or_saving() {
        open();
        typeLeadTime("10");

        robot.interact(form::handleTestNotification);

        then(tests).isEqualTo(1);
        then(dialog.isShowing()).isTrue();
        then(LeadTime.read(settings)).isEqualTo(Duration.ofMinutes(5));   // edits are not saved by a test
        then(leadTimes).isEmpty();
    }

    @Test
    void should_ask_for_a_test_notification_also_when_the_notifications_are_switched_off() {
        NotificationsEnabled.save(settings, false);
        open();

        robot.interact(form::handleTestNotification);

        then(tests).isEqualTo(1);
        then(NotificationsEnabled.read(settings)).isFalse();   // the switch is unaffected
        then(switches).isEmpty();
    }

    @Test
    void should_have_a_test_button_in_the_dialog() {
        then(form.getTestButton().getText()).isEqualTo("Test Notification");
    }
}
