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

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The only thing of the logging that is decided in code. Everything else (handlers, formatter, the
 * log file and its cap) is in {@code logging.properties}, which JUL reads itself when the JVM is
 * started with {@code -Djava.util.logging.config.file=.../logging.properties}.
 */
public final class LogSetup {

    /**
     * The parent of all the application's loggers. A strong reference is kept: the LogManager only
     * holds loggers weakly, and a level set on a logger that is garbage collected is lost.
     */
    private static final Logger APPLICATION = Logger.getLogger("ste.wakemeapp");

    private LogSetup() {
    }

    /** @param debug whether the application's DEBUG messages are logged too (default: INFO and above) */
    public static void configure(boolean debug) {
        APPLICATION.setLevel(debug ? Level.FINE : Level.INFO);
    }
}
