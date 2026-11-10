package ste.wakemeapp.settings;

import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * The Microsoft client id and tenant to sign in with. A command line value overrules the stored
 * one for that run and is never saved; the tenant defaults to "organizations" (see
 * uzz/stories/settings/scope.md).
 *
 * @param clientId the application (client) id, or null if none is given or stored
 * @param tenant "organizations" or a tenant id
 */
public record ConnectionSettings(String clientId, String tenant) {

    public static final String CLIENT_ID_KEY = "graph.clientId";
    public static final String TENANT_KEY = "graph.tenant";
    public static final String DEFAULT_TENANT = "organizations";

    private static final Logger LOG = Logger.getLogger(ConnectionSettings.class.getName());
    private static final Pattern UUID = Pattern.compile(
        "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    /**
     * Checks the command line values (null means not given).
     *
     * @throws IllegalArgumentException with a message naming the option that is wrong
     */
    public static void validate(String clientId, String tenant) {
        if (clientId != null && !isClientId(clientId)) {
            throw new IllegalArgumentException("--client-id must be a UUID");
        }
        if (tenant != null && !isTenant(tenant)) {
            throw new IllegalArgumentException("--tenant must be a UUID or \"" + DEFAULT_TENANT + "\"");
        }
    }

    /**
     * Command line first, then the stored value, then the default. A stored value that is not valid
     * is ignored with a WARNING.
     *
     * @param cliClientId the value of --client-id, or null
     * @param cliTenant the value of --tenant, or null
     * @throws IllegalArgumentException if a command line value is not valid
     */
    public static ConnectionSettings resolve(String cliClientId, String cliTenant, Settings stored) {
        validate(cliClientId, cliTenant);
        String clientId = cliClientId != null ? cliClientId : validStored(stored, CLIENT_ID_KEY, true);
        String tenant = cliTenant != null ? cliTenant : validStored(stored, TENANT_KEY, false);
        return new ConnectionSettings(clientId, tenant != null ? tenant : DEFAULT_TENANT);
    }

    private static String validStored(Settings stored, String key, boolean clientId) {
        String value = stored.getString(key, null);
        if (value == null) {
            return null;
        }
        if (clientId ? isClientId(value) : isTenant(value)) {
            return value;
        }
        LOG.warning("The setting " + key + " is '" + value + "', which is not valid; ignored");
        return null;
    }

    static boolean isClientId(String value) {
        return UUID.matcher(value).matches();
    }

    static boolean isTenant(String value) {
        return DEFAULT_TENANT.equals(value) || UUID.matcher(value).matches();
    }
}
