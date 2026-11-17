package ste.wakemeapp.notify;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;

/** Opening the link of an online meeting, or copying it when the browser cannot be opened. */
class JoinMeetingTest {

    private static final String TEAMS = "https://teams.microsoft.com/l/meetup-join/19%3ameeting_abc%40thread.v2/0";

    private final List<URI> opened = new ArrayList<>();
    private final List<String> copied = new ArrayList<>();

    @Test
    void a_teams_link_is_opened_in_the_browser() {
        JoinMeeting join = new JoinMeeting(opened::add, copied::add);

        then(join.join(TEAMS)).isEqualTo(JoinMeeting.Result.OPENED);

        then(opened).containsExactly(URI.create(TEAMS));
        then(copied).isEmpty();
    }

    @Test
    void a_teams_app_link_is_opened_too() {
        JoinMeeting join = new JoinMeeting(opened::add, copied::add);

        then(join.join("msteams://teams.microsoft.com/l/meetup-join/abc")).isEqualTo(JoinMeeting.Result.OPENED);

        then(opened).containsExactly(URI.create("msteams://teams.microsoft.com/l/meetup-join/abc"));
    }

    @Test
    void the_link_is_copied_when_the_browser_cannot_be_opened() {
        JoinMeeting join = new JoinMeeting(uri -> { throw new UnsupportedOperationException("no browser"); }, copied::add);

        then(join.join(TEAMS)).isEqualTo(JoinMeeting.Result.COPIED);

        then(copied).containsExactly(TEAMS);
    }

    @Test
    void only_web_and_teams_links_can_be_joined() {
        then(JoinMeeting.canJoin(TEAMS)).isTrue();
        then(JoinMeeting.canJoin("http://example.com/meeting")).isTrue();
        then(JoinMeeting.canJoin("msteams://teams.microsoft.com/l/meetup-join/abc")).isTrue();
        then(JoinMeeting.canJoin("  " + TEAMS + "  ")).isTrue();

        then(JoinMeeting.canJoin(null)).isFalse();
        then(JoinMeeting.canJoin("")).isFalse();
        then(JoinMeeting.canJoin("   ")).isFalse();
        then(JoinMeeting.canJoin("file:///etc/passwd")).isFalse();
        then(JoinMeeting.canJoin("javascript:alert(1)")).isFalse();
        then(JoinMeeting.canJoin("ftp://example.com/x")).isFalse();
        then(JoinMeeting.canJoin("https://")).isFalse();
        then(JoinMeeting.canJoin("not a link")).isFalse();
    }

    @Test
    void a_link_that_cannot_be_joined_is_never_given_to_the_browser() {
        JoinMeeting join = new JoinMeeting(opened::add, copied::add);

        thenThrownBy(() -> join.join("file:///etc/passwd"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not a meeting link");

        then(opened).isEmpty();
        then(copied).isEmpty();
    }
}
