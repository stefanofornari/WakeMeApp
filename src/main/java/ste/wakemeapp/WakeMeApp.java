package ste.wakemeapp;

import javafx.application.Application;
import ste.wakemeapp.cli.CLILauncher;

public class WakeMeApp {

    /** Entry point of the jar: options are parsed by {@link CLILauncher} (see {@code --help}). */
    public static void main(String[] args) {
        Application.launch(CLILauncher.class, args);
    }
}
