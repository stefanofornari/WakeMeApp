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

/**
 * Why a sign-in did not complete, in words for the user. A temporary failure (the network, a busy
 * server) may pass by itself; the others need the user or the configuration to change. Either way
 * the sign-in can be started again, which asks for a new device code.
 */
public class SignInException extends RuntimeException {

    private final boolean temporary;

    public SignInException(String message, boolean temporary, Throwable cause) {
        super(message, cause);
        this.temporary = temporary;
    }

    /** True if trying again later may work without changing anything. */
    public boolean isTemporary() {
        return temporary;
    }
}
