# Notes: auth/US-000001

## Before implementing: findings to check
1. **MSAL stubbing.** Check first that MSAL can be driven with a stubbed `IHttpClient` (no request leaves the machine). MSAL may insist on an authority-discovery call on first use: the stub then answers it, or the discovery is switched off if MSAL allows it. If MSAL cannot be stubbed, go back to our own RFC 8628 implementation with the JDK `HttpClient`, and reshape auth/US-000001, 2, 40, 41 and 42.
2. **SLF4J bridge.** MSAL logs through SLF4J. Add a JUL binding that follows `logging.properties`, prints nothing to stderr, and check with a test that an MSAL message reaches the application log.
3. **Dependency footprint.** Record the transitive dependencies and jar sizes (from memory: Nimbus, json-smart, slf4j-api; unverified) as input for the packaging stories (jpackage).
4. **Versions.** The MSAL4J version is chosen and checked when implementing; Maven Central was not reachable while writing the stories.
