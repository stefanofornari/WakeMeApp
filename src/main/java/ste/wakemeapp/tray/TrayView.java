package ste.wakemeapp.tray;

/** What {@link Tray} needs from the native tray / menu bar icon. */
interface TrayView {

    /** Puts the icon in the tray. */
    void show();

    /** Whether the icon carries the exclamation mark. */
    void setAlert(boolean alert);

    void setTooltip(String tooltip);
}
