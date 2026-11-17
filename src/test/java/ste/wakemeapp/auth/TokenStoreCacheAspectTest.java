package ste.wakemeapp.auth;

import com.microsoft.aad.msal4j.IAccount;
import com.microsoft.aad.msal4j.ITokenCache;
import com.microsoft.aad.msal4j.ITokenCacheAccessContext;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.BDDAssertions.then;

/** MSAL asks the aspect to load its cache before using it and to save it after changing it. */
class TokenStoreCacheAspectTest {

    private final MemoryTokenStore store = new MemoryTokenStore();
    private final List<String> deserialized = new ArrayList<>();
    private boolean deserializeFails;
    private String serialized = "SERIALIZED";
    private final ITokenCache cache = new ITokenCache() {
        @Override public void deserialize(String data) {
            if (deserializeFails) { throw new IllegalArgumentException("not a cache"); }
            deserialized.add(data);
        }
        @Override public String serialize() { return serialized; }
    };
    private final List<LogRecord> logged = new ArrayList<>();
    private final Logger log = Logger.getLogger(TokenStoreCacheAspect.class.getName());
    private final Handler capture = new Handler() {
        @Override public void publish(LogRecord record) { logged.add(record); }
        @Override public void flush() { }
        @Override public void close() { }
    };

    @BeforeEach
    void captureLog() {
        log.addHandler(capture);
        log.setLevel(Level.ALL);
    }

    @AfterEach
    void releaseLog() {
        log.removeHandler(capture);
    }

    /** The warnings only: the details of a failure are logged at FINE too. */
    private List<LogRecord> warnings() {
        return logged.stream().filter(r -> r.getLevel() == Level.WARNING).toList();
    }

    private ITokenCacheAccessContext context(boolean changed) {
        return new ITokenCacheAccessContext() {
            @Override public ITokenCache tokenCache() { return cache; }
            @Override public String clientId() { return "client"; }
            @Override public IAccount account() { return null; }
            @Override public boolean hasCacheChanged() { return changed; }
        };
    }

    @Test
    void the_stored_cache_is_given_to_msal_before_it_is_used() {
        store.save("STORED");
        store.saves = 0;

        new TokenStoreCacheAspect(store).beforeCacheAccess(context(false));

        then(deserialized).containsExactly("STORED");
    }

    @Test
    void with_nothing_stored_msal_keeps_its_empty_cache() {
        new TokenStoreCacheAspect(store).beforeCacheAccess(context(false));

        then(deserialized).isEmpty();
        then(logged).isEmpty();
    }

    @Test
    void a_changed_cache_is_saved_after_msal_used_it() {
        new TokenStoreCacheAspect(store).afterCacheAccess(context(true));

        then(store.load()).hasValue("SERIALIZED");
    }

    @Test
    void an_unchanged_cache_is_not_saved() {
        new TokenStoreCacheAspect(store).afterCacheAccess(context(false));

        then(store.saves).isEqualTo(0);
    }

    @Test
    void a_stored_text_that_is_not_a_cache_is_ignored_with_a_warning() {
        store.save("garbage");
        deserializeFails = true;

        new TokenStoreCacheAspect(store).beforeCacheAccess(context(false));

        then(warnings()).hasSize(1);
        then(warnings().get(0).getMessage()).contains("saved sign-in").contains("ignored");
    }

    @Test
    void a_store_that_cannot_be_read_or_written_does_not_stop_msal() {
        TokenStore broken = new TokenStore() {
            @Override public Optional<String> load() throws IOException { throw new IOException("unreadable"); }
            @Override public void save(String text) throws IOException { throw new IOException("read-only"); }
            @Override public void clear() { }
        };
        TokenStoreCacheAspect aspect = new TokenStoreCacheAspect(broken);

        aspect.beforeCacheAccess(context(false));
        aspect.afterCacheAccess(context(true));

        then(deserialized).isEmpty();
        then(warnings()).hasSize(2);
        then(warnings().get(0).getMessage()).contains("unreadable");
        then(warnings().get(1).getMessage()).contains("not saved").contains("read-only");
    }
}
