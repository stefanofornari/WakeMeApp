package ste.wakemeapp.auth;

import com.microsoft.aad.msal4j.HttpRequest;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
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

    private static final String CLIENT_ID = "11111111-2222-3333-4444-555555555555";
    private static final long WAIT_SECONDS = 10;

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
        return new DeviceCodeSignIn(CLIENT_ID, tenant,
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
        then(decoded(request)).contains("client_id=" + CLIENT_ID).contains("Calendars.Read").contains("User.Read");
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
        DeviceCodeSignIn signIn = new DeviceCodeSignIn(CLIENT_ID, "organizations",
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
        thenThrownBy(() -> new DeviceCodeSignIn(CLIENT_ID, "", null))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("tenant");
    }

    @Test
    void the_user_is_signed_in_only_after_the_tokens_are_received() throws Exception {
        DeviceCodeSignIn signIn = signIn(userSignsIn("AT-1"), "organizations");
        then(signIn.isSignedIn()).isFalse();

        signIn.signIn().get(WAIT_SECONDS, TimeUnit.SECONDS);

        then(signIn.isSignedIn()).isTrue();
    }
}
