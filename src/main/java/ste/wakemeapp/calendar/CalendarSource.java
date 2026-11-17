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

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

/** Where meetings come from: a remote calendar, or a test double. */
@FunctionalInterface
public interface CalendarSource {

    /**
     * @return the events starting at or after {@code from} and before {@code to}, in local time
     * @throws IOException when the calendar cannot be read; the caller decides when to retry
     */
    List<CalendarEvent> meetingsStartingBetween(LocalDateTime from, LocalDateTime to) throws IOException;
}
