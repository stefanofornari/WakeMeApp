package ste.wakemeapp.auth;

import com.microsoft.aad.msal4j.AuthenticationErrorCode;
import com.microsoft.aad.msal4j.DeviceCode;
import com.microsoft.aad.msal4j.DeviceCodeFlowParameters;
import com.microsoft.aad.msal4j.IAuthenticationResult;
import com.microsoft.aad.msal4j.IHttpClient;
import com.microsoft.aad.msal4j.MsalClientException;
import com.microsoft.aad.msal4j.MsalServiceException;
import com.microsoft.aad.msal4j.PublicClientApplication;
import com.microsoft.aad.msal4j.SilentParameters;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
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
    private final AtomicReference<IAuthenticationResult> last = new AtomicReference<>();

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
        CompletableFuture<Void> outcome = new CompletableFuture<>();
        app.acquireToken(DeviceCodeFlowParameters.builder(SCOPES, consumer).build())
            .whenComplete((result, failure) -> {
                if (failure == null) {
                    last.set(result);
                    outcome.complete(null);
                } else {
                    Explained why = explain(failure);
                    LOG.log(why.level(), why.exception().getMessage());
                    LOG.log(Level.FINE, "Details of the sign-in failure", why.exception().getCause());
                    outcome.completeExceptionally(why.exception());
                }
            });
        return outcome;
    }

    /**
     * Turns what MSAL reports into a {@link SignInException} worded for the user, with the level to
     * log it at: a failure that may pass by itself (network, busy server) or an expired code is a
     * WARNING, the others (declined, unknown client or tenant) are SEVERE.
     */
    private Explained explain(Throwable failure) {
        Throwable cause = failure;
        while ((cause instanceof CompletionException || cause instanceof ExecutionException) && cause.getCause() != null) {
            cause = cause.getCause();
        }
        Level level = Level.SEVERE;
        boolean temporary = false;
        String message;
        if (cause instanceof MsalServiceException service) {
            String code = service.errorCode();
            Integer status = service.statusCode();
            if ("authorization_declined".equals(code)) {
                message = "The sign-in was declined. Start the sign-in again if this was a mistake (" + service.getMessage() + ")";
            } else if ("expired_token".equals(code)) {
                level = Level.WARNING;
                message = "The code expired before it was entered: start the sign-in again to get a new code (" + service.getMessage() + ")";
            } else if (status != null && (status == 408 || status == 429 || status >= 500)) {
                level = Level.WARNING;
                temporary = true;
                message = "Microsoft cannot be reached right now (status " + status + "): start the sign-in again later";
            } else {
                message = "Microsoft refused the sign-in: " + service.getMessage();
            }
        } else if (cause instanceof MsalClientException client
                   && AuthenticationErrorCode.CODE_EXPIRED.equals(client.errorCode())) {
            level = Level.WARNING;
            message = "The code expired before it was entered: start the sign-in again to get a new code";
        } else if (cause instanceof MsalClientException && causedByIO(cause)) {
            level = Level.WARNING;
            temporary = true;
            message = "Microsoft cannot be reached (" + rootMessage(cause) + "): start the sign-in again when the connection is back";
        } else {
            message = "The sign-in failed: " + cause.getMessage();
        }
        return new Explained(level, new SignInException(message, temporary, cause));
    }

    private record Explained(Level level, SignInException exception) {
    }

    private static boolean causedByIO(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof IOException || c instanceof UncheckedIOException) {
                return true;
            }
        }
        return false;
    }

    private static String rootMessage(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getMessage();
    }

    /** True once the sign-in has completed and an access token is available. */
    public boolean isSignedIn() {
        return last.get() != null;
    }

    /**
     * The access token of the signed in user. MSAL answers from its cache, or renews the token with
     * the refresh token when it expires within 5 minutes; one renewal at a time whatever the number of
     * callers. A temporary failure to renew keeps the current token while it is still valid.
     *
     * @throws IllegalStateException if the sign-in has not completed
     * @throws SignInException if the token cannot be renewed
     */
    public Supplier<String> accessTokenSupplier() {
        return this::currentAccessToken;
    }

    private synchronized String currentAccessToken() {
        IAuthenticationResult current = last.get();
        if (current == null) {
            throw new IllegalStateException("The user is not signed in yet");
        }
        try {
            IAuthenticationResult renewed = app.acquireTokenSilently(
                SilentParameters.builder(SCOPES, current.account()).build()).join();
            last.set(renewed);
            return renewed.accessToken();
        } catch (CompletionException | MalformedURLException x) {
            SignInException why = explain(x).exception();
            if (why.isTemporary() && current.expiresOnDate().after(new Date())) {
                LOG.warning("The access token could not be renewed, using the current one until it expires: "
                    + why.getMessage());
                return current.accessToken();
            }
            throw why;   // the caller (the calendar poller) reports it
        }
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
