package ste.wakemeapp.notify;

import javafx.stage.Screen;
import javafx.stage.Stage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class OverlayManager {

    private final List<OverlayWindow> overlays = new CopyOnWriteArrayList<>();

    public void showNotification(MeetingNotification notification) {
        // Close any existing overlays
        hideAll();

        // Create overlay for each screen
        for (Screen screen : Screen.getScreens()) {
            OverlayWindow overlay = new OverlayWindow();
            overlay.setNotification(notification);
            configureForScreen(overlay, screen);
            overlay.getStage().setOnHidden(e -> overlays.remove(overlay));
            overlays.add(overlay);
            overlay.show();
        }
    }

    private void configureForScreen(OverlayWindow overlay, Screen screen) {
        Stage stage = overlay.getStage();
        stage.setX(screen.getVisualBounds().getMinX());
        stage.setY(screen.getVisualBounds().getMinY());
        stage.setWidth(screen.getVisualBounds().getWidth());
        stage.setHeight(screen.getVisualBounds().getHeight());
    }

    public void hideAll() {
        for (OverlayWindow overlay : overlays) {
            overlay.hide();
        }
        overlays.clear();
    }

    public int getOverlayCount() {
        return overlays.size();
    }

    public List<OverlayWindow> getOverlays() {
        return new ArrayList<>(overlays);
    }
}