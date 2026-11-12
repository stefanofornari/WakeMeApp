package ste.wakemeapp.settings;

/** The kinds of calendar the user can give, in the order they are recognised. */
public enum CalendarKind {

    ICS_LINK("ICS link"),
    GRAPH_ADDRESS("Microsoft Graph address"),
    CSV_FILE("CSV file");

    private final String label;

    CalendarKind(String label) {
        this.label = label;
    }

    /** The name shown to the user. */
    public String label() {
        return label;
    }
}
