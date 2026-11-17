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

package ste.wakemeapp.notify;

import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

/**
 * A small window that stays on top for a few seconds after a meeting was dismissed and offers to
 * take it back. It closes by itself when the time is over or after the user used it.
 */
final class DismissToast {

    static final String MESSAGE = "Dismissed. Undo?";

    private final Stage stage = new Stage(StageStyle.TRANSPARENT);
    private final Button undoButton = new Button("Undo");
    private final PauseTransition timer;

    DismissToast(java.time.Duration time, Runnable onUndo) {
        Label message = new Label(MESSAGE);
        message.setStyle("-fx-text-fill: white; -fx-font-size: 14px;");
        undoButton.setStyle("-fx-font-weight: bold;");
        undoButton.setOnAction(e -> {
            close();
            onUndo.run();
        });
        HBox box = new HBox(12, message, undoButton);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(10, 16, 10, 16));
        box.setStyle("-fx-background-color: rgba(30, 30, 30, 0.92); -fx-background-radius: 10;");
        Scene scene = new Scene(box);
        scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        stage.setScene(scene);
        stage.setAlwaysOnTop(true);
        timer = new PauseTransition(Duration.millis(time.toMillis()));
        timer.setOnFinished(e -> close());
    }

    void show() {
        stage.show();
        var bounds = Screen.getPrimary().getVisualBounds();
        stage.setX(bounds.getMinX() + (bounds.getWidth() - stage.getWidth()) / 2);
        stage.setY(bounds.getMaxY() - stage.getHeight() - 48);
        timer.playFromStart();
    }

    void close() {
        timer.stop();
        stage.hide();
    }

    boolean isShowing() {
        return stage.isShowing();
    }

    String getMessage() {
        return MESSAGE;
    }

    Button getUndoButton() {
        return undoButton;
    }
}
