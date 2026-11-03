package ste.wakemeapp.notify;

import java.time.LocalDateTime;

public record MeetingNotification(
    String title,
    LocalDateTime startTime,
    String joinUrl,
    String organizer,
    String description
) {
    public MeetingNotification(String title, LocalDateTime startTime, String joinUrl) {
        this(title, startTime, joinUrl, null, null);
    }
}