package ste.wakemeapp.ui;

import javafx.scene.Node;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.stage.Window;

/**
 * Lets an undecorated window be moved by dragging it with the primary mouse button anywhere that is not a
 * control that uses the drag itself (a text field, a slider, a list...): those consume the events, so only the
 * background and the labels move the window.
 */
public final class WindowDrag {

    private double offsetX;
    private double offsetY;

    private WindowDrag() {
    }

    public static void enable(Node root, Window window) {
        WindowDrag drag = new WindowDrag();
        root.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                drag.offsetX = e.getScreenX() - window.getX();
                drag.offsetY = e.getScreenY() - window.getY();
            }
        });
        root.addEventHandler(MouseEvent.MOUSE_DRAGGED, e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                window.setX(e.getScreenX() - drag.offsetX);
                window.setY(e.getScreenY() - drag.offsetY);
            }
        });
    }
}
