package ste.wakemeapp.tray;

import com.dustinredmond.fxtrayicon.FXTrayIcon;
import java.awt.Image;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import javafx.scene.control.MenuItem;
import javafx.stage.Stage;
import javax.imageio.ImageIO;

/** The native tray icon, through FXTrayIcon. Menu: "Settings…" (disabled for now) and "Quit". */
final class FxTrayView implements TrayView {

    private final FXTrayIcon icon;
    private final Image normal = load("tray.png");
    private final Image alert = load("tray-alert.png");

    FxTrayView(Stage stage, Runnable onQuit) {
        icon = new FXTrayIcon.Builder(stage, normal).noDefaultAction().build();
        icon.addTitleItem(false);
        MenuItem settings = new MenuItem("Settings…");
        settings.setDisable(true);   // enabled by settings/US-000056
        icon.addMenuItem(settings);
        icon.addMenuItem("Quit", e -> onQuit.run());
    }

    static boolean supported() {
        return FXTrayIcon.isSupported();
    }

    @Override
    public void show() {
        icon.show();
    }

    @Override
    public void setAlert(boolean alert) {
        icon.getRestricted().getTrayIcon().setImage(alert ? this.alert : normal);
    }

    @Override
    public void setTooltip(String tooltip) {
        icon.setTrayIconTooltip(tooltip);
    }

    private static Image load(String name) {
        URL url = FxTrayView.class.getResource(name);
        try {
            return ImageIO.read(url);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the tray icon " + name, e);
        }
    }
}
