package ste.wakemeapp.about;

import java.util.ArrayList;
import java.util.List;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import static org.assertj.core.api.BDDAssertions.then;

@ExtendWith(ApplicationExtension.class)
class AboutDialogTest {

    private final FxRobot robot = new FxRobot();
    private final List<String> opened = new ArrayList<>();
    private AboutDialog dialog;
    private AboutController about;

    @Start
    private void start(Stage stage) {
        opened.clear();
        dialog = new AboutDialog("1.2.3", opened::add);
        about = dialog.getController();
    }

    private void open() {
        robot.interact(dialog::show);
    }

    @Test
    void should_show_the_name_the_version_the_project_link_and_the_licence() {
        open();

        then(dialog.isShowing()).isTrue();
        then(dialog.getStage().getTitle()).isEqualTo("About WakeMeApp");
        then(about.getNameLabel().getText()).isEqualTo("WakeMeApp");
        then(about.getVersionLabel().getText()).isEqualTo("Version 1.2.3");
        then(about.getLinkLink().getText()).isEqualTo("https://github.com/stefanofornari/WakeMeApp");
        then(about.getLicenceLabel().getText()).contains("Apache License 2.0");
        then(about.getDescriptionLabel().getText()).isNotBlank();
    }

    @Test
    void should_show_unknown_when_the_version_cannot_be_read() {
        robot.interact(() -> {
            AboutDialog unknown = new AboutDialog(AppInfo.UNKNOWN, opened::add);
            then(unknown.getController().getVersionLabel().getText()).isEqualTo("Version unknown");
        });
    }

    @Test
    void should_not_block_the_rest_of_the_application() {
        open();

        then(dialog.getStage().getModality()).isEqualTo(Modality.NONE);
    }

    @Test
    void should_open_only_one_dialog_and_bring_it_to_the_front() {
        open();
        Stage first = dialog.getStage();

        open();

        then(dialog.getStage()).isSameAs(first);
        then(dialog.isShowing()).isTrue();
    }

    @Test
    void should_close_without_ending_anything_and_open_again() {
        open();

        robot.interact(about::handleClose);

        then(dialog.isShowing()).isFalse();

        open();

        then(dialog.isShowing()).isTrue();
    }

    @Test
    void should_open_the_project_link_in_the_browser_and_send_nothing_else() {
        open();

        robot.interact(about::handleOpenLink);

        then(opened).containsExactly("https://github.com/stefanofornari/WakeMeApp");
    }
}
