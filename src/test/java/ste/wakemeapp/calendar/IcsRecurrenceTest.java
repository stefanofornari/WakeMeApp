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
import ste.xtest.net.http.StubHttpClient.StubHttpResponse;
import ste.xtest.net.http.URIMatcher;
import static org.assertj.core.api.BDDAssertions.then;

/** Recurring meetings of an ICS feed: one event per occurrence, in local time. */
class IcsRecurrenceTest {

    private static final String LINK = "https://outlook.office365.com/owa/calendar/abc123/secret456/calendar.ics";
    private static final ZoneId ROME = ZoneId.of("Europe/Rome");

    /** Mondays at 10:00 Rome time, from 2025-11-10. */
    private static final String WEEKLY_MONDAY = "UID:weekly\r\nSUMMARY:Weekly\r\n"
        + "DTSTART;TZID=Europe/Rome:20251110T100000\r\nDTEND;TZID=Europe/Rome:20251110T103000\r\n";

    private final HttpClientStubber http = new HttpClientStubber();

    private static String feed(String... events) {
        StringBuilder b = new StringBuilder("BEGIN:VCALENDAR\r\nVERSION:2.0\r\n");
        for (String e : events) {
            b.append("BEGIN:VEVENT\r\n").append(e).append("END:VEVENT\r\n");
        }
        return b.append("END:VCALENDAR\r\n").toString();
    }

    private List<CalendarEvent> read(ZoneId zone, String feed, LocalDateTime from, LocalDateTime to) throws IOException {
        http.withStub(new URIMatcher(LINK), new StubHttpResponse<String>().statusCode(200).text(feed));
        return new IcsCalendarSource(http, URI.create(LINK), zone).meetingsStartingBetween(from, to);
    }

    private List<CalendarEvent> read(String feed, LocalDateTime from, LocalDateTime to) throws IOException {
        return read(ROME, feed, from, to);
    }

    private static LocalDateTime at(int month, int day, int hour, int minute) {
        return LocalDateTime.of(2026, month, day, hour, minute);
    }

    @Test
    void should_return_the_occurrence_of_a_weekly_meeting_in_the_window() throws IOException {
        List<CalendarEvent> events = read(feed(WEEKLY_MONDAY + "RRULE:FREQ=WEEKLY\r\n"), at(11, 9, 9, 55), at(11, 9, 10, 5));

        then(events).hasSize(1);
        then(events.get(0).title()).isEqualTo("Weekly");
        then(events.get(0).start()).isEqualTo(at(11, 9, 10, 0));
        then(events.get(0).endTime()).isEqualTo(at(11, 9, 10, 30));
    }

    @Test
    void should_return_nothing_for_a_day_the_meeting_does_not_fall_on() throws IOException {
        then(read(feed(WEEKLY_MONDAY + "RRULE:FREQ=WEEKLY\r\n"), at(11, 10, 9, 55), at(11, 10, 10, 5))).isEmpty();
    }

    @Test
    void should_give_every_occurrence_its_own_id() throws IOException {
        List<CalendarEvent> events = read(feed(WEEKLY_MONDAY + "RRULE:FREQ=WEEKLY\r\n"), at(11, 9, 0, 0), at(11, 24, 0, 0));

        then(events).extracting(CalendarEvent::id)
            .containsExactly("weekly#2026-11-09T10:00", "weekly#2026-11-16T10:00", "weekly#2026-11-23T10:00");
    }

    @Test
    void should_leave_out_an_excluded_occurrence() throws IOException {
        String series = WEEKLY_MONDAY + "RRULE:FREQ=WEEKLY\r\nEXDATE;TZID=Europe/Rome:20261109T100000\r\n";

        then(read(feed(series), at(11, 9, 9, 55), at(11, 9, 10, 5))).isEmpty();
        then(read(feed(series), at(11, 16, 9, 55), at(11, 16, 10, 5))).hasSize(1);
    }

    @Test
    void should_use_the_moved_occurrence_with_the_id_of_the_original() throws IOException {
        String series = WEEKLY_MONDAY + "RRULE:FREQ=WEEKLY\r\n";
        String moved = "UID:weekly\r\nSUMMARY:Weekly (moved)\r\nRECURRENCE-ID;TZID=Europe/Rome:20261109T100000\r\n"
            + "DTSTART;TZID=Europe/Rome:20261109T113000\r\nDTEND;TZID=Europe/Rome:20261109T120000\r\n";

        List<CalendarEvent> later = read(feed(series, moved), at(11, 9, 11, 25), at(11, 9, 11, 35));

        then(later).hasSize(1);
        then(later.get(0).id()).isEqualTo("weekly#2026-11-09T10:00");
        then(later.get(0).title()).isEqualTo("Weekly (moved)");
        then(later.get(0).start()).isEqualTo(at(11, 9, 11, 30));
        then(read(feed(series, moved), at(11, 9, 9, 55), at(11, 9, 10, 5))).isEmpty();
    }

    @Test
    void should_leave_out_a_cancelled_occurrence() throws IOException {
        String series = WEEKLY_MONDAY + "RRULE:FREQ=WEEKLY\r\n";
        String cancelled = "UID:weekly\r\nRECURRENCE-ID;TZID=Europe/Rome:20261109T100000\r\n"
            + "DTSTART;TZID=Europe/Rome:20261109T100000\r\nSTATUS:CANCELLED\r\n";

        then(read(feed(series, cancelled), at(11, 9, 9, 55), at(11, 9, 10, 5))).isEmpty();
        then(read(feed(series, cancelled), at(11, 16, 9, 55), at(11, 16, 10, 5))).hasSize(1);
    }

    @Test
    void should_return_a_moved_occurrence_whose_series_is_not_in_the_feed() throws IOException {
        String moved = "UID:weekly\r\nSUMMARY:Weekly\r\nRECURRENCE-ID;TZID=Europe/Rome:20261109T100000\r\n"
            + "DTSTART;TZID=Europe/Rome:20261109T113000\r\n";

        List<CalendarEvent> events = read(feed(moved), at(11, 9, 11, 25), at(11, 9, 11, 35));

        then(events).extracting(CalendarEvent::id).containsExactly("weekly#2026-11-09T10:00");
    }

    @Test
    void should_keep_the_time_of_the_series_zone_across_daylight_saving_time() throws IOException {
        // 2026-03-29 is the Sunday the clocks go forward in Europe; seen from UTC the meeting moves by one hour
        String series = WEEKLY_MONDAY + "RRULE:FREQ=WEEKLY\r\n";
        ZoneId utc = ZoneId.of("UTC");

        List<CalendarEvent> before = read(utc, feed(series), at(3, 23, 0, 0), at(3, 24, 0, 0));
        List<CalendarEvent> after = read(utc, feed(series), at(3, 30, 0, 0), at(3, 31, 0, 0));

        then(before).extracting(CalendarEvent::start).containsExactly(at(3, 23, 9, 0));
        then(after).extracting(CalendarEvent::start).containsExactly(at(3, 30, 8, 0));
    }

    @Test
    void should_convert_a_series_in_another_zone_to_local_time() throws IOException {
        String series = "UID:ny\r\nSUMMARY:NY\r\nDTSTART;TZID=America/New_York:20251110T100000\r\nRRULE:FREQ=WEEKLY\r\n";

        then(read(feed(series), at(11, 9, 0, 0), at(11, 10, 0, 0))).extracting(CalendarEvent::start)
            .containsExactly(at(11, 9, 16, 0));
    }

    @Test
    void should_read_the_windows_zone_name_of_a_series() throws IOException {
        String series = "UID:win\r\nSUMMARY:Win\r\nDTSTART;TZID=W. Europe Standard Time:20251110T100000\r\nRRULE:FREQ=WEEKLY\r\n";

        then(read(feed(series), at(11, 9, 0, 0), at(11, 10, 0, 0))).extracting(CalendarEvent::start)
            .containsExactly(at(11, 9, 10, 0));
    }

    @Test
    void should_stop_a_series_at_its_count() throws IOException {
        String series = "UID:d\r\nSUMMARY:D\r\nDTSTART;TZID=Europe/Rome:20261109T100000\r\nRRULE:FREQ=DAILY;COUNT=3\r\n";

        then(read(feed(series), at(11, 9, 0, 0), at(11, 20, 0, 0))).extracting(CalendarEvent::start)
            .containsExactly(at(11, 9, 10, 0), at(11, 10, 10, 0), at(11, 11, 10, 0));
    }

    @Test
    void should_stop_a_series_at_its_until() throws IOException {
        String series = "UID:d\r\nSUMMARY:D\r\nDTSTART;TZID=Europe/Rome:20261109T100000\r\nRRULE:FREQ=DAILY;UNTIL=20261111T090000Z\r\n";

        then(read(feed(series), at(11, 9, 0, 0), at(11, 20, 0, 0))).extracting(CalendarEvent::start)
            .containsExactly(at(11, 9, 10, 0), at(11, 10, 10, 0), at(11, 11, 10, 0));
    }

    @Test
    void should_skip_the_weeks_between_occurrences_of_an_interval() throws IOException {
        String series = WEEKLY_MONDAY + "RRULE:FREQ=WEEKLY;INTERVAL=2\r\n";

        then(read(feed(series), at(11, 2, 0, 0), at(11, 24, 0, 0))).extracting(CalendarEvent::start)
            .containsExactly(at(11, 9, 10, 0), at(11, 23, 10, 0));
    }

    @Test
    void should_return_the_chosen_days_of_the_week() throws IOException {
        String series = WEEKLY_MONDAY + "RRULE:FREQ=WEEKLY;BYDAY=MO,WE,FR\r\n";

        then(read(feed(series), at(11, 9, 0, 0), at(11, 14, 0, 0))).extracting(CalendarEvent::start)
            .containsExactly(at(11, 9, 10, 0), at(11, 11, 10, 0), at(11, 13, 10, 0));
    }

    @Test
    void should_return_a_monthly_meeting_on_the_day_of_the_month() throws IOException {
        String series = "UID:m\r\nSUMMARY:M\r\nDTSTART;TZID=Europe/Rome:20260115T100000\r\nRRULE:FREQ=MONTHLY\r\n";

        then(read(feed(series), at(11, 1, 0, 0), at(12, 31, 0, 0))).extracting(CalendarEvent::start)
            .containsExactly(at(11, 15, 10, 0), at(12, 15, 10, 0));
    }

    @Test
    void should_return_a_monthly_meeting_on_the_nth_weekday() throws IOException {
        String second = "UID:m\r\nSUMMARY:M\r\nDTSTART;TZID=Europe/Rome:20260113T100000\r\nRRULE:FREQ=MONTHLY;BYDAY=2TU\r\n";
        String last = "UID:l\r\nSUMMARY:L\r\nDTSTART;TZID=Europe/Rome:20260130T100000\r\nRRULE:FREQ=MONTHLY;BYDAY=-1FR\r\n";

        then(read(feed(second), at(11, 1, 0, 0), at(12, 31, 0, 0))).extracting(CalendarEvent::start)
            .containsExactly(at(11, 10, 10, 0), at(12, 8, 10, 0));
        then(read(feed(last), at(11, 1, 0, 0), at(12, 31, 0, 0))).extracting(CalendarEvent::start)
            .containsExactly(at(11, 27, 10, 0), at(12, 25, 10, 0));
    }

    @Test
    void should_skip_a_month_without_the_day() throws IOException {
        String series = "UID:m\r\nSUMMARY:M\r\nDTSTART;TZID=Europe/Rome:20260131T100000\r\nRRULE:FREQ=MONTHLY\r\n";

        then(read(feed(series), at(2, 1, 0, 0), at(5, 1, 0, 0))).extracting(CalendarEvent::start)
            .containsExactly(at(3, 31, 10, 0));
    }

    @Test
    void should_return_a_yearly_meeting_once_a_year() throws IOException {
        String series = "UID:y\r\nSUMMARY:Y\r\nDTSTART;TZID=Europe/Rome:20200311T100000\r\nRRULE:FREQ=YEARLY\r\n";

        then(read(feed(series), at(1, 1, 0, 0), at(12, 31, 0, 0))).extracting(CalendarEvent::start)
            .containsExactly(at(3, 11, 10, 0));
    }

    @Test
    void should_read_a_daily_series_of_ten_years_without_trouble() throws IOException {
        String series = "UID:d\r\nSUMMARY:D\r\nDTSTART;TZID=Europe/Rome:20160104T100000\r\nRRULE:FREQ=DAILY\r\n";

        then(read(feed(series), at(11, 9, 9, 55), at(11, 9, 10, 5))).hasSize(1);
    }

    @Test
    void should_not_return_the_occurrences_before_the_series_starts() throws IOException {
        String series = "UID:d\r\nSUMMARY:D\r\nDTSTART;TZID=Europe/Rome:20261110T100000\r\nRRULE:FREQ=DAILY\r\n";

        then(read(feed(series), at(11, 9, 0, 0), at(11, 11, 0, 0))).extracting(CalendarEvent::start)
            .containsExactly(at(11, 10, 10, 0));
    }

    @Test
    void should_use_the_first_occurrence_only_and_warn_for_a_rule_it_does_not_know() throws IOException {
        List<LogRecord> warnings = new ArrayList<>();
        Handler handler = new Handler() {
            @Override public void publish(LogRecord r) { if (r.getLevel() == Level.WARNING) warnings.add(r); }
            @Override public void flush() { }
            @Override public void close() { }
        };
        Logger log = Logger.getLogger(IcsCalendarSource.class.getName());
        log.addHandler(handler);
        try {
            String series = "UID:r\r\nSUMMARY:Odd\r\nDTSTART;TZID=Europe/Rome:20261109T100000\r\n"
                + "RRULE:FREQ=MONTHLY;BYDAY=MO,TU;BYSETPOS=1\r\n";

            then(read(feed(series), at(11, 9, 9, 55), at(11, 9, 10, 5))).hasSize(1);
            then(read(feed(series), at(12, 7, 9, 55), at(12, 7, 10, 5))).isEmpty();
        } finally {
            log.removeHandler(handler);
        }

        then(warnings).isNotEmpty();
        then(new java.util.logging.SimpleFormatter().formatMessage(warnings.get(0))).contains("Odd").contains("BYSETPOS");
    }

    @Test
    void should_still_return_a_single_meeting_with_the_id_of_the_event() throws IOException {
        String single = "UID:single\r\nSUMMARY:One\r\nDTSTART;TZID=Europe/Rome:20261109T100000\r\n";

        then(read(feed(single), at(11, 9, 9, 55), at(11, 9, 10, 5))).extracting(CalendarEvent::id).containsExactly("single");
    }
}
