package ste.wakemeapp.settings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;

/** What the calendar field of the settings dialog recognises, accepts and shows. */
class CalendarInputTest {

    private static final String LINK = "https://outlook.office365.com/owa/calendar/abc123/secret456/calendar.ics";
    private static final String GRAPH = "https://graph.microsoft.com/v1.0/me/calendars/AAMk";

    @TempDir
    Path folder;

    @Test
    void the_kind_is_detected_like_the_command_line_does() {
        then(CalendarInput.kindOf(LINK)).hasValue(CalendarKind.ICS_LINK);
        then(CalendarInput.kindOf("webcal://outlook.office365.com/x/calendar.ics")).hasValue(CalendarKind.ICS_LINK);
        then(CalendarInput.kindOf("http://example.com/calendar.ics")).hasValue(CalendarKind.ICS_LINK);
        then(CalendarInput.kindOf(GRAPH)).hasValue(CalendarKind.GRAPH_ADDRESS);
        then(CalendarInput.kindOf("meetings.csv")).hasValue(CalendarKind.CSV_FILE);
        then(CalendarInput.kindOf("/home/ste/meetings")).hasValue(CalendarKind.CSV_FILE);
        then(CalendarInput.kindOf("C:\\\\meetings\\\\all.csv")).hasValue(CalendarKind.CSV_FILE);
    }

    @Test
    void a_value_of_no_known_kind_has_no_kind() {
        then(CalendarInput.kindOf("hello")).isEmpty();
        then(CalendarInput.kindOf("")).isEmpty();
        then(CalendarInput.kindOf("   ")).isEmpty();
        then(CalendarInput.kindOf(null)).isEmpty();
    }

    @Test
    void the_kinds_have_a_name_for_the_user() {
        then(CalendarKind.ICS_LINK.label()).isEqualTo("ICS link");
        then(CalendarKind.GRAPH_ADDRESS.label()).isEqualTo("Microsoft Graph address");
        then(CalendarKind.CSV_FILE.label()).isEqualTo("CSV file");
    }

    @Test
    void valid_values_of_each_kind_are_accepted() throws IOException {
        Path csv = Files.writeString(folder.resolve("meetings.csv"), "x\n");

        CalendarInput.validate(LINK);
        CalendarInput.validate("webcal://outlook.office365.com/owa/calendar/a/b/calendar.ics");
        CalendarInput.validate(GRAPH);
        CalendarInput.validate(csv.toString());
    }

    @Test
    void a_plain_http_link_is_refused_without_echoing_it() {
        String link = "http://outlook.office365.com/owa/calendar/abc123/secret456/calendar.ics";

        thenThrownBy(() -> CalendarInput.validate(link))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("https://").hasMessageContaining("webcal://")
            .hasMessageNotContaining("secret456").hasMessageNotContaining("abc123");
    }

    @Test
    void a_graph_address_that_cannot_be_used_is_refused_in_the_words_of_the_dialog() {
        thenThrownBy(() -> CalendarInput.validate("http://graph.microsoft.com/v1.0/me/calendars/AAMk"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageNotContaining("--calendar");
    }

    @Test
    void a_file_that_does_not_exist_is_refused() {
        thenThrownBy(() -> CalendarInput.validate(folder.resolve("missing.csv").toString()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("cannot be read");
    }

    @Test
    void a_directory_is_refused() {
        thenThrownBy(() -> CalendarInput.validate(folder.toString()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("cannot be read");
    }

    @Test
    void a_value_of_no_known_kind_is_refused_naming_the_three_accepted_forms_and_not_echoed() {
        thenThrownBy(() -> CalendarInput.validate("hello"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("https://").hasMessageContaining("graph.microsoft.com").hasMessageContaining("CSV")
            .hasMessageNotContaining("hello");
    }

    @Test
    void a_link_is_shown_as_its_host_only_and_the_other_kinds_as_they_are() {
        then(CalendarInput.shown(LINK)).isEqualTo("https://outlook.office365.com/\u2026");
        then(CalendarInput.shown("webcal://outlook.office365.com/x/y.ics")).isEqualTo("https://outlook.office365.com/\u2026");
        then(CalendarInput.shown(GRAPH)).isEqualTo(GRAPH);
        then(CalendarInput.shown("meetings.csv")).isEqualTo("meetings.csv");
        then(CalendarInput.shown(null)).isEmpty();
    }
}
