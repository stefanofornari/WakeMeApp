package ste.wakemeapp.calendar;

import java.io.IOException;

/**
 * The calendar cannot be read.
 * <p>
 * It tells whether waiting may help: a {@linkplain #isTemporary() temporary} failure (throttling,
 * a server that is down, no network) is expected to pass by itself, a permanent one (the token is
 * refused, a permission is missing, the answer cannot be understood) will not, and the user has to
 * be told.
 */
public class CalendarAccessException extends IOException {

    private final boolean temporary;

    public CalendarAccessException(String message, boolean temporary) {
        super(message);
        this.temporary = temporary;
    }

    public CalendarAccessException(String message, Throwable cause, boolean temporary) {
        super(message, cause);
        this.temporary = temporary;
    }

    /** @return true when trying again later may succeed */
    public boolean isTemporary() {
        return temporary;
    }
}
