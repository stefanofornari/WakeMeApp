package ste.wakemeapp.calendar;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;

class CsvCalendarSourceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 7, 9, 0);
    private static final String HEADER = "id,title,start,end,url,organizer,description,status,allday\n";

    @TempDir
    Path dir;

    private CsvCalendarSource source(String content) throws IOException {
        Path file = dir.resolve("events.csv");
        Files.writeString(file, content);
        return new CsvCalendarSource(file);
    }

    private List<CalendarEvent> window(CsvCalendarSource source) throws IOException {
        return source.meetingsStartingBetween(NOW, NOW.plusMinutes(15));
    }

    @Test
    void should_read_all_the_fields_of_a_meeting() throws IOException {
        CsvCalendarSource source = source(HEADER
            + "e1,Standup,2026-11-07T09:12,2026-11-07T09:42,https://teams.example.com/j,Ann,Daily sync,tentative,false\n");

        then(window(source)).containsExactly(new CalendarEvent("e1", "Standup",
            LocalDateTime.of(2026, 11, 7, 9, 12), LocalDateTime.of(2026, 11, 7, 9, 42),
            "https://teams.example.com/j", "Ann", "Daily sync", ResponseStatus.TENTATIVE, false));
    }

    @Test
    void should_default_the_optional_fields_when_empty_or_missing() throws IOException {
        CsvCalendarSource source = source("title,start,id\nStandup,2026-11-07T09:12:30,e1\n");

        CalendarEvent event = window(source).get(0);

        then(event.endTime()).isNull();
        then(event.joinUrl()).isNull();
        then(event.organizer()).isNull();
        then(event.responseStatus()).isEqualTo(ResponseStatus.ACCEPTED);
        then(event.allDay()).isFalse();
        then(event.start()).isEqualTo(LocalDateTime.of(2026, 11, 7, 9, 12, 30));
    }

    @Test
    void should_map_every_status_and_the_all_day_flag() throws IOException {
        CsvCalendarSource source = source(HEADER
            + "a,A,2026-11-07T09:01,,,,,accepted,\n"
            + "b,B,2026-11-07T09:02,,,,,Declined,\n"
            + "c,C,2026-11-07T09:03,,,,,none,\n"
            + "d,D,2026-11-07T09:04,,,,,ORGANIZER,true\n");

        then(window(source)).extracting(CalendarEvent::responseStatus).containsExactlyInAnyOrder(
            ResponseStatus.ACCEPTED, ResponseStatus.DECLINED, ResponseStatus.NOT_RESPONDED, ResponseStatus.ORGANIZER);
        then(window(source)).filteredOn(CalendarEvent::allDay).extracting(CalendarEvent::id).containsExactly("d");
    }

    @Test
    void should_return_only_the_meetings_starting_in_the_window() throws IOException {
        CsvCalendarSource source = source(HEADER
            + "a,In five,2026-11-07T09:05,,,,,,\n"
            + "b,In twelve,2026-11-07T09:12,,,,,,\n"
            + "c,In forty,2026-11-07T09:40,,,,,,\n");

        then(window(source)).extracting(CalendarEvent::id).containsExactlyInAnyOrder("a", "b");
    }

    @Test
    void should_handle_quoted_fields_blank_lines_and_comments() throws IOException {
        CsvCalendarSource source = source("# my meetings\n" + HEADER + "\n"
            + "a,\"Review, part \"\"2\"\"\",2026-11-07T09:05,,,,,,\n"
            + "# b,Disabled,2026-11-07T09:06,,,,,,\n");

        then(window(source)).extracting(CalendarEvent::title).containsExactly("Review, part \"2\"");
    }

    @Test
    void should_see_the_changes_made_to_the_file_between_two_queries() throws IOException {
        CsvCalendarSource source = source(HEADER + "e1,Standup,2026-11-07T09:12,,,,,,\n");
        then(window(source).get(0).start()).isEqualTo(LocalDateTime.of(2026, 11, 7, 9, 12));

        Files.writeString(dir.resolve("events.csv"), HEADER + "e1,Standup,2026-11-07T09:08,,,,,,\n");

        then(window(source).get(0).start()).isEqualTo(LocalDateTime.of(2026, 11, 7, 9, 8));
    }

    @Test
    void should_name_the_line_of_a_malformed_row() throws IOException {
        CsvCalendarSource source = source(HEADER
            + "a,Fine,2026-11-07T09:05,,,,,,\n"
            + "b,Broken,tomorrow morning,,,,,,\n");

        thenThrownBy(() -> window(source)).isInstanceOf(IOException.class).hasMessageContaining("line 3");
    }

    @Test
    void should_reject_a_file_without_the_required_columns() throws IOException {
        CsvCalendarSource source = source("id,title\na,Standup\n");

        thenThrownBy(() -> window(source)).isInstanceOf(IOException.class).hasMessageContaining("start");
    }

    @Test
    void should_return_no_meetings_and_warn_once_while_the_file_is_missing() throws IOException {
        Path file = dir.resolve("missing.csv");
        CsvCalendarSource source = new CsvCalendarSource(file);
        Logger logger = Logger.getLogger(CsvCalendarSource.class.getName());
        List<LogRecord> logged = new CopyOnWriteArrayList<>();
        Handler handler = new Handler() {
            @Override public void publish(LogRecord record) { logged.add(record); }
            @Override public void flush() { }
            @Override public void close() { }
        };
        logger.addHandler(handler);
        try {
            then(window(source)).isEmpty();
            then(window(source)).isEmpty();
        } finally {
            logger.removeHandler(handler);
        }

        then(logged).hasSize(1);
        then(logged.get(0).getLevel()).isEqualTo(Level.WARNING);
        then(logged.get(0).getMessage()).contains("missing.csv");
    }

    @Test
    void should_pick_up_the_file_as_soon_as_it_appears() throws IOException {
        Path file = dir.resolve("late.csv");
        CsvCalendarSource source = new CsvCalendarSource(file);
        then(window(source)).isEmpty();

        Files.writeString(file, HEADER + "e1,Standup,2026-11-07T09:12,,,,,,\n");

        then(window(source)).extracting(CalendarEvent::id).containsExactly("e1");
    }
}
