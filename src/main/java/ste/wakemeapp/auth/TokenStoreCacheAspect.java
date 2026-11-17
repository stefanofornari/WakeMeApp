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

import com.microsoft.aad.msal4j.ITokenCacheAccessAspect;
import com.microsoft.aad.msal4j.ITokenCacheAccessContext;
import java.io.IOException;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Lets MSAL keep its token cache in a {@link TokenStore}: it loads the saved text before MSAL uses
 * the cache and saves it after MSAL changed it. A store that fails never stops MSAL: the user just
 * signs in again.
 */
public class TokenStoreCacheAspect implements ITokenCacheAccessAspect {

    private static final Logger LOG = Logger.getLogger(TokenStoreCacheAspect.class.getName());

    private final TokenStore store;

    public TokenStoreCacheAspect(TokenStore store) {
        this.store = store;
    }

    @Override
    public void beforeCacheAccess(ITokenCacheAccessContext context) {
        Optional<String> saved;
        try {
            saved = store.load();
        } catch (IOException | RuntimeException x) {
            LOG.warning("The saved sign-in cannot be read and is ignored: " + x.getMessage());
            return;
        }
        if (saved.isEmpty()) {
            return;
        }
        try {
            context.tokenCache().deserialize(saved.get());
        } catch (RuntimeException x) {
            LOG.warning("The saved sign-in is not usable and is ignored: " + x.getMessage());
            LOG.log(Level.FINE, "Details of the unusable saved sign-in", x);
        }
    }

    @Override
    public void afterCacheAccess(ITokenCacheAccessContext context) {
        if (!context.hasCacheChanged()) {
            return;
        }
        try {
            store.save(context.tokenCache().serialize());
        } catch (IOException | RuntimeException x) {
            LOG.warning("The sign-in was not saved, it will not survive a restart: " + x.getMessage());
            LOG.log(Level.FINE, "Details of the failure to save the sign-in", x);
        }
    }
}
