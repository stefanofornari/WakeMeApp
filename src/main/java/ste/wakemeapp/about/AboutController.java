package ste.wakemeapp.about;

import java.util.function.Consumer;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;

/** Fills the About dialog (about.fxml); it needs neither settings nor a calendar. */
public final class AboutController {

    @FXML private Label nameLabel;
    @FXML private Label versionLabel;
    @FXML private Label descriptionLabel;
    @FXML private Hyperlink linkLink;
    @FXML private Label licenceLabel;
    @FXML private Button closeButton;

    private Consumer<String> opener = url -> { };
    private Runnable onClose = () -> { };

    /**
     * @param version the version to show
     * @param opener  opens the project link (in the browser); nothing else is sent anywhere
     * @param onClose hides the dialog
     */
    void bind(String version, Consumer<String> opener, Runnable onClose) {
        this.opener = opener;
        this.onClose = onClose;
        nameLabel.setText(AppInfo.NAME);
        versionLabel.setText("Version " + version);
        descriptionLabel.setText(AppInfo.DESCRIPTION);
        linkLink.setText(AppInfo.PROJECT_URL);
        licenceLabel.setText("Licence: " + AppInfo.LICENCE);
    }

    @FXML
    void handleOpenLink() {
        opener.accept(AppInfo.PROJECT_URL);
    }

    @FXML
    void handleClose() {
        onClose.run();
    }

    Label getNameLabel() { return nameLabel; }
    Label getVersionLabel() { return versionLabel; }
    Label getDescriptionLabel() { return descriptionLabel; }
    Hyperlink getLinkLink() { return linkLink; }
    Label getLicenceLabel() { return licenceLabel; }
    Button getCloseButton() { return closeButton; }
}
