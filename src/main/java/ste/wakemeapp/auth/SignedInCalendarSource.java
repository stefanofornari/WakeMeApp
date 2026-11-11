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

package ste.wakemeapp.auth;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.BooleanSupplier;
import ste.wakemeapp.calendar.CalendarAccessException;
import ste.wakemeapp.calendar.CalendarEvent;
import ste.wakemeapp.calendar.CalendarSource;

/**
 * A calendar that is read only once the user has signed in. Until then every poll fails with a
 * temporary calendar error ("waiting for the sign-in"), which the poller logs as a WARNING and
 * retries at the next poll.
 */
public class SignedInCalendarSource implements CalendarSource {

    private final CalendarSource calendar;
    private final BooleanSupplier signedIn;

    public SignedInCalendarSource(CalendarSource calendar, BooleanSupplier signedIn) {
        this.calendar = calendar;
        this.signedIn = signedIn;
    }

    @Override
    public List<CalendarEvent> meetingsStartingBetween(LocalDateTime from, LocalDateTime to) throws IOException {
        if (!signedIn.getAsBoolean()) {
            throw new CalendarAccessException("Waiting for the sign-in to complete: the calendar is not read yet", true);
        }
        try {
            return calendar.meetingsStartingBetween(from, to);
        } catch (SignInException x) {
            // the access token supplier could not renew the sign-in: a calendar error of the same kind
            throw new CalendarAccessException(x.getMessage(), x, x.isTemporary());
        }
    }
}
