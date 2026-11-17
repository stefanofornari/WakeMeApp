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

package ste.wakemeapp.log;

import java.util.logging.LogRecord;
import java.util.logging.StreamHandler;

/**
 * Writes the log to {@code System.out}. The JUL {@code ConsoleHandler} always writes to
 * {@code System.err}, where runners and shells present every line as a warning, even a plain INFO
 * message. Level, filter, formatter and encoding come from the JUL configuration under this class's
 * name, like for any handler.
 */
public class StdoutHandler extends StreamHandler {

    public StdoutHandler() {
        super();
        setOutputStream(System.out);
    }

    /** Messages are shown as they come, not when a buffer is full. */
    @Override
    public synchronized void publish(LogRecord record) {
        super.publish(record);
        flush();
    }

    /** Stdout is not ours to close. */
    @Override
    public synchronized void close() {
        flush();
    }
}
