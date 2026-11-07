package ste.wakemeapp.calendar;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A calendar read from a CSV file, to simulate a remote calendar: the file is read again on every
 * query, so editing it while the app runs plays the part of changes made in a real calendar.
 * <p>
 * The first line (comments and blank lines aside) is a header naming the columns, in any order:
 * required {@code id}, {@code title}, {@code start}; optional {@code end}, {@code url},
 * {@code organizer}, {@code description}, {@code status}
 * ({@code accepted|tentative|declined|none|organizer}, empty = accepted) and {@code allday}.
 * Date-times are ISO local ({@code 2026-11-07T16:30}). Fields with commas are wrapped in double
 * quotes ({@code ""} for a quote); lines starting with {@code #} are ignored.
 */
public final class CsvCalendarSource implements CalendarSource {

    private static final List<String> REQUIRED = List.of("id", "title", "start");

    private final Path file;

    public CsvCalendarSource(Path file) {
        this.file = file;
    }

    @Override
    public List<CalendarEvent> meetingsStartingBetween(LocalDateTime from, LocalDateTime to) throws IOException {
        return read().stream()
            .filter(e -> !e.start().isBefore(from) && e.start().isBefore(to))
            .toList();
    }

    private List<CalendarEvent> read() throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        Map<String, Integer> columns = null;
        List<CalendarEvent> events = new ArrayList<>();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank() || line.stripLeading().startsWith("#")) {
                continue;
            }
            List<String> fields = split(line);
            if (columns == null) {
                columns = header(fields);
                continue;
            }
            events.add(event(columns, fields, i + 1));
        }
        return events;
    }

    private Map<String, Integer> header(List<String> fields) throws IOException {
        Map<String, Integer> columns = new HashMap<>();
        for (int i = 0; i < fields.size(); i++) {
            columns.put(fields.get(i).trim().toLowerCase(Locale.ROOT), i);
        }
        List<String> missing = REQUIRED.stream().filter(name -> !columns.containsKey(name)).toList();
        if (!missing.isEmpty()) {
            throw new IOException(file + ": missing required column(s): " + String.join(", ", missing));
        }
        return columns;
    }

    private CalendarEvent event(Map<String, Integer> columns, List<String> fields, int lineNumber) throws IOException {
        String id = value(columns, fields, "id");
        String title = value(columns, fields, "title");
        String start = value(columns, fields, "start");
        if (id == null || title == null || start == null) {
            throw error(lineNumber, "id, title and start are required");
        }
        return new CalendarEvent(
            id, title,
            dateTime(start, "start", lineNumber),
            optionalDateTime(value(columns, fields, "end"), "end", lineNumber),
            value(columns, fields, "url"),
            value(columns, fields, "organizer"),
            value(columns, fields, "description"),
            status(value(columns, fields, "status"), lineNumber),
            Boolean.parseBoolean(value(columns, fields, "allday"))
        );
    }

    /** The trimmed field, or {@code null} when the column is absent, the row is short or the field empty. */
    private static String value(Map<String, Integer> columns, List<String> fields, String name) {
        Integer index = columns.get(name);
        if (index == null || index >= fields.size()) {
            return null;
        }
        String value = fields.get(index).trim();
        return value.isEmpty() ? null : value;
    }

    private LocalDateTime optionalDateTime(String text, String column, int lineNumber) throws IOException {
        return text == null ? null : dateTime(text, column, lineNumber);
    }

    private LocalDateTime dateTime(String text, String column, int lineNumber) throws IOException {
        try {
            return LocalDateTime.parse(text);
        } catch (DateTimeParseException e) {
            throw error(lineNumber, "invalid " + column + " '" + text + "', expected e.g. 2026-11-07T16:30");
        }
    }

    private ResponseStatus status(String text, int lineNumber) throws IOException {
        if (text == null) {
            return ResponseStatus.ACCEPTED;
        }
        return switch (text.toLowerCase(Locale.ROOT)) {
            case "accepted" -> ResponseStatus.ACCEPTED;
            case "tentative" -> ResponseStatus.TENTATIVE;
            case "declined" -> ResponseStatus.DECLINED;
            case "none" -> ResponseStatus.NOT_RESPONDED;
            case "organizer" -> ResponseStatus.ORGANIZER;
            default -> throw error(lineNumber, "invalid status '" + text
                + "', expected accepted, tentative, declined, none or organizer");
        };
    }

    private IOException error(int lineNumber, String message) {
        return new IOException(file.getFileName() + " line " + lineNumber + ": " + message);
    }

    /** Splits one CSV line; quotes may wrap a field and are doubled inside it. */
    private static List<String> split(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    current.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());
        return fields;
    }
}
