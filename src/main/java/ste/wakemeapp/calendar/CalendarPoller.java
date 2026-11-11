/*
 * Copyright 2026 the original author or authors from the WakeMeApp project (https://github.com/stefanofornari/WakeMeApp)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ste.wakemeapp.calendar;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import ste.wakemeapp.notify.MeetingNotification;
import ste.wakemeapp.scheduler.NotificationScheduler;
import ste.wakemeapp.scheduler.ScheduledNotification;

/**
 * Keeps the scheduler fed from a calendar: every poll interval it asks the source for the meetings
 * starting in the next window and keeps the scheduler in line with them.
 * <p>
 * The window should be at least poll interval + lead time, so that a meeting is known to the
 * scheduler before its notification is due. Polling runs on its own background thread.
 */
public final class CalendarPoller implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(CalendarPoller.class.getName());
    private static final java.time.format.DateTimeFormatter TIME = java.time.format.DateTimeFormatter.ofPattern("HH:mm");

    public static final Duration DEFAULT_POLL_INTERVAL = Duration.ofMinutes(5);
    public static final Duration DEFAULT_WINDOW = Duration.ofMinutes(15);

    private final CalendarSource source;
    private final NotificationScheduler scheduler;
    private final Duration pollInterval;
    private final Duration window;
    private final Clock clock;
    private final ScheduledExecutorService executor;
    /** What has been handed to the scheduler for each event id: its start and the notification. */
    private final Map<String, Known> known = new ConcurrentHashMap<>();
    private final DismissedMeetings dismissed;
    /** What was known of the meetings dismissed in this run, to give back on undo. */
    private final Map<String, Known> dismissedKnown = new ConcurrentHashMap<>();

    private record Known(LocalDateTime start, ScheduledNotification scheduled) { }

    public CalendarPoller(CalendarSource source, NotificationScheduler scheduler) {
        this(source, scheduler, DEFAULT_POLL_INTERVAL, DEFAULT_WINDOW, Clock.systemDefaultZone());
    }

    public CalendarPoller(CalendarSource source, NotificationScheduler scheduler,
                          Duration pollInterval, Duration window, Clock clock) {
        this(source, scheduler, pollInterval, window, clock, new DismissedMeetings());
    }

    public CalendarPoller(CalendarSource source, NotificationScheduler scheduler,
                          Duration pollInterval, Duration window, Clock clock, DismissedMeetings dismissed) {
        this.dismissed = dismissed;
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
     * One poll: brings the scheduler in line with the calendar. A new meeting is scheduled; one
     * whose start changed is rescheduled (its snooze count starts again from zero); one that is
     * declined, deleted or moved out of the window is cancelled; an unchanged one is left alone.
     * <p>
     * A failure is logged and never thrown: it must not stop the periodic polling, and what is
     * already scheduled keeps its time.
     */
    public void pollOnce() {
        try {
            LocalDateTime now = LocalDateTime.now(clock);
            Set<String> returned = new HashSet<>();
            List<CalendarEvent> events = source.meetingsStartingBetween(now, now.plus(window));
            LOG.fine(() -> "Poll found " + events.size() + " meeting(s) starting between " + now + " and " + now.plus(window));
            for (CalendarEvent event : events) {
                returned.add(event.id());
                if (!event.isEligible()) {
                    cancel(event.id());
                    continue;
                }
                dismissed.meetingMoved(event.id(), event.start());
                if (dismissed.isDismissed(event.id(), event.start())) {
                    forget(event.id());
                    continue;
                }
                Known current = known.get(event.id());
                if (current == null) {
                    schedule(event, false);
                } else if (!current.start().equals(event.start())) {
                    // also drops a pending snooze of the old time
                    forget(event.id());
                    schedule(event, true);
                }
            }
            for (Map.Entry<String, Known> entry : known.entrySet()) {
                if (!returned.contains(entry.getKey())) {
                    if (entry.getValue().start().isBefore(now)) {
                        // it started, so it left the window: what is pending (a snooze) must still ring
                        known.remove(entry.getKey());
                    } else {
                        cancel(entry.getKey());
                    }
                }
            }
        } catch (CalendarAccessException e) {
            if (e.isTemporary()) {
                LOG.warning(() -> "Cannot read the calendar, will try again in " + phrase(pollInterval) + ": " + e.getMessage());
            } else {
                LOG.severe(() -> "Cannot read the calendar, and trying again will not help: " + e.getMessage()
                    + " (next poll in " + phrase(pollInterval) + ")");
            }
            LOG.log(Level.FINE, "Details of the calendar failure", e);
        } catch (Exception e) {
            LOG.log(Level.WARNING, e, () -> "Calendar poll failed, will retry in " + phrase(pollInterval) + ": " + e.getMessage());
        }
    }

    private static String phrase(Duration duration) {
        if (duration.toMinutes() >= 1) {
            return duration.toMinutes() + " min";
        }
        return duration.toSeconds() >= 1 ? duration.toSeconds() + " s" : duration.toMillis() + " ms";
    }

    private void schedule(CalendarEvent event, boolean rescheduled) {
        ScheduledNotification scheduled = scheduler.schedule(event.toNotification());
        known.put(event.id(), new Known(event.start(), scheduled));
        LOG.info(() -> "Meeting \"" + event.title() + "\" "
            + (rescheduled ? "moved to " : "starting at ") + TIME.format(event.start())
            + ", notification " + (rescheduled ? "rescheduled" : "scheduled") + " for " + TIME.format(scheduled.triggerTime()));
    }

    /** Cancels what is pending for the meeting and says so. */
    private void cancel(String eventId) {
        Known removed = forget(eventId);
        if (removed != null) {
            LOG.info(() -> "Meeting \"" + removed.scheduled().notification().title()
                + "\" no longer applies, its notification was cancelled");
        }
    }

    /**
     * The user dismissed this occurrence: whatever is pending for it, a snooze included, is cancelled
     * and it is not notified again, not even after a restart. A notification that does not come from
     * the calendar has nothing to remember.
     */
    public void dismiss(MeetingNotification notification) {
        String id = notification.eventId();
        if (id == null) {
            return;
        }
        dismissed.dismiss(id, notification.originalStartTime());
        Known removed = forget(id);
        if (removed != null) {
            dismissedKnown.put(id, removed);
        }
        LOG.info(() -> "Meeting \"" + notification.title() + "\" dismissed, it will not be notified again");
    }

    /** Takes back {@link #dismiss}: the meeting counts as it did before, without being scheduled again. */
    public void undoDismiss(MeetingNotification notification) {
        String id = notification.eventId();
        if (id == null) {
            return;
        }
        dismissed.undo(id, notification.originalStartTime());
        Known previous = dismissedKnown.remove(id);
        if (previous != null) {
            known.put(id, previous);
        }
        LOG.info(() -> "Dismissal of meeting \"" + notification.title() + "\" undone");
    }

    private Known forget(String eventId) {
        Known removed = known.remove(eventId);
        if (removed != null) {
            scheduler.cancelAll(eventId);
        }
        return removed;
    }

    /** The notifications currently scheduled for the calendar's meetings. */
    public List<ScheduledNotification> scheduled() {
        return known.values().stream().map(Known::scheduled).toList();
    }

    /** Stops polling; notifications already scheduled stay with the scheduler. */
    @Override
    public void close() {
        executor.shutdownNow();
    }
}
