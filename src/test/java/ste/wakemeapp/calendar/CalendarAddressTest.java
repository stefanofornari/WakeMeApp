package ste.wakemeapp.calendar;

import java.net.URI;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;

/** The --calendar value when it is the address of an Outlook calendar on Microsoft Graph. */
class CalendarAddressTest {

    private static final String DEFAULT = "https://graph.microsoft.com/v1.0/me/calendarView";

    @Test
    void an_http_address_is_an_address_and_a_path_is_not() {
        then(CalendarAddress.isAddress(DEFAULT)).isTrue();
        then(CalendarAddress.isAddress("HTTPS://graph.microsoft.com/v1.0/me")).isTrue();
        then(CalendarAddress.isAddress("http://example.com/x")).isTrue();
        then(CalendarAddress.isAddress("meetings.csv")).isFalse();
        then(CalendarAddress.isAddress("/home/ste/meetings.csv")).isFalse();
        then(CalendarAddress.isAddress("C:\\meetings.csv")).isFalse();
    }

    @Test
    void the_default_calendar_is_accepted_as_it_is() {
        then(CalendarAddress.parse(DEFAULT)).isEqualTo(URI.create(DEFAULT));
    }

    @Test
    void another_calendar_of_the_user_is_accepted() {
        String address = "https://graph.microsoft.com/v1.0/me/calendars/AAMkAGI2-abc=/calendarView";

        then(CalendarAddress.parse(address)).isEqualTo(URI.create(address));
    }

    @Test
    void calendar_view_is_added_when_it_is_missing() {
        then(CalendarAddress.parse("https://graph.microsoft.com/v1.0/me")).isEqualTo(URI.create(DEFAULT));
        then(CalendarAddress.parse("https://graph.microsoft.com/v1.0/me/")).isEqualTo(URI.create(DEFAULT));
        then(CalendarAddress.parse("https://graph.microsoft.com/v1.0/me/calendars/AAMk"))
            .isEqualTo(URI.create("https://graph.microsoft.com/v1.0/me/calendars/AAMk/calendarView"));
    }

    @Test
    void the_host_is_compared_without_regard_to_case() {
        then(CalendarAddress.parse("https://Graph.Microsoft.com/v1.0/me/calendarView")).isEqualTo(URI.create(DEFAULT));
    }

    @Test
    void an_address_on_another_host_is_refused() {
        for (String address : new String[] {
            "https://example.com/v1.0/me/calendarView",
            "https://outlook.office.com/calendar/view/month",
            "https://graph.microsoft.com.evil.com/v1.0/me/calendarView",
            "https://graph.microsoft.com@evil.com/v1.0/me/calendarView",
            "https://evil.com/graph.microsoft.com/v1.0/me/calendarView"}) {
            thenThrownBy(() -> CalendarAddress.parse(address))
                .as(address)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("--calendar address must be on graph.microsoft.com over https");
        }
    }

    @Test
    void plain_http_a_port_or_credentials_are_refused() {
        for (String address : new String[] {
            "http://graph.microsoft.com/v1.0/me/calendarView",
            "https://graph.microsoft.com:8443/v1.0/me/calendarView",
            "https://user:pw@graph.microsoft.com/v1.0/me/calendarView"}) {
            thenThrownBy(() -> CalendarAddress.parse(address))
                .as(address)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("over https");
        }
    }

    @Test
    void only_version_1_0_is_accepted() {
        thenThrownBy(() -> CalendarAddress.parse("https://graph.microsoft.com/beta/me/calendarView"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("--calendar address must be a Microsoft Graph v1.0 calendar");
        thenThrownBy(() -> CalendarAddress.parse("https://graph.microsoft.com/"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void a_query_or_a_fragment_is_refused_because_the_query_is_ours() {
        thenThrownBy(() -> CalendarAddress.parse(DEFAULT + "?$top=5"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("--calendar address must not have a query");
        thenThrownBy(() -> CalendarAddress.parse(DEFAULT + "#x"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void dot_dot_segments_cannot_leave_the_version() {
        thenThrownBy(() -> CalendarAddress.parse("https://graph.microsoft.com/v1.0/../beta/me/calendarView"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void an_address_that_is_not_a_uri_is_refused() {
        thenThrownBy(() -> CalendarAddress.parse("https://graph.microsoft.com/v1.0/me/calendar View"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("--calendar");
    }
}
