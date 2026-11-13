package ste.wakemeapp.settings;

import java.io.IOException;
import java.time.Duration;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

/**
 * The controller of settings.fxml. The calendar: one field whose kind is recognised as the user types,
 * checked and saved on Save, a saved link shown masked (host only). The lead time: a number of minutes.
 * Save checks everything first and saves nothing if anything is wrong.
 */
public final class SettingsController {

    private static final String INVALID = "settings-invalid";

    @FXML private TextField calendarField;
    @FXML private Label kindLabel;
    @FXML private Label hintLabel;
    @FXML private Label errorLabel;
    @FXML private TextField leadTimeField;
    @FXML private Label leadTimeErrorLabel;
    @FXML private Label leadTimeWarningLabel;
    @FXML private Button saveButton;
    @FXML private Button cancelButton;

    private StoredCalendar stored;
    private Settings settings;
    private Duration pollInterval = Duration.ofMinutes(5);
    private SettingsListener listener;
    private Runnable onClose = () -> { };
    private String shownAtLoad = "";   // how the saved calendar was shown
    private String leadAtLoad = "";    // the saved lead time, in minutes

    @FXML
    private void initialize() {
        calendarField.textProperty().addListener((o, was, now) -> {
            showKind(now);
            clearError();
        });
        leadTimeField.textProperty().addListener((o, was, now) -> clearLeadTimeMessages());
    }

    /**
     * @param stored       where the calendar is kept
     * @param settings     where the other settings are kept
     * @param pollInterval how often the calendar is polled, to warn about a lead time shorter than it
     * @param listener     told what was saved
     * @param onClose      closes the window
     */
    void bind(StoredCalendar stored, Settings settings, Duration pollInterval, SettingsListener listener,
              Runnable onClose) {
        this.stored = stored;
        this.settings = settings;
        this.pollInterval = pollInterval;
        this.listener = listener;
        this.onClose = onClose;
    }

    /** Shows what is saved (a link masked) and clears any message. */
    void load() {
        shownAtLoad = CalendarInput.shown(stored.value().orElse(null));
        calendarField.setText(shownAtLoad);
        leadAtLoad = Long.toString(LeadTime.read(settings).toMinutes());
        leadTimeField.setText(leadAtLoad);
        clearError();
        clearLeadTimeMessages();
        showKind(shownAtLoad);
    }

    @FXML
    void handleSave() {
        String calendarText = calendarField.getText() == null ? "" : calendarField.getText();
        String leadText = leadTimeField.getText() == null ? "" : leadTimeField.getText().strip();
        boolean calendarChanged = !calendarText.equals(shownAtLoad);
        boolean leadChanged = !leadText.equals(leadAtLoad);
        if (!calendarChanged && !leadChanged) {
            onClose.run();   // untouched, also when the calendar is the masked form of a saved link
            return;
        }

        boolean valid = true;
        if (calendarChanged && !calendarText.isBlank()) {
            try {
                CalendarInput.validate(calendarText);
            } catch (IllegalArgumentException x) {
                showError(x.getMessage());
                valid = false;
            }
        }
        int minutes = 0;
        if (leadChanged) {
            try {
                minutes = LeadTime.parse(leadText);
            } catch (IllegalArgumentException x) {
                valid = false;
                int typed = numberOf(leadText);
                if (typed != Integer.MIN_VALUE) {
                    // out of range: the value goes back to the nearest limit
                    leadTimeField.setText(Integer.toString(typed < LeadTime.MIN_MINUTES
                        ? LeadTime.MIN_MINUTES : LeadTime.MAX_MINUTES));
                }
                showLeadTimeError(x.getMessage());
            }
        }
        if (!valid) {
            return;
        }

        String savedCalendar = calendarText.isBlank() ? null : calendarText.strip();
        try {
            if (leadChanged) {
                LeadTime.save(settings, minutes);
            }
            if (calendarChanged) {
                stored.save(calendarText);
            }
        } catch (IOException x) {
            showError("The calendar could not be saved: " + x.getMessage());
            return;
        }
        if (calendarChanged) {
            listener.calendarSaved(savedCalendar);
        } else {
            listener.leadTimeSaved(Duration.ofMinutes(minutes));
        }
        String warning = leadChanged ? LeadTime.pollWarning(minutes, pollInterval).orElse(null) : null;
        if (warning != null) {
            load();   // what is saved now is what is shown; the dialog stays open to say so
            showLeadTimeWarning(warning);
        } else {
            onClose.run();
        }
    }

    @FXML
    void handleCancel() {
        onClose.run();
    }

    private static int numberOf(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException x) {
            return Integer.MIN_VALUE;
        }
    }

    private void showKind(String text) {
        CalendarKind kind = CalendarInput.kindOf(text).orElse(null);
        kindLabel.setText(kind == null ? "" : kind.label());
        boolean graph = kind == CalendarKind.GRAPH_ADDRESS;
        hintLabel.setVisible(graph);
        hintLabel.setManaged(graph);
    }

    private void showError(String message) {
        show(errorLabel, message);
        if (!calendarField.getStyleClass().contains(INVALID)) {
            calendarField.getStyleClass().add(INVALID);
        }
    }

    private void clearError() {
        hide(errorLabel);
        calendarField.getStyleClass().remove(INVALID);
    }

    private void showLeadTimeError(String message) {
        show(leadTimeErrorLabel, message);
        if (!leadTimeField.getStyleClass().contains(INVALID)) {
            leadTimeField.getStyleClass().add(INVALID);
        }
    }

    private void showLeadTimeWarning(String message) {
        show(leadTimeWarningLabel, message);
    }

    private void clearLeadTimeMessages() {
        hide(leadTimeErrorLabel);
        hide(leadTimeWarningLabel);
        leadTimeField.getStyleClass().remove(INVALID);
    }

    private static void show(Label label, String message) {
        label.setText(message);
        label.setVisible(true);
        label.setManaged(true);
    }

    private static void hide(Label label) {
        label.setText("");
        label.setVisible(false);
        label.setManaged(false);
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

    TextField getLeadTimeField() {
        return leadTimeField;
    }

    Label getLeadTimeErrorLabel() {
        return leadTimeErrorLabel;
    }

    Label getLeadTimeWarningLabel() {
        return leadTimeWarningLabel;
    }

    Button getSaveButton() {
        return saveButton;
    }

    Button getCancelButton() {
        return cancelButton;
    }
}
