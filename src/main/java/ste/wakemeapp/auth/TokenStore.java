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
import java.util.Optional;

/**
 * Where the sign-in (MSAL's serialized token cache) is kept between runs. Another store, a system
 * password manager for instance, can replace the file store without touching the sign-in.
 */
public interface TokenStore {

    /** @return the saved text, or empty if there is none or it cannot be read */
    Optional<String> load() throws IOException;

    void save(String text) throws IOException;

    /** Removes what is saved; nothing happens if there is nothing. */
    void clear() throws IOException;
}
