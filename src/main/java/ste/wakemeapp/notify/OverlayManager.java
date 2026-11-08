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

    public OverlayManager() {
        // Default constructor for backward compatibility
    }

    public OverlayManager(NotificationScheduler scheduler) {
        this.scheduler = scheduler;
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

    public void showNotification(MeetingNotification notification) {
        // Close any existing overlays
        hideAll();

        // Create overlay for each screen
        for (Screen screen : Screen.getScreens()) {
            OverlayWindow overlay = new OverlayWindow();
            overlay.setNotification(notification);
            configureForScreen(overlay, screen);
            overlay.getStage().setOnHidden(e -> overlays.remove(overlay));
            
            // Set up snooze handler
            overlay.setOnSnooze(minutes -> handleSnooze(notification, minutes));
            
            overlays.add(overlay);
            overlay.show();
        }
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