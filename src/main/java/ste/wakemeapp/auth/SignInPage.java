package ste.wakemeapp.auth;

import javafx.scene.Node;

/** The web page of the sign-in, as shown in the window: the real one is a WebView. */
interface SignInPage {

    /** What is put in the window. */
    Node node();

    /** Loads the address in the page. */
    void load(String address);
}
