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
 * Shows the user what to do to complete a device code sign-in: where to go and which code to enter.
 */
@FunctionalInterface
public interface VerificationPresenter {

    /**
     * @param verificationUri the address where the user enters the code
     * @param userCode the code to enter
     * @param message the instructions as worded by Microsoft
     */
    void show(String verificationUri, String userCode, String message);

    /** The sign-in ended, successfully or not: whatever was shown can go away. */
    default void completed() {
    }
}
