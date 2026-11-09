package ste.wakemeapp.calendar;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import ste.wakemeapp.notify.MeetingNotification;
import ste.wakemeapp.scheduler.NotificationScheduler;
import ste.wakemeapp.scheduler.ScheduledNotification;
import static org.assertj.core.api.BDDAssertions.then;

class CalendarPollerTest {

    private static final Duration POLL_INTERVAL = Duration.ofMinutes(5);
    private static final Duration WINDOW = Duration.ofMinutes(15);

    private final NotificationScheduler scheduler = new NotificationScheduler(n -> { });
    private CalendarPoller poller;

    @AfterEach
    void close() {
        if (poller != null) {
            poller.close();
        }
        scheduler.close();
    }

    private static CalendarEvent event(String id, LocalDateTime start) {
        return new CalendarEvent(id, id, start, start.plusHours(1), null, null, null, ResponseStatus.ACCEPTED, false);
    }

    /** A clock the test moves by hand. */
    private static final class MutableClock extends Clock {
        private Instant now = Instant.now();
        @Override public ZoneId getZone() { return ZoneId.systemDefault(); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
        void advance(Duration d) { now = now.plus(d); }
    }

    @Test
    void should_schedule_a_meeting_starting_inside_the_window() {
        InMemoryCalendarSource source = new InMemoryCalendarSource();
        LocalDateTime start = LocalDateTime.now().plusMinutes(12);
        source.add(event("standup", start));
        poller = new CalendarPoller(source, scheduler, POLL_INTERVAL, WINDOW, Clock.systemDefaultZone());

        poller.pollOnce();

        then(poller.scheduled()).hasSize(1);
        then(poller.scheduled().get(0).notification().title()).isEqualTo("standup");
        then(poller.scheduled().get(0).triggerTime()).isEqualTo(start.minus(NotificationScheduler.DEFAULT_LEAD_TIME));
    }

    @Test
    void should_not_schedule_a_meeting_beyond_the_window_until_it_gets_inside() {
        InMemoryCalendarSource source = new InMemoryCalendarSource();
        source.add(event("review", LocalDateTime.now().plusMinutes(40)));
        MutableClock clock = new MutableClock();
        poller = new CalendarPoller(source, scheduler, POLL_INTERVAL, WINDOW, clock);

        poller.pollOnce();
        then(poller.scheduled()).isEmpty();

        clock.advance(Duration.ofMinutes(30)); // the meeting now starts in 10 minutes
        poller.pollOnce();
        then(poller.scheduled()).hasSize(1);
    }

    @Test
    void should_ask_the_source_for_the_window_starting_now() {
        Clock fixed = Clock.fixed(LocalDateTime.of(2026, 11, 7, 9, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        List<LocalDateTime> asked = new CopyOnWriteArrayList<>();
        poller = new CalendarPoller((from, to) -> {
            asked.add(from);
            asked.add(to);
            return List.of();
        }, scheduler, POLL_INTERVAL, WINDOW, fixed);

        poller.pollOnce();

        then(asked).containsExactly(LocalDateTime.of(2026, 11, 7, 9, 0), LocalDateTime.of(2026, 11, 7, 9, 15));
    }

    @Test
    void should_schedule_a_meeting_returned_by_several_polls_only_once() {
        InMemoryCalendarSource source = new InMemoryCalendarSource();
        source.add(event("standup", LocalDateTime.now().plusMinutes(12)));
        poller = new CalendarPoller(source, scheduler, POLL_INTERVAL, WINDOW, Clock.systemDefaultZone());

        poller.pollOnce();
        poller.pollOnce();
        poller.pollOnce();

        then(poller.scheduled()).hasSize(1);
    }

    @Test
    void should_poll_repeatedly_on_a_background_thread() throws Exception {
        CountDownLatch polls = new CountDownLatch(3);
        List<String> threads = new CopyOnWriteArrayList<>();
        poller = new CalendarPoller((from, to) -> {
            threads.add(Thread.currentThread().getName());
            polls.countDown();
            return List.of();
        }, scheduler, Duration.ofMillis(100), WINDOW, Clock.systemDefaultZone());

        poller.start();

        then(polls.await(3, TimeUnit.SECONDS)).as("polled 3 times").isTrue();
        then(threads).allMatch(name -> name.equals("calendar-poller"));
    }

    @Test
    void should_keep_polling_and_keep_notifications_after_a_failed_poll() throws Exception {
        CountDownLatch polls = new CountDownLatch(3);
        AtomicInteger calls = new AtomicInteger();
        CalendarEvent standup = event("standup", LocalDateTime.now().plusMinutes(12));
        poller = new CalendarPoller((from, to) -> {
            int call = calls.incrementAndGet();
            polls.countDown();
            if (call == 2) {
                throw new IOException("calendar unreachable");
            }
            return List.of(standup);
        }, scheduler, Duration.ofMillis(100), WINDOW, Clock.systemDefaultZone());

        poller.start();

        then(polls.await(3, TimeUnit.SECONDS)).as("polled again after the failure").isTrue();
        then(poller.scheduled()).hasSize(1);
    }

    @Test
    void should_accept_a_window_of_at_least_poll_interval_plus_lead_time() {
        CalendarPoller.requireValidWindow(Duration.ofMinutes(10), Duration.ofMinutes(15), Duration.ofMinutes(5));
    }

    @Test
    void should_reject_a_window_shorter_than_poll_interval_plus_lead_time() {
        org.assertj.core.api.BDDAssertions.thenThrownBy(() ->
                CalendarPoller.requireValidWindow(Duration.ofMinutes(10), Duration.ofMinutes(12), Duration.ofMinutes(5)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("at least 15 minutes");
    }

    private static CalendarEvent event(String id, ResponseStatus status, boolean allDay) {
        return new CalendarEvent(id, id, LocalDateTime.now().plusMinutes(12), null, null, null, null, status, allDay);
    }

    private List<String> scheduledIds(CalendarEvent... events) {
        InMemoryCalendarSource source = new InMemoryCalendarSource();
        for (CalendarEvent event : events) {
            source.add(event);
        }
        poller = new CalendarPoller(source, scheduler, POLL_INTERVAL, WINDOW, Clock.systemDefaultZone());
        poller.pollOnce();
        return poller.scheduled().stream().map(s -> s.notification().title()).sorted().toList();
    }

    @Test
    void should_schedule_accepted_tentative_and_organized_meetings() {
        then(scheduledIds(
            event("accepted", ResponseStatus.ACCEPTED, false),
            event("tentative", ResponseStatus.TENTATIVE, false),
            event("organized", ResponseStatus.ORGANIZER, false)
        )).containsExactly("accepted", "organized", "tentative");
    }

    @Test
    void should_ignore_declined_and_unanswered_meetings() {
        then(scheduledIds(
            event("declined", ResponseStatus.DECLINED, false),
            event("unanswered", ResponseStatus.NOT_RESPONDED, false)
        )).isEmpty();
    }

    @Test
    void should_ignore_all_day_events() {
        then(scheduledIds(
            event("all-day", ResponseStatus.ACCEPTED, true),
            event("normal", ResponseStatus.ACCEPTED, false)
        )).containsExactly("normal");
    }

    // --- keeping the scheduler in sync with the calendar (scheduler/US-000030)

    private static CalendarEvent event(String id, LocalDateTime start, ResponseStatus status) {
        return new CalendarEvent(id, id, start, null, null, null, null, status, false);
    }

    private CalendarPoller pollerOn(CalendarSource source, Clock clock) {
        poller = new CalendarPoller(source, scheduler, POLL_INTERVAL, WINDOW, clock);
        return poller;
    }

    @Test
    void should_reschedule_a_moved_meeting_for_its_new_start() {
        InMemoryCalendarSource source = new InMemoryCalendarSource();
        LocalDateTime start = LocalDateTime.now().plusMinutes(10);
        source.add(event("standup", start, ResponseStatus.ACCEPTED));
        pollerOn(source, Clock.systemDefaultZone()).pollOnce();
        ScheduledNotification before = poller.scheduled().get(0);

        source.add(event("standup", start.plusMinutes(3), ResponseStatus.ACCEPTED));
        poller.pollOnce();

        then(before.isCancelled()).isTrue();
        then(poller.scheduled()).hasSize(1);
        then(poller.scheduled().get(0).triggerTime())
            .isEqualTo(start.plusMinutes(3).minus(NotificationScheduler.DEFAULT_LEAD_TIME));
    }

    @Test
    void should_cancel_a_meeting_that_disappears_from_the_calendar() {
        InMemoryCalendarSource source = new InMemoryCalendarSource();
        source.add(event("standup", LocalDateTime.now().plusMinutes(10), ResponseStatus.ACCEPTED));
        pollerOn(source, Clock.systemDefaultZone()).pollOnce();
        ScheduledNotification before = poller.scheduled().get(0);

        source.remove("standup");
        poller.pollOnce();

        then(before.isCancelled()).isTrue();
        then(poller.scheduled()).isEmpty();
    }

    @Test
    void should_cancel_a_meeting_that_is_declined_after_being_scheduled() {
        InMemoryCalendarSource source = new InMemoryCalendarSource();
        LocalDateTime start = LocalDateTime.now().plusMinutes(10);
        source.add(event("standup", start, ResponseStatus.ACCEPTED));
        pollerOn(source, Clock.systemDefaultZone()).pollOnce();
        ScheduledNotification before = poller.scheduled().get(0);

        source.add(event("standup", start, ResponseStatus.DECLINED));
        poller.pollOnce();

        then(before.isCancelled()).isTrue();
        then(poller.scheduled()).isEmpty();
    }

    @Test
    void should_cancel_a_meeting_moved_out_of_the_window() {
        InMemoryCalendarSource source = new InMemoryCalendarSource();
        source.add(event("standup", LocalDateTime.now().plusMinutes(10), ResponseStatus.ACCEPTED));
        pollerOn(source, Clock.systemDefaultZone()).pollOnce();
        ScheduledNotification before = poller.scheduled().get(0);

        source.add(event("standup", LocalDateTime.now().plusMinutes(60), ResponseStatus.ACCEPTED));
        poller.pollOnce();

        then(before.isCancelled()).isTrue();
        then(poller.scheduled()).isEmpty();
    }

    @Test
    void should_leave_an_unchanged_meeting_alone() {
        InMemoryCalendarSource source = new InMemoryCalendarSource();
        source.add(event("standup", LocalDateTime.now().plusMinutes(10), ResponseStatus.ACCEPTED));
        pollerOn(source, Clock.systemDefaultZone()).pollOnce();
        ScheduledNotification before = poller.scheduled().get(0);

        poller.pollOnce();
        poller.pollOnce();

        then(poller.scheduled()).containsExactly(before);
        then(before.isCancelled()).isFalse();
    }

    @Test
    void should_keep_the_pending_snooze_of_an_unchanged_meeting() {
        InMemoryCalendarSource source = new InMemoryCalendarSource();
        LocalDateTime start = LocalDateTime.now().plusMinutes(10);
        source.add(event("standup", start, ResponseStatus.ACCEPTED));
        pollerOn(source, Clock.systemDefaultZone()).pollOnce();
        MeetingNotification shown = poller.scheduled().get(0).notification();
        ScheduledNotification snooze = scheduler.scheduleAtExactTime(
            shown.withSnooze(5, LocalDateTime.now().plusMinutes(5)));

        poller.pollOnce();

        then(snooze.isCancelled()).isFalse();
    }

    @Test
    void should_replace_the_pending_snooze_of_a_moved_meeting_and_reset_the_snooze_count() {
        InMemoryCalendarSource source = new InMemoryCalendarSource();
        LocalDateTime start = LocalDateTime.now().plusMinutes(10);
        source.add(event("standup", start, ResponseStatus.ACCEPTED));
        pollerOn(source, Clock.systemDefaultZone()).pollOnce();
        MeetingNotification shown = poller.scheduled().get(0).notification();
        ScheduledNotification snooze = scheduler.scheduleAtExactTime(
            shown.withSnooze(5, LocalDateTime.now().plusMinutes(5)).withSnooze(5, LocalDateTime.now().plusMinutes(5)));

        source.add(event("standup", start.plusMinutes(2), ResponseStatus.ACCEPTED));
        poller.pollOnce();

        then(snooze.isCancelled()).isTrue();
        then(poller.scheduled().get(0).notification().snoozeCount()).isZero();
        then(poller.scheduled().get(0).notification().originalStartTime()).isEqualTo(start.plusMinutes(2));
    }

    @Test
    void should_not_cancel_the_snooze_of_a_meeting_that_has_started() {
        InMemoryCalendarSource source = new InMemoryCalendarSource();
        source.add(event("standup", LocalDateTime.now().plusMinutes(10), ResponseStatus.ACCEPTED));
        MutableClock clock = new MutableClock();
        pollerOn(source, clock).pollOnce();
        MeetingNotification shown = poller.scheduled().get(0).notification();
        ScheduledNotification snooze = scheduler.scheduleAtExactTime(
            shown.withSnooze(10, LocalDateTime.now().plusMinutes(20)));

        clock.advance(Duration.ofMinutes(11)); // the meeting started: it is no longer in the window
        poller.pollOnce();

        then(snooze.isCancelled()).isFalse();
    }

    @Test
    void should_notify_once_for_an_unchanged_meeting_and_never_for_a_cancelled_one() throws Exception {
        List<String> fired = new CopyOnWriteArrayList<>();
        try (NotificationScheduler quick = new NotificationScheduler(
                n -> fired.add(n.title()), Duration.ofMillis(100), Clock.systemDefaultZone())) {
            InMemoryCalendarSource source = new InMemoryCalendarSource();
            source.add(event("kept", LocalDateTime.now().plusNanos(500_000_000), ResponseStatus.ACCEPTED));
            source.add(event("cancelled", LocalDateTime.now().plusNanos(500_000_000), ResponseStatus.ACCEPTED));
            poller = new CalendarPoller(source, quick, POLL_INTERVAL, WINDOW, Clock.systemDefaultZone());
            poller.pollOnce();
            poller.pollOnce();

            source.remove("cancelled");
            poller.pollOnce();
            Thread.sleep(900);

            then(fired).containsExactly("kept");
        }
    }

    // --- what the poller tells in the log (logging/US-000037)

    private List<java.util.logging.LogRecord> logged(Runnable action) {
        java.util.logging.Logger logger = java.util.logging.Logger.getLogger(CalendarPoller.class.getName());
        List<java.util.logging.LogRecord> records = new CopyOnWriteArrayList<>();
        java.util.logging.Handler handler = new java.util.logging.Handler() {
            @Override public void publish(java.util.logging.LogRecord record) { records.add(record); }
            @Override public void flush() { }
            @Override public void close() { }
        };
        java.util.logging.Level previous = logger.getLevel();
        logger.setLevel(java.util.logging.Level.FINE);
        logger.addHandler(handler);
        try {
            action.run();
        } finally {
            logger.removeHandler(handler);
            logger.setLevel(previous);
        }
        return records;
    }

    private static String text(java.util.logging.LogRecord record) {
        return record.getMessage();
    }

    @Test
    void should_log_at_info_when_a_meeting_is_scheduled() {
        InMemoryCalendarSource source = new InMemoryCalendarSource();
        source.add(event("standup", LocalDateTime.now().plusMinutes(12), ResponseStatus.ACCEPTED));
        pollerOn(source, Clock.systemDefaultZone());

        List<java.util.logging.LogRecord> records = logged(poller::pollOnce);

        then(records).anySatisfy(r -> {
            then(r.getLevel()).isEqualTo(java.util.logging.Level.INFO);
            then(text(r)).contains("standup").contains("scheduled");
        });
    }

    @Test
    void should_log_at_info_when_a_meeting_is_rescheduled_and_when_it_is_cancelled() {
        InMemoryCalendarSource source = new InMemoryCalendarSource();
        LocalDateTime start = LocalDateTime.now().plusMinutes(10);
        source.add(event("standup", start, ResponseStatus.ACCEPTED));
        pollerOn(source, Clock.systemDefaultZone()).pollOnce();

        source.add(event("standup", start.plusMinutes(3), ResponseStatus.ACCEPTED));
        List<java.util.logging.LogRecord> moved = logged(poller::pollOnce);
        source.remove("standup");
        List<java.util.logging.LogRecord> removed = logged(poller::pollOnce);

        then(moved).anySatisfy(r -> {
            then(r.getLevel()).isEqualTo(java.util.logging.Level.INFO);
            then(text(r)).contains("standup").contains("rescheduled");
        });
        then(removed).anySatisfy(r -> {
            then(r.getLevel()).isEqualTo(java.util.logging.Level.INFO);
            then(text(r)).contains("standup").contains("cancelled");
        });
    }

    @Test
    void should_log_what_each_poll_found_only_at_debug() {
        InMemoryCalendarSource source = new InMemoryCalendarSource();
        source.add(event("standup", LocalDateTime.now().plusMinutes(12), ResponseStatus.ACCEPTED));
        pollerOn(source, Clock.systemDefaultZone());

        List<java.util.logging.LogRecord> records = logged(poller::pollOnce);

        then(records).anySatisfy(r -> {
            then(r.getLevel()).isEqualTo(java.util.logging.Level.FINE);
            then(text(r)).contains("1 meeting");
        });
    }

    // --- failures reading the calendar (logging/US-000039)

    private List<java.util.logging.LogRecord> pollFailing(IOException failure) {
        pollerOn((from, to) -> { throw failure; }, Clock.systemDefaultZone());
        return logged(poller::pollOnce);
    }

    @Test
    void should_log_a_temporary_calendar_failure_at_warning() {
        List<java.util.logging.LogRecord> records = pollFailing(
            new CalendarAccessException("Microsoft Graph answered 503", true)
        );

        then(records).anySatisfy(r -> {
            then(r.getLevel()).isEqualTo(java.util.logging.Level.WARNING);
            then(text(r)).contains("Microsoft Graph answered 503");
        });
        then(records).noneMatch(r -> r.getLevel().equals(java.util.logging.Level.SEVERE));
    }

    @Test
    void should_log_a_permanent_calendar_failure_at_severe() {
        List<java.util.logging.LogRecord> records = pollFailing(
            new CalendarAccessException("Microsoft Graph answered 401 (InvalidAuthenticationToken)", false)
        );

        then(records).anySatisfy(r -> {
            then(r.getLevel()).isEqualTo(java.util.logging.Level.SEVERE);
            then(text(r)).contains("Microsoft Graph answered 401 (InvalidAuthenticationToken)");
        });
        then(records).noneMatch(r -> r.getLevel().equals(java.util.logging.Level.WARNING));
    }

    @Test
    void should_log_any_other_io_failure_at_warning() {
        List<java.util.logging.LogRecord> records = pollFailing(new IOException("calendar unreachable"));

        then(records).anySatisfy(r -> {
            then(r.getLevel()).isEqualTo(java.util.logging.Level.WARNING);
            then(text(r)).contains("calendar unreachable");
        });
    }

    @Test
    void should_tell_when_the_next_poll_is_in_the_failure_messages() {
        List<java.util.logging.LogRecord> records = pollFailing(new CalendarAccessException("down", true));

        then(records).anySatisfy(r -> then(text(r)).contains("5 min").contains("down"));
    }
}
