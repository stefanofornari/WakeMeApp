# Notes: auth/US-000001

## Findings before implementing (MSAL4J 1.26.1, from its sources)
1. **Stubbing works.** `IHttpClient` has one method, `IHttpResponse send(HttpRequest)`; `HttpResponse` has public `statusCode(int)` and `body(String)`. The application builder takes it with `httpClient(...)`. No request leaves the machine.
2. **No discovery call needed.** `PublicClientApplication.builder(...).instanceDiscovery(false)` switches off the central instance-discovery request; the device code endpoint is derived from the authority (`.../oauth2/v2.0/devicecode`), the token endpoint likewise. In production we leave discovery on (the default).
3. **Polling is fast in tests.** MSAL sleeps `interval` seconds of the device code answer after `authorization_pending`; a stub answering `interval: 0` makes the tests instant.
4. **The stub sees**: the method, the URL, the headers and the form body of each request, so the tests check the client id, the scopes and the tenant from there.
5. **Dependencies of msal4j 1.26.1**: `slf4j-api` 1.7.36, `azure-json` 1.4.0 and `oauth2-oidc-sdk` 11.23 (Nimbus), with their own transitive ones; the sizes are recorded in the packaging stories when measured.
6. **SLF4J.** MSAL logs through SLF4J. A JUL binding is to be added so that its messages follow `logging.properties` and nothing goes to stderr.

## Constraints of the working environment
Maven Central is not reachable from the environment where the stories were written, so MSAL4J could not be compiled or run there; its API was read from the sources. The build and the tests of this story are run with Maven by the developer.
