package ste.wakemeapp.notify;

import java.time.LocalDateTime;

/**
 * The data shown by the overlay for one meeting.
 *
 * @param endTime when the meeting ends; {@code null} when the calendar did not provide it
 * @param snoozeCount how many times this meeting has been snoozed
 * @param maxSnoozes maximum snoozes allowed per meeting (default 3)
 * @param originalStartTime the original meeting start time (preserved across snoozes)
 * @param eventId the id of the meeting in the calendar; {@code null} for a meeting not coming from a calendar
 */
public record MeetingNotification(
    String title,
    LocalDateTime startTime,
    String joinUrl,
    String organizer,
    String description,
    LocalDateTime endTime,
    int snoozeCount,
    int maxSnoozes,
    LocalDateTime originalStartTime,
    String eventId
) {
    // Full constructor with all fields (canonical)
    public MeetingNotification {
        // Compact constructor - ensure defaults
    }

    // Backward compatible constructors
    public MeetingNotification(String title, LocalDateTime startTime, String joinUrl,
                               String organizer, String description, LocalDateTime endTime) {
        this(title, startTime, joinUrl, organizer, description, endTime, 0, 3, startTime, null);
    }

    public MeetingNotification(String title, LocalDateTime startTime, String joinUrl,
                               String organizer, String description) {
        this(title, startTime, joinUrl, organizer, description, null, 0, 3, startTime, null);
    }

    public MeetingNotification(String title, LocalDateTime startTime, String joinUrl) {
        this(title, startTime, joinUrl, null, null, null, 0, 3, startTime, null);
    }

    /**
     * Creates a new notification for a snooze, preserving the original meeting time
     * and incrementing the snooze count.
     */
    public MeetingNotification withSnooze(int snoozeDurationMinutes, LocalDateTime newTriggerTime) {
        return new MeetingNotification(
            title,
            newTriggerTime,  // The snooze end time becomes the new "start" for display
            joinUrl,
            organizer,
            description,
            endTime,
            snoozeCount + 1,
            maxSnoozes,
            originalStartTime,
            eventId
        );
    }

    public boolean canSnooze() {
        return snoozeCount < maxSnoozes;
    }

    public String snoozeStatusText() {
        return String.format("Snoozed %d/%d times", snoozeCount, maxSnoozes);
    }
}
