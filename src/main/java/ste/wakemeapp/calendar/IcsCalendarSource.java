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
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.Temporal;
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
import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.data.ParserException;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.Parameter;
import net.fortuna.ical4j.model.component.VEvent;

/**
 * Reads the meetings from a published calendar (an ICS feed), such as the one Outlook on the web can
 * publish. The feed is downloaded at every call and read as iCalendar (RFC 5545) with ical4j, which
 * also expands the recurring events and knows the time zone names Outlook writes.
 * <p>
 * What is read: id, title, start and end, organizer, description, the join link and the all-day flag;
 * cancelled events are skipped. A feed does not say whether the user accepted: every event is reported
 * as accepted, and what the feed says about the attendees is logged to be able to use it later.
 * <p>
 * Every event is read on its own, so one that cannot be read (an unknown time zone, a rule that is not
 * valid) is logged and left out without losing the others. The definitions of time zones in the feed
 * are not used: ical4j would register a new zone in the Java runtime at every poll, and Outlook uses
 * names it already knows. An event in a zone it does not know is skipped.
 * <p>
 * The id of a recurring event's occurrence is the UID plus the original start of the occurrence in the
 * zone of the series, so that an occurrence that was moved keeps its id.
 * <p>
 * The address of the feed is its credential: no message made here contains more than its host.
 */
public class IcsCalendarSource implements CalendarSource {

    private static final Logger LOG = Logger.getLogger(IcsCalendarSource.class.getName());
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final DateTimeFormatter ID_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
    private static final String CALENDAR_START = "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//WakeMeApp//EN\r\n";
    private static final String CALENDAR_END = "END:VCALENDAR\r\n";
    private static final Pattern SUMMARY = Pattern.compile("(?m)^SUMMARY[^:\\r\\n]*:(.*)$");
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
        for (List<VEvent> series : bySeries(vevents(download()))) {
            for (Occurrence occurrence : occurrences(series, from, to)) {
                CalendarEvent event = occurrence.event();
                if (!event.start().isBefore(from) && event.start().isBefore(to)) {
                    events.add(event);
                    logAttendees(event.title(), occurrence.source());
                }
            }
        }
        LOG.log(Level.FINE, "The feed on {0} returned {1} meeting(s) between {2} and {3}",
            new Object[] {IcsAddress.hostOf(feed), events.size(), from, to});
        return events;
    }

    private record Occurrence(CalendarEvent event, VEvent source) {
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
        if (body.startsWith("﻿")) {
            body = body.substring(1).stripLeading();
        }
        if (!body.regionMatches(true, 0, "BEGIN:VCALENDAR", 0, 15)) {
            throw new CalendarAccessException("The answer of " + host + " is not an iCalendar feed", false);
        }
        return body;
    }

    // ------------------------------------------------------------------ reading the feed

    /**
     * The events of the feed, each read on its own. Whatever is outside the events (the definitions of
     * time zones above all) is left out.
     */
    private static List<VEvent> vevents(String calendar) {
        List<VEvent> events = new ArrayList<>();
        StringBuilder block = null;
        for (String line : calendar.split("\\r?\\n")) {
            if (block == null) {
                if (line.strip().equalsIgnoreCase("BEGIN:VEVENT")) {
                    block = new StringBuilder(line.strip()).append("\r\n");
                }
                continue;
            }
            block.append(line).append("\r\n");
            if (line.strip().equalsIgnoreCase("END:VEVENT")) {
                events.addAll(read(block.toString()));
                block = null;
            }
        }
        return events;
    }

    private static List<VEvent> read(String block) {
        try {
            var calendar = new CalendarBuilder().build(new StringReader(CALENDAR_START + block + CALENDAR_END));
            List<VEvent> events = new ArrayList<>();
            for (VEvent event : calendar.<VEvent>getComponents(Component.VEVENT)) {
                if (event.getDateTimeStart() == null) {
                    LOG.log(Level.WARNING, "Skipping the event \"{0}\" of the feed: it has no start", titleOf(block));
                } else {
                    events.add(event);
                }
            }
            return events;
        } catch (IOException | ParserException | RuntimeException x) {
            LOG.log(Level.WARNING, "Skipping the event \"{0}\" of the feed: {1}",
                new Object[] {titleOf(block), (x.getMessage() == null) ? x.getClass().getSimpleName() : x.getMessage()});
            return List.of();
        }
    }

    private static String titleOf(String block) {
        Matcher m = SUMMARY.matcher(block);
        return (m.find() && !m.group(1).isBlank()) ? m.group(1).strip() : "(no title)";
    }

    private static String summaryOf(VEvent event) {
        return (event.getSummary() == null || event.getSummary().getValue().isBlank()) ? "(no title)" : event.getSummary().getValue();
    }

    /** The events of the same UID together: a series, with the occurrences that were changed. */
    private static List<List<VEvent>> bySeries(List<VEvent> events) {
        Map<String, List<VEvent>> series = new LinkedHashMap<>();
        int anonymous = 0;
        for (VEvent event : events) {
            String uid = uidOf(event);
            series.computeIfAbsent((uid == null || uid.isBlank()) ? "\0" + anonymous++ : uid, k -> new ArrayList<>()).add(event);
        }
        return new ArrayList<>(series.values());
    }

    private static String uidOf(VEvent event) {
        return event.getUid().map(u -> u.getValue()).orElse(null);
    }

    // ------------------------------------------------------------------ times

    private ZoneId zoneOf(Temporal time) {
        if (time instanceof ZonedDateTime z) {
            return z.getZone();
        }
        if (time instanceof OffsetDateTime o) {
            return o.getOffset();
        }
        return zone;   // a floating time or a date is in the local zone
    }

    private Instant instantOf(Temporal time) {
        if (time instanceof ZonedDateTime z) {
            return z.toInstant();
        }
        if (time instanceof OffsetDateTime o) {
            return o.toInstant();
        }
        if (time instanceof Instant i) {
            return i;
        }
        if (time instanceof LocalDate d) {
            return d.atStartOfDay(zone).toInstant();
        }
        return ((LocalDateTime) time).atZone(zone).toInstant();
    }

    private LocalDateTime local(Temporal time) {
        return instantOf(time).atZone(zone).toLocalDateTime();
    }

    // ------------------------------------------------------------------ events

    /** The occurrences of a series that may start in the window. */
    private List<Occurrence> occurrences(List<VEvent> series, LocalDateTime from, LocalDateTime to) {
        VEvent master = null;
        List<VEvent> changed = new ArrayList<>();
        for (VEvent event : series) {
            if (event.getRecurrenceId() != null) {
                changed.add(event);
            } else if (master == null) {
                master = event;
            }
        }
        List<Occurrence> result = new ArrayList<>();
        try {
            if (master != null && !repeats(master)) {
                add(result, single(master, uidOf(master)));
                return result;
            }
            String uid = uidOf(series.get(0));
            ZoneId seriesZone = zoneOf((master != null) ? master.getDateTimeStart().getDate()
                : changed.get(0).getRecurrenceId().getDate());
            Set<Instant> replaced = new HashSet<>();
            for (VEvent event : changed) {
                Instant original = instantOf(event.getRecurrenceId().getDate());
                replaced.add(original);
                add(result, single(event, idOf(uid, original, seriesZone)));
            }
            if (master != null) {
                expand(master, from, to, replaced, seriesZone, result);
            }
        } catch (DateTimeException | UnsupportedOperationException x) {
            LOG.log(Level.WARNING, "Skipping the event \"{0}\" of the feed: its time cannot be read ({1})",
                new Object[] {summaryOf(series.get(0)), x.getMessage()});
            return List.of();
        }
        return result;
    }

    private static boolean repeats(VEvent event) {
        return !event.getProperties("RRULE").isEmpty() || !event.getProperties("RDATE").isEmpty();
    }

    private void expand(VEvent master, LocalDateTime from, LocalDateTime to, Set<Instant> replaced,
                        ZoneId seriesZone, List<Occurrence> result) {
        if (isCancelled(master)) {
            return;
        }
        boolean hasEnd = master.getDateTimeEnd() != null || master.getDuration() != null;
        var window = new net.fortuna.ical4j.model.Period<ZonedDateTime>(from.atZone(zone), to.atZone(zone));
        // ical4j applies RRULE, RDATE and EXDATE, with the zone of the series
        for (net.fortuna.ical4j.model.Period<Temporal> occurrence : master.<Temporal>calculateRecurrenceSet(window)) {
            Instant instant = instantOf(occurrence.getStart());
            if (replaced.contains(instant)) {
                continue;
            }
            LocalDateTime start = local(occurrence.getStart());
            LocalDateTime end = hasEnd ? local(occurrence.getEnd()) : null;
            add(result, build(master, idOf(uidOf(master), instant, seriesZone), start, end,
                occurrence.getStart() instanceof LocalDate));
        }
    }

    /** UID of the series plus the original start of the occurrence in the zone of the series. */
    private static String idOf(String uid, Instant original, ZoneId seriesZone) {
        return uid + "#" + ID_TIME.format(original.atZone(seriesZone));
    }

    private static void add(List<Occurrence> result, Occurrence occurrence) {
        if (occurrence != null) {
            result.add(occurrence);
        }
    }

    /** A single event (or one changed occurrence), by its own times: null if it is cancelled. */
    private Occurrence single(VEvent event, String id) {
        Temporal start = event.getDateTimeStart().getDate();
        LocalDateTime end = null;
        if (event.getDateTimeEnd() != null) {
            end = local(event.getDateTimeEnd().getDate());
        } else if (event.getDuration() != null) {
            end = local(start.plus(event.getDuration().getDuration()));
        }
        return build(event, id, local(start), end, start instanceof LocalDate);
    }

    private Occurrence build(VEvent event, String id, LocalDateTime start, LocalDateTime end, boolean allDay) {
        if (isCancelled(event)) {
            return null;
        }
        String title = summaryOf(event);
        String description = (event.getDescription() == null) ? null : event.getDescription().getValue();
        return new Occurrence(new CalendarEvent(
            (id == null || id.isBlank()) ? title + "@" + start : id,
            title, start, end, joinUrl(event), organizer(event), description, ResponseStatus.ACCEPTED, allDay), event);
    }

    private static boolean isCancelled(VEvent event) {
        return event.getStatus() != null && "CANCELLED".equalsIgnoreCase(event.getStatus().getValue());
    }

    private static String organizer(VEvent event) {
        var organizer = event.getOrganizer();
        if (organizer == null) {
            return null;
        }
        var name = organizer.getParameter(Parameter.CN);
        if (name.isPresent() && !name.get().getValue().isBlank()) {
            return name.get().getValue();
        }
        String value = organizer.getValue().trim();
        return value.regionMatches(true, 0, "mailto:", 0, 7) ? value.substring(7) : value;
    }

    /** The first link to an online meeting: the Teams property, then the URL, the location, the description. */
    private static String joinUrl(VEvent event) {
        List<String> texts = new ArrayList<>();
        event.getProperty("X-MICROSOFT-SKYPETEAMSMEETINGURL").ifPresent(p -> texts.add(p.getValue()));
        if (event.getUrl() != null) {
            texts.add(event.getUrl().getValue());
        }
        if (event.getLocation() != null) {
            texts.add(event.getLocation().getValue());
        }
        if (event.getDescription() != null) {
            texts.add(event.getDescription().getValue());
        }
        for (String text : texts) {
            Matcher m = LINK.matcher(text);
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
    private static void logAttendees(String title, VEvent event) {
        LOG.fine(() -> {
            List<String> states = event.getAttendees().stream()
                .map(a -> a.getParameter(Parameter.PARTSTAT)).filter(p -> p.isPresent()).map(p -> p.get().getValue()).toList();
            return "\"" + title + "\": " + (states.isEmpty()
                ? "the feed has no attendee status" : "the feed reports attendee status " + String.join(", ", states));
        });
    }
}
