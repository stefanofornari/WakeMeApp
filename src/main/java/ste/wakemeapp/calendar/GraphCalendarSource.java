package ste.wakemeapp.calendar;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reads the meetings from the calendar of the signed-in user with Microsoft Graph.
 * <p>
 * It asks for the <em>calendar view</em> of the window, which also expands the occurrences of
 * recurring meetings (the plain events list returns only the series), and keeps the events that
 * start in the window, since the view also contains the ones that started earlier and are still
 * running. Everything is converted to the local time. Eligibility (response, all-day) is not
 * decided here: cancelled meetings are skipped because they are gone for the user, anything else
 * is reported as it is.
 * <p>
 * It is called from the poller's thread and may block: pages are read one after the other and, when
 * Graph throttles with a 429, the call waits as long as asked (doubling at every further 429, up to
 * {@link #MAX_ATTEMPTS} attempts, never more than {@link #MAX_WAIT} at a time).
 * The access token comes from a supplier, asked at every query, so a refreshed token is used.
 */
public class GraphCalendarSource implements CalendarSource {

    static final String BASE = "https://graph.microsoft.com/v1.0/me/calendarView";
    static final int MAX_ATTEMPTS = 5;
    static final Duration MAX_WAIT = Duration.ofMinutes(1);

    private static final Logger LOG = Logger.getLogger(GraphCalendarSource.class.getName());
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration DEFAULT_RETRY_AFTER = Duration.ofSeconds(1);
    private static final String FIELDS = "id,subject,start,end,isAllDay,isCancelled,responseStatus,organizer,"
        + "onlineMeeting,onlineMeetingUrl,bodyPreview";

    private final HttpClient client;
    private final Supplier<String> accessToken;
    private final ZoneId zone;
    private final Consumer<Duration> sleeper;

    public GraphCalendarSource(HttpClient.Builder http, Supplier<String> accessToken) {
        this(http, accessToken, ZoneId.systemDefault(), GraphCalendarSource::sleep);
    }

    GraphCalendarSource(HttpClient.Builder http, Supplier<String> accessToken, ZoneId zone, Consumer<Duration> sleeper) {
        this.client = http.build();
        this.accessToken = accessToken;
        this.zone = zone;
        this.sleeper = sleeper;
    }

    @Override
    public List<CalendarEvent> meetingsStartingBetween(LocalDateTime from, LocalDateTime to) throws IOException {
        String token = accessToken.get();
        List<CalendarEvent> events = new ArrayList<>();

        URI page = URI.create(BASE
            + "?startDateTime=" + utc(from) + "&endDateTime=" + utc(to)
            + "&$select=" + FIELDS + "&$orderby=start/dateTime&$top=50");
        while (page != null) {
            JsonObject answer = get(page, token);
            for (JsonElement item : array(answer, "value")) {
                CalendarEvent event = toEvent(item);
                if (event != null && !event.start().isBefore(from) && event.start().isBefore(to)) {
                    events.add(event);
                }
            }
            String next = string(answer, "@odata.nextLink");
            page = (next == null) ? null : URI.create(next);
        }
        LOG.log(Level.FINE, "Microsoft Graph returned {0} meeting(s) between {1} and {2}", new Object[] {events.size(), from, to});
        return events;
    }

    // ------------------------------------------------------------------ HTTP

    private JsonObject get(URI uri, String token) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(uri)
            .GET()
            .timeout(REQUEST_TIMEOUT)
            .header("Authorization", "Bearer " + token)
            .header("Accept", "application/json")
            .header("Prefer", "outlook.timezone=\"UTC\"")
            .build();

        Duration waited = Duration.ZERO;
        for (int attempt = 1; ; attempt++) {
            HttpResponse<String> response = send(request);
            int status = response.statusCode();
            if (status == 429) {
                Duration wait = retryAfter(response).multipliedBy(1L << (attempt - 1));
                if (attempt >= MAX_ATTEMPTS || wait.compareTo(MAX_WAIT) > 0) {
                    throw new IOException(
                        "Microsoft Graph is throttling the requests (429, retry after "
                        + retryAfter(response).toSeconds() + " s)"
                    );
                }
                LOG.log(Level.WARNING, "Microsoft Graph asked to slow down; waiting {0} s", wait.toSeconds());
                sleeper.accept(wait);
                continue;
            }
            if (status < 200 || status > 299) {
                throw new IOException("Microsoft Graph answered " + status + errorCode(response.body()));
            }
            return parse(response.body());
        }
    }

    private HttpResponse<String> send(HttpRequest request) throws IOException {
        try {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException x) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Interrupted while reading the calendar");
        }
    }

    private static Duration retryAfter(HttpResponse<?> response) {
        return response.headers().firstValue("Retry-After").map(String::trim).map(value -> {
            try {
                return Duration.ofSeconds(Math.max(1, Long.parseLong(value)));
            } catch (NumberFormatException x) {
                return DEFAULT_RETRY_AFTER;
            }
        }).orElse(DEFAULT_RETRY_AFTER);
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException x) {
            Thread.currentThread().interrupt();
        }
    }

    private static String errorCode(String body) {
        try {
            String code = string(object(JsonParser.parseString(body).getAsJsonObject(), "error"), "code");
            return (code == null) ? "" : " (" + code + ")";
        } catch (RuntimeException x) {
            return "";
        }
    }

    private static JsonObject parse(String body) throws IOException {
        try {
            JsonElement root = JsonParser.parseString(body);
            if (!root.isJsonObject()) {
                throw new IOException("Microsoft Graph answered something that is not a JSON object");
            }
            return root.getAsJsonObject();
        } catch (RuntimeException x) {
            throw new IOException("Microsoft Graph answered something that is not JSON", x);
        }
    }

    // --------------------------------------------------------------- mapping

    /** @return the event, or null for a cancelled meeting */
    private CalendarEvent toEvent(JsonElement item) throws IOException {
        if (!item.isJsonObject()) {
            throw new IOException("Microsoft Graph returned an event that is not an object");
        }
        JsonObject event = item.getAsJsonObject();
        if (bool(event, "isCancelled")) {
            return null;
        }
        String id = string(event, "id");
        LocalDateTime start = time(object(event, "start"));
        if (id == null || start == null) {
            throw new IOException("Microsoft Graph returned an event without id or start time");
        }
        return new CalendarEvent(
            id,
            string(event, "subject"),
            start,
            time(object(event, "end")),
            joinUrl(event),
            organizer(event),
            string(event, "bodyPreview"),
            status(string(object(event, "responseStatus"), "response")),
            bool(event, "isAllDay")
        );
    }

    private static String joinUrl(JsonObject event) {
        String url = string(object(event, "onlineMeeting"), "joinUrl");
        return (url != null) ? url : string(event, "onlineMeetingUrl");
    }

    private static String organizer(JsonObject event) {
        JsonObject address = object(object(event, "organizer"), "emailAddress");
        String name = string(address, "name");
        return (name != null && !name.isBlank()) ? name : string(address, "address");
    }

    private static ResponseStatus status(String response) {
        if (response == null) {
            return ResponseStatus.NOT_RESPONDED;
        }
        return switch (response) {
            case "organizer" -> ResponseStatus.ORGANIZER;
            case "accepted" -> ResponseStatus.ACCEPTED;
            case "tentativelyAccepted" -> ResponseStatus.TENTATIVE;
            case "declined" -> ResponseStatus.DECLINED;
            default -> ResponseStatus.NOT_RESPONDED; // none, notResponded
        };
    }

    /** @return the Graph date-time in local time, or null when it is missing or unreadable */
    private LocalDateTime time(JsonObject dateTime) {
        String value = string(dateTime, "dateTime");
        if (value == null) {
            return null;
        }
        try {
            ZoneId from = ZoneOffset.UTC;
            String name = string(dateTime, "timeZone");
            if (name != null) {
                try {
                    from = ZoneId.of(name);
                } catch (RuntimeException x) {
                    LOG.log(Level.FINE, "Unknown time zone {0}: taken as UTC", name);
                }
            }
            return LocalDateTime.parse(value).atZone(from).withZoneSameInstant(zone).toLocalDateTime();
        } catch (RuntimeException x) {
            return null;
        }
    }

    private String utc(LocalDateTime local) {
        return local.atZone(zone).toInstant().truncatedTo(ChronoUnit.SECONDS).toString();
    }

    // ------------------------------------------------------------------ JSON

    private static JsonObject object(JsonObject parent, String name) {
        if (parent == null) {
            return null;
        }
        JsonElement element = parent.get(name);
        return (element != null && element.isJsonObject()) ? element.getAsJsonObject() : null;
    }

    private static JsonArray array(JsonObject parent, String name) throws IOException {
        JsonElement element = parent.get(name);
        if (element == null || !element.isJsonArray()) {
            throw new IOException("Microsoft Graph answered without '" + name + "'");
        }
        return element.getAsJsonArray();
    }

    private static String string(JsonObject parent, String name) {
        if (parent == null) {
            return null;
        }
        JsonElement element = parent.get(name);
        return (element == null || element.isJsonNull() || !element.isJsonPrimitive()) ? null : element.getAsString();
    }

    private static boolean bool(JsonObject parent, String name) {
        String value = string(parent, name);
        return Boolean.parseBoolean(value);
    }
}
