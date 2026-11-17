package ste.wakemeapp.auth;

import com.microsoft.aad.msal4j.HttpRequest;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;
import static ste.wakemeapp.auth.StubMsalHttpClient.answer;

/**
 * The device code sign-in with MSAL4J talking to a stub instead of Microsoft (see
 * auth/US-000001/notes.md).
 */
class DeviceCodeSignInTest {

    private static final long WAIT_SECONDS = 10;

    /** One per test: MSAL's throttling of failed requests is static and would make tests disturb each other. */
    private final String clientId = java.util.UUID.randomUUID().toString();
    private final List<String[]> shown = new ArrayList<>();
    private final List<LogRecord> logged = new ArrayList<>();
    private final Logger log = Logger.getLogger(DeviceCodeSignIn.class.getName());
    private final Handler capture = new Handler() {
        @Override public void publish(LogRecord record) { synchronized (logged) { logged.add(record); } }
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

    private static StubMsalHttpClient userSignsIn(String accessToken) {
        AtomicInteger polls = new AtomicInteger();
        return new StubMsalHttpClient(r -> {
            if (r.url().getPath().endsWith("/devicecode")) {
                return answer(200, StubMsalHttpClient.DEVICE_CODE);
            }
            // the user has not finished the first time MSAL asks
            return polls.getAndIncrement() == 0
                ? answer(400, StubMsalHttpClient.PENDING)
                : answer(200, StubMsalHttpClient.token(accessToken));
        });
    }

    private DeviceCodeSignIn signIn(StubMsalHttpClient http, String tenant) {
        return new DeviceCodeSignIn(clientId, tenant,
            (uri, code, message) -> shown.add(new String[] {uri, code, message}), http, false);
    }

    private static String decoded(HttpRequest request) {
        return URLDecoder.decode(request.body(), StandardCharsets.UTF_8);
    }

    @Test
    void the_device_code_request_carries_the_client_id_and_the_scopes_and_the_presenter_is_asked_to_show_the_code()
    throws Exception {
        StubMsalHttpClient http = userSignsIn("AT-1");

        signIn(http, "organizations").signIn().get(WAIT_SECONDS, TimeUnit.SECONDS);

        HttpRequest request = http.requestsTo("/devicecode").get(0);
        then(decoded(request)).contains("client_id=" + clientId).contains("Calendars.Read").contains("User.Read");
        then(shown).hasSize(1);
        then(shown.get(0)[0]).isEqualTo("https://microsoft.com/devicelogin");
        then(shown.get(0)[1]).isEqualTo("ABCD-EFGH");
        then(shown.get(0)[2]).contains("ABCD-EFGH");
    }

    @Test
    void the_access_token_is_available_after_the_sign_in_and_the_polling_goes_on_until_the_user_is_done()
    throws Exception {
        StubMsalHttpClient http = userSignsIn("AT-1");
        DeviceCodeSignIn signIn = signIn(http, "organizations");

        signIn.signIn().get(WAIT_SECONDS, TimeUnit.SECONDS);

        then(signIn.accessTokenSupplier().get()).isEqualTo("AT-1");
        then(http.requestsTo("/token")).hasSize(2);  // pending, then the tokens
    }

    @Test
    void a_second_token_is_given_when_the_sign_in_gives_another() throws Exception {
        DeviceCodeSignIn signIn = signIn(userSignsIn("AT-2"), "organizations");

        signIn.signIn().get(WAIT_SECONDS, TimeUnit.SECONDS);

        then(signIn.accessTokenSupplier().get()).isEqualTo("AT-2");
    }

    @Test
    void there_is_no_token_before_the_sign_in() {
        DeviceCodeSignIn signIn = signIn(userSignsIn("AT-1"), "organizations");

        thenThrownBy(() -> signIn.accessTokenSupplier().get())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("not signed in");
    }

    @Test
    void the_tenant_selects_the_endpoints() throws Exception {
        StubMsalHttpClient http = userSignsIn("AT-1");

        signIn(http, "organizations").signIn().get(WAIT_SECONDS, TimeUnit.SECONDS);

        then(http.requestsTo("/devicecode").get(0).url().getPath())
            .isEqualTo("/organizations/oauth2/v2.0/devicecode");
        then(http.requestsTo("/token").get(0).url().getPath())
            .isEqualTo("/organizations/oauth2/v2.0/token");
    }

    @Test
    void a_tenant_id_selects_the_endpoints_of_that_tenant() throws Exception {
        StubMsalHttpClient http = userSignsIn("AT-1");
        String tenant = "99999999-8888-7777-6666-555555555555";

        signIn(http, tenant).signIn().get(WAIT_SECONDS, TimeUnit.SECONDS);

        then(http.requestsTo("/devicecode").get(0).url().getPath())
            .isEqualTo("/" + tenant + "/oauth2/v2.0/devicecode");
    }

    @Test
    void the_message_is_logged_even_if_the_presenter_fails() throws Exception {
        StubMsalHttpClient http = userSignsIn("AT-1");
        DeviceCodeSignIn signIn = new DeviceCodeSignIn(clientId, "organizations",
            (uri, code, message) -> { throw new IllegalStateException("no window"); }, http, false);

        signIn.signIn().get(WAIT_SECONDS, TimeUnit.SECONDS);

        then(signIn.accessTokenSupplier().get()).isEqualTo("AT-1");
        synchronized (logged) {
            then(logged).anySatisfy(r -> {
                then(r.getLevel()).isEqualTo(Level.INFO);
                then(r.getMessage()).contains("ABCD-EFGH");
            });
            then(logged).anySatisfy(r -> {
                then(r.getLevel()).isEqualTo(Level.WARNING);
                then(r.getMessage()).contains("no window");
            });
        }
    }

    @Test
    void the_sign_in_does_not_block_the_caller_while_the_user_is_at_the_browser() throws Exception {
        CountDownLatch userDone = new CountDownLatch(1);
        StubMsalHttpClient http = new StubMsalHttpClient(r -> {
            if (r.url().getPath().endsWith("/devicecode")) {
                return answer(200, StubMsalHttpClient.DEVICE_CODE);
            }
            try {
                userDone.await(WAIT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException x) {
                Thread.currentThread().interrupt();
            }
            return answer(200, StubMsalHttpClient.token("AT-1"));
        });

        var future = signIn(http, "organizations").signIn();   // returns at once

        then(future).isNotDone();
        userDone.countDown();
        future.get(WAIT_SECONDS, TimeUnit.SECONDS);
        then(future).isDone();
    }

    @Test
    void a_blank_client_id_or_tenant_is_refused() {
        thenThrownBy(() -> new DeviceCodeSignIn(" ", "organizations", null))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("client id");
        thenThrownBy(() -> new DeviceCodeSignIn(clientId, "", null))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("tenant");
    }

    @Test
    void the_user_is_signed_in_only_after_the_tokens_are_received() throws Exception {
        DeviceCodeSignIn signIn = signIn(userSignsIn("AT-1"), "organizations");
        then(signIn.isSignedIn()).isFalse();

        signIn.signIn().get(WAIT_SECONDS, TimeUnit.SECONDS);

        then(signIn.isSignedIn()).isTrue();
    }

    // --- failures (auth/US-000040) ---

    private static String error(String code, String description) {
        return "{\"error\":\"" + code + "\",\"error_description\":\"" + description + "\"}";
    }

    /** Runs the sign-in and returns why it failed. */
    private static SignInException failureOf(DeviceCodeSignIn signIn) throws InterruptedException {
        try {
            signIn.signIn().get(WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (ExecutionException x) {
            then(x.getCause()).isInstanceOf(SignInException.class);
            return (SignInException) x.getCause();
        } catch (java.util.concurrent.TimeoutException x) {
            throw new AssertionError("the sign-in did not end", x);
        }
        throw new AssertionError("the sign-in was expected to fail");
    }

    private List<LogRecord> logged(Level level) {
        synchronized (logged) {
            return logged.stream().filter(r -> r.getLevel() == level).toList();
        }
    }

    @Test
    void a_declined_sign_in_ends_with_a_severe_message_and_can_be_started_again() throws Exception {
        AtomicReference<String> tokenAnswer = new AtomicReference<>(
            error("authorization_declined", "AADSTS70016: The user declined the request"));
        StubMsalHttpClient http = new StubMsalHttpClient(r -> r.url().getPath().endsWith("/devicecode")
            ? answer(200, StubMsalHttpClient.DEVICE_CODE)
            : tokenAnswer.get().contains("access_token") ? answer(200, tokenAnswer.get()) : answer(400, tokenAnswer.get()));
        DeviceCodeSignIn signIn = signIn(http, "organizations");

        SignInException failure = failureOf(signIn);

        then(failure.getMessage()).contains("declined").contains("AADSTS70016");
        then(failure.isTemporary()).isFalse();
        then(logged(Level.SEVERE)).hasSize(1);
        then(logged(Level.SEVERE).get(0).getMessage()).contains("declined");
        then(signIn.isSignedIn()).isFalse();

        tokenAnswer.set(StubMsalHttpClient.token("AT-9"));
        signIn.signIn().get(WAIT_SECONDS, TimeUnit.SECONDS);
        then(signIn.accessTokenSupplier().get()).isEqualTo("AT-9");
        then(http.requestsTo("/devicecode")).hasSize(2);   // a new code for the new attempt
    }

    @Test
    void an_expired_code_asks_to_start_again_and_the_next_attempt_gets_a_new_code() throws Exception {
        StubMsalHttpClient http = new StubMsalHttpClient(r -> r.url().getPath().endsWith("/devicecode")
            ? answer(200, StubMsalHttpClient.DEVICE_CODE)
            : answer(400, error("expired_token", "AADSTS70020: The code has expired")));
        DeviceCodeSignIn signIn = signIn(http, "organizations");

        SignInException failure = failureOf(signIn);

        then(failure.getMessage()).contains("expired").contains("start the sign-in again");
        then(failure.isTemporary()).isFalse();
        then(logged(Level.WARNING)).hasSize(1);
        failureOf(signIn);
        then(http.requestsTo("/devicecode")).hasSize(2);
    }

    @Test
    void a_device_code_that_cannot_be_requested_gives_microsofts_reason_and_does_not_poll() throws Exception {
        StubMsalHttpClient http = new StubMsalHttpClient(r -> answer(400,
            error("unauthorized_client", "AADSTS700016: Application with identifier 'x' was not found")));

        SignInException failure = failureOf(signIn(http, "organizations"));

        then(failure.getMessage()).contains("AADSTS700016");
        then(failure.isTemporary()).isFalse();
        then(logged(Level.SEVERE)).hasSize(1);
        then(logged(Level.SEVERE).get(0).getMessage()).contains("AADSTS700016");
        then(http.requestsTo("/token")).isEmpty();
        then(shown).isEmpty();
    }

    @Test
    void a_network_failure_is_a_warning_and_the_sign_in_can_be_started_again() throws Exception {
        AtomicReference<Boolean> networkUp = new AtomicReference<>(false);
        StubMsalHttpClient http = new StubMsalHttpClient(r -> {
            if (!networkUp.get()) {
                throw new IOException("connection refused");
            }
            return r.url().getPath().endsWith("/devicecode")
                ? answer(200, StubMsalHttpClient.DEVICE_CODE)
                : answer(200, StubMsalHttpClient.token("AT-3"));
        });
        DeviceCodeSignIn signIn = signIn(http, "organizations");

        SignInException failure = failureOf(signIn);

        then(failure.isTemporary()).isTrue();
        then(failure.getMessage()).contains("cannot be reached").contains("connection refused");
        then(logged(Level.WARNING)).hasSize(1);
        then(logged(Level.SEVERE)).isEmpty();

        networkUp.set(true);
        signIn.signIn().get(WAIT_SECONDS, TimeUnit.SECONDS);
        then(signIn.accessTokenSupplier().get()).isEqualTo("AT-3");
    }

    @Test
    void the_same_failure_is_logged_once() throws Exception {
        StubMsalHttpClient http = new StubMsalHttpClient(r -> answer(400,
            error("unauthorized_client", "AADSTS700016: not found")));

        failureOf(signIn(http, "organizations"));

        then(logged(Level.SEVERE).size() + logged(Level.WARNING).size()).isEqualTo(1);
    }

    // --- silent renewal (auth/US-000041) ---

    /**
     * The user signs in at once and gets {@code firstToken} valid for {@code expiresIn} seconds; a refresh
     * is answered by {@code refresh}.
     */
    private static StubMsalHttpClient signedInWith(String firstToken, long expiresIn, StubMsalHttpClient.Answer refresh) {
        return new StubMsalHttpClient(r -> {
            if (r.url().getPath().endsWith("/devicecode")) {
                return answer(200, StubMsalHttpClient.DEVICE_CODE);
            }
            if (r.body().contains("grant_type=refresh_token")) {
                return refresh.to(r);
            }
            return answer(200, StubMsalHttpClient.token(firstToken, expiresIn));
        });
    }

    private static int refreshes(StubMsalHttpClient http) {
        return (int) http.requestsTo("/token").stream().filter(r -> r.body().contains("grant_type=refresh_token")).count();
    }

    @Test
    void a_token_far_from_its_expiry_is_used_as_it_is_without_a_request() throws Exception {
        StubMsalHttpClient http = signedInWith("AT-1", 3599, r -> answer(500, "must not be asked"));
        DeviceCodeSignIn signIn = signIn(http, "organizations");
        signIn.signIn().get(WAIT_SECONDS, TimeUnit.SECONDS);
        int before = http.requests.size();

        then(signIn.accessTokenSupplier().get()).isEqualTo("AT-1");
        then(signIn.accessTokenSupplier().get()).isEqualTo("AT-1");

        then(http.requests.size()).isEqualTo(before);
    }

    @Test
    void a_token_close_to_its_expiry_is_renewed_first_and_the_new_one_is_kept() throws Exception {
        StubMsalHttpClient http = signedInWith("AT-1", 240, r -> answer(200, StubMsalHttpClient.token("AT-2", 3599)));
        DeviceCodeSignIn signIn = signIn(http, "organizations");
        signIn.signIn().get(WAIT_SECONDS, TimeUnit.SECONDS);

        then(signIn.accessTokenSupplier().get()).isEqualTo("AT-2");
        then(refreshes(http)).isEqualTo(1);

        then(signIn.accessTokenSupplier().get()).isEqualTo("AT-2");   // from the cache now
        then(refreshes(http)).isEqualTo(1);
    }

    @Test
    void callers_asking_at_the_same_time_cause_one_refresh() throws Exception {
        StubMsalHttpClient http = signedInWith("AT-1", 240, r -> {
            Thread.sleep(300);   // long enough for the second caller to arrive
            return answer(200, StubMsalHttpClient.token("AT-2", 3599));
        });
        DeviceCodeSignIn signIn = signIn(http, "organizations");
        signIn.signIn().get(WAIT_SECONDS, TimeUnit.SECONDS);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<String> first = pool.submit(() -> signIn.accessTokenSupplier().get());
            Future<String> second = pool.submit(() -> signIn.accessTokenSupplier().get());

            then(first.get(WAIT_SECONDS, TimeUnit.SECONDS)).isEqualTo("AT-2");
            then(second.get(WAIT_SECONDS, TimeUnit.SECONDS)).isEqualTo("AT-2");
        } finally {
            pool.shutdownNow();
        }
        then(refreshes(http)).isEqualTo(1);
    }

    @Test
    void a_temporary_failure_keeps_the_current_token_while_it_is_valid_and_msal_backs_off_before_asking_again() throws Exception {
        StubMsalHttpClient http = signedInWith("AT-1", 240, r -> answer(503,
            error("temporarily_unavailable", "AADSTS50000: The service is busy")));
        DeviceCodeSignIn signIn = signIn(http, "organizations");
        signIn.signIn().get(WAIT_SECONDS, TimeUnit.SECONDS);

        then(signIn.accessTokenSupplier().get()).isEqualTo("AT-1");

        then(logged(Level.WARNING)).hasSize(1);
        then(logged(Level.WARNING).get(0).getMessage()).contains("current one");
        int first = refreshes(http);
        then(first >= 1).isTrue();

        // MSAL does not ask again for a few seconds after a server error; the token still serves
        then(signIn.accessTokenSupplier().get()).isEqualTo("AT-1");
        then(refreshes(http)).isEqualTo(first);
        then(logged(Level.WARNING)).hasSize(2);
        then(logged(Level.WARNING).get(1).getMessage()).contains("current one").contains("wait");
    }

    @Test
    void a_token_that_expired_cannot_be_used_when_the_renewal_fails() throws Exception {
        StubMsalHttpClient http = signedInWith("AT-1", -60, r -> { throw new IOException("connection refused"); });
        DeviceCodeSignIn signIn = signIn(http, "organizations");
        signIn.signIn().get(WAIT_SECONDS, TimeUnit.SECONDS);

        thenThrownBy(() -> signIn.accessTokenSupplier().get())
            .isInstanceOf(SignInException.class)
            .hasMessageContaining("cannot be reached")
            .satisfies(x -> then(((SignInException) x).isTemporary()).isTrue());
    }

    // --- a rejected sign-in (auth/US-000042) ---

    private static void waitUntil(java.util.function.BooleanSupplier condition) throws InterruptedException {
        long end = System.currentTimeMillis() + WAIT_SECONDS * 1000;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < end) {
            Thread.sleep(20);
        }
        then(condition.getAsBoolean()).isTrue();
    }

    @Test
    void a_rejected_refresh_token_clears_the_sign_in_and_starts_a_new_one_that_replaces_it() throws Exception {
        AtomicInteger grants = new AtomicInteger();
        CountDownLatch secondGrantMayAnswer = new CountDownLatch(1);
        StubMsalHttpClient http = new StubMsalHttpClient(r -> {
            if (r.url().getPath().endsWith("/devicecode")) {
                return answer(200, StubMsalHttpClient.DEVICE_CODE);
            }
            if (r.body().contains("grant_type=refresh_token")) {
                return answer(400, error("invalid_grant", "AADSTS70008: The refresh token has expired"));
            }
            if (grants.incrementAndGet() == 1) {
                return answer(200, StubMsalHttpClient.token("AT-1", 240));   // within the renewal buffer
            }
            secondGrantMayAnswer.await(WAIT_SECONDS, TimeUnit.SECONDS);       // the user is at the browser
            return answer(200, StubMsalHttpClient.token("AT-9", 3599));
        });
        DeviceCodeSignIn signIn = signIn(http, "organizations");
        signIn.signIn().get(WAIT_SECONDS, TimeUnit.SECONDS);

        thenThrownBy(() -> signIn.accessTokenSupplier().get())
            .isInstanceOf(SignInException.class)
            .hasMessageContaining("expired")
            .satisfies(x -> then(((SignInException) x).isTemporary()).isTrue());

        // the old sign-in is gone and a new one is waiting for the user
        then(signIn.isSignedIn()).isFalse();
        waitUntil(() -> shown.size() == 2);
        then(http.requestsTo("/devicecode")).hasSize(2);
        then(logged(Level.WARNING)).anySatisfy(r -> then(r.getMessage()).contains("sign in again"));

        // the user completes the new sign-in
        secondGrantMayAnswer.countDown();
        waitUntil(signIn::isSignedIn);
        then(signIn.accessTokenSupplier().get()).isEqualTo("AT-9");
        then(refreshes(http)).isEqualTo(1);   // the new token needs no renewal
    }

    // --- remembering the sign-in (auth/US-000002) ---

    private DeviceCodeSignIn signInWith(StubMsalHttpClient http, TokenStore store) {
        return new DeviceCodeSignIn(clientId, "organizations",
            (uri, code, message) -> shown.add(new String[] {uri, code, message}), http, false, store);
    }

    @Test
    void the_sign_in_is_saved_in_the_store() throws Exception {
        MemoryTokenStore store = new MemoryTokenStore();

        signInWith(userSignsIn("AT-1"), store).signIn().get(WAIT_SECONDS, TimeUnit.SECONDS);

        then(store.load()).isPresent();
        then(store.text.get()).contains("AT-1").contains("RT-1");
    }

    @Test
    void a_saved_sign_in_is_used_after_a_restart_without_asking_the_user() throws Exception {
        MemoryTokenStore store = new MemoryTokenStore();
        StubMsalHttpClient http = userSignsIn("AT-1");
        signInWith(http, store).signIn().get(WAIT_SECONDS, TimeUnit.SECONDS);
        shown.clear();
        int deviceCodeRequests = http.requestsTo("/devicecode").size();

        DeviceCodeSignIn restarted = signInWith(http, store);   // a new run of the application
        then(restarted.isSignedIn()).isFalse();
        boolean resumed = restarted.resume().get(WAIT_SECONDS, TimeUnit.SECONDS);

        then(resumed).isTrue();
        then(restarted.isSignedIn()).isTrue();
        then(restarted.accessTokenSupplier().get()).isEqualTo("AT-1");
        then(shown).isEmpty();
        then(http.requestsTo("/devicecode")).hasSize(deviceCodeRequests);
    }

    @Test
    void a_renewed_token_replaces_the_saved_one() throws Exception {
        MemoryTokenStore store = new MemoryTokenStore();
        StubMsalHttpClient http = signedInWith("AT-1", 240, r -> answer(200, StubMsalHttpClient.token("AT-2", 3599)));
        DeviceCodeSignIn signIn = signInWith(http, store);
        signIn.signIn().get(WAIT_SECONDS, TimeUnit.SECONDS);
        then(store.text.get()).contains("AT-1");

        then(signIn.accessTokenSupplier().get()).isEqualTo("AT-2");

        then(store.text.get()).contains("AT-2");
    }

    @Test
    void without_a_saved_sign_in_there_is_nothing_to_resume() throws Exception {
        DeviceCodeSignIn signIn = signInWith(userSignsIn("AT-1"), new MemoryTokenStore());

        then(signIn.resume().get(WAIT_SECONDS, TimeUnit.SECONDS)).isFalse();
        then(signIn.isSignedIn()).isFalse();
    }

    @Test
    void a_saved_text_that_is_not_a_cache_is_ignored_and_the_user_can_sign_in() throws Exception {
        MemoryTokenStore store = new MemoryTokenStore();
        store.save("this is not a cache");
        StubMsalHttpClient http = userSignsIn("AT-1");
        DeviceCodeSignIn signIn = signInWith(http, store);

        then(signIn.resume().get(WAIT_SECONDS, TimeUnit.SECONDS)).isFalse();

        signIn.signIn().get(WAIT_SECONDS, TimeUnit.SECONDS);
        then(signIn.accessTokenSupplier().get()).isEqualTo("AT-1");
        then(store.text.get()).contains("AT-1");   // the new sign-in replaced the unusable text
    }

    @Test
    void a_saved_sign_in_that_microsoft_rejects_is_forgotten_and_not_resumed() throws Exception {
        MemoryTokenStore store = new MemoryTokenStore();
        StubMsalHttpClient first = signedInWith("AT-1", 240, r -> answer(200, StubMsalHttpClient.token("AT-2", 3599)));
        signInWith(first, store).signIn().get(WAIT_SECONDS, TimeUnit.SECONDS);
        StubMsalHttpClient rejecting = new StubMsalHttpClient(r -> answer(400,
            error("invalid_grant", "AADSTS70008: The refresh token has expired")));
        DeviceCodeSignIn restarted = signInWith(rejecting, store);

        then(restarted.resume().get(WAIT_SECONDS, TimeUnit.SECONDS)).isFalse();

        then(restarted.isSignedIn()).isFalse();
        then(store.text.get()).doesNotContain("RT-1");   // the rejected sign-in is no longer saved
    }

    @Test
    void the_presenter_is_told_when_the_sign_in_completes_or_fails() throws Exception {
        List<String> events = new ArrayList<>();
        VerificationPresenter presenter = new VerificationPresenter() {
            @Override public void show(String uri, String code, String message) { events.add("show"); }
            @Override public void completed() { events.add("completed"); }
        };
        new DeviceCodeSignIn(clientId, "organizations", presenter, userSignsIn("AT-1"), false)
            .signIn().get(WAIT_SECONDS, TimeUnit.SECONDS);
        then(events).containsExactly("show", "completed");

        events.clear();
        StubMsalHttpClient declined = new StubMsalHttpClient(r -> r.url().getPath().endsWith("/devicecode")
            ? answer(200, StubMsalHttpClient.DEVICE_CODE)
            : answer(400, error("authorization_declined", "AADSTS70016: declined")));
        failureOf(new DeviceCodeSignIn(clientId, "organizations", presenter, declined, false));
        then(events).containsExactly("show", "completed");   // the window must not stay open either
    }
}
