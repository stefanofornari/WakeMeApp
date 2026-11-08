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
import ste.wakemeapp.scheduler.NotificationScheduler;
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
}
