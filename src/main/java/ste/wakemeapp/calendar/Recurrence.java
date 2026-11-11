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

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

/**
 * The recurrence rule (RRULE) of an event: DAILY, WEEKLY, MONTHLY and YEARLY with INTERVAL, COUNT,
 * UNTIL, BYDAY (also with an ordinal, as in {@code 2TU} or {@code -1FR}), BYMONTHDAY, BYMONTH and WKST,
 * which is what calendars write for the usual recurring meetings. Anything else is refused with
 * {@link Unsupported}: guessing would notify at the wrong time.
 * <p>
 * The occurrences are worked out on the calendar of the zone of the series, so that a meeting at 10:00
 * stays at 10:00 there when the clocks change.
 */
final class Recurrence {

    /** The rule uses something that is not supported; the message says what. */
    static final class Unsupported extends Exception {
        Unsupported(String what) {
            super(what);
        }
    }

    private record DayRule(int ordinal, DayOfWeek day) {
    }

    private static final int MAX_PERIODS = 200_000;
    private static final Map<String, DayOfWeek> DAYS = Map.of(
        "MO", DayOfWeek.MONDAY, "TU", DayOfWeek.TUESDAY, "WE", DayOfWeek.WEDNESDAY, "TH", DayOfWeek.THURSDAY,
        "FR", DayOfWeek.FRIDAY, "SA", DayOfWeek.SATURDAY, "SU", DayOfWeek.SUNDAY);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final String frequency;
    private final int interval;
    private final int count;                 // -1: not limited by a count
    private final String until;              // null, or the value of UNTIL
    private final List<DayRule> byDay = new ArrayList<>();
    private final List<Integer> byMonthDay = new ArrayList<>();
    private final List<Integer> byMonth = new ArrayList<>();
    private DayOfWeek weekStart = DayOfWeek.MONDAY;

    private Recurrence(String frequency, int interval, int count, String until) {
        this.frequency = frequency;
        this.interval = interval;
        this.count = count;
        this.until = until;
    }

    static Recurrence parse(String rule) throws Unsupported {
        Map<String, String> parts = new java.util.LinkedHashMap<>();
        for (String part : rule.split(";")) {
            int eq = part.indexOf('=');
            if (eq > 0) {
                parts.put(part.substring(0, eq).trim().toUpperCase(Locale.ROOT), part.substring(eq + 1).trim());
            }
        }
        for (String key : parts.keySet()) {
            if (!List.of("FREQ", "INTERVAL", "COUNT", "UNTIL", "BYDAY", "BYMONTHDAY", "BYMONTH", "WKST").contains(key)) {
                throw new Unsupported(key);
            }
        }
        String frequency = parts.getOrDefault("FREQ", "").toUpperCase(Locale.ROOT);
        if (!List.of("DAILY", "WEEKLY", "MONTHLY", "YEARLY").contains(frequency)) {
            throw new Unsupported("FREQ=" + frequency);
        }
        try {
            Recurrence r = new Recurrence(frequency,
                Math.max(1, Integer.parseInt(parts.getOrDefault("INTERVAL", "1"))),
                parts.containsKey("COUNT") ? Integer.parseInt(parts.get("COUNT")) : -1,
                parts.get("UNTIL"));
            if (parts.containsKey("WKST")) {
                r.weekStart = day(parts.get("WKST"));
            }
            if (parts.containsKey("BYDAY")) {
                for (String d : parts.get("BYDAY").split(",")) {
                    String v = d.trim().toUpperCase(Locale.ROOT);
                    String name = v.substring(v.length() - 2);
                    String ordinal = v.substring(0, v.length() - 2);
                    r.byDay.add(new DayRule(ordinal.isEmpty() ? 0 : Integer.parseInt(ordinal), day(name)));
                }
            }
            if (parts.containsKey("BYMONTHDAY")) {
                for (String d : parts.get("BYMONTHDAY").split(",")) {
                    r.byMonthDay.add(Integer.parseInt(d.trim()));
                }
            }
            if (parts.containsKey("BYMONTH")) {
                for (String m : parts.get("BYMONTH").split(",")) {
                    r.byMonth.add(Integer.parseInt(m.trim()));
                }
            }
            return r;
        } catch (NumberFormatException | StringIndexOutOfBoundsException x) {
            throw new Unsupported("the rule cannot be read");
        }
    }

    private static DayOfWeek day(String name) throws Unsupported {
        DayOfWeek day = DAYS.get(name.toUpperCase(Locale.ROOT));
        if (day == null) {
            throw new Unsupported("day " + name);
        }
        return day;
    }

    /**
     * @return the occurrences of the series that start at or after {@code notBefore} and before
     *         {@code end}, in the zone of {@code first} (the start of the series)
     */
    List<ZonedDateTime> occurrences(ZonedDateTime first, Instant notBefore, Instant end) {
        ZoneId zone = first.getZone();
        LocalDate firstDate = first.toLocalDate();
        LocalDate endDate = end.atZone(zone).toLocalDate();
        Instant until = untilInstant(zone);
        LocalDate untilDate = (this.until != null && this.until.length() == 8) ? LocalDate.parse(this.until, DATE) : null;

        long period = 0;
        if (count < 0) {
            period = Math.max(0, periodsBetween(firstDate, notBefore.atZone(zone).toLocalDate()) - 1);
        }
        List<ZonedDateTime> found = new ArrayList<>();
        int generated = 0;
        for (; period < MAX_PERIODS; period++) {
            if (periodStart(firstDate, period).isAfter(endDate)) {
                break;
            }
            for (LocalDate date : candidates(firstDate, period)) {
                if (date.isBefore(firstDate)) {
                    continue;
                }
                ZonedDateTime occurrence = ZonedDateTime.of(date, first.toLocalTime(), zone);
                if (count >= 0 && ++generated > count
                    || until != null && occurrence.toInstant().isAfter(until)
                    || untilDate != null && date.isAfter(untilDate)
                    || !occurrence.toInstant().isBefore(end)) {
                    return found;
                }
                if (!occurrence.toInstant().isBefore(notBefore)) {
                    found.add(occurrence);
                }
            }
        }
        return found;
    }

    private Instant untilInstant(ZoneId zone) {
        if (until == null || until.length() == 8) {
            return null;
        }
        if (until.endsWith("Z") || until.endsWith("z")) {
            return LocalDateTime.parse(until.substring(0, until.length() - 1), DATE_TIME).atZone(ZoneId.of("UTC")).toInstant();
        }
        return LocalDateTime.parse(until, DATE_TIME).atZone(zone).toInstant();
    }

    // ------------------------------------------------------------------ periods

    private LocalDate weekStartOf(LocalDate date) {
        return date.minusDays(Math.floorMod(date.getDayOfWeek().getValue() - weekStart.getValue(), 7));
    }

    /** First day of the period (a day, a week, a month, a year) of the given number. */
    private LocalDate periodStart(LocalDate first, long period) {
        return switch (frequency) {
            case "DAILY" -> first.plusDays(period * interval);
            case "WEEKLY" -> weekStartOf(first).plusWeeks(period * interval);
            case "MONTHLY" -> first.withDayOfMonth(1).plusMonths(period * interval);
            default -> first.withDayOfYear(1).plusYears(period * interval);
        };
    }

    private long periodsBetween(LocalDate first, LocalDate date) {
        long units = switch (frequency) {
            case "DAILY" -> ChronoUnit.DAYS.between(first, date);
            case "WEEKLY" -> ChronoUnit.WEEKS.between(weekStartOf(first), weekStartOf(date));
            case "MONTHLY" -> ChronoUnit.MONTHS.between(first.withDayOfMonth(1), date.withDayOfMonth(1));
            default -> ChronoUnit.YEARS.between(first.withDayOfYear(1), date.withDayOfYear(1));
        };
        return units / interval;
    }

    private List<LocalDate> candidates(LocalDate first, long period) {
        LocalDate start = periodStart(first, period);
        TreeSet<LocalDate> dates = new TreeSet<>();
        switch (frequency) {
            case "DAILY" -> {
                if (matchesDay(start) && (byMonth.isEmpty() || byMonth.contains(start.getMonthValue()))
                    && (byMonthDay.isEmpty() || monthDays(YearMonth.from(start)).contains(start))) {
                    dates.add(start);
                }
            }
            case "WEEKLY" -> {
                List<DayOfWeek> days = new ArrayList<>();
                byDay.forEach(r -> days.add(r.day()));
                if (days.isEmpty()) {
                    days.add(first.getDayOfWeek());
                }
                for (DayOfWeek day : days) {
                    dates.add(start.plusDays(Math.floorMod(day.getValue() - weekStart.getValue(), 7)));
                }
            }
            case "MONTHLY" -> dates.addAll(monthDates(YearMonth.from(start), first));
            default -> {
                List<Integer> months = byMonth.isEmpty() ? List.of(first.getMonthValue()) : byMonth;
                for (int month : months) {
                    dates.addAll(monthDates(YearMonth.of(start.getYear(), month), first));
                }
            }
        }
        return new ArrayList<>(dates);
    }

    private boolean matchesDay(LocalDate date) {
        return byDay.isEmpty() || byDay.stream().anyMatch(r -> r.day() == date.getDayOfWeek());
    }

    /** The days of a month the rule selects, by BYMONTHDAY, by BYDAY, or the day of the first occurrence. */
    private List<LocalDate> monthDates(YearMonth month, LocalDate first) {
        if (!byMonthDay.isEmpty()) {
            return monthDays(month);
        }
        if (!byDay.isEmpty()) {
            List<LocalDate> dates = new ArrayList<>();
            for (DayRule rule : byDay) {
                List<LocalDate> same = new ArrayList<>();
                for (int d = 1; d <= month.lengthOfMonth(); d++) {
                    if (month.atDay(d).getDayOfWeek() == rule.day()) {
                        same.add(month.atDay(d));
                    }
                }
                if (rule.ordinal() == 0) {
                    dates.addAll(same);
                } else {
                    int index = rule.ordinal() > 0 ? rule.ordinal() - 1 : same.size() + rule.ordinal();
                    if (index >= 0 && index < same.size()) {
                        dates.add(same.get(index));
                    }
                }
            }
            return dates;
        }
        return first.getDayOfMonth() <= month.lengthOfMonth() ? List.of(month.atDay(first.getDayOfMonth())) : List.of();
    }

    private List<LocalDate> monthDays(YearMonth month) {
        List<LocalDate> dates = new ArrayList<>();
        for (int d : byMonthDay) {
            int day = d > 0 ? d : month.lengthOfMonth() + 1 + d;
            if (day >= 1 && day <= month.lengthOfMonth()) {
                dates.add(month.atDay(day));
            }
        }
        return dates;
    }
}
