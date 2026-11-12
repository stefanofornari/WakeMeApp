package ste.wakemeapp.cli;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The application-level shutdown: runs the registered cleanups (in registration order) and then
 * ends the process with exit code 0. Asking twice has no further effect; a failing cleanup is
 * logged and does not stop the others. Call {@link #run()} on the JavaFX Application Thread when
 * the cleanups touch windows.
 */
final class ApplicationShutdown {

    private static final Logger LOG = Logger.getLogger(ApplicationShutdown.class.getName());

    private final List<Runnable> cleanups = new CopyOnWriteArrayList<>();
    private final AtomicBoolean started = new AtomicBoolean();
    private final IntConsumer exit;

    /** @param exit ends the process with the given code; tests pass a recorder */
    ApplicationShutdown(IntConsumer exit) {
        this.exit = exit;
    }

    void onShutdown(Runnable cleanup) {
        cleanups.add(cleanup);
    }

    void run() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        LOG.info("Shutting down");
        for (Runnable cleanup : cleanups) {
            try {
                cleanup.run();
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "A cleanup at shutdown failed: " + e.getMessage(), e);
            }
        }
        exit.accept(0);
    }
}
