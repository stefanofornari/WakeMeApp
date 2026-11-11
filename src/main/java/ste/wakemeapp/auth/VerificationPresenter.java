package ste.wakemeapp.auth;

/**
 * Shows the user what to do to complete a device code sign-in: where to go and which code to enter.
 */
@FunctionalInterface
public interface VerificationPresenter {

    /**
     * @param verificationUri the address where the user enters the code
     * @param userCode the code to enter
     * @param message the instructions as worded by Microsoft
     */
    void show(String verificationUri, String userCode, String message);

    /** The sign-in ended, successfully or not: whatever was shown can go away. */
    default void completed() {
    }
}
