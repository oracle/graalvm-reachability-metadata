/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_common;

import io.netty.util.HashedWheelTimer;
import io.netty.util.Timeout;
import io.netty.util.Timer;
import io.netty.util.TimerTask;
import io.netty.util.concurrent.DefaultThreadFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the `HashedWheelTimer` scheduling subsystem of netty-common: one-shot timeouts on a
 * hashed timing wheel, their cancellation, tasks that reschedule themselves, the pending-timeout
 * cap and the shutdown hand-over of unprocessed timeouts.
 *
 * A login service gives every session an idle timeout on a shared wheel. An idle session expires
 * on the wheel's worker thread, a logout cancels the timeout, and activity replaces it with a new
 * one. A reminder task reschedules itself from inside the wheel until it has run three times, a
 * capped wheel rejects timeouts beyond its limit, and stopping the wheel hands the application the
 * sessions that were still alive and refuses any session opened after it.
 */
class SessionExpiryWheelTimerTest {

    private static final long WAIT_SECONDS = 10;

    private static final long SHORT_IDLE_MILLIS = 30;

    private static final long LONG_IDLE_MINUTES = 60;

    private HashedWheelTimer wheel;

    private SessionStore sessions;

    @BeforeEach
    void startWheel() {
        wheel = new HashedWheelTimer(new DefaultThreadFactory("session-expiry"), 10, TimeUnit.MILLISECONDS, 64);
        sessions = new SessionStore(wheel);
    }

    @AfterEach
    void stopWheel() {
        wheel.stop();
    }

    @Test
    void idleSessionsExpireAndLogoutCancelsTheirTimeout() throws Exception {
        Session alice = sessions.open("alice", SHORT_IDLE_MILLIS, TimeUnit.MILLISECONDS);
        Session bob = sessions.open("bob", LONG_IDLE_MINUTES, TimeUnit.MINUTES);
        Session carol = sessions.open("carol", LONG_IDLE_MINUTES, TimeUnit.MINUTES);

        assertThat(sessions.logout("carol")).isTrue();
        assertThat(alice.expired.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();

        assertThat(alice.timeout().isExpired()).isTrue();
        assertThat(alice.timeout().timer()).isSameAs(wheel);
        assertThat(alice.expiredOn).startsWith("session-expiry");
        assertThat(carol.timeout().isCancelled()).isTrue();
        assertThat(carol.timeout().isExpired()).isFalse();
        assertThat(sessions.logout("carol")).isFalse();
        assertThat(sessions.active()).containsExactly("bob");
        assertThat(sessions.expired()).containsExactly("alice");

        Set<Timeout> unprocessed = wheel.stop();
        assertThat(unprocessed).hasSize(1);
        Timeout leftOver = unprocessed.iterator().next();
        assertThat(leftOver).isSameAs(bob.timeout());
        assertThat(leftOver.task()).isSameAs(bob);
        assertThat(leftOver.isExpired()).isFalse();
        assertThat(sessions.expired()).containsExactly("alice");
        assertThatThrownBy(() -> sessions.open("dave", SHORT_IDLE_MILLIS, TimeUnit.MILLISECONDS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot be started once stopped");
        assertThat(sessions.active()).containsExactly("bob");
    }

    @Test
    void activityReplacesTheIdleTimeoutOfASession() throws Exception {
        Session dave = sessions.open("dave", LONG_IDLE_MINUTES, TimeUnit.MINUTES);
        Timeout firstIdle = dave.timeout();

        sessions.touch("dave", SHORT_IDLE_MILLIS, TimeUnit.MILLISECONDS);
        assertThat(firstIdle.isCancelled()).isTrue();
        assertThat(dave.timeout()).isNotSameAs(firstIdle);

        assertThat(dave.expired.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
        assertThat(dave.timeout().isExpired()).isTrue();
        assertThat(firstIdle.isExpired()).isFalse();
        assertThat(sessions.active()).isEmpty();
        assertThat(sessions.expired()).containsExactly("dave");
        assertThat(wheel.stop()).isEmpty();
    }

    @Test
    void reminderReschedulesItselfOnTheWheelUntilItIsDone() throws Exception {
        PasswordResetReminder reminder = new PasswordResetReminder("erin@example.com", 3);
        Timeout first = wheel.newTimeout(reminder, SHORT_IDLE_MILLIS, TimeUnit.MILLISECONDS);

        assertThat(reminder.done.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();

        assertThat(first.isExpired()).isTrue();
        assertThat(reminder.sent()).containsExactly(
                "reminder 1 to erin@example.com",
                "reminder 2 to erin@example.com",
                "reminder 3 to erin@example.com");
        assertThat(reminder.timers()).containsOnly(wheel).hasSize(3);
        assertThat(wheel.stop()).isEmpty();
    }

    @Test
    void cappedWheelRejectsTimeoutsBeyondItsLimit() {
        HashedWheelTimer capped = new HashedWheelTimer(
                new DefaultThreadFactory("capped-expiry"), 10, TimeUnit.MILLISECONDS, 8, true, 2);
        try {
            SessionStore store = new SessionStore(capped);
            store.open("frank", LONG_IDLE_MINUTES, TimeUnit.MINUTES);
            store.open("grace", LONG_IDLE_MINUTES, TimeUnit.MINUTES);
            assertThat(capped.pendingTimeouts()).isEqualTo(2);

            assertThatThrownBy(() -> store.open("heidi", LONG_IDLE_MINUTES, TimeUnit.MINUTES))
                    .isInstanceOf(RejectedExecutionException.class)
                    .hasMessageContaining("maximum allowed pending timeouts (2)");
            assertThat(store.active()).containsExactly("frank", "grace");
            assertThat(capped.pendingTimeouts()).isEqualTo(2);

            assertThat(capped.stop()).hasSize(2);
        } finally {
            capped.stop();
        }
    }

    /// Keeps the live sessions of the login service and the idle timeout of each on a wheel.
    private static final class SessionStore {

        private final Timer timer;

        private final Map<String, Session> active = new LinkedHashMap<>();

        private final List<String> expired = new ArrayList<>();

        SessionStore(Timer timer) {
            this.timer = timer;
        }

        Session open(String user, long idle, TimeUnit unit) {
            Session session = new Session(user, this);
            synchronized (this) {
                active.put(user, session);
            }
            try {
                session.schedule(timer, idle, unit);
            } catch (RuntimeException rejected) {
                synchronized (this) {
                    active.remove(user);
                }
                throw rejected;
            }
            return session;
        }

        void touch(String user, long idle, TimeUnit unit) {
            Session session;
            synchronized (this) {
                session = active.get(user);
            }
            assertThat(session).as("active session %s", user).isNotNull();
            assertThat(session.timeout().cancel()).isTrue();
            session.schedule(timer, idle, unit);
        }

        boolean logout(String user) {
            Session session;
            synchronized (this) {
                session = active.remove(user);
            }
            return session != null && session.timeout().cancel();
        }

        synchronized void expire(Session session) {
            if (active.remove(session.user, session)) {
                expired.add(session.user);
            }
        }

        synchronized List<String> active() {
            return List.copyOf(active.keySet());
        }

        synchronized List<String> expired() {
            return List.copyOf(expired);
        }
    }

    /// A login session whose `run` is called by the wheel when the session has been idle too long;
    /// the wheel never runs a timeout that a logout or activity cancelled.
    private static final class Session implements TimerTask {

        private final String user;

        private final SessionStore store;

        private final CountDownLatch expired = new CountDownLatch(1);

        private volatile Timeout timeout;

        private volatile String expiredOn;

        Session(String user, SessionStore store) {
            this.user = user;
            this.store = store;
        }

        void schedule(Timer timer, long idle, TimeUnit unit) {
            timeout = timer.newTimeout(this, idle, unit);
        }

        Timeout timeout() {
            return timeout;
        }

        @Override
        public void run(Timeout fired) {
            store.expire(this);
            expiredOn = Thread.currentThread().getName();
            expired.countDown();
        }
    }

    /// Sends a password-reset reminder each time it fires and schedules the next one on the same
    /// wheel until it has sent as many as it was asked to.
    private static final class PasswordResetReminder implements TimerTask {

        private final String address;

        private final int reminders;

        private final List<String> sent = new ArrayList<>();

        private final List<Timer> timers = new ArrayList<>();

        private final CountDownLatch done = new CountDownLatch(1);

        PasswordResetReminder(String address, int reminders) {
            this.address = address;
            this.reminders = reminders;
        }

        @Override
        public void run(Timeout timeout) {
            int count;
            synchronized (this) {
                sent.add("reminder " + (sent.size() + 1) + " to " + address);
                timers.add(timeout.timer());
                count = sent.size();
            }
            if (count < reminders) {
                timeout.timer().newTimeout(this, SHORT_IDLE_MILLIS, TimeUnit.MILLISECONDS);
            } else {
                done.countDown();
            }
        }

        synchronized List<String> sent() {
            return List.copyOf(sent);
        }

        synchronized List<Timer> timers() {
            return List.copyOf(timers);
        }
    }
}
