package ste.wakemeapp.notify;

import java.awt.Desktop;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Joins an online meeting: opens its link in the browser (or the Teams app for an msteams: link),
 * and copies the link when the browser cannot be opened. Only http, https and msteams links are ever
 * handed to the system, whatever the calendar contains.
 */
public final class JoinMeeting {

    /** What happened when the user asked to join. */
    public enum Result {
        /** The link was handed to the browser or to Teams. */
        OPENED,
        /** The browser could not be opened: the link is on the clipboard. */
        COPIED
    }

    private static final Logger LOG = Logger.getLogger(JoinMeeting.class.getName());
    private static final Set<String> SCHEMES = Set.of("http", "https", "msteams");

    private final Consumer<URI> browser;
    private final Consumer<String> clipboard;

    /** @param browser opens the link, failing if it cannot; @param clipboard copies the text */
    public JoinMeeting(Consumer<URI> browser, Consumer<String> clipboard) {
        this.browser = browser;
        this.clipboard = clipboard;
    }

    /** True if the text is a link that can be joined: http, https or msteams, with something to open. */
    public static boolean canJoin(String link) {
        return parse(link) != null;
    }

    /**
     * @throws IllegalArgumentException if the text is not a link that can be joined
     */
    public Result join(String link) {
        URI uri = parse(link);
        if (uri == null) {
            throw new IllegalArgumentException("not a meeting link: " + link);
        }
        try {
            browser.accept(uri);
            LOG.info(() -> "Joining " + uri.getScheme() + "://" + uri.getHost());
            return Result.OPENED;
        } catch (RuntimeException x) {
            LOG.warning("The browser could not be opened (" + x.getMessage() + "): the meeting link is copied to the clipboard");
            clipboard.accept(uri.toString());
            return Result.COPIED;
        }
    }

    private static URI parse(String link) {
        if (link == null || link.isBlank()) {
            return null;
        }
        try {
            URI uri = new URI(link.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!SCHEMES.contains(scheme) || uri.getHost() == null || uri.getHost().isBlank()) {
                return null;
            }
            return uri;
        } catch (URISyntaxException x) {
            return null;
        }
    }

    /** The browser of the system. */
    static void openInBrowser(URI uri) {
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            throw new UnsupportedOperationException("this system cannot open a browser");
        }
        try {
            Desktop.getDesktop().browse(uri);
        } catch (IOException x) {
            throw new UncheckedIOException(x);
        }
    }
}
