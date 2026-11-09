package ste.wakemeapp.calendar;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import ste.xtest.net.http.ANDMatcher;
import ste.xtest.net.http.HeaderMatcher;
import ste.xtest.net.http.HttpClientStubber;
import ste.xtest.net.http.RequestMatcher;
import ste.xtest.net.http.StubHttpClient.NetworkError;
import ste.xtest.net.http.StubHttpClient.StubHttpResponse;
import ste.xtest.net.http.URIMatcher;
import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;

/**
 * The Graph calendar source against stubbed HTTP answers (see "HOWTO StubHttpClient"): no network,
 * no real service.
 */
class GraphCalendarSourceTest {

    private static final String BASE = "https://graph.microsoft.com/v1.0/me/calendarView";
    private static final String FIELDS = "id,subject,start,end,isAllDay,isCancelled,responseStatus,organizer,"
        + "onlineMeeting,onlineMeetingUrl,bodyPreview";

    private static final LocalDateTime FROM = LocalDateTime.of(2026, 11, 9, 11, 45);
    private static final LocalDateTime TO = LocalDateTime.of(2026, 11, 9, 12, 0);

    /** The first page of the request for [FROM, TO) when the system zone is UTC. */
    private static final String FIRST_PAGE = BASE
        + "?startDateTime=2026-11-09T11:45:00Z&endDateTime=2026-11-09T12:00:00Z"
        + "&$select=" + FIELDS + "&$orderby=start/dateTime&$top=50";

    private final HttpClientStubber http = new HttpClientStubber();
    private final List<Duration> waits = new ArrayList<>();

    private GraphCalendarSource source() {
        return new GraphCalendarSource(http, () -> "token-1", ZoneId.of("UTC"), waits::add);
    }

    private static String event(String id, String title, String startUtc, String response) {
        return """
            {"id":"%s","subject":"%s","isAllDay":false,"isCancelled":false,
             "start":{"dateTime":"%s","timeZone":"UTC"},
             "end":{"dateTime":"2026-11-09T13:00:00.0000000","timeZone":"UTC"},
             "responseStatus":{"response":"%s"},
             "organizer":{"emailAddress":{"name":"Ann Smith","address":"ann@example.com"}},
             "onlineMeeting":{"joinUrl":"https://teams.example.com/%s"},
             "bodyPreview":"Agenda"}
            """.formatted(id, title, startUtc, response, id);
    }

    private static String page(String nextLink, String... events) {
        return "{" + (nextLink == null ? "" : "\"@odata.nextLink\":\"" + nextLink + "\",")
            + "\"value\":[" + String.join(",", events) + "]}";
    }

    private void stub(String uri, int status, String json) {
        http.withStub(new URIMatcher(uri), new StubHttpResponse<String>().statusCode(status).json(json));
    }

    // --- meetings in the window ---

    @Test
    void should_ask_graph_for_the_window_with_the_token_and_return_the_meeting_details() throws IOException {
        http.withStub(
            new ANDMatcher(
                new URIMatcher(FIRST_PAGE),
                new HeaderMatcher("Authorization", "Bearer token-1"),
                new HeaderMatcher("Prefer", "outlook.timezone=\"UTC\"")
            ),
            new StubHttpResponse<String>().json(page(null,
                event("ev-1", "Standup", "2026-11-09T11:57:00.0000000", "accepted")
            ))
        );

        List<CalendarEvent> events = source().meetingsStartingBetween(FROM, TO);

        then(events).containsExactly(new CalendarEvent(
            "ev-1", "Standup",
            LocalDateTime.of(2026, 11, 9, 11, 57), LocalDateTime.of(2026, 11, 9, 13, 0),
            "https://teams.example.com/ev-1", "Ann Smith", "Agenda",
            ResponseStatus.ACCEPTED, false
        ));
    }

    @Test
    void should_return_only_the_meetings_that_start_inside_the_window() throws IOException {
        stub(FIRST_PAGE, 200, page(null,
            event("running", "Already running", "2026-11-09T11:30:00.0000000", "accepted"), // started before
            event("inside", "Inside", "2026-11-09T11:45:00.0000000", "accepted"),            // on the lower bound
            event("end", "At the end", "2026-11-09T12:00:00.0000000", "accepted")            // the upper bound is open
        ));

        then(source().meetingsStartingBetween(FROM, TO)).extracting(CalendarEvent::id).containsExactly("inside");
    }

    @Test
    void should_return_an_empty_list_when_there_are_no_meetings() throws IOException {
        stub(FIRST_PAGE, 200, page(null));

        then(source().meetingsStartingBetween(FROM, TO)).isEmpty();
    }

    // --- mapping ---

    @Test
    void should_map_the_response_statuses() throws IOException {
        stub(FIRST_PAGE, 200, page(null,
            event("a", "a", "2026-11-09T11:50:00.0000000", "organizer"),
            event("b", "b", "2026-11-09T11:50:00.0000000", "accepted"),
            event("c", "c", "2026-11-09T11:50:00.0000000", "tentativelyAccepted"),
            event("d", "d", "2026-11-09T11:50:00.0000000", "declined"),
            event("e", "e", "2026-11-09T11:50:00.0000000", "notResponded"),
            event("f", "f", "2026-11-09T11:50:00.0000000", "none")
        ));

        then(source().meetingsStartingBetween(FROM, TO)).extracting(CalendarEvent::responseStatus).containsExactly(
            ResponseStatus.ORGANIZER, ResponseStatus.ACCEPTED, ResponseStatus.TENTATIVE,
            ResponseStatus.DECLINED, ResponseStatus.NOT_RESPONDED, ResponseStatus.NOT_RESPONDED
        );
    }

    @Test
    void should_use_the_old_online_meeting_url_when_there_is_no_online_meeting() throws IOException {
        stub(FIRST_PAGE, 200, page(null, """
            {"id":"x","subject":"Old","isAllDay":false,"isCancelled":false,
             "start":{"dateTime":"2026-11-09T11:50:00.0000000","timeZone":"UTC"},
             "end":{"dateTime":"2026-11-09T12:30:00.0000000","timeZone":"UTC"},
             "responseStatus":{"response":"accepted"},
             "onlineMeetingUrl":"https://old.example.com/join"}
            """));

        then(source().meetingsStartingBetween(FROM, TO)).singleElement()
            .extracting(CalendarEvent::joinUrl).isEqualTo("https://old.example.com/join");
    }

    @Test
    void should_accept_meetings_with_only_the_mandatory_data() throws IOException {
        stub(FIRST_PAGE, 200, page(null, """
            {"id":"x","subject":"Bare","isAllDay":false,"isCancelled":false,
             "start":{"dateTime":"2026-11-09T11:50:00.0000000","timeZone":"UTC"},
             "responseStatus":{"response":"accepted"},
             "onlineMeeting":null,"onlineMeetingUrl":null,"organizer":null,"bodyPreview":null}
            """));

        then(source().meetingsStartingBetween(FROM, TO)).singleElement().satisfies(e -> {
            then(e.joinUrl()).isNull();
            then(e.organizer()).isNull();
            then(e.description()).isNull();
            then(e.endTime()).isNull();
        });
    }

    @Test
    void should_use_the_address_when_the_organizer_has_no_name() throws IOException {
        stub(FIRST_PAGE, 200, page(null, """
            {"id":"x","subject":"Who","isAllDay":false,"isCancelled":false,
             "start":{"dateTime":"2026-11-09T11:50:00.0000000","timeZone":"UTC"},
             "responseStatus":{"response":"accepted"},
             "organizer":{"emailAddress":{"address":"ann@example.com"}}}
            """));

        then(source().meetingsStartingBetween(FROM, TO)).singleElement()
            .extracting(CalendarEvent::organizer).isEqualTo("ann@example.com");
    }

    @Test
    void should_report_the_all_day_events_and_leave_their_eligibility_to_the_caller() throws IOException {
        stub(FIRST_PAGE, 200, page(null, """
            {"id":"x","subject":"Holiday","isAllDay":true,"isCancelled":false,
             "start":{"dateTime":"2026-11-09T11:50:00.0000000","timeZone":"UTC"},
             "responseStatus":{"response":"accepted"}}
            """));

        then(source().meetingsStartingBetween(FROM, TO)).singleElement().extracting(CalendarEvent::allDay).isEqualTo(true);
    }

    @Test
    void should_skip_the_cancelled_meetings() throws IOException {
        stub(FIRST_PAGE, 200, page(null,
            event("kept", "Kept", "2026-11-09T11:50:00.0000000", "accepted"),
            event("gone", "Gone", "2026-11-09T11:50:00.0000000", "accepted").replace("\"isCancelled\":false", "\"isCancelled\":true")
        ));

        then(source().meetingsStartingBetween(FROM, TO)).extracting(CalendarEvent::id).containsExactly("kept");
    }

    // --- time zones ---

    @Test
    void should_convert_the_utc_times_to_the_local_time_and_ask_with_the_window_in_utc() throws IOException {
        // Los Angeles is UTC-8 in November: 11:45 local is 19:45 UTC
        stub(BASE + "?startDateTime=2026-11-09T19:45:00Z&endDateTime=2026-11-09T20:00:00Z"
            + "&$select=" + FIELDS + "&$orderby=start/dateTime&$top=50", 200, page(null,
            event("la", "Local", "2026-11-09T19:57:00.0000000", "accepted")
        ));
        GraphCalendarSource source = new GraphCalendarSource(http, () -> "t", ZoneId.of("America/Los_Angeles"), waits::add);

        then(source.meetingsStartingBetween(FROM, TO)).singleElement().satisfies(e -> {
            then(e.start()).isEqualTo(LocalDateTime.of(2026, 11, 9, 11, 57));
            then(e.endTime()).isEqualTo(LocalDateTime.of(2026, 11, 9, 5, 0)); // 13:00 UTC
        });
    }

    // --- pagination ---

    @Test
    void should_follow_the_next_links_and_return_all_the_events_as_one_list() throws IOException {
        String second = BASE + "?$skiptoken=page2";
        String third = BASE + "?$skiptoken=page3";
        stub(FIRST_PAGE, 200, page(second, event("1", "one", "2026-11-09T11:46:00.0000000", "accepted")));
        stub(second, 200, page(third, event("2", "two", "2026-11-09T11:47:00.0000000", "accepted")));
        stub(third, 200, page(null, event("3", "three", "2026-11-09T11:48:00.0000000", "accepted")));

        then(source().meetingsStartingBetween(FROM, TO)).extracting(CalendarEvent::id).containsExactly("1", "2", "3");
    }

    @Test
    void should_send_the_token_with_every_page() throws IOException {
        String second = BASE + "?$skiptoken=page2";
        http.withStub(new ANDMatcher(new URIMatcher(FIRST_PAGE), new HeaderMatcher("Authorization", "Bearer token-1")),
            new StubHttpResponse<String>().json(page(second, event("1", "one", "2026-11-09T11:46:00.0000000", "accepted"))));
        http.withStub(new ANDMatcher(new URIMatcher(second), new HeaderMatcher("Authorization", "Bearer token-1")),
            new StubHttpResponse<String>().json(page(null, event("2", "two", "2026-11-09T11:47:00.0000000", "accepted"))));

        then(source().meetingsStartingBetween(FROM, TO)).hasSize(2);
    }

    @Test
    void should_ask_for_the_token_at_every_query() throws IOException {
        AtomicInteger counter = new AtomicInteger();
        AtomicReference<String> last = new AtomicReference<>();
        RequestMatcher any = request -> {
            last.set(request.headers().firstValue("Authorization").orElse(""));
            return request.uri().equals(URI.create(FIRST_PAGE));
        };
        http.withStub(any, new StubHttpResponse<String>().json(page(null)));
        GraphCalendarSource source = new GraphCalendarSource(http, () -> "token-" + counter.incrementAndGet(), ZoneId.of("UTC"), waits::add);

        source.meetingsStartingBetween(FROM, TO);
        then(last.get()).isEqualTo("Bearer token-1");
        source.meetingsStartingBetween(FROM, TO);
        then(last.get()).isEqualTo("Bearer token-2");
    }

    // --- rate limiting ---

    private void throttled(String uri, int times, String retryAfter, String okJson) {
        AtomicInteger calls = new AtomicInteger();
        http.withStub(request -> request.uri().equals(URI.create(uri)) && calls.getAndIncrement() < times,
            new StubHttpResponse<String>().statusCode(429).header("Retry-After", retryAfter).text("slow down"));
        http.withStub(new URIMatcher(uri), new StubHttpResponse<String>().json(okJson));
    }

    @Test
    void should_wait_the_requested_time_and_try_again_after_a_429() throws IOException {
        throttled(FIRST_PAGE, 1, "3", page(null, event("1", "one", "2026-11-09T11:46:00.0000000", "accepted")));

        then(source().meetingsStartingBetween(FROM, TO)).hasSize(1);
        then(waits).containsExactly(Duration.ofSeconds(3));
    }

    @Test
    void should_double_the_wait_at_every_further_429() throws IOException {
        throttled(FIRST_PAGE, 3, "2", page(null));

        source().meetingsStartingBetween(FROM, TO);

        then(waits).containsExactly(Duration.ofSeconds(2), Duration.ofSeconds(4), Duration.ofSeconds(8));
    }

    @Test
    void should_give_up_when_graph_keeps_throttling() {
        throttled(FIRST_PAGE, 100, "1", page(null));

        thenThrownBy(() -> source().meetingsStartingBetween(FROM, TO))
            .isInstanceOf(IOException.class).hasMessageContaining("429");
        then(waits).hasSize(GraphCalendarSource.MAX_ATTEMPTS - 1);
    }

    @Test
    void should_not_wait_longer_than_a_minute_but_report_the_throttling() {
        throttled(FIRST_PAGE, 100, "3600", page(null));

        thenThrownBy(() -> source().meetingsStartingBetween(FROM, TO))
            .isInstanceOf(IOException.class).hasMessageContaining("3600");
        then(waits).isEmpty();
    }

    @Test
    void should_wait_one_second_when_the_429_does_not_say_how_long() throws IOException {
        AtomicInteger calls = new AtomicInteger();
        http.withStub(request -> calls.getAndIncrement() < 1, new StubHttpResponse<String>().statusCode(429).text("slow"));
        http.withStub(new URIMatcher(FIRST_PAGE), new StubHttpResponse<String>().json(page(null)));

        source().meetingsStartingBetween(FROM, TO);

        then(waits).containsExactly(Duration.ofSeconds(1));
    }

    // --- failures ---

    @Test
    void should_throw_on_a_network_error() {
        http.withStub(new URIMatcher(FIRST_PAGE), new NetworkError());

        thenThrownBy(() -> source().meetingsStartingBetween(FROM, TO)).isInstanceOf(IOException.class);
    }

    @Test
    void should_return_no_partial_result_when_a_later_page_fails() {
        String second = BASE + "?$skiptoken=page2";
        stub(FIRST_PAGE, 200, page(second, event("1", "one", "2026-11-09T11:46:00.0000000", "accepted")));
        http.withStub(new URIMatcher(second), new NetworkError());

        thenThrownBy(() -> source().meetingsStartingBetween(FROM, TO)).isInstanceOf(IOException.class);
    }

    @Test
    void should_throw_with_the_status_when_graph_refuses() {
        stub(FIRST_PAGE, 401, "{\"error\":{\"code\":\"InvalidAuthenticationToken\",\"message\":\"Access token has expired\"}}");

        thenThrownBy(() -> source().meetingsStartingBetween(FROM, TO))
            .isInstanceOf(IOException.class)
            .hasMessageContaining("401")
            .hasMessageContaining("InvalidAuthenticationToken");
    }

    @Test
    void should_throw_when_the_answer_is_not_json() {
        http.withStub(new URIMatcher(FIRST_PAGE), new StubHttpResponse<String>().text("<html>oops</html>"));

        thenThrownBy(() -> source().meetingsStartingBetween(FROM, TO)).isInstanceOf(IOException.class);
    }

    @Test
    void should_throw_when_an_event_has_no_id_or_start() {
        stub(FIRST_PAGE, 200, page(null, "{\"subject\":\"No id\"}"));

        thenThrownBy(() -> source().meetingsStartingBetween(FROM, TO)).isInstanceOf(IOException.class);
    }
}
