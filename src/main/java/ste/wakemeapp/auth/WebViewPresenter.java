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

package ste.wakemeapp.auth;

import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Shows the device code sign-in in a window: the message and the code (selectable, with a button to
 * copy it) above the Microsoft page in a WebView, and a button to open the address in the system
 * browser instead. The window closes when the sign-in ends; closing it by hand does not cancel the
 * sign-in. A new code (the sign-in expired and was started again) reuses the window.
 */
public class WebViewPresenter implements VerificationPresenter {

    private final Supplier<SignInPage> pages;
    private final Consumer<String> systemBrowser;

    private volatile Stage stage;
    private TextField userCode;
    private Label message;
    private SignInPage page;
    private String address;

    /** @param systemBrowser opens an address in the browser of the system (the fallback button) */
    public WebViewPresenter(Consumer<String> systemBrowser) {
        this(WebViewPage::new, systemBrowser);
    }

    WebViewPresenter(Supplier<SignInPage> pages, Consumer<String> systemBrowser) {
        this.pages = pages;
        this.systemBrowser = systemBrowser;
    }

    @Override
    public void show(String verificationUri, String code, String text) {
        onFxThread(() -> display(verificationUri, code, text));
    }

    @Override
    public void completed() {
        onFxThread(() -> {
            if (stage != null) {
                stage.hide();
            }
        });
    }

    boolean isShowing() {
        Stage current = stage;
        return current != null && current.isShowing();
    }

    Stage stage() {
        return stage;
    }

    private void display(String verificationUri, String code, String text) {
        if (stage == null) {
            build();
        }
        address = verificationUri;
        userCode.setText(code);
        message.setText(text);
        page.load(verificationUri);
        stage.show();
        stage.toFront();
    }

    private void build() {
        message = new Label();
        message.setId("message");
        message.setWrapText(true);

        userCode = new TextField();
        userCode.setId("userCode");
        userCode.setEditable(false);
        userCode.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");
        HBox.setHgrow(userCode, Priority.ALWAYS);

        Button copy = new Button("Copy code");
        copy.setId("copyCode");
        copy.setOnAction(e -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(userCode.getText());
            Clipboard.getSystemClipboard().setContent(content);
        });

        Button browser = new Button("Open in the browser");
        browser.setId("openInBrowser");
        browser.setOnAction(e -> systemBrowser.accept(address));

        page = pages.get();
        VBox.setVgrow(page.node(), Priority.ALWAYS);

        VBox root = new VBox(8, message, new HBox(8, userCode, copy), browser, page.node());
        root.setPadding(new Insets(12));

        stage = new Stage();
        stage.setTitle("Sign in to Microsoft");
        stage.setScene(new Scene(root, 520, 720));
    }

    private static void onFxThread(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }
}
