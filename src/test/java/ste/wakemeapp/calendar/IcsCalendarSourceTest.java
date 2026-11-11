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
import java.net.URI;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import ste.xtest.net.http.HttpClientStubber;
import ste.xtest.net.http.StubHttpClient.NetworkError;
import ste.xtest.net.http.StubHttpClient.StubHttpResponse;
import ste.xtest.net.http.URIMatcher;
import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;

/** The ICS feed source against stubbed HTTP answers: no network, no real calendar. */
class IcsCalendarSourceTest {

    private static final String LINK = "https://outlook.office365.com/owa/calendar/abc123/secret456/calendar.ics";
    private static final ZoneId ROME = ZoneId.of("Europe/Rome");
    private static final LocalDateTime FROM = LocalDateTime.of(2026, 11, 9, 11, 45);
    private static final LocalDateTime TO = LocalDateTime.of(2026, 11, 9, 12, 0);

    private HttpClientStubber http = new HttpClientStubber();

    private IcsCalendarSource source() {
        return new IcsCalendarSource(http, URI.create(LINK), ROME);
    }

    private static String feed(String... events) {
        return "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//test//EN\r\n" + String.join("", events) + "END:VCALENDAR\r\n";
    }

    /** One VEVENT from the given content lines. */
    private static String event(String... lines) {
        return "BEGIN:VEVENT\r\n" + String.join("\r\n", lines) + "\r\nEND:VEVENT\r\n";
    }

    private void answer(String body) {
        answer(200, body);
    }

    private void answer(int status, String body) {
        http.withStub(new URIMatcher(LINK), new StubHttpResponse<String>().statusCode(status).text(body));
    }

    private List<CalendarEvent> read() throws IOException {
        return source().meetingsStartingBetween(FROM, TO);
    }

    // --- the fields of a meeting ---

    @Test
    void should_read_the_fields_of_a_meeting() throws IOException {
        answer(feed(event(
            "UID:standup-1",
            "SUMMARY:Standup",
            "DTSTART:20261109T105000Z",
            "DTEND:20261109T115000Z",
            "ORGANIZER;CN=Ann Smith:mailto:ann@example.com",
            "DESCRIPTION:Agenda\\nJoin: <https://teams.microsoft.com/l/meetup-join/abc>\\n"
        )));

        then(read()).containsExactly(new CalendarEvent(
            "standup-1", "Standup",
            LocalDateTime.of(2026, 11, 9, 11, 50), LocalDateTime.of(2026, 11, 9, 12, 50),
            "https://teams.microsoft.com/l/meetup-join/abc", "Ann Smith",
            "Agenda\nJoin: <https://teams.microsoft.com/l/meetup-join/abc>\n",
            ResponseStatus.ACCEPTED, false));
    }

    @Test
    void should_use_the_address_when_the_organizer_has_no_name() throws IOException {
        answer(feed(event("UID:1", "SUMMARY:A", "DTSTART:20261109T105000Z", "ORGANIZER:mailto:ann@example.com")));

        then(read().get(0).organizer()).isEqualTo("ann@example.com");
    }

    @Test
    void should_leave_out_what_the_feed_does_not_say() throws IOException {
        answer(feed(event("UID:1", "SUMMARY:A", "DTSTART:20261109T105000Z")));

        CalendarEvent event = read().get(0);
        then(event.endTime()).isNull();
        then(event.organizer()).isNull();
        then(event.description()).isNull();
        then(event.joinUrl()).isNull();
    }

    @Test
    void should_unfold_long_lines_and_unescape_the_text() throws IOException {
        answer(feed(event("UID:1", "SUMMARY:Review\\, part\r\n  two\\; final", "DTSTART:20261109T105000Z")));

        then(read().get(0).title()).isEqualTo("Review, part two; final");
    }

    @Test
    void should_give_an_untitled_meeting_a_title() throws IOException {
        answer(feed(event("UID:1", "DTSTART:20261109T105000Z")));

        then(read().get(0).title()).isEqualTo("(no title)");
    }

    // --- the join link ---

    @Test
    void should_prefer_the_teams_property_over_the_text() throws IOException {
        answer(feed(event("UID:1", "SUMMARY:A", "DTSTART:20261109T105000Z",
            "X-MICROSOFT-SKYPETEAMSMEETINGURL:https://teams.microsoft.com/l/meetup-join/own",
            "DESCRIPTION:see https://teams.microsoft.com/l/meetup-join/other")));

        then(read().get(0).joinUrl()).isEqualTo("https://teams.microsoft.com/l/meetup-join/own");
    }

    @Test
    void should_find_a_meeting_link_in_the_location_and_then_in_the_description() throws IOException {
        answer(feed(
            event("UID:1", "SUMMARY:A", "DTSTART:20261109T105000Z", "LOCATION:https://zoom.us/j/123",
                "DESCRIPTION:https://teams.microsoft.com/l/meetup-join/x"),
            event("UID:2", "SUMMARY:B", "DTSTART:20261109T105500Z", "LOCATION:Room 4",
                "DESCRIPTION:Join https://meet.google.com/abc-defg-hij now")));

        then(read()).extracting(CalendarEvent::joinUrl)
            .containsExactly("https://zoom.us/j/123", "https://meet.google.com/abc-defg-hij");
    }

    @Test
    void should_not_take_any_link_for_a_meeting_link() throws IOException {
        answer(feed(event("UID:1", "SUMMARY:A", "DTSTART:20261109T105000Z",
            "DESCRIPTION:Slides at https://example.com/deck.pdf")));

        then(read().get(0).joinUrl()).isNull();
    }

    // --- which events ---

    @Test
    void should_skip_cancelled_events() throws IOException {
        answer(feed(
            event("UID:1", "SUMMARY:Gone", "DTSTART:20261109T105000Z", "STATUS:CANCELLED"),
            event("UID:2", "SUMMARY:Kept", "DTSTART:20261109T105500Z", "STATUS:CONFIRMED")));

        then(read()).extracting(CalendarEvent::title).containsExactly("Kept");
    }

    @Test
    void should_flag_all_day_events() throws IOException {
        answer(feed(event("UID:1", "SUMMARY:Holiday", "DTSTART;VALUE=DATE:20261109", "DTEND;VALUE=DATE:20261110")));

        then(source().meetingsStartingBetween(LocalDateTime.of(2026, 11, 9, 0, 0), TO))
            .extracting(CalendarEvent::allDay).containsExactly(true);
    }

    @Test
    void should_return_only_the_meetings_starting_in_the_window() throws IOException {
        answer(feed(
            event("UID:before", "SUMMARY:Before", "DTSTART:20261109T104400Z"),    // 11:44
            event("UID:from", "SUMMARY:AtFrom", "DTSTART:20261109T104500Z"),      // 11:45
            event("UID:to", "SUMMARY:AtTo", "DTSTART:20261109T110000Z")));       // 12:00

        then(read()).extracting(CalendarEvent::id).containsExactly("from");
    }

    @Test
    void should_see_only_the_first_occurrence_of_a_recurring_meeting_for_now() throws IOException {
        // calendar/US-000052 expands the series; until then the first occurrence is all there is
        answer(feed(event("UID:1", "SUMMARY:Weekly", "DTSTART:20261109T105000Z", "RRULE:FREQ=WEEKLY")));

        then(read()).hasSize(1);
        then(source().meetingsStartingBetween(FROM.plusDays(7), TO.plusDays(7))).isEmpty();
    }

    // --- times ---

    @Test
    void should_convert_every_kind_of_time_to_local_time() throws IOException {
        answer(feed(
            event("UID:utc", "SUMMARY:A", "DTSTART:20261109T105000Z"),
            event("UID:iana", "SUMMARY:B", "DTSTART;TZID=Europe/Rome:20261109T115100"),
            event("UID:windows", "SUMMARY:C", "DTSTART;TZID=W. Europe Standard Time:20261109T115200"),
            event("UID:other", "SUMMARY:D", "DTSTART;TZID=Pacific Standard Time:20261109T025300"),
            event("UID:floating", "SUMMARY:E", "DTSTART:20261109T115400")));

        then(read()).extracting(CalendarEvent::start).containsExactly(
            LocalDateTime.of(2026, 11, 9, 11, 50), LocalDateTime.of(2026, 11, 9, 11, 51),
            LocalDateTime.of(2026, 11, 9, 11, 52), LocalDateTime.of(2026, 11, 9, 11, 53),
            LocalDateTime.of(2026, 11, 9, 11, 54));
    }

    @Test
    void should_skip_an_unknown_time_zone_with_a_warning_and_read_the_others() throws Exception {
        List<LogRecord> warnings = warningsOf(() -> {
            answer(feed(
                event("UID:1", "SUMMARY:Lost", "DTSTART;TZID=Nowhere Standard Time:20261109T115000"),
                event("UID:2", "SUMMARY:Found", "DTSTART:20261109T105500Z")));

            then(read()).extracting(CalendarEvent::title).containsExactly("Found");
        });

        then(warnings).hasSize(1);
        then(new java.util.logging.SimpleFormatter().formatMessage(warnings.get(0))).contains("Lost").contains("Nowhere Standard Time");
    }

    // --- what the feed says about the response ---

    @Test
    void should_log_what_the_feed_says_about_the_attendees_without_naming_them() throws IOException {
        List<String> debug = new ArrayList<>();
        Logger log = Logger.getLogger(IcsCalendarSource.class.getName());
        Level before = log.getLevel();
        Handler handler = new Handler() {
            @Override public void publish(LogRecord r) { debug.add(new java.util.logging.SimpleFormatter().formatMessage(r)); }
            @Override public void flush() { }
            @Override public void close() { }
        };
        log.setLevel(Level.FINE);
        log.addHandler(handler);
        try {
            answer(feed(
                event("UID:1", "SUMMARY:Standup", "DTSTART:20261109T105000Z",
                    "ATTENDEE;PARTSTAT=DECLINED;CN=Ste:mailto:ste@example.com",
                    "ATTENDEE;PARTSTAT=ACCEPTED:mailto:ann@example.com"),
                event("UID:2", "SUMMARY:Solo", "DTSTART:20261109T105500Z")));

            then(read()).hasSize(2);   // the status does not decide anything yet
        } finally {
            log.removeHandler(handler);
            log.setLevel(before);
        }

        then(debug).anyMatch(m -> m.contains("Standup") && m.contains("DECLINED") && m.contains("ACCEPTED"));
        then(debug).anyMatch(m -> m.contains("Solo") && m.contains("no attendee"));
        then(debug).noneMatch(m -> m.contains("example.com") || m.contains("Ste") && m.contains("@"));
    }

    // --- failures ---

    @Test
    void should_say_the_link_does_not_work_any_more_on_404_and_410() {
        for (int status : new int[] {404, 410}) {
            http = new HttpClientStubber();
            answer(status, "gone");

            thenThrownBy(this::read)
                .isInstanceOfSatisfying(CalendarAccessException.class, x -> {
                    then(x.isTemporary()).isFalse();
                    then(x.getMessage()).contains("no longer works").contains("publish").contains("outlook.office365.com");
                    then(x.getMessage()).doesNotContain("secret456").doesNotContain("abc123");
                });
        }
    }

    @Test
    void should_call_a_server_failure_temporary() {
        for (int status : new int[] {408, 429, 500, 503}) {
            http = new HttpClientStubber();
            answer(status, "busy");

            thenThrownBy(this::read)
                .isInstanceOfSatisfying(CalendarAccessException.class, x -> {
                    then(x.isTemporary()).isTrue();
                    then(x.getMessage()).contains(String.valueOf(status)).doesNotContain("secret456");
                });
        }
    }

    @Test
    void should_call_a_refusal_permanent() {
        answer(401, "no");

        thenThrownBy(this::read)
            .isInstanceOfSatisfying(CalendarAccessException.class, x -> then(x.isTemporary()).isFalse());
    }

    @Test
    void should_call_a_network_failure_temporary_without_the_address() {
        http.withStub(new URIMatcher(LINK), new NetworkError());

        thenThrownBy(this::read)
            .isInstanceOfSatisfying(CalendarAccessException.class, x -> {
                then(x.isTemporary()).isTrue();
                then(x.getMessage()).contains("outlook.office365.com").doesNotContain("secret456");
            });
    }

    @Test
    void should_say_when_the_answer_is_not_a_calendar() {
        answer("<html><body>Sign in</body></html>");

        thenThrownBy(this::read)
            .isInstanceOfSatisfying(CalendarAccessException.class, x -> {
                then(x.isTemporary()).isFalse();
                then(x.getMessage()).contains("not an iCalendar feed");
            });
    }

    @FunctionalInterface
    private interface Action {
        void run() throws Exception;
    }

    private static List<LogRecord> warningsOf(Action action) throws Exception {
        List<LogRecord> warnings = new ArrayList<>();
        Handler handler = new Handler() {
            @Override public void publish(LogRecord r) { if (r.getLevel() == Level.WARNING) warnings.add(r); }
            @Override public void flush() { }
            @Override public void close() { }
        };
        Logger log = Logger.getLogger(IcsCalendarSource.class.getName());
        log.addHandler(handler);
        try {
            action.run();
        } finally {
            log.removeHandler(handler);
        }
        return warnings;
    }
}
