package ste.wakemeapp.auth;

/**
 * Why a sign-in did not complete, in words for the user. A temporary failure (the network, a busy
 * server) may pass by itself; the others need the user or the configuration to change. Either way
 * the sign-in can be started again, which asks for a new device code.
 */
public class SignInException extends RuntimeException {

    private final boolean temporary;

    public SignInException(String message, boolean temporary, Throwable cause) {
        super(message, cause);
        this.temporary = temporary;
    }

    /** True if trying again later may work without changing anything. */
    public boolean isTemporary() {
        return temporary;
    }
}
