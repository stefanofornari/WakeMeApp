package ste.wakemeapp.notify;

import javafx.stage.Screen;
import javafx.stage.Stage;
import ste.wakemeapp.scheduler.NotificationScheduler;
import ste.wakemeapp.scheduler.ScheduledNotification;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public final class OverlayManager {

    private static final java.util.logging.Logger LOG = java.util.logging.Logger.getLogger(OverlayManager.class.getName());

    private final List<OverlayWindow> overlays = new CopyOnWriteArrayList<>();
    private NotificationScheduler scheduler;
    private final AtomicInteger pendingSnoozes = new AtomicInteger(0);
    private java.util.function.BooleanSupplier hasPendingSnoozesSupplier;
    private Runnable onSnoozeScheduled;
    private java.util.function.Consumer<MeetingNotification> onDismissed;
    private java.util.function.Consumer<MeetingNotification> onDismissUndone;
    private java.time.Duration undoTime = java.time.Duration.ofSeconds(5);
    private DismissToast undoToast;
    private java.time.Duration autoClose;   // null: the overlay's own default

    public OverlayManager() {
        // Default constructor for backward compatibility
    }

    public OverlayManager(NotificationScheduler scheduler) {
        this.scheduler = scheduler;
    }

    /** How long the overlays shown from now on stay up before closing by themselves (the default if never set). */
    public void setAutoClose(java.time.Duration autoClose) {
        this.autoClose = autoClose;
    }

    public void setScheduler(NotificationScheduler scheduler) {
        this.scheduler = scheduler;
    }

    /** Register a callback invoked when a snooze is scheduled. */
    public void setOnSnoozeScheduled(Runnable callback) {
        this.onSnoozeScheduled = callback;
    }

    /** Register a supplier that returns true if there are pending scheduled notifications (snoozes). */
    public void setHasPendingSnoozesSupplier(java.util.function.BooleanSupplier supplier) {
        this.hasPendingSnoozesSupplier = supplier;
    }

    /** Register a listener called when the user dismisses a meeting for good. */
    public void setOnDismissed(java.util.function.Consumer<MeetingNotification> listener) {
        this.onDismissed = listener;
    }

    /** Register a listener called when the user takes a dismissal back within the undo time. */
    public void setOnDismissUndone(java.util.function.Consumer<MeetingNotification> listener) {
        this.onDismissUndone = listener;
    }

    /** For tests: how long the undo is offered (5 seconds by default). */
    void setUndoTime(java.time.Duration undoTime) {
        this.undoTime = undoTime;
    }

    java.time.Duration getUndoTime() {
        return undoTime;
    }

    DismissToast getUndoToast() {
        return undoToast;
    }

    public void showNotification(MeetingNotification notification) {
        // Close any existing overlays
        hideAll();

        // Create overlay for each screen
        for (Screen screen : Screen.getScreens()) {
            OverlayWindow overlay = new OverlayWindow();
            if (autoClose != null) {
                overlay.setAutoClose(autoClose);
            }
            overlay.setNotification(notification);
            configureForScreen(overlay, screen);
            overlay.getStage().setOnHidden(e -> overlays.remove(overlay));
            
            // Set up snooze handler
            overlay.setOnSnooze(minutes -> handleSnooze(notification, minutes));
            overlay.setOnDismiss(() -> handleDismiss(notification));
            
            overlays.add(overlay);
            overlay.show();
        }
    }

    private void handleDismiss(MeetingNotification notification) {
        if (onDismissed != null) {
            onDismissed.accept(notification);
        }
        hideAll();   // the same meeting may be up on other screens
        if (undoToast != null) {
            undoToast.close();
        }
        undoToast = new DismissToast(undoTime, () -> {
            if (onDismissUndone != null) {
                onDismissUndone.accept(notification);
            }
            showNotification(notification);
        });
        undoToast.show();
    }

    private void handleSnooze(MeetingNotification notification, int minutes) {
        if (scheduler == null) {
            return; // No scheduler configured, can't reschedule
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime snoozeUntil;

        if (minutes == -1) {
            // "Until meeting starts" = 1 minute before meeting
            snoozeUntil = notification.originalStartTime().minusMinutes(1);
            if (snoozeUntil.isBefore(now) || snoozeUntil.isEqual(now)) {
                snoozeUntil = now; // Meeting already started or starting very soon
            }
        } else {
            // Fixed duration snooze
            snoozeUntil = now.plusMinutes(minutes);
        }

        // Create new notification for the snooze end time, preserving original meeting time
        MeetingNotification snoozedNotification = notification.withSnooze(minutes, snoozeUntil);
        
        // Schedule the re-notification at exact time (no lead time for snoozes)
        ScheduledNotification scheduled = scheduler.scheduleAtExactTime(snoozedNotification);
        
        LocalDateTime dueAt = snoozeUntil;
        LOG.info(() -> "Meeting \"" + notification.title() + "\" snoozed ("
            + snoozedNotification.snoozeStatusText().toLowerCase() + "), notification due at "
            + java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss").format(dueAt));

        // Track pending snooze
        pendingSnoozes.incrementAndGet();
        if (onSnoozeScheduled != null) {
            onSnoozeScheduled.run();
        }
        
        // The scheduled notification will trigger the callback which should call showNotification again
        // We need to decrement when it fires - wrap the callback
    }

    private void configureForScreen(OverlayWindow overlay, Screen screen) {
        Stage stage = overlay.getStage();
        stage.setX(screen.getVisualBounds().getMinX());
        stage.setY(screen.getVisualBounds().getMinY());
        stage.setWidth(screen.getVisualBounds().getWidth());
        stage.setHeight(screen.getVisualBounds().getHeight());
    }

    public void hideAll() {
        for (OverlayWindow overlay : overlays) {
            overlay.hide();
        }
        overlays.clear();
    }

    public int getOverlayCount() {
        return overlays.size();
    }

    public List<OverlayWindow> getOverlays() {
        return new ArrayList<>(overlays);
    }

    /** Returns true if there are visible overlays OR pending snoozes scheduled. */
    public boolean hasActiveOrPendingNotifications() {
        if (overlays.size() > 0) {
            return true;
        }
        if (hasPendingSnoozesSupplier != null) {
            return hasPendingSnoozesSupplier.getAsBoolean();
        }
        return pendingSnoozes.get() > 0;
    }
}