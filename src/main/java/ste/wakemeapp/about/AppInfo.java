package ste.wakemeapp.about;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** What the About dialog says about the application: where it is, what it is called, the version of this build. */
public final class AppInfo {

    public static final String NAME = "WakeMeApp";
    public static final String DESCRIPTION = "Full-screen reminders for the meetings on your calendar, so none is missed.";
    public static final String PROJECT_URL = "https://github.com/stefanofornari/WakeMeApp";
    public static final String LICENCE = "Apache License 2.0";
    public static final String UNKNOWN = "unknown";

    static final String RESOURCE = "/ste/wakemeapp/about/app.properties";

    private AppInfo() {
    }

    /** The version of the running build (the Maven project version), or "unknown" if it cannot be read. */
    public static String version() {
        return version(RESOURCE);
    }

    static String version(String resource) {
        try (InputStream in = AppInfo.class.getResourceAsStream(resource)) {
            if (in == null) {
                return UNKNOWN;
            }
            Properties properties = new Properties();
            properties.load(in);
            String version = properties.getProperty("version", "").trim();
            // an unfiltered file (run from an IDE that skips Maven's resource filtering) still has the placeholder
            return version.isEmpty() || version.startsWith("${") ? UNKNOWN : version;
        } catch (IOException | IllegalArgumentException x) {
            return UNKNOWN;
        }
    }
}
