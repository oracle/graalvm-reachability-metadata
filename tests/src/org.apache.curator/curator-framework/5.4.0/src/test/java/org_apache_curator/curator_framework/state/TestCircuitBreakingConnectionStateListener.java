/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_curator.curator_framework.state;

import org.apache.curator.RetryPolicy;
import org.apache.curator.RetrySleeper;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.state.CircuitBreakingConnectionStateListener;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.curator.framework.state.ConnectionStateListener;
import org.apache.curator.retry.RetryForever;
import org.apache.curator.retry.RetryOneTime;
import org.apache.curator.test.compatibility.Timing2;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
public class TestCircuitBreakingConnectionStateListener {
    private final CuratorFramework dummyClient = CuratorFrameworkFactory.newClient("foo", new RetryOneTime(1));
    private final Timing2 timing = new Timing2();
    private final Timing2 retryTiming = timing.multiple(.25);
    private volatile ScheduledThreadPoolExecutor service;

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private static class RecordingListener implements ConnectionStateListener {
        final BlockingQueue<ConnectionState> stateChanges = new LinkedBlockingQueue<>();

        @Override
        public void stateChanged(CuratorFramework client, ConnectionState newState) {
            stateChanges.offer(newState);
        }
    }

    private class TestRetryPolicy extends RetryForever {
        volatile boolean isRetrying = true;

        TestRetryPolicy() {
            super(retryTiming.milliseconds());
        }

        @Override
        public boolean allowRetry(int retryCount, long elapsedTimeMs, RetrySleeper sleeper) {
            return isRetrying && super.allowRetry(retryCount, elapsedTimeMs, sleeper);
        }
    }

    @BeforeEach
    public void setup() {
        service = new ScheduledThreadPoolExecutor(1);
    }

    @AfterEach
    public void tearDown() {
        service.shutdownNow();
    }

    @Test
    public void testBasic() throws Exception {
        RecordingListener recordingListener = new RecordingListener();
        TestRetryPolicy retryPolicy = new TestRetryPolicy();
        CircuitBreakingConnectionStateListener listener = new CircuitBreakingConnectionStateListener(dummyClient, recordingListener, retryPolicy, service);
        listener.stateChanged(dummyClient, ConnectionState.RECONNECTED);
        assertEquals(timing.takeFromQueue(recordingListener.stateChanges), ConnectionState.RECONNECTED);
        listener.stateChanged(dummyClient, ConnectionState.SUSPENDED);
        assertEquals(timing.takeFromQueue(recordingListener.stateChanges), ConnectionState.SUSPENDED);
        listener.stateChanged(dummyClient, ConnectionState.SUSPENDED);
        assertTrue(recordingListener.stateChanges.isEmpty());
        listener.stateChanged(dummyClient, ConnectionState.LOST);
        assertEquals(timing.takeFromQueue(recordingListener.stateChanges), ConnectionState.LOST);
        synchronized (listener) {
            listener.stateChanged(dummyClient, ConnectionState.READ_ONLY);
            listener.stateChanged(dummyClient, ConnectionState.RECONNECTED);
            listener.stateChanged(dummyClient, ConnectionState.SUSPENDED);
            listener.stateChanged(dummyClient, ConnectionState.LOST);
            listener.stateChanged(dummyClient, ConnectionState.SUSPENDED);
        }
        retryTiming.multiple(2).sleep();
        assertTrue(recordingListener.stateChanges.isEmpty());
        retryPolicy.isRetrying = false;
        assertEquals(timing.takeFromQueue(recordingListener.stateChanges), ConnectionState.SUSPENDED);
    }

    @Test
    public void testResetsAfterReconnect() throws Exception {
        RecordingListener recordingListener = new RecordingListener();
        TestRetryPolicy retryPolicy = new TestRetryPolicy();
        CircuitBreakingConnectionStateListener listener = new CircuitBreakingConnectionStateListener(dummyClient, recordingListener, retryPolicy, service);
        synchronized (listener) {
            listener.stateChanged(dummyClient, ConnectionState.LOST);
            listener.stateChanged(dummyClient, ConnectionState.LOST);
        }
        assertEquals(timing.takeFromQueue(recordingListener.stateChanges), ConnectionState.LOST);
        assertTrue(recordingListener.stateChanges.isEmpty());
        listener.stateChanged(dummyClient, ConnectionState.RECONNECTED);
        assertEquals(timing.takeFromQueue(recordingListener.stateChanges), ConnectionState.RECONNECTED);
    }

    @Test
    public void testRetryNever() throws Exception {
        RecordingListener recordingListener = new RecordingListener();
        RetryPolicy retryNever = (retryCount, elapsedTimeMs, sleeper) -> false;
        CircuitBreakingConnectionStateListener listener = new CircuitBreakingConnectionStateListener(dummyClient, recordingListener, retryNever, service);
        listener.stateChanged(dummyClient, ConnectionState.LOST);
        assertEquals(timing.takeFromQueue(recordingListener.stateChanges), ConnectionState.LOST);
        assertFalse(listener.isOpen());
        listener.stateChanged(dummyClient, ConnectionState.LOST);
        assertEquals(timing.takeFromQueue(recordingListener.stateChanges), ConnectionState.LOST);
        assertFalse(listener.isOpen());
    }

    @Test
    public void testRetryOnce() throws Exception {
        RecordingListener recordingListener = new RecordingListener();
        RetryPolicy retryOnce = new RetryOneTime(retryTiming.milliseconds());
        CircuitBreakingConnectionStateListener listener = new CircuitBreakingConnectionStateListener(dummyClient, recordingListener, retryOnce, service);
        synchronized (listener) {
            listener.stateChanged(dummyClient, ConnectionState.LOST);
            listener.stateChanged(dummyClient, ConnectionState.SUSPENDED);
            assertTrue(listener.isOpen());
        }
        assertEquals(timing.takeFromQueue(recordingListener.stateChanges), ConnectionState.LOST);
        assertEquals(timing.takeFromQueue(recordingListener.stateChanges), ConnectionState.SUSPENDED);
        assertFalse(listener.isOpen());
    }

    @Test
    public void testSuspendedToLostRatcheting() throws Exception {
        RecordingListener recordingListener = new RecordingListener();
        RetryPolicy retryInfinite = new RetryForever(Integer.MAX_VALUE);
        CircuitBreakingConnectionStateListener listener = new CircuitBreakingConnectionStateListener(dummyClient, recordingListener, retryInfinite, service);
        listener.stateChanged(dummyClient, ConnectionState.RECONNECTED);
        assertFalse(listener.isOpen());
        assertEquals(timing.takeFromQueue(recordingListener.stateChanges), ConnectionState.RECONNECTED);
        listener.stateChanged(dummyClient, ConnectionState.SUSPENDED);
        assertTrue(listener.isOpen());
        assertEquals(timing.takeFromQueue(recordingListener.stateChanges), ConnectionState.SUSPENDED);
        listener.stateChanged(dummyClient, ConnectionState.RECONNECTED);
        listener.stateChanged(dummyClient, ConnectionState.READ_ONLY);
        listener.stateChanged(dummyClient, ConnectionState.SUSPENDED);
        listener.stateChanged(dummyClient, ConnectionState.SUSPENDED);
        listener.stateChanged(dummyClient, ConnectionState.SUSPENDED);
        listener.stateChanged(dummyClient, ConnectionState.SUSPENDED);
        listener.stateChanged(dummyClient, ConnectionState.RECONNECTED);
        listener.stateChanged(dummyClient, ConnectionState.READ_ONLY);
        listener.stateChanged(dummyClient, ConnectionState.SUSPENDED);
        assertTrue(recordingListener.stateChanges.isEmpty());
        assertTrue(listener.isOpen());
        listener.stateChanged(dummyClient, ConnectionState.LOST);
        assertEquals(timing.takeFromQueue(recordingListener.stateChanges), ConnectionState.LOST);
        assertTrue(listener.isOpen());
        listener.stateChanged(dummyClient, ConnectionState.RECONNECTED);
        listener.stateChanged(dummyClient, ConnectionState.READ_ONLY);
        listener.stateChanged(dummyClient, ConnectionState.SUSPENDED);
        listener.stateChanged(dummyClient, ConnectionState.SUSPENDED);
        listener.stateChanged(dummyClient, ConnectionState.SUSPENDED);
        listener.stateChanged(dummyClient, ConnectionState.SUSPENDED);
        listener.stateChanged(dummyClient, ConnectionState.RECONNECTED);
        listener.stateChanged(dummyClient, ConnectionState.READ_ONLY);
        listener.stateChanged(dummyClient, ConnectionState.SUSPENDED);
        assertTrue(recordingListener.stateChanges.isEmpty());
        assertTrue(listener.isOpen());
        listener.stateChanged(dummyClient, ConnectionState.RECONNECTED);
        listener.stateChanged(dummyClient, ConnectionState.READ_ONLY);
        listener.stateChanged(dummyClient, ConnectionState.SUSPENDED);
        listener.stateChanged(dummyClient, ConnectionState.SUSPENDED);
        listener.stateChanged(dummyClient, ConnectionState.SUSPENDED);
        listener.stateChanged(dummyClient, ConnectionState.SUSPENDED);
        listener.stateChanged(dummyClient, ConnectionState.LOST);
        listener.stateChanged(dummyClient, ConnectionState.LOST);
        listener.stateChanged(dummyClient, ConnectionState.LOST);
        listener.stateChanged(dummyClient, ConnectionState.RECONNECTED);
        listener.stateChanged(dummyClient, ConnectionState.READ_ONLY);
        listener.stateChanged(dummyClient, ConnectionState.LOST);
        listener.stateChanged(dummyClient, ConnectionState.SUSPENDED);
        listener.stateChanged(dummyClient, ConnectionState.LOST);
        assertTrue(recordingListener.stateChanges.isEmpty());
        assertTrue(listener.isOpen());
    }
}
