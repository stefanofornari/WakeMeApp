package ste.wakemeapp.settings;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.Tooltip;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

/**
 * The controller of settings.fxml. The calendar: one field whose kind is recognised as the user types,
 * checked and saved on Save, a saved link shown masked (host only). The lead time: one of a few choices.
 * Save checks everything first and saves nothing if anything is wrong.
 */
public final class SettingsController {

    private static final String INVALID = "settings-invalid";

    @FXML private TextField calendarField;
    @FXML private Label kindLabel;
    @FXML private Label hintLabel;
    @FXML private Label errorLabel;
    @FXML private ComboBox<Integer> leadTimeBox;
    @FXML private CheckBox notificationsCheck;
    @FXML private Button testButton;
    @FXML private Button saveButton;
    @FXML private Button cancelButton;

    private StoredCalendar stored;
    private Settings settings;
    private Duration pollInterval = Duration.ofMinutes(5);
    private SettingsListener listener;
    private Runnable onClose = () -> { };
    private String shownAtLoad = "";   // how the saved calendar was shown
    private Integer leadAtLoad = LeadTime.DEFAULT_MINUTES;   // the saved lead time, in minutes
    private boolean onAtLoad = true;   // whether the notifications are on

    @FXML
    private void initialize() {
        calendarField.textProperty().addListener((o, was, now) -> {
            showKind(now);
            clearError();
        });
        leadTimeBox.getItems().setAll(LeadTime.CHOICES);
        // the choices shorter than the poll interval stay in the list but cannot be picked (see bind)
        leadTimeBox.setCellFactory(list -> new LeadTimeCell(true));
        leadTimeBox.setButtonCell(new LeadTimeCell(false));
    }

    /** A choice of the list: "5 minutes", greyed out and not selectable if the poll interval does not allow it. */
    private final class LeadTimeCell extends ListCell<Integer> {
        private final boolean inList;

        LeadTimeCell(boolean inList) {
            this.inList = inList;
        }

        @Override
        protected void updateItem(Integer minutes, boolean empty) {
            super.updateItem(minutes, empty);
            setText(empty || minutes == null ? null : minutes + (minutes == 1 ? " minute" : " minutes"));
            // only in the list: the shown choice stays readable even if the poll interval rules it out
            setDisable(inList && !empty && minutes != null && !allowedLeadTimes().contains(minutes));
        }
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
        // a visible but not intrusive reason, when the poll interval rules some choices out
        leadTimeBox.setTooltip(LeadTime.tooltip(pollInterval).map(Tooltip::new).orElse(null));
    }

    /** The lead times the user may pick: not shorter than the poll interval. */
    List<Integer> allowedLeadTimes() {
        return LeadTime.allowed(pollInterval);
    }

    /** Shows what is saved (a link masked) and clears any message. */
    void load() {
        shownAtLoad = CalendarInput.shown(stored.value().orElse(null));
        calendarField.setText(shownAtLoad);
        leadAtLoad = (int) LeadTime.read(settings).toMinutes();
        leadTimeBox.setValue(leadAtLoad);
        onAtLoad = NotificationsEnabled.read(settings);
        notificationsCheck.setSelected(onAtLoad);
        clearError();
        showKind(shownAtLoad);
    }

    @FXML
    void handleSave() {
        String calendarText = calendarField.getText() == null ? "" : calendarField.getText();
        Integer minutes = leadTimeBox.getValue();
        boolean calendarChanged = !calendarText.equals(shownAtLoad);
        boolean leadChanged = minutes != null && !minutes.equals(leadAtLoad);
        boolean switched = notificationsCheck.isSelected() != onAtLoad;
        if (!calendarChanged && !leadChanged && !switched) {
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
        if (leadChanged && !allowedLeadTimes().contains(minutes)) {
            valid = false;   // the list does not offer it; the tooltip of the list says why
        }
        if (!valid) {
            return;
        }

        String savedCalendar = calendarText.isBlank() ? null : calendarText.strip();
        try {
            if (leadChanged) {
                LeadTime.save(settings, minutes);
            }
            if (switched) {
                NotificationsEnabled.save(settings, notificationsCheck.isSelected());
            }
            if (calendarChanged) {
                stored.save(calendarText);
            }
        } catch (IOException x) {
            showError("The calendar could not be saved: " + x.getMessage());
            return;
        }
        if (switched) {
            listener.notificationsSwitched(notificationsCheck.isSelected());
        }
        if (calendarChanged) {
            listener.calendarSaved(savedCalendar);
        } else if (leadChanged) {
            listener.leadTimeSaved(Duration.ofMinutes(minutes));
        }
        onClose.run();
    }

    /** Shows a test notification; it does not save anything and does not close the dialog. */
    @FXML
    void handleTestNotification() {
        listener.testNotificationRequested();
    }

    @FXML
    void handleCancel() {
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
        show(errorLabel, message);
        if (!calendarField.getStyleClass().contains(INVALID)) {
            calendarField.getStyleClass().add(INVALID);
        }
    }

    private void clearError() {
        hide(errorLabel);
        calendarField.getStyleClass().remove(INVALID);
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

    ComboBox<Integer> getLeadTimeBox() {
        return leadTimeBox;
    }

    CheckBox getNotificationsCheck() {
        return notificationsCheck;
    }

    Button getTestButton() {
        return testButton;
    }

    Button getSaveButton() {
        return saveButton;
    }

    Button getCancelButton() {
        return cancelButton;
    }
}
