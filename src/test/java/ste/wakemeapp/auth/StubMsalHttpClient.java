package ste.wakemeapp.auth;

import com.microsoft.aad.msal4j.HttpRequest;
import com.microsoft.aad.msal4j.HttpResponse;
import com.microsoft.aad.msal4j.IHttpClient;
import com.microsoft.aad.msal4j.IHttpResponse;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Replaces the HTTP client of MSAL4J with canned answers: no request leaves the machine. The
 * requests are kept so that tests can look at them.
 */
class StubMsalHttpClient implements IHttpClient {

    static final String DEVICE_CODE = """
        {"user_code":"ABCD-EFGH","device_code":"DC-1","verification_uri":"https://microsoft.com/devicelogin",
         "expires_in":900,"interval":0,
         "message":"To sign in, use a web browser to open the page https://microsoft.com/devicelogin and enter the code ABCD-EFGH to authenticate."}
        """;

    static final String PENDING = """
        {"error":"authorization_pending","error_description":"The user has not yet entered the code"}
        """;

    static String token(String accessToken) {
        return token(accessToken, 3599);
    }

    /** A token answer with the identity MSAL needs to cache the tokens for an account (silent renewal). */
    static String token(String accessToken, long expiresIn) {
        String idToken = b64("{\"alg\":\"none\"}") + "." + b64("""
            {"iss":"https://login.microsoftonline.com/tid-1/v2.0","sub":"sub-1","aud":"client","exp":4102444800,
             "iat":1700000000,"nbf":1700000000,"name":"Ann Smith","preferred_username":"ann@example.com",
             "oid":"oid-1","tid":"tid-1"}""") + ".sig";
        return """
            {"token_type":"Bearer","scope":"Calendars.Read User.Read","expires_in":%d,
             "ext_expires_in":3599,"access_token":"%s","refresh_token":"RT-1",
             "id_token":"%s","client_info":"%s"}
            """.formatted(expiresIn, accessToken, idToken, b64("{\"uid\":\"oid-1\",\"utid\":\"tid-1\"}"));
    }

    private static String b64(String text) {
        return java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    static HttpResponse answer(int status, String body) {
        return new HttpResponse().statusCode(status).body(body);
    }

    final List<HttpRequest> requests = new CopyOnWriteArrayList<>();
    /** How the stub answers a request: with a response, or by failing like a broken network. */
    @FunctionalInterface
    interface Answer {
        IHttpResponse to(HttpRequest request) throws Exception;
    }

    private final Answer handler;

    StubMsalHttpClient(Answer handler) {
        this.handler = handler;
    }

    @Override
    public IHttpResponse send(HttpRequest request) throws Exception {
        requests.add(request);
        return handler.to(request);
    }

    /** The requests whose path ends with the given suffix, e.g. "/devicecode" or "/token". */
    List<HttpRequest> requestsTo(String suffix) {
        return requests.stream().filter(r -> r.url().getPath().endsWith(suffix)).toList();
    }
}
