package ste.wakemeapp.notify;

import java.time.LocalDateTime;

/**
 * The data shown by the overlay for one meeting.
 *
 * @param endTime when the meeting ends; {@code null} when the calendar did not provide it
 */
public record MeetingNotification(
    String title,
    LocalDateTime startTime,
    String joinUrl,
    String organizer,
    String description,
    LocalDateTime endTime
) {
    public MeetingNotification(String title, LocalDateTime startTime, String joinUrl,
                               String organizer, String description) {
        this(title, startTime, joinUrl, organizer, description, null);
    }

    public MeetingNotification(String title, LocalDateTime startTime, String joinUrl) {
        this(title, startTime, joinUrl, null, null, null);
    }
}
