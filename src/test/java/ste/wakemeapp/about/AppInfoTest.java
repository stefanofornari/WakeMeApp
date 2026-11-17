package ste.wakemeapp.about;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.BDDAssertions.then;

class AppInfoTest {

    @Test
    void should_read_the_version_of_the_build() {
        then(AppInfo.version("/ste/wakemeapp/about/test-version.properties")).isEqualTo("1.2.3");
    }

    @Test
    void should_say_unknown_when_the_build_information_is_missing() {
        then(AppInfo.version("/ste/wakemeapp/about/nothing.properties")).isEqualTo("unknown");
    }

    @Test
    void should_say_unknown_when_the_placeholder_was_not_replaced() {
        then(AppInfo.version("/ste/wakemeapp/about/test-unfiltered.properties")).isEqualTo("unknown");
    }

    @Test
    void should_say_unknown_when_the_version_is_empty() {
        then(AppInfo.version("/ste/wakemeapp/about/test-empty.properties")).isEqualTo("unknown");
    }

    @Test
    void should_know_the_project_and_the_licence() {
        then(AppInfo.PROJECT_URL).isEqualTo("https://github.com/stefanofornari/WakeMeApp");
        then(AppInfo.LICENCE).isEqualTo("Apache License 2.0");
    }
}
