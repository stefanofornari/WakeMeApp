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
 * The address of an Outlook calendar on Microsoft Graph, given as the value of --calendar, e.g.
 * {@code https://graph.microsoft.com/v1.0/me/calendarView}. The access token is sent to this
 * address, so only graph.microsoft.com over https is accepted: a wrong address never receives it.
 * The query is ours, so the address has none, and {@code /calendarView} is added when missing.
 */
public final class CalendarAddress {

    static final String HOST = "graph.microsoft.com";
    private static final String EXAMPLE = "https://" + HOST + "/v1.0/me/calendarView";

    private CalendarAddress() {
    }

    /** True if the --calendar value is an address (starts with http:// or https://), not a file. */
    public static boolean isAddress(String value) {
        if (value == null) {
            return false;
        }
        String v = value.trim().toLowerCase(Locale.ROOT);
        return v.startsWith("https://") || v.startsWith("http://");
    }

    /**
     * @return the address of the calendar view to read, on graph.microsoft.com over https
     * @throws IllegalArgumentException with a message naming --calendar if the address cannot be used
     */
    public static URI parse(String value) {
        URI uri;
        try {
            uri = new URI(value.trim()).normalize();
        } catch (URISyntaxException x) {
            throw new IllegalArgumentException("--calendar is not a valid address: " + value);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || !HOST.equalsIgnoreCase(uri.getHost())
            || uri.getUserInfo() != null || (uri.getPort() != -1 && uri.getPort() != 443)) {
            throw new IllegalArgumentException("--calendar address must be on " + HOST + " over https: " + value);
        }
        if (uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IllegalArgumentException(
                "--calendar address must not have a query or a fragment, WakeMeApp adds them: " + value);
        }
        String path = uri.getRawPath() == null ? "" : uri.getRawPath();
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        if (!path.startsWith("/v1.0/")) {
            throw new IllegalArgumentException(
                "--calendar address must be a Microsoft Graph v1.0 calendar, e.g. " + EXAMPLE);
        }
        if (!path.endsWith("/calendarView")) {
            path += "/calendarView";
        }
        return URI.create("https://" + HOST + path);
    }
}
