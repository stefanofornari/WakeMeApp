package ste.wakemeapp.log;

import dev.dirs.ProjectDirectories;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.FileHandler;
import java.util.logging.LogManager;

/**
 * A {@link FileHandler} fully configured by {@code logging.properties}, whose {@code pattern} may
 * contain {@code ${name}} placeholders: a system property, or {@code app.data.dir}, the
 * application data directory of the platform (overridden by {@code -Dapp.data.dir=...}). The
 * standard {@code %g} (generation) and friends keep working.
 * <pre>
 * ste.wakemeapp.log.AppFileHandler.pattern = ${app.data.dir}/logs/wakemeapp.%g.log
 * </pre>
 * The settings are read under this class's name ({@code ste.wakemeapp.log.AppFileHandler.limit},
 * {@code .count}, {@code .append}, {@code .level}, {@code .formatter}, {@code .encoding}).
 * The log never grows indefinitely: without {@code limit} and {@code count} the defaults apply.
 * The folder is created when missing.
 */
public class AppFileHandler extends FileHandler {

    static final String DATA_DIR = "app.data.dir";
    static final String DEFAULT_PATTERN = "${" + DATA_DIR + "}/logs/wakemeapp.%g.log";
    static final int DEFAULT_LIMIT = 1_000_000;
    static final int DEFAULT_COUNT = 3;

    private static final String PREFIX = AppFileHandler.class.getName() + ".";

    public AppFileHandler() throws IOException {
        super(preparePattern(), intProperty("limit", DEFAULT_LIMIT), intProperty("count", DEFAULT_COUNT),
            booleanProperty("append", true));
        // level, filter, formatter and encoding come from the configuration; defaults when it does not say
        if (property("formatter") == null) {
            setFormatter(new LineFormatter());
        }
        if (property("encoding") == null) {
            setEncoding("UTF-8");
        }
    }

    /** Replaces the {@code ${name}} placeholders with system properties (or the data directory). */
    static String expand(String pattern) {
        StringBuilder expanded = new StringBuilder();
        int from = 0;
        for (int start = pattern.indexOf("${"); start >= 0; start = pattern.indexOf("${", from)) {
            int end = pattern.indexOf('}', start);
            if (end < 0) {
                throw new IllegalArgumentException("Unclosed placeholder in the log file pattern " + pattern);
            }
            String name = pattern.substring(start + 2, end);
            expanded.append(pattern, from, start).append(asPatternText(valueOf(name)));
            from = end + 1;
        }
        return expanded.append(pattern.substring(from)).toString();
    }

    private static String valueOf(String name) {
        String value = System.getProperty(name);
        if (value == null && DATA_DIR.equals(name)) {
            value = ProjectDirectories.from("", "", "WakeMeApp").dataLocalDir;
        }
        if (value == null) {
            throw new IllegalArgumentException("Unknown placeholder ${" + name + "} in the log file pattern");
        }
        return value;
    }

    /** FileHandler patterns use '/' on every platform and '%' as the escape character. */
    private static String asPatternText(String value) {
        return value.replace('\\', '/').replace("%", "%%");
    }

    private static String preparePattern() throws IOException {
        String property = property("pattern");
        String pattern = expand(property == null ? DEFAULT_PATTERN : property.trim());
        int slash = pattern.lastIndexOf('/');
        if (slash > 0) {
            Path folder = Path.of(pattern.substring(0, slash).replace("%%", "%"));
            if (folder.toString().indexOf('%') < 0) { // %t, %h... are resolved by FileHandler itself
                Files.createDirectories(folder);
            }
        }
        return pattern;
    }

    private static String property(String name) {
        return LogManager.getLogManager().getProperty(PREFIX + name);
    }

    private static int intProperty(String name, int defaultValue) {
        String value = property(name);
        try {
            return value == null ? defaultValue : Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static boolean booleanProperty(String name, boolean defaultValue) {
        String value = property(name);
        return value == null ? defaultValue : Boolean.parseBoolean(value.trim());
    }
}
