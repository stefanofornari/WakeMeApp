package ste.wakemeapp.calendar;

/** How the user answered the invitation to a calendar event. */
public enum ResponseStatus {
    /** The user organises the event. */
    ORGANIZER,
    ACCEPTED,
    TENTATIVE,
    DECLINED,
    NOT_RESPONDED
}
