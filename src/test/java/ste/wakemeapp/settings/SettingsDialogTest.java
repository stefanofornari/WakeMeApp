package ste.wakemeapp.settings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
    private StoredCalendar stored;
    private SettingsDialog dialog;
    private SettingsController form;

    @Start
    private void start(Stage stage) throws IOException {
        saved.clear();
        stored = new StoredCalendar(Files.createTempDirectory("settings").resolve("calendar.txt"));
        dialog = new SettingsDialog(stored, saved::add);
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
        robot.interact(() -> dialog = new SettingsDialog(stored, saved::add));
        form = dialog.getController();
        open();
        type(LINK);

        save();

        then(form.getErrorLabel().getText()).startsWith("The calendar could not be saved").doesNotContain("secret456");
        then(saved).isEmpty();
        then(dialog.isShowing()).isTrue();
    }
}
