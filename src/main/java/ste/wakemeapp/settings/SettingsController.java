package ste.wakemeapp.settings;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.Slider;
import javafx.scene.control.Tooltip;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;

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
    @FXML private ComboBox<SoundOption> soundBox;
    @FXML private Button previewButton;
    @FXML private Label soundErrorLabel;
    @FXML private Label soundWarningLabel;
    @FXML private Slider volumeSlider;
    @FXML private Label volumeLabel;
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
    private SoundChoice soundAtLoad = SoundChoice.bundled(SoundSetting.DEFAULT_SOUND);
    private int volumeAtLoad = SoundSetting.DEFAULT_VOLUME;
    private SoundOption previousSound;     // what was selected before the user picked "Custom file..."
    private boolean settingSound;          // the selection is being set by the code, not by the user
    private Supplier<File> filePicker = this::chooseFile;

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

        soundBox.setConverter(new StringConverter<>() {
            @Override public String toString(SoundOption option) { return option == null ? "" : option.label(); }
            @Override public SoundOption fromString(String text) { return null; }
        });
        soundBox.getItems().setAll(soundOptions(null));
        soundBox.valueProperty().addListener((o, was, now) -> soundSelected(was, now));
        volumeSlider.valueProperty().addListener((o, was, now) -> volumeLabel.setText(Math.round(now.doubleValue()) + " %"));
    }

    /** The bundled sounds, silent, the custom file if there is one, and "Custom file...". */
    private static List<SoundOption> soundOptions(SoundOption custom) {
        java.util.ArrayList<SoundOption> options = new java.util.ArrayList<>();
        SoundSetting.BUNDLED.forEach(name -> options.add(SoundOption.of(SoundChoice.bundled(name))));
        options.add(SoundOption.NONE);
        if (custom != null) {
            options.add(custom);
        }
        options.add(SoundOption.PICK);
        return options;
    }

    /** The user selected an entry: "Custom file..." opens the picker, a file that is not audio is refused. */
    private void soundSelected(SoundOption was, SoundOption now) {
        if (settingSound || now == null) {
            return;
        }
        hide(soundErrorLabel);
        if (now != SoundOption.PICK) {
            previousSound = now;
            return;
        }
        File file = filePicker.get();
        if (file == null) {
            select(previousSound != null ? previousSound : was);   // cancelled: back to what it was
            return;
        }
        try {
            SoundFile.validate(file.getAbsolutePath());
        } catch (IllegalArgumentException x) {
            select(previousSound != null ? previousSound : was);   // the custom selection is cleared
            show(soundErrorLabel, x.getMessage());
            return;
        }
        SoundOption custom = SoundOption.of(SoundChoice.custom(file.getAbsolutePath()));
        settingSound = true;
        try {
            soundBox.getItems().setAll(soundOptions(custom));
            soundBox.setValue(custom);
        } finally {
            settingSound = false;
        }
        previousSound = custom;
    }

    private void select(SoundOption option) {
        settingSound = true;
        try {
            soundBox.setValue(option);
        } finally {
            settingSound = false;
        }
    }

    private File chooseFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose a notification sound");
        chooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Sound files (WAV, AIFF)", "*.wav", "*.aif", "*.aiff"));
        return chooser.showOpenDialog(soundBox.getScene() == null ? null : soundBox.getScene().getWindow());
    }

    /** For tests: what the "Custom file..." entry opens instead of the file chooser. */
    void setFilePicker(Supplier<File> filePicker) {
        this.filePicker = filePicker;
    }

    /** The sound as selected now. */
    SoundChoice selectedSound() {
        SoundOption option = soundBox.getValue();
        return option == null || option.choice() == null ? soundAtLoad : option.choice();
    }

    private int selectedVolume() {
        return (int) Math.round(volumeSlider.getValue());
    }

    /** An entry of the sound list: what is shown, and what it stands for (null for "Custom file...", which opens the picker). */
    record SoundOption(String label, SoundChoice choice) {
        static final SoundOption NONE = new SoundOption("None (Silent)", SoundChoice.none());
        static final SoundOption PICK = new SoundOption("Custom file\u2026", null);

        static SoundOption of(SoundChoice choice) {
            return switch (choice.kind()) {
                case BUNDLED -> new SoundOption(label(choice.value()), choice);
                case NONE -> NONE;
                case CUSTOM -> new SoundOption(new File(choice.value()).getName(), choice);
            };
        }

        private static String label(String name) {
            return Character.toUpperCase(name.charAt(0)) + name.substring(1);
        }
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
        soundAtLoad = SoundSetting.choice(settings);
        volumeAtLoad = SoundSetting.volume(settings);
        SoundOption stored = SoundOption.of(soundAtLoad);
        settingSound = true;
        try {
            soundBox.getItems().setAll(soundOptions(soundAtLoad.kind() == SoundChoice.Kind.CUSTOM ? stored : null));
            soundBox.setValue(stored);
        } finally {
            settingSound = false;
        }
        previousSound = stored;
        volumeSlider.setValue(volumeAtLoad);
        volumeLabel.setText(volumeAtLoad + " %");
        hide(soundErrorLabel);
        if (soundAtLoad.kind() == SoundChoice.Kind.CUSTOM && !SoundFile.isUsable(soundAtLoad.value())) {
            show(soundWarningLabel, "The custom sound file is missing or not usable: the default sound plays instead.");
        } else {
            hide(soundWarningLabel);
        }
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
        boolean soundChanged = !selectedSound().equals(soundAtLoad) || selectedVolume() != volumeAtLoad;
        if (!calendarChanged && !leadChanged && !switched && !soundChanged) {
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
            if (soundChanged) {
                SoundSetting.save(settings, selectedSound(), selectedVolume());
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
        if (soundChanged) {
            listener.soundSaved();
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

    /** Plays the selected sound at the volume of the slider; it does not save anything. */
    @FXML
    void handlePreview() {
        listener.previewSound(selectedSound(), selectedVolume());
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

    ComboBox<SoundOption> getSoundBox() {
        return soundBox;
    }

    Label getSoundErrorLabel() {
        return soundErrorLabel;
    }

    Label getSoundWarningLabel() {
        return soundWarningLabel;
    }

    Slider getVolumeSlider() {
        return volumeSlider;
    }

    Label getVolumeLabel() {
        return volumeLabel;
    }

    Button getPreviewButton() {
        return previewButton;
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
