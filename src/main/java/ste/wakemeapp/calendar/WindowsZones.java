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

import java.time.ZoneId;
import java.util.Map;

/**
 * The Windows names of time zones that Outlook writes in {@code TZID}, for the zones people use most.
 * It is not the whole table (calendar/US-000052 completes it): an unknown name is reported, not guessed.
 */
final class WindowsZones {

    private static final Map<String, String> IANA = Map.ofEntries(
        Map.entry("UTC", "UTC"),
        Map.entry("GMT Standard Time", "Europe/London"),
        Map.entry("W. Europe Standard Time", "Europe/Berlin"),
        Map.entry("Romance Standard Time", "Europe/Paris"),
        Map.entry("Central Europe Standard Time", "Europe/Budapest"),
        Map.entry("Central European Standard Time", "Europe/Warsaw"),
        Map.entry("E. Europe Standard Time", "Europe/Chisinau"),
        Map.entry("FLE Standard Time", "Europe/Kiev"),
        Map.entry("GTB Standard Time", "Europe/Bucharest"),
        Map.entry("Russian Standard Time", "Europe/Moscow"),
        Map.entry("Turkey Standard Time", "Europe/Istanbul"),
        Map.entry("Eastern Standard Time", "America/New_York"),
        Map.entry("Central Standard Time", "America/Chicago"),
        Map.entry("Mountain Standard Time", "America/Denver"),
        Map.entry("Pacific Standard Time", "America/Los_Angeles"),
        Map.entry("Alaskan Standard Time", "America/Anchorage"),
        Map.entry("Canada Central Standard Time", "America/Regina"),
        Map.entry("SA Pacific Standard Time", "America/Bogota"),
        Map.entry("E. South America Standard Time", "America/Sao_Paulo"),
        Map.entry("India Standard Time", "Asia/Kolkata"),
        Map.entry("China Standard Time", "Asia/Shanghai"),
        Map.entry("Tokyo Standard Time", "Asia/Tokyo"),
        Map.entry("Singapore Standard Time", "Asia/Singapore"),
        Map.entry("Arabian Standard Time", "Asia/Dubai"),
        Map.entry("AUS Eastern Standard Time", "Australia/Sydney"),
        Map.entry("New Zealand Standard Time", "Pacific/Auckland"),
        Map.entry("South Africa Standard Time", "Africa/Johannesburg"));

    private WindowsZones() {
    }

    /** The zone of a TZID: an IANA name, or a Windows one from the table; null if it is neither. */
    static ZoneId zoneOf(String tzid) {
        try {
            return ZoneId.of(tzid);
        } catch (java.time.DateTimeException x) {
            String iana = IANA.get(tzid);
            return (iana == null) ? null : ZoneId.of(iana);
        }
    }
}
