package ste.wakemeapp.calendar;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import ste.wakemeapp.scheduler.NotificationScheduler;
import ste.wakemeapp.scheduler.ScheduledNotification;

/**
 * Keeps the scheduler fed from a calendar: every poll interval it asks the source for the meetings
 * starting in the next window and schedules those it does not know yet.
 * <p>
 * The window should be at least poll interval + lead time, so that a meeting is known to the
 * scheduler before its notification is due. Polling runs on its own background thread.
 */
public final class CalendarPoller implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(CalendarPoller.class.getName());

    public static final Duration DEFAULT_POLL_INTERVAL = Duration.ofMinutes(5);
    public static final Duration DEFAULT_WINDOW = Duration.ofMinutes(15);

    private final CalendarSource source;
    private final NotificationScheduler scheduler;
    private final Duration pollInterval;
    private final Duration window;
    private final Clock clock;
    private final ScheduledExecutorService executor;
    /** What has been handed to the scheduler, by event id. */
    private final Map<String, ScheduledNotification> scheduled = new ConcurrentHashMap<>();

    public CalendarPoller(CalendarSource source, NotificationScheduler scheduler) {
        this(source, scheduler, DEFAULT_POLL_INTERVAL, DEFAULT_WINDOW, Clock.systemDefaultZone());
    }

    public CalendarPoller(CalendarSource source, NotificationScheduler scheduler,
                          Duration pollInterval, Duration window, Clock clock) {
        this.source = source;
        this.scheduler = scheduler;
        this.pollInterval = pollInterval;
        this.window = window;
        this.clock = clock;
        this.executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "calendar-poller");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * A window shorter than poll interval + lead time could let a meeting slip between two polls
     * and be notified late.
     *
     * @throws IllegalArgumentException when the window is too short
     */
    public static void requireValidWindow(Duration pollInterval, Duration window, Duration leadTime) {
        Duration minimum = pollInterval.plus(leadTime);
        if (window.compareTo(minimum) < 0) {
            throw new IllegalArgumentException("The window must be at least " + minimum.toMinutes()
                + " minutes (poll interval " + pollInterval.toMinutes() + " + lead time " + leadTime.toMinutes() + ")");
        }
    }

    /** Polls now and then every poll interval. */
    public void start() {
        executor.scheduleAtFixedRate(this::pollOnce, 0, pollInterval.toMillis(), TimeUnit.MILLISECONDS);
    }

    /**
     * One poll. A failure is logged and never thrown: it must not stop the periodic polling, and
     * what is already scheduled keeps its time.
     */
    public void pollOnce() {
        try {
            LocalDateTime now = LocalDateTime.now(clock);
            for (CalendarEvent event : source.meetingsStartingBetween(now, now.plus(window))) {
                // the window is larger than the poll interval: the same meeting comes back on later polls
                scheduled.computeIfAbsent(event.id(), id -> scheduler.schedule(event.toNotification()));
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, e, () -> "Calendar poll failed, will retry in " + pollInterval);
        }
    }

    /** The notifications handed to the scheduler so far. */
    public List<ScheduledNotification> scheduled() {
        return List.copyOf(scheduled.values());
    }

    /** Stops polling; notifications already scheduled stay with the scheduler. */
    @Override
    public void close() {
        executor.shutdownNow();
    }
}
