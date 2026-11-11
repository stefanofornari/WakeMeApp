package ste.wakemeapp.auth;

import javafx.scene.Node;
import javafx.scene.web.WebView;

/**
 * The sign-in page in a JavaFX WebView. No user data folder is set, so the cookies of the Microsoft
 * page are not kept between runs: what is kept is the sign-in in the token store.
 */
class WebViewPage implements SignInPage {

    private final WebView view = new WebView();

    @Override
    public Node node() {
        return view;
    }

    @Override
    public void load(String address) {
        view.getEngine().load(address);
    }
}
