package ste.wakemeapp.ui;

import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import static org.assertj.core.api.BDDAssertions.then;

@ExtendWith(ApplicationExtension.class)
class WindowDragTest {

    private final FxRobot robot = new FxRobot();
    private Stage stage;
    private VBox root;

    @Start
    private void start(Stage stage) {
        this.stage = stage;
        root = new VBox();
        stage.setScene(new Scene(root, 200, 100));
        WindowDrag.enable(root, stage);
        stage.setX(50);
        stage.setY(60);
        stage.show();
    }

    private void fire(javafx.event.EventType<MouseEvent> type, MouseButton button, double screenX, double screenY) {
        robot.interact(() -> Event.fireEvent(root, new MouseEvent(type, 5, 5, screenX, screenY, button, 1,
            false, false, false, false, true, false, false, true, false, true, null)));
    }

    @Test
    void should_move_the_window_by_the_distance_the_mouse_is_dragged() {
        fire(MouseEvent.MOUSE_PRESSED, MouseButton.PRIMARY, 100, 100);
        fire(MouseEvent.MOUSE_DRAGGED, MouseButton.PRIMARY, 130, 90);

        then(stage.getX()).isEqualTo(80.0);
        then(stage.getY()).isEqualTo(50.0);
    }

    @Test
    void should_keep_moving_with_the_mouse_and_not_jump_on_the_next_drag() {
        fire(MouseEvent.MOUSE_PRESSED, MouseButton.PRIMARY, 100, 100);
        fire(MouseEvent.MOUSE_DRAGGED, MouseButton.PRIMARY, 110, 110);
        fire(MouseEvent.MOUSE_DRAGGED, MouseButton.PRIMARY, 120, 130);
        fire(MouseEvent.MOUSE_PRESSED, MouseButton.PRIMARY, 300, 300);
        fire(MouseEvent.MOUSE_DRAGGED, MouseButton.PRIMARY, 301, 301);

        then(stage.getX()).isEqualTo(71.0);   // the second press anchors at where the window is then (70), not at the first press
        then(stage.getY()).isEqualTo(91.0);
    }

    @Test
    void should_ignore_the_other_mouse_buttons() {
        fire(MouseEvent.MOUSE_PRESSED, MouseButton.SECONDARY, 100, 100);
        fire(MouseEvent.MOUSE_DRAGGED, MouseButton.SECONDARY, 150, 150);

        then(stage.getX()).isEqualTo(50.0);
        then(stage.getY()).isEqualTo(60.0);
    }
}
