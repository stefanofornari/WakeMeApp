package ste.wakemeapp.settings;

import java.io.IOException;
import java.util.function.Consumer;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

/**
 * The controller of settings.fxml: one calendar field whose kind is recognised as the user types,
 * checked and saved on Save. A saved link is shown masked (host only); typing replaces it.
 */
public final class SettingsController {

    private static final String INVALID = "settings-invalid";

    @FXML private TextField calendarField;
    @FXML private Label kindLabel;
    @FXML private Label hintLabel;
    @FXML private Label errorLabel;
    @FXML private Button saveButton;
    @FXML private Button cancelButton;

    private StoredCalendar stored;
    private Consumer<String> onSaved = value -> { };
    private Runnable onClose = () -> { };
    private String storedValue;       // what is saved, null if nothing
    private String shownAtLoad = "";  // how the saved value was shown

    @FXML
    private void initialize() {
        calendarField.textProperty().addListener((o, was, now) -> {
            showKind(now);
            clearError();
        });
    }

    /**
     * @param stored  where the calendar is kept
     * @param onSaved called with the new calendar, or null when it was removed, after it is stored
     * @param onClose closes the window
     */
    void bind(StoredCalendar stored, Consumer<String> onSaved, Runnable onClose) {
        this.stored = stored;
        this.onSaved = onSaved;
        this.onClose = onClose;
    }

    /** Shows what is saved (a link masked) and clears any message. */
    void load() {
        storedValue = stored.value().orElse(null);
        shownAtLoad = CalendarInput.shown(storedValue);
        calendarField.setText(shownAtLoad);
        clearError();
        showKind(shownAtLoad);
    }

    @FXML
    void handleSave() {
        String text = calendarField.getText() == null ? "" : calendarField.getText();
        if (text.equals(shownAtLoad)) {
            onClose.run();   // untouched, also when it is the masked form of a saved link
            return;
        }
        try {
            if (text.isBlank()) {
                stored.save("");
                finish(null);
                return;
            }
            CalendarInput.validate(text);
            stored.save(text);
            finish(text.strip());
        } catch (IllegalArgumentException x) {
            showError(x.getMessage());
        } catch (IOException x) {
            showError("The calendar could not be saved: " + x.getMessage());
        }
    }

    @FXML
    void handleCancel() {
        onClose.run();
    }

    private void finish(String saved) {
        onSaved.accept(saved);
        onClose.run();
    }

    private void showKind(String text) {
        CalendarKind kind = CalendarInput.kindOf(text).orElse(null);
        kindLabel.setText(kind == null ? "" : kind.label());
        boolean graph = kind == CalendarKind.GRAPH_ADDRESS;
        hintLabel.setVisible(graph);
        hintLabel.setManaged(graph);
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
        if (!calendarField.getStyleClass().contains(INVALID)) {
            calendarField.getStyleClass().add(INVALID);
        }
    }

    private void clearError() {
        errorLabel.setText("");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
        calendarField.getStyleClass().remove(INVALID);
    }

    TextField getCalendarField() {
        return calendarField;
    }

    Label getKindLabel() {
        return kindLabel;
    }

    Label getHintLabel() {
        return hintLabel;
    }

    Label getErrorLabel() {
        return errorLabel;
    }

    Button getSaveButton() {
        return saveButton;
    }

    Button getCancelButton() {
        return cancelButton;
    }
}
