package ste.wakemeapp.auth;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/** A token store in memory, to see what is saved without a file. */
class MemoryTokenStore implements TokenStore {

    final AtomicReference<String> text = new AtomicReference<>();
    int saves;

    @Override
    public Optional<String> load() {
        return Optional.ofNullable(text.get());
    }

    @Override
    public synchronized void save(String content) {
        saves++;
        text.set(content);
    }

    @Override
    public void clear() {
        text.set(null);
    }
}
