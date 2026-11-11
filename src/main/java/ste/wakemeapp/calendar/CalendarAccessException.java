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
