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
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;

class IcsAddressTest {

    private static final String LINK = "https://outlook.office365.com/owa/calendar/abc123/secret456/calendar.ics";

    @Test
    void should_tell_a_feed_from_graph_and_from_a_file() {
        then(IcsAddress.isFeed(LINK)).isTrue();
        then(IcsAddress.isFeed("webcal://host/c.ics")).isTrue();
        then(IcsAddress.isFeed("WEBCAL://host/c.ics")).isTrue();
        then(IcsAddress.isFeed("http://host/c.ics")).isTrue();   // recognised, then refused by parse
        then(IcsAddress.isFeed("https://graph.microsoft.com/v1.0/me/calendarView")).isFalse();
        then(IcsAddress.isFeed("HTTPS://Graph.Microsoft.com:443/v1.0/me")).isFalse();
        then(IcsAddress.isFeed("https://graph.microsoft.com.evil.example/c.ics")).isTrue();
        then(IcsAddress.isFeed("meetings.csv")).isFalse();
        then(IcsAddress.isFeed("/home/ste/meetings.csv")).isFalse();
        then(IcsAddress.isFeed("C:\\meetings.csv")).isFalse();
        then(IcsAddress.isFeed(null)).isFalse();
    }

    @Test
    void should_accept_an_https_address_as_it_is() {
        then(IcsAddress.parse(LINK)).isEqualTo(URI.create(LINK));
    }

    @Test
    void should_read_webcal_as_https() {
        then(IcsAddress.parse("webcal://outlook.office365.com/c.ics"))
            .isEqualTo(URI.create("https://outlook.office365.com/c.ics"));
    }

    @Test
    void should_refuse_plain_http_saying_why() {
        thenThrownBy(() -> IcsAddress.parse("http://outlook.office365.com/c.ics"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("--calendar")
            .hasMessageContaining("https")
            .hasMessageNotContaining("c.ics");
    }

    @Test
    void should_refuse_an_address_that_is_not_valid_without_showing_it() {
        thenThrownBy(() -> IcsAddress.parse("https://host/a b/secret456"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("--calendar")
            .hasMessageNotContaining("secret456");
    }

    @Test
    void should_name_the_host_only() {
        then(IcsAddress.hostOf(URI.create(LINK))).isEqualTo("outlook.office365.com");
    }
}
