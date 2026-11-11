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
import java.io.InterruptedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the meetings from a published calendar (an ICS feed), such as the one Outlook on the web can
 * publish. The feed is downloaded at every call and read as iCalendar (RFC 5545).
 * <p>
 * What is read: id (UID), title, start and end, organizer, description, the join link and the
 * all-day flag; cancelled events are skipped. A feed does not say whether the user accepted: every
 * event is reported as accepted, and what the feed says about the attendees is logged to be able to
 * use it later. Recurring events are not expanded yet (calendar/US-000052): only the first occurrence
 * is seen.
 * <p>
 * The address of the feed is its credential: no message made here contains more than its host.
 */
public class IcsCalendarSource implements CalendarSource {

    private static final Logger LOG = Logger.getLogger(IcsCalendarSource.class.getName());
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final Pattern LINK = Pattern.compile("https://[^\\s<>\"')\\]]+");
    private static final List<String> MEETING_HOSTS = List.of(
        "teams.microsoft.com", "teams.live.com", "zoom.us", "meet.google.com", "webex.com");

    private final HttpClient client;
    private final URI feed;
    private final ZoneId zone;

    public IcsCalendarSource(HttpClient.Builder http, URI feed) {
        this(http, feed, ZoneId.systemDefault());
    }

    IcsCalendarSource(HttpClient.Builder http, URI feed, ZoneId zone) {
        this.client = http.build();
        this.feed = feed;
        this.zone = zone;
    }

    @Override
    public List<CalendarEvent> meetingsStartingBetween(LocalDateTime from, LocalDateTime to) throws IOException {
        List<CalendarEvent> events = new ArrayList<>();
        for (List<Property> properties : vevents(download())) {
            CalendarEvent event = toEvent(properties);
            if (event != null && !event.start().isBefore(from) && event.start().isBefore(to)) {
                events.add(event);
                logAttendees(event.title(), properties);
            }
        }
        LOG.log(Level.FINE, "The feed on {0} returned {1} meeting(s) between {2} and {3}",
            new Object[] {IcsAddress.hostOf(feed), events.size(), from, to});
        return events;
    }

    // ------------------------------------------------------------------ HTTP

    private String download() throws IOException {
        String host = IcsAddress.hostOf(feed);
        HttpRequest request = HttpRequest.newBuilder(feed)
            .GET()
            .timeout(REQUEST_TIMEOUT)
            .header("Accept", "text/calendar, */*;q=0.5")
            .build();
        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedIOException x) {
            throw x;
        } catch (IOException x) {
            throw new CalendarAccessException("Cannot reach the calendar feed on " + host + " (" + x.getClass().getSimpleName() + ")", true);
        } catch (InterruptedException x) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Interrupted while reading the calendar");
        }
        int status = response.statusCode();
        if (status == 404 || status == 410) {
            throw new CalendarAccessException("The calendar link no longer works (" + status + ") on " + host
                + ": publish the calendar again in Outlook and use the new link", false);
        }
        if (status < 200 || status > 299) {
            throw new CalendarAccessException("The calendar feed on " + host + " answered " + status,
                status == 408 || status == 429 || status >= 500);
        }
        String body = response.body() == null ? "" : response.body().stripLeading();
        if (body.startsWith("\uFEFF")) {
            body = body.substring(1).stripLeading();
        }
        if (!body.regionMatches(true, 0, "BEGIN:VCALENDAR", 0, 15)) {
            throw new CalendarAccessException("The answer of " + host + " is not an iCalendar feed", false);
        }
        return body;
    }

    // ------------------------------------------------------------------ iCalendar

    private record Property(String name, Map<String, String> parameters, String value) {
    }

    /** The properties of every VEVENT (those of a nested VALARM are left out). */
    private static List<List<Property>> vevents(String calendar) {
        List<List<Property>> events = new ArrayList<>();
        List<Property> current = null;
        int nested = 0;
        for (String line : unfold(calendar)) {
            Property p = property(line);
            if (p == null) {
                continue;
            }
            if (p.name().equals("BEGIN")) {
                if (current != null) {
                    nested++;
                } else if (p.value().equalsIgnoreCase("VEVENT")) {
                    current = new ArrayList<>();
                }
            } else if (p.name().equals("END")) {
                if (current != null && nested > 0) {
                    nested--;
                } else if (current != null && p.value().equalsIgnoreCase("VEVENT")) {
                    events.add(current);
                    current = null;
                }
            } else if (current != null && nested == 0) {
                current.add(p);
            }
        }
        return events;
    }

    /** A line that starts with a space or a tab continues the previous one. */
    private static List<String> unfold(String calendar) {
        List<String> lines = new ArrayList<>();
        for (String line : calendar.split("\\r?\\n")) {
            if (!lines.isEmpty() && !line.isEmpty() && (line.charAt(0) == ' ' || line.charAt(0) == '\t')) {
                int last = lines.size() - 1;
                lines.set(last, lines.get(last) + line.substring(1));
            } else {
                lines.add(line);
            }
        }
        return lines;
    }

    private static Property property(String line) {
        boolean quoted = false;
        int colon = -1;
        List<Integer> semicolons = new ArrayList<>();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                quoted = !quoted;
            } else if (!quoted && c == ';' && colon < 0) {
                semicolons.add(i);
            } else if (!quoted && c == ':' && colon < 0) {
                colon = i;
                break;
            }
        }
        if (colon <= 0) {
            return null;
        }
        int nameEnd = semicolons.isEmpty() ? colon : semicolons.get(0);
        Map<String, String> parameters = new LinkedHashMap<>();
        for (int i = 0; i < semicolons.size(); i++) {
            int end = (i + 1 < semicolons.size()) ? semicolons.get(i + 1) : colon;
            String parameter = line.substring(semicolons.get(i) + 1, end);
            int eq = parameter.indexOf('=');
            if (eq > 0) {
                String value = parameter.substring(eq + 1);
                if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1);
                }
                parameters.put(parameter.substring(0, eq).toUpperCase(Locale.ROOT), value);
            }
        }
        return new Property(line.substring(0, nameEnd).toUpperCase(Locale.ROOT), parameters, line.substring(colon + 1));
    }

    private static String text(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' && i + 1 < value.length()) {
                char next = value.charAt(++i);
                out.append(next == 'n' || next == 'N' ? '\n' : next);
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static Property first(List<Property> properties, String name) {
        return properties.stream().filter(p -> p.name().equals(name)).findFirst().orElse(null);
    }

    private static String textOf(List<Property> properties, String name) {
        Property p = first(properties, name);
        return (p == null) ? null : text(p.value());
    }

    // ------------------------------------------------------------------ events

    /** @return the event, or null if it is cancelled or cannot be read (the latter is logged) */
    private CalendarEvent toEvent(List<Property> properties) {
        String status = textOf(properties, "STATUS");
        if (status != null && status.equalsIgnoreCase("CANCELLED")) {
            return null;
        }
        String title = textOf(properties, "SUMMARY");
        if (title == null || title.isBlank()) {
            title = "(no title)";
        }
        Property startProperty = first(properties, "DTSTART");
        if (startProperty == null) {
            LOG.log(Level.WARNING, "Skipping the event \"{0}\" of the feed: it has no start", title);
            return null;
        }
        try {
            boolean allDay = "DATE".equalsIgnoreCase(startProperty.parameters().get("VALUE"));
            LocalDateTime start = time(startProperty);
            Property endProperty = first(properties, "DTEND");
            LocalDateTime end = (endProperty == null) ? null : time(endProperty);
            String id = textOf(properties, "UID");
            return new CalendarEvent(
                (id == null || id.isBlank()) ? title + "@" + start : id,
                title, start, end, joinUrl(properties), organizer(properties), textOf(properties, "DESCRIPTION"),
                ResponseStatus.ACCEPTED, allDay);
        } catch (UnknownZone x) {
            LOG.log(Level.WARNING, "Skipping the event \"{0}\" of the feed: unknown time zone \"{1}\"",
                new Object[] {title, x.getMessage()});
        } catch (DateTimeParseException x) {
            LOG.log(Level.WARNING, "Skipping the event \"{0}\" of the feed: its time cannot be read", title);
        }
        return null;
    }

    private static final class UnknownZone extends RuntimeException {
        UnknownZone(String zone) {
            super(zone);
        }
    }

    /** A date, a UTC time, a time in a zone or a floating time (taken as local), in the local time. */
    private LocalDateTime time(Property property) {
        String value = property.value().trim();
        if ("DATE".equalsIgnoreCase(property.parameters().get("VALUE")) || value.length() == 8) {
            return LocalDate.parse(value, DATE).atStartOfDay();
        }
        if (value.endsWith("Z") || value.endsWith("z")) {
            return LocalDateTime.parse(value.substring(0, value.length() - 1), DATE_TIME)
                .atOffset(ZoneOffset.UTC).atZoneSameInstant(zone).toLocalDateTime();
        }
        LocalDateTime time = LocalDateTime.parse(value, DATE_TIME);
        String tzid = property.parameters().get("TZID");
        if (tzid == null) {
            return time;
        }
        ZoneId zoneOfTime = WindowsZones.zoneOf(tzid);
        if (zoneOfTime == null) {
            throw new UnknownZone(tzid);
        }
        return time.atZone(zoneOfTime).withZoneSameInstant(zone).toLocalDateTime();
    }

    private static String organizer(List<Property> properties) {
        Property p = first(properties, "ORGANIZER");
        if (p == null) {
            return null;
        }
        String name = p.parameters().get("CN");
        if (name != null && !name.isBlank()) {
            return name;
        }
        String value = p.value().trim();
        return value.regionMatches(true, 0, "mailto:", 0, 7) ? value.substring(7) : value;
    }

    /** The first link to an online meeting: the Teams property, then the URL, the location, the description. */
    private static String joinUrl(List<Property> properties) {
        for (String name : new String[] {"X-MICROSOFT-SKYPETEAMSMEETINGURL", "URL", "LOCATION", "DESCRIPTION"}) {
            String value = textOf(properties, name);
            if (value == null) {
                continue;
            }
            Matcher m = LINK.matcher(value);
            while (m.find()) {
                String link = m.group().replaceAll("[.,;]+$", "");
                if (isMeetingLink(link)) {
                    return link;
                }
            }
        }
        return null;
    }

    private static boolean isMeetingLink(String link) {
        try {
            String host = URI.create(link).getHost();
            return host != null && MEETING_HOSTS.stream().anyMatch(h -> host.equalsIgnoreCase(h) || host.toLowerCase(Locale.ROOT).endsWith("." + h));
        } catch (IllegalArgumentException x) {
            return false;
        }
    }

    /** What the feed says about the attendees, to know whether the response can be used later. No names. */
    private static void logAttendees(String title, List<Property> properties) {
        LOG.fine(() -> {
            List<String> states = properties.stream().filter(p -> p.name().equals("ATTENDEE"))
                .map(p -> p.parameters().get("PARTSTAT")).filter(s -> s != null).toList();
            return "\"" + title + "\": " + (states.isEmpty()
                ? "the feed has no attendee status" : "the feed reports attendee status " + String.join(", ", states));
        });
    }
}
