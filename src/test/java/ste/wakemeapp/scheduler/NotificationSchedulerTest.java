package ste.wakemeapp.scheduler;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import ste.wakemeapp.notify.MeetingNotification;
import static org.assertj.core.api.BDDAssertions.then;

/**
 * Uses real (short) delays: the lead time is a few hundred milliseconds so the
 * tests stay fast without needing to fake the executor.
 */
class NotificationSchedulerTest {

    private final List<String> fired = new CopyOnWriteArrayList<>();
    private NotificationScheduler scheduler;

    @AfterEach
    void close_scheduler() {
        if (scheduler != null) {
            scheduler.close();
        }
    }

    private MeetingNotification meeting(String title, Duration startsIn) {
        return new MeetingNotification(title, LocalDateTime.now().plus(startsIn), "https://example.com/join");
    }

    private NotificationScheduler schedulerWith(Duration leadTime, CountDownLatch latch) {
        return new NotificationScheduler(n -> {
            fired.add(n.title());
            latch.countDown();
        }, leadTime, Clock.systemDefaultZone());
    }

    @Test
    void should_set_trigger_time_to_start_minus_lead_time() {
        scheduler = schedulerWith(Duration.ofMinutes(5), new CountDownLatch(1));
        MeetingNotification meeting = meeting("Standup", Duration.ofMinutes(10));

        ScheduledNotification scheduled = scheduler.schedule(meeting);

        then(scheduled.triggerTime()).isEqualTo(meeting.startTime().minusMinutes(5));
        then(scheduled.notification()).isSameAs(meeting);
        then(fired).isEmpty();
    }

    @Test
    void should_use_five_minutes_as_default_lead_time() {
        scheduler = new NotificationScheduler(n -> { });
        MeetingNotification meeting = meeting("Standup", Duration.ofMinutes(30));

        then(scheduler.schedule(meeting).triggerTime()).isEqualTo(meeting.startTime().minusMinutes(5));
    }

    @Test
    void should_fire_when_the_trigger_time_is_reached_and_not_before() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        scheduler = schedulerWith(Duration.ofMillis(500), latch);

        scheduler.schedule(meeting("Standup", Duration.ofMillis(800))); // fires in ~300 ms

        then(fired).isEmpty();
        then(latch.await(2, TimeUnit.SECONDS)).isTrue();
        then(fired).containsExactly("Standup");
    }

    @Test
    void should_fire_multiple_meetings_independently_and_once_each() throws Exception {
        CountDownLatch latch = new CountDownLatch(2);
        scheduler = schedulerWith(Duration.ofMillis(200), latch);

        scheduler.schedule(meeting("Second", Duration.ofMillis(900))); // fires in ~700 ms
        scheduler.schedule(meeting("First", Duration.ofMillis(500)));  // fires in ~300 ms

        then(latch.await(2, TimeUnit.SECONDS)).isTrue();
        Thread.sleep(300);
        then(fired).containsExactly("First", "Second");
    }

    @Test
    void should_not_fire_a_cancelled_notification_and_leave_the_others_alone() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        scheduler = schedulerWith(Duration.ofMillis(200), latch);
        ScheduledNotification cancelled = scheduler.schedule(meeting("Cancelled", Duration.ofMillis(500)));
        scheduler.schedule(meeting("Kept", Duration.ofMillis(800)));

        cancelled.cancel();

        then(latch.await(2, TimeUnit.SECONDS)).isTrue();
        Thread.sleep(300);
        then(fired).containsExactly("Kept");
    }

    @Test
    void should_fire_immediately_when_the_trigger_time_has_already_passed() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        scheduler = schedulerWith(Duration.ofMinutes(5), latch);

        scheduler.schedule(meeting("Late", Duration.ofMinutes(2)));

        then(latch.await(1, TimeUnit.SECONDS)).isTrue();
        then(fired).containsExactly("Late");
    }

    @Test
    void should_drop_pending_notifications_when_closed() throws Exception {
        scheduler = schedulerWith(Duration.ofMillis(200), new CountDownLatch(2));
        scheduler.schedule(meeting("One", Duration.ofMillis(500)));
        scheduler.schedule(meeting("Two", Duration.ofMillis(700)));

        scheduler.close();
        Thread.sleep(1000);

        then(fired).isEmpty();
    }

    @Test
    void should_keep_firing_after_a_handler_fails() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        scheduler = new NotificationScheduler(n -> {
            if (n.title().equals("Broken")) {
                throw new IllegalStateException("boom");
            }
            fired.add(n.title());
            latch.countDown();
        }, Duration.ofMillis(100), Clock.systemDefaultZone());

        scheduler.schedule(meeting("Broken", Duration.ofMillis(300)));
        scheduler.schedule(meeting("Fine", Duration.ofMillis(600)));

        then(latch.await(2, TimeUnit.SECONDS)).isTrue();
        then(fired).containsExactly("Fine");
    }

    @Test
    void should_schedule_at_exact_time_without_lead_time() {
        scheduler = new NotificationScheduler(n -> { }, Duration.ofMinutes(5), Clock.systemDefaultZone());
        LocalDateTime exactTime = LocalDateTime.now().plusMinutes(10);
        MeetingNotification meeting = new MeetingNotification("Exact", exactTime, "https://example.com/join");

        ScheduledNotification scheduled = scheduler.scheduleAtExactTime(meeting);

        then(scheduled.triggerTime()).isEqualTo(exactTime);
        then(scheduled.notification()).isSameAs(meeting);
    }

    @Test
    void should_fire_at_exact_time_for_snooze() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        scheduler = new NotificationScheduler(n -> {
            fired.add(n.title());
            latch.countDown();
        }, Duration.ofMinutes(5), Clock.systemDefaultZone());

        // Schedule for 500ms from now (exact time, no lead time)
        LocalDateTime exactTime = LocalDateTime.now().plusNanos(500_000_000);
        MeetingNotification meeting = new MeetingNotification("Snooze", exactTime, "https://example.com/join");

        scheduler.scheduleAtExactTime(meeting);

        then(fired).isEmpty();
        then(latch.await(2, TimeUnit.SECONDS)).isTrue();
        then(fired).containsExactly("Snooze");
    }

    private MeetingNotification calendarMeeting(String eventId, Duration startsIn) {
        LocalDateTime start = LocalDateTime.now().plus(startsIn);
        return new MeetingNotification(eventId, start, null, null, null, null, 0, 3, start, eventId);
    }

    @Test
    void should_cancel_everything_pending_for_an_event_and_nothing_else() {
        scheduler = new NotificationScheduler(n -> { });
        ScheduledNotification regular = scheduler.schedule(calendarMeeting("e1", Duration.ofMinutes(30)));
        ScheduledNotification snooze = scheduler.scheduleAtExactTime(calendarMeeting("e1", Duration.ofMinutes(20)));
        ScheduledNotification other = scheduler.schedule(calendarMeeting("e2", Duration.ofMinutes(30)));
        ScheduledNotification single = scheduler.schedule(meeting("No event id", Duration.ofMinutes(30)));

        scheduler.cancelAll("e1");

        then(regular.isCancelled()).isTrue();
        then(snooze.isCancelled()).isTrue();
        then(other.isCancelled()).isFalse();
        then(single.isCancelled()).isFalse();
    }

    @Test
    void should_not_fire_what_was_cancelled_by_event_id() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        scheduler = schedulerWith(Duration.ofMillis(100), latch);
        scheduler.schedule(calendarMeeting("e1", Duration.ofMillis(400)));

        scheduler.cancelAll("e1");

        then(latch.await(800, TimeUnit.MILLISECONDS)).isFalse();
        then(fired).isEmpty();
    }
}
