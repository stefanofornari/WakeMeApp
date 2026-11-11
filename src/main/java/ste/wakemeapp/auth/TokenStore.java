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
