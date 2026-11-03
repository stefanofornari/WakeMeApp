package ste.wakemeapp;

import javafx.application.Application;
import ste.wakemeapp.cli.OverlayLauncher;

public class WakeMeApp {

    public static void main(String[] args) {
        if (args.length == 0 || java.util.Arrays.stream(args).allMatch(String::isBlank)) {
            System.err.println("Error: No arguments provided.");
            System.err.println("Usage: WakeMeApp <meeting-title> <minutes-from-now> [join-url]");
            System.exit(1);
        }
        Application.launch(OverlayLauncher.class, args);
    }
}
