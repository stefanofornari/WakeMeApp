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

import java.time.LocalDateTime;
import ste.wakemeapp.notify.MeetingNotification;

/**
 * A meeting as reported by a {@link CalendarSource}.
 *
 * @param id stable identifier of the event in the calendar; survives changes of time or title
 * @param endTime {@code null} when the calendar does not provide it
 * @param allDay whether the event spans whole days instead of a time range
 */
public record CalendarEvent(
    String id,
    String title,
    LocalDateTime start,
    LocalDateTime endTime,
    String joinUrl,
    String organizer,
    String description,
    ResponseStatus responseStatus,
    boolean allDay
) {

    /**
     * Whether the user wants to be notified: accepted, tentative and organised meetings, but not
     * declined or unanswered ones, nor all-day events.
     */
    public boolean isEligible() {
        return !allDay && switch (responseStatus) {
            case ACCEPTED, TENTATIVE, ORGANIZER -> true;
            case DECLINED, NOT_RESPONDED -> false;
        };
    }

    /** The data the overlay shows for this meeting. */
    public MeetingNotification toNotification() {
        return new MeetingNotification(title, start, joinUrl, organizer, description, endTime, 0, 3, start, id);
    }
}
