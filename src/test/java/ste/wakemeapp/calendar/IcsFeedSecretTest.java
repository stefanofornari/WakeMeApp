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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ste.wakemeapp.scheduler.NotificationScheduler;
import ste.xtest.net.http.HttpClientStubber;
import ste.xtest.net.http.StubHttpClient.NetworkError;
import ste.xtest.net.http.StubHttpClient.StubHttpResponse;
import ste.xtest.net.http.URIMatcher;
import static org.assertj.core.api.BDDAssertions.then;

/**
 * The address of an ICS feed is its credential: whatever the app logs about the feed, in whatever
 * situation, must not contain more than the host.
 */
class IcsFeedSecretTest {

    private static final String LINK = "https://outlook.office365.com/owa/calendar/abc123/secret456/calendar.ics";

    @TempDir
    Path folder;

    /** Everything logged, at every level, with the stack traces, while the action runs. */
    private static String logged(Runnable action) {
        List<String> lines = new ArrayList<>();
        Handler handler = new Handler() {
            @Override public void publish(LogRecord r) {
                lines.add(new SimpleFormatter().format(r));
                if (r.getThrown() != null) {
                    StringWriter trace = new StringWriter();
                    r.getThrown().printStackTrace(new PrintWriter(trace));
                    lines.add(trace.toString());
                }
            }
            @Override public void flush() { }
            @Override public void close() { }
        };
        handler.setLevel(Level.ALL);
        Logger root = Logger.getLogger("");
        Logger app = Logger.getLogger("ste.wakemeapp");
        Level before = app.getLevel();
        root.addHandler(handler);
        app.setLevel(Level.ALL);
        try {
            action.run();
        } finally {
            app.setLevel(before);
            root.removeHandler(handler);
        }
        return String.join("\n", lines);
    }

    private String pollWith(Object answer) {
        HttpClientStubber http = new HttpClientStubber();
        http.withStub(new URIMatcher(LINK), answer);
        NotificationScheduler scheduler = new NotificationScheduler(n -> { });
        CalendarPoller poller = new CalendarPoller(new IcsCalendarSource(http, URI.create(LINK)), scheduler,
            Duration.ofMinutes(5), Duration.ofMinutes(15), Clock.systemDefaultZone(),
            new DismissedMeetings(folder.resolve("dismissed.json")));
        try {
            return logged(poller::pollOnce);
        } finally {
            poller.close();
            scheduler.close();
        }
    }

    private static StubHttpResponse<String> answer(int status, String body) {
        return new StubHttpResponse<String>().statusCode(status).text(body);
    }

    private static void thenNothingOfTheLinkIsIn(String log) {
        then(log).isNotBlank();   // something was logged, or the test proves nothing
        then(log).doesNotContain("abc123").doesNotContain("secret456").doesNotContain("calendar.ics");
    }

    @Test
    void should_show_the_host_only_when_the_poll_works() {
        LocalDateTime start = LocalDateTime.now().plusMinutes(12).withNano(0);
        String feed = "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nBEGIN:VEVENT\r\nUID:1\r\nSUMMARY:Standup\r\nDTSTART:"
            + start.atZone(java.time.ZoneId.systemDefault()).withZoneSameInstant(java.time.ZoneOffset.UTC)
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"))
            + "\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n";

        thenNothingOfTheLinkIsIn(pollWith(answer(200, feed)));
    }

    @Test
    void should_show_the_host_only_when_the_link_does_not_work_any_more() {
        String log = pollWith(answer(404, "gone"));

        thenNothingOfTheLinkIsIn(log);
        then(log).contains("outlook.office365.com");
    }

    @Test
    void should_show_the_host_only_when_the_server_fails() {
        String log = pollWith(answer(503, "busy"));

        thenNothingOfTheLinkIsIn(log);
        then(log).contains("outlook.office365.com");
    }

    @Test
    void should_show_the_host_only_when_the_answer_is_not_a_calendar() {
        thenNothingOfTheLinkIsIn(pollWith(answer(200, "<html>Sign in</html>")));
    }

    @Test
    void should_show_the_host_only_when_the_network_fails() {
        thenNothingOfTheLinkIsIn(pollWith(new NetworkError()));
    }

    @Test
    void should_not_save_the_link_when_a_meeting_is_dismissed() throws Exception {
        Path file = folder.resolve("dismissed.json");
        DismissedMeetings dismissed = new DismissedMeetings(file);
        LocalDateTime start = LocalDateTime.now().plusMinutes(12).withNano(0);
        HttpClientStubber http = new HttpClientStubber();
        http.withStub(new URIMatcher(LINK), answer(200, "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nBEGIN:VEVENT\r\nUID:standup-1\r\n"
            + "SUMMARY:Standup\r\nDTSTART:" + start.atZone(java.time.ZoneId.systemDefault())
                .withZoneSameInstant(java.time.ZoneOffset.UTC).format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"))
            + "\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n"));
        NotificationScheduler scheduler = new NotificationScheduler(n -> { });
        CalendarPoller poller = new CalendarPoller(new IcsCalendarSource(http, URI.create(LINK)), scheduler,
            Duration.ofMinutes(5), Duration.ofMinutes(15), Clock.systemDefaultZone(), dismissed);
        try {
            poller.pollOnce();
            then(poller.scheduled()).hasSize(1);

            poller.dismiss(poller.scheduled().get(0).notification());
        } finally {
            poller.close();
            scheduler.close();
        }

        String saved = Files.readString(file);
        then(saved).contains("standup-1");
        then(saved).doesNotContain("abc123").doesNotContain("secret456").doesNotContain("office365");
    }
}
