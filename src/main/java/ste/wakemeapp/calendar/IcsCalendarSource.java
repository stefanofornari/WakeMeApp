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
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
 * use it later. Recurring events give one event per occurrence (see {@link Recurrence}); a rule that is
 * not supported leaves the first occurrence only, with a warning.
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
        for (List<List<Property>> series : bySeries(vevents(download()))) {
            for (Occurrence occurrence : occurrences(series, from, to)) {
                CalendarEvent event = occurrence.event();
                if (!event.start().isBefore(from) && event.start().isBefore(to)) {
                    events.add(event);
                    logAttendees(event.title(), occurrence.properties());
                }
            }
        }
        LOG.log(Level.FINE, "The feed on {0} returned {1} meeting(s) between {2} and {3}",
            new Object[] {IcsAddress.hostOf(feed), events.size(), from, to});
        return events;
    }

    private record Occurrence(CalendarEvent event, List<Property> properties) {
    }

    /** The VEVENTs of the same UID together: a series, with the occurrences that were changed. */
    private static List<List<List<Property>>> bySeries(List<List<Property>> vevents) {
        Map<String, List<List<Property>>> series = new LinkedHashMap<>();
        int anonymous = 0;
        for (List<Property> vevent : vevents) {
            String uid = textOf(vevent, "UID");
            series.computeIfAbsent((uid == null || uid.isBlank()) ? "\0" + anonymous++ : uid, k -> new ArrayList<>()).add(vevent);
        }
        return new ArrayList<>(series.values());
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

    /** The occurrences of a series that may start in the window; what cannot be read is logged and left out. */
    private List<Occurrence> occurrences(List<List<Property>> series, LocalDateTime from, LocalDateTime to) {
        List<Property> master = null;
        List<List<Property>> changed = new ArrayList<>();
        for (List<Property> vevent : series) {
            if (first(vevent, "RECURRENCE-ID") != null) {
                changed.add(vevent);
            } else if (master == null) {
                master = vevent;
            }
        }
        String title = titleOf(series.get(0));
        List<Occurrence> result = new ArrayList<>();
        try {
            Property rule = (master == null) ? null : first(master, "RRULE");
            if (master != null && rule == null) {
                add(result, build(master, textOf(master, "UID"), time(first(master, "DTSTART")), master));
                return result;
            }
            ZoneId seriesZone = (master != null) ? zoneOf(first(master, "DTSTART")) : zoneOf(first(changed.get(0), "RECURRENCE-ID"));
            Set<Instant> replaced = new HashSet<>();
            for (List<Property> vevent : changed) {
                Instant original = zoned(first(vevent, "RECURRENCE-ID")).toInstant();
                replaced.add(original);
                String id = idOf(vevent, original, seriesZone);
                add(result, build(vevent, id, time(first(vevent, "DTSTART")), vevent));
            }
            if (master != null) {
                expand(master, rule.value(), from, to, replaced, seriesZone, result);
            }
        } catch (UnknownZone x) {
            LOG.log(Level.WARNING, "Skipping the event \"{0}\" of the feed: unknown time zone \"{1}\"",
                new Object[] {title, x.getMessage()});
            return List.of();
        } catch (DateTimeParseException | NullPointerException x) {
            LOG.log(Level.WARNING, "Skipping the event \"{0}\" of the feed: its time cannot be read", title);
            return List.of();
        }
        return result;
    }

    private void expand(List<Property> master, String rule, LocalDateTime from, LocalDateTime to,
                        Set<Instant> replaced, ZoneId seriesZone, List<Occurrence> result) {
        String status = textOf(master, "STATUS");
        if (status != null && status.equalsIgnoreCase("CANCELLED")) {
            return;
        }
        Property startProperty = first(master, "DTSTART");
        ZonedDateTime start = zoned(startProperty);
        Property endProperty = first(master, "DTEND");
        java.time.Duration length = (endProperty == null) ? null : java.time.Duration.between(start, zoned(endProperty));
        Recurrence recurrence;
        try {
            recurrence = Recurrence.parse(rule);
        } catch (Recurrence.Unsupported x) {
            LOG.log(Level.WARNING, "The event \"{0}\" of the feed repeats in a way that is not supported ({1}): only its first occurrence is used",
                new Object[] {titleOf(master), x.getMessage()});
            add(result, build(master, textOf(master, "UID"), start.withZoneSameInstant(zone).toLocalDateTime(), master));
            return;
        }
        Set<Instant> excluded = new HashSet<>();
        Set<LocalDate> excludedDates = new HashSet<>();
        for (Property p : master) {
            if (p.name().equals("EXDATE")) {
                for (String value : p.value().split(",")) {
                    Property one = new Property("EXDATE", p.parameters(), value.trim());
                    if (value.trim().length() == 8) {
                        excludedDates.add(LocalDate.parse(value.trim(), DATE));
                    } else {
                        excluded.add(zoned(one).toInstant());
                    }
                }
            }
        }
        Instant notBefore = from.atZone(zone).toInstant();
        Instant end = to.atZone(zone).toInstant();
        // an occurrence may last into the window while it started before it: only the start counts
        for (ZonedDateTime occurrence : recurrence.occurrences(start, notBefore, end)) {
            Instant instant = occurrence.toInstant();
            if (replaced.contains(instant) || excluded.contains(instant) || excludedDates.contains(occurrence.toLocalDate())) {
                continue;
            }
            String id = idOf(master, instant, seriesZone);
            LocalDateTime localStart = occurrence.withZoneSameInstant(zone).toLocalDateTime();
            LocalDateTime localEnd = (length == null) ? null : occurrence.plus(length).withZoneSameInstant(zone).toLocalDateTime();
            CalendarEvent event = build(master, id, localStart, localEnd, "DATE".equalsIgnoreCase(startProperty.parameters().get("VALUE")));
            add(result, new Occurrence(event, master));
        }
    }

    private static final DateTimeFormatter ID_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    /** UID of the series plus the original start of the occurrence in the zone of the series. */
    private static String idOf(List<Property> vevent, Instant original, ZoneId seriesZone) {
        String uid = textOf(vevent, "UID");
        return uid + "#" + ID_TIME.format(original.atZone(seriesZone));
    }

    private static String titleOf(List<Property> properties) {
        String title = textOf(properties, "SUMMARY");
        return (title == null || title.isBlank()) ? "(no title)" : title;
    }

    private static void add(List<Occurrence> result, Occurrence occurrence) {
        if (occurrence != null) {
            result.add(occurrence);
        }
    }

    /** A single event (or one changed occurrence): null if it is cancelled. */
    private Occurrence build(List<Property> properties, String id, LocalDateTime start, List<Property> source) {
        Property endProperty = first(source, "DTEND");
        return build0(properties, id, start, endProperty == null ? null : time(endProperty),
            "DATE".equalsIgnoreCase(first(source, "DTSTART").parameters().get("VALUE")));
    }

    private CalendarEvent build(List<Property> properties, String id, LocalDateTime start, LocalDateTime end, boolean allDay) {
        return build0(properties, id, start, end, allDay).event();
    }

    private Occurrence build0(List<Property> properties, String id, LocalDateTime start, LocalDateTime end, boolean allDay) {
        String status = textOf(properties, "STATUS");
        if (status != null && status.equalsIgnoreCase("CANCELLED")) {
            return null;
        }
        String title = titleOf(properties);
        return new Occurrence(new CalendarEvent(
            (id == null || id.isBlank()) ? title + "@" + start : id,
            title, start, end, joinUrl(properties), organizer(properties), textOf(properties, "DESCRIPTION"),
            ResponseStatus.ACCEPTED, allDay), properties);
    }

    private static final class UnknownZone extends RuntimeException {
        UnknownZone(String zone) {
            super(zone);
        }
    }

    /** The zone a time is written in: UTC for Z, the TZID, the local zone for a floating time or a date. */
    private ZoneId zoneOf(Property property) {
        String value = property.value().trim();
        if (value.endsWith("Z") || value.endsWith("z")) {
            return ZoneOffset.UTC;
        }
        String tzid = property.parameters().get("TZID");
        if (tzid == null || "DATE".equalsIgnoreCase(property.parameters().get("VALUE")) || value.length() == 8) {
            return zone;
        }
        ZoneId zoneOfTime = WindowsZones.zoneOf(tzid);
        if (zoneOfTime == null) {
            throw new UnknownZone(tzid);
        }
        return zoneOfTime;
    }

    /** A date, a UTC time, a time in a zone or a floating time (taken as local), with its zone. */
    private ZonedDateTime zoned(Property property) {
        String value = property.value().trim();
        if ("DATE".equalsIgnoreCase(property.parameters().get("VALUE")) || value.length() == 8) {
            return LocalDate.parse(value, DATE).atStartOfDay(zone);
        }
        boolean utc = value.endsWith("Z") || value.endsWith("z");
        LocalDateTime time = LocalDateTime.parse(utc ? value.substring(0, value.length() - 1) : value, DATE_TIME);
        return time.atZone(zoneOf(property));
    }

    private LocalDateTime time(Property property) {
        return zoned(property).withZoneSameInstant(zone).toLocalDateTime();
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
