package ste.wakemeapp.auth;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;
import static org.assertj.core.api.BDDAssertions.then;
import static ste.wakemeapp.auth.StubMsalHttpClient.answer;

/**
 * The window of the device code sign-in. The page is a fake: the real WebView is a thin wrapper that
 * only a real sign-in can exercise (see auth/US-000049/notes.md).
 */
@ExtendWith(ApplicationExtension.class)
class WebViewPresenterTest {

    private static final String ADDRESS = "https://microsoft.com/devicelogin";

    /** A page that remembers what it was asked to load. */
    private static class FakePage implements SignInPage {
        final List<String> loaded = new ArrayList<>();
        private final Pane node = new Pane();
        @Override public Node node() { return node; }
        @Override public void load(String address) { loaded.add(address); }
    }

    private final FakePage page = new FakePage();
    private final List<String> browser = new ArrayList<>();
    private final WebViewPresenter presenter = new WebViewPresenter(() -> page, browser::add);

    @Start
    private void start(Stage stage) {
        // starts the JavaFX toolkit
    }

    private static <T extends Node> T find(Stage stage, String id, Class<T> type) {
        return type.cast(stage.getScene().lookup("#" + id));
    }

    @Test
    void the_code_and_the_page_are_shown_in_a_window(FxRobot robot) {
        presenter.show(ADDRESS, "ABCD-EFGH", "Go to the page and enter the code ABCD-EFGH");
        WaitForAsyncUtils.waitForFxEvents();

        then(presenter.isShowing()).isTrue();
        Stage stage = presenter.stage();
        then(find(stage, "userCode", TextField.class).getText()).isEqualTo("ABCD-EFGH");
        then(find(stage, "userCode", TextField.class).isEditable()).isFalse();   // selectable and copyable, not editable
        then(find(stage, "message", Label.class).getText()).contains("enter the code ABCD-EFGH");
        then(page.loaded).containsExactly(ADDRESS);
    }

    @Test
    void the_window_is_opened_from_any_thread() throws Exception {
        CountDownLatch shown = new CountDownLatch(1);
        Thread other = new Thread(() -> {
            presenter.show(ADDRESS, "ABCD-EFGH", "message");   // like the thread of MSAL
            shown.countDown();
        });
        other.start();

        then(shown.await(10, TimeUnit.SECONDS)).isTrue();
        WaitForAsyncUtils.waitForFxEvents();
        then(presenter.isShowing()).isTrue();
    }

    @Test
    void the_window_closes_when_the_sign_in_completes() {
        presenter.show(ADDRESS, "ABCD-EFGH", "message");
        WaitForAsyncUtils.waitForFxEvents();

        presenter.completed();
        WaitForAsyncUtils.waitForFxEvents();

        then(presenter.isShowing()).isFalse();
    }

    @Test
    void completing_without_a_window_does_nothing() {
        presenter.completed();
        WaitForAsyncUtils.waitForFxEvents();

        then(presenter.isShowing()).isFalse();
    }

    @Test
    void the_address_can_be_opened_in_the_browser_instead(FxRobot robot) {
        presenter.show(ADDRESS, "ABCD-EFGH", "message");
        WaitForAsyncUtils.waitForFxEvents();

        robot.interact(() -> find(presenter.stage(), "openInBrowser", Button.class).fire());

        then(browser).containsExactly(ADDRESS);
    }

    @Test
    void a_new_code_replaces_the_old_one_in_the_same_window() {
        presenter.show(ADDRESS, "ABCD-EFGH", "first");
        WaitForAsyncUtils.waitForFxEvents();
        Stage first = presenter.stage();

        presenter.show(ADDRESS, "WXYZ-1234", "second");   // e.g. after the sign-in expired
        WaitForAsyncUtils.waitForFxEvents();

        then(presenter.stage()).isSameAs(first);
        then(find(first, "userCode", TextField.class).getText()).isEqualTo("WXYZ-1234");
        then(find(first, "message", Label.class).getText()).isEqualTo("second");
        then(page.loaded).containsExactly(ADDRESS, ADDRESS);
    }

    @Test
    void closing_the_window_does_not_cancel_the_sign_in() throws Exception {
        CountDownLatch userDone = new CountDownLatch(1);
        StubMsalHttpClient http = new StubMsalHttpClient(r -> {
            if (r.url().getPath().endsWith("/devicecode")) {
                return answer(200, StubMsalHttpClient.DEVICE_CODE);
            }
            userDone.await(10, TimeUnit.SECONDS);
            return answer(200, StubMsalHttpClient.token("AT-1"));
        });
        DeviceCodeSignIn signIn = new DeviceCodeSignIn(
            java.util.UUID.randomUUID().toString(), "organizations", presenter, http, false);
        var future = signIn.signIn();

        // the window opens with the code of the sign-in ...
        long end = System.currentTimeMillis() + 10_000;
        while (!presenter.isShowing() && System.currentTimeMillis() < end) {
            WaitForAsyncUtils.waitForFxEvents();
            Thread.sleep(20);
        }
        then(presenter.isShowing()).isTrue();
        then(find(presenter.stage(), "userCode", TextField.class).getText()).isEqualTo("ABCD-EFGH");

        // ... the user closes it: the sign-in is still waiting
        javafx.application.Platform.runLater(() -> presenter.stage().close());
        WaitForAsyncUtils.waitForFxEvents();
        then(presenter.isShowing()).isFalse();
        then(future).isNotDone();

        // ... and completes when the user is done; nothing is left open
        userDone.countDown();
        future.get(10, TimeUnit.SECONDS);
        WaitForAsyncUtils.waitForFxEvents();
        then(signIn.isSignedIn()).isTrue();
        then(presenter.isShowing()).isFalse();
    }
}
