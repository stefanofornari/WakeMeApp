package ste.wakemeapp.auth;

import com.microsoft.aad.msal4j.DeviceCode;
import com.microsoft.aad.msal4j.DeviceCodeFlowParameters;
import com.microsoft.aad.msal4j.IHttpClient;
import com.microsoft.aad.msal4j.PublicClientApplication;
import java.net.MalformedURLException;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Signs the user in to Microsoft with the OAuth 2.0 device code flow, done by MSAL4J: the user is
 * given a code and an address, enters the code in a browser, and MSAL gets the tokens. The calendar
 * code only sees the access token supplier.
 */
public class DeviceCodeSignIn {

    private static final Logger LOG = Logger.getLogger(DeviceCodeSignIn.class.getName());

    /** MSAL adds openid, profile and offline_access (which yields the refresh token) itself. */
    public static final Set<String> SCOPES = Set.of("Calendars.Read", "User.Read");

    private final PublicClientApplication app;
    private final VerificationPresenter presenter;
    private final AtomicReference<String> accessToken = new AtomicReference<>();

    /**
     * @param clientId the application (client) id registered with Microsoft
     * @param tenant "organizations" or a tenant id
     * @param presenter shows the user where to go and which code to enter; may be null, the message
     *        is always logged
     */
    public DeviceCodeSignIn(String clientId, String tenant, VerificationPresenter presenter) {
        this(clientId, tenant, presenter, null, true);
    }

    /** For tests: {@code http} replaces the HTTP client of MSAL, {@code discovery} its instance discovery. */
    DeviceCodeSignIn(String clientId, String tenant, VerificationPresenter presenter,
                     IHttpClient http, boolean discovery) {
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException("The client id is required to sign in");
        }
        if (tenant == null || tenant.isBlank()) {
            throw new IllegalArgumentException("The tenant is required to sign in");
        }
        this.presenter = presenter;
        try {
            PublicClientApplication.Builder builder = PublicClientApplication.builder(clientId)
                .authority("https://login.microsoftonline.com/" + tenant + "/")
                .instanceDiscovery(discovery);
            if (http != null) {
                builder.httpClient(http);
            }
            this.app = builder.build();
        } catch (MalformedURLException x) {
            throw new IllegalArgumentException("The tenant is not valid: " + tenant, x);
        }
    }

    /**
     * Starts the sign-in and returns at once; the future completes when the user has entered the
     * code and the tokens have been received, or fails if the sign-in does not complete.
     */
    public CompletableFuture<Void> signIn() {
        Consumer<DeviceCode> consumer = this::present;
        return app.acquireToken(DeviceCodeFlowParameters.builder(SCOPES, consumer).build())
            .thenAccept(result -> accessToken.set(result.accessToken()));
    }

    /** True once the sign-in has completed and an access token is available. */
    public boolean isSignedIn() {
        return accessToken.get() != null;
    }

    /** The access token of the signed in user; fails if the sign-in has not completed. */
    public Supplier<String> accessTokenSupplier() {
        return () -> {
            String token = accessToken.get();
            if (token == null) {
                throw new IllegalStateException("The user is not signed in yet");
            }
            return token;
        };
    }

    private void present(DeviceCode code) {
        LOG.info(code.message());
        if (presenter == null) {
            return;
        }
        try {
            presenter.show(code.verificationUri(), code.userCode(), code.message());
        } catch (RuntimeException x) {
            // the sign-in must go on: the message above is in the log
            LOG.log(Level.WARNING, "Cannot show the sign-in page, use the message above: " + x.getMessage());
            LOG.log(Level.FINE, "Details of the failure to show the sign-in page", x);
        }
    }
}
