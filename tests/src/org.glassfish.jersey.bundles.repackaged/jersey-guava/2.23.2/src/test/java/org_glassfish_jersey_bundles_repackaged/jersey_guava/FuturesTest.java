/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jersey_bundles_repackaged.jersey_guava;

import java.io.IOException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import jersey.repackaged.com.google.common.util.concurrent.Futures;
import jersey.repackaged.com.google.common.util.concurrent.ListenableFuture;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class FuturesTest {
    @Test
    void getWrapsCheckedExecutionFailureWithRequestedExceptionType() {
        IOException cause = new IOException("storage unavailable");
        ListenableFuture<String> failedFuture = Futures.immediateFailedFuture(cause);

        WrappedCheckedException thrown = assertThrows(
                WrappedCheckedException.class,
                () -> Futures.get(failedFuture, WrappedCheckedException.class));

        assertThat(thrown).hasMessage(cause.toString()).hasCause(cause);
    }

    @Test
    void timedGetWrapsTimeoutWithRequestedExceptionType() {
        Future<String> timedOutFuture = new TimedOutFuture<>();

        WrappedCheckedException thrown = assertThrows(
                WrappedCheckedException.class,
                () -> Futures.get(timedOutFuture, 1L, TimeUnit.NANOSECONDS, WrappedCheckedException.class));

        assertThat(thrown).hasMessageContaining("deadline exceeded").hasCauseInstanceOf(TimeoutException.class);
    }

    public static final class WrappedCheckedException extends Exception {
        public WrappedCheckedException(String message) {
            super(message);
        }
    }

    private static final class TimedOutFuture<V> implements Future<V> {
        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return false;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean isDone() {
            return false;
        }

        @Override
        public V get() {
            throw new UnsupportedOperationException("Only timed get is used by this test");
        }

        @Override
        public V get(long timeout, TimeUnit unit) throws TimeoutException {
            throw new TimeoutException("deadline exceeded");
        }
    }
}
