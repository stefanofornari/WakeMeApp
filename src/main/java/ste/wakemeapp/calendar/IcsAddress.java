/*
 * Copyright 2026 the original author or authors from the WakeMeApp project (https://github.com/stefanofornari/WakeMeApp)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ste.wakemeapp.calendar;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 * The address of an ICS feed (a published calendar), given as the value of --calendar. The address
 * is the only credential of the feed, so no message made here contains it: only the host.
 */
public final class IcsAddress {

    private IcsAddress() {
    }

    /**
     * True if the --calendar value is the address of an ICS feed: {@code webcal://}, or {@code http(s)://}
     * on any host but graph.microsoft.com (that is {@link CalendarAddress}).
     */
    public static boolean isFeed(String value) {
        if (value == null) {
            return false;
        }
        String v = value.trim().toLowerCase(Locale.ROOT);
        if (v.startsWith("webcal://")) {
            return true;
        }
        if (!v.startsWith("https://") && !v.startsWith("http://")) {
            return false;
        }
        String rest = v.substring(v.indexOf("://") + 3);
        int end = rest.length();
        for (char c : new char[] {'/', '?', '#', ':'}) {
            int i = rest.indexOf(c);
            if (i >= 0 && i < end) {
                end = i;
            }
        }
        return !CalendarAddress.HOST.equals(rest.substring(0, end));
    }

    /**
     * @return the address to read, over https ({@code webcal://} is read as {@code https://})
     * @throws IllegalArgumentException naming --calendar, without the address, if it cannot be used
     */
    public static URI parse(String value) {
        String v = value.trim();
        if (v.regionMatches(true, 0, "webcal://", 0, 9)) {
            v = "https://" + v.substring(9);
        }
        URI uri;
        try {
            uri = new URI(v);
        } catch (URISyntaxException x) {
            throw new IllegalArgumentException("--calendar is not a valid address");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException(
                "--calendar address must be https (or webcal): the link is a secret and must not travel in the clear");
        }
        if (uri.getHost() == null) {
            throw new IllegalArgumentException("--calendar is not a valid address");
        }
        return uri;
    }

    /**
     * The address of a feed as it may be shown: scheme and host, the rest replaced by an ellipsis. Nothing
     * of the path, the query or the user info is kept. {@code webcal} is shown as {@code https}.
     */
    public static String redact(String value) {
        String v = value.trim();
        int sep = v.indexOf("://");
        if (sep < 0) {
            return "\u2026";
        }
        String scheme = v.substring(0, sep).toLowerCase(Locale.ROOT);
        String authority = v.substring(sep + 3);
        for (char c : new char[] {'/', '?', '#'}) {
            int i = authority.indexOf(c);
            if (i >= 0) {
                authority = authority.substring(0, i);
            }
        }
        authority = authority.substring(authority.lastIndexOf('@') + 1);
        if (!authority.startsWith("[") && authority.indexOf(':') >= 0) {
            authority = authority.substring(0, authority.indexOf(':'));
        }
        return ("webcal".equals(scheme) ? "https" : scheme) + "://" + authority + "/\u2026";
    }

    /** The only part of the address that may be shown. */
    public static String hostOf(URI feed) {
        return feed.getHost();
    }
}
