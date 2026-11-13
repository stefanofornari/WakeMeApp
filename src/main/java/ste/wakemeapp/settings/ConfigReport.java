package ste.wakemeapp.settings;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * The text of --print-config: one line per setting with its effective value and where the value
 * comes from (command line, stored or default). Nothing printed is a secret; the sign-in is not in
 * the settings.
 */
public final class ConfigReport {

    private static final String NOT_SET = "(not set)";
    private static final String NOTIFICATIONS_KEY = NotificationsEnabled.KEY;

    private ConfigReport() {
    }

    /**
     * @param cliClientId the value of --client-id, or null
     * @param cliTenant the value of --tenant, or null
     * @param stored the stored settings
     */
    public static List<String> lines(String cliClientId, String cliTenant, Settings stored) {
        List<String> lines = new ArrayList<>();
        lines.add(line(ConnectionSettings.CLIENT_ID_KEY, cliClientId, stored, null, ConnectionSettings::isClientId));
        lines.add(line(ConnectionSettings.TENANT_KEY, cliTenant, stored,
            ConnectionSettings.DEFAULT_TENANT, ConnectionSettings::isTenant));
        lines.add(line(LeadTime.KEY, null, stored, Integer.toString(LeadTime.DEFAULT_MINUTES), LeadTime::isValid));
        lines.add(line(NOTIFICATIONS_KEY, null, stored, "true", v -> v.equals("true") || v.equals("false")));
        lines.add(line(SoundSetting.SOUND_KEY, null, stored, SoundSetting.DEFAULT_SOUND, SoundSetting.BUNDLED::contains));
        lines.add(line(SoundSetting.VOLUME_KEY, null, stored, Integer.toString(SoundSetting.DEFAULT_VOLUME),
            SoundSetting::isValidVolume));
        for (String key : new TreeSet<>(stored.keys())) {
            if (!key.equals(ConnectionSettings.CLIENT_ID_KEY) && !key.equals(ConnectionSettings.TENANT_KEY)
                && !key.equals(LeadTime.KEY) && !key.equals(NOTIFICATIONS_KEY)
                && !key.equals(SoundSetting.SOUND_KEY) && !key.equals(SoundSetting.VOLUME_KEY)) {
                lines.add(format(key, stored.getString(key, ""), "stored, unknown key", null));
            }
        }
        return lines;
    }

    private static String line(String key, String cli, Settings stored, String defaultValue,
                               java.util.function.Predicate<String> valid) {
        if (cli != null) {
            return format(key, cli, "command line", null);
        }
        String value = stored.getString(key, null);
        if (value != null && valid.test(value)) {
            return format(key, value, "stored", null);
        }
        String shown = defaultValue != null ? defaultValue : NOT_SET;
        return format(key, shown, "default", value != null ? "stored value '" + value + "' ignored" : null);
    }

    private static String format(String key, String value, String source, String note) {
        String line = String.format("%-22s %s  (%s)", key, value, source);
        return note == null ? line : line + " - " + note;
    }
}
