package ste.wakemeapp.tray;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import java.util.logging.Logger;

/**
 * On macOS the application lives in the menu bar only. When started from the bundle, the JVM is a child of the
 * launcher and not the process Launch Services knows, so LSUIElement does not apply to it and the Dock shows an icon:
 * the activation policy is set to "accessory" by hand.
 */
final class MacDock {

    private static final Logger LOG = Logger.getLogger(MacDock.class.getName());
    private static final long NS_APPLICATION_ACTIVATION_POLICY_ACCESSORY = 1;

    private interface ObjC extends Library {
        Pointer objc_getClass(String name);
        Pointer sel_registerName(String name);
        Pointer objc_msgSend(Pointer receiver, Pointer selector);
        boolean objc_msgSend(Pointer receiver, Pointer selector, long argument);
    }

    private MacDock() {
    }

    /** Removes the icon from the Dock; it does nothing on other systems. Call it on the JavaFX Application Thread. */
    static void hideIcon() {
        if (!System.getProperty("os.name", "").toLowerCase().contains("mac")) {
            return;
        }
        try {
            ObjC objc = Native.load("objc", ObjC.class);
            Pointer application = objc.objc_msgSend(
                objc.objc_getClass("NSApplication"), objc.sel_registerName("sharedApplication")
            );
            objc.objc_msgSend(
                application, objc.sel_registerName("setActivationPolicy:"), NS_APPLICATION_ACTIVATION_POLICY_ACCESSORY
            );
        } catch (RuntimeException | LinkageError x) {
            LOG.warning("The Dock icon cannot be removed: " + x);
        }
    }
}
