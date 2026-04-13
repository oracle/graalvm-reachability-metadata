/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_guava.failureaccess;

import com.google.common.util.concurrent.internal.InternalFutureFailureAccess;
import com.google.common.util.concurrent.internal.InternalFutures;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FailureaccessTest {

    @Test
    void returnsTheExactFailureInstanceProvidedByImplementation() {
        Throwable failure = new IllegalArgumentException("boom");
        CountingFailureAccess access = new CountingFailureAccess(failure, null);

        Throwable result = InternalFutures.tryInternalFastPathGetFailure(access);

        assertThat(result).isSameAs(failure);
        assertThat(access.invocationCount()).isEqualTo(1);
    }

    @Test
    void returnsNullWhenImplementationReportsNoCachedFailure() {
        CountingFailureAccess access = new CountingFailureAccess(null, null);

        Throwable result = InternalFutures.tryInternalFastPathGetFailure(access);

        assertThat(result).isNull();
        assertThat(access.invocationCount()).isEqualTo(1);
    }

    @Test
    void delegatesEveryCallToTheUnderlyingAccessObject() {
        Throwable failure = new UnsupportedOperationException("failed");
        CountingFailureAccess access = new CountingFailureAccess(failure, null);

        Throwable firstResult = InternalFutures.tryInternalFastPathGetFailure(access);
        Throwable secondResult = InternalFutures.tryInternalFastPathGetFailure(access);

        assertThat(firstResult).isSameAs(failure);
        assertThat(secondResult).isSameAs(failure);
        assertThat(access.invocationCount()).isEqualTo(2);
    }

    @Test
    void propagatesExceptionsThrownByTheImplementation() {
        RuntimeException expected = new IllegalStateException("unexpected");
        CountingFailureAccess access = new CountingFailureAccess(null, expected);

        assertThatThrownBy(() -> InternalFutures.tryInternalFastPathGetFailure(access))
                .isSameAs(expected);
        assertThat(access.invocationCount()).isEqualTo(1);
    }

    @Test
    void throwsNullPointerExceptionForNullAccessObject() {
        assertThatThrownBy(() -> InternalFutures.tryInternalFastPathGetFailure(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void exposesFailureConsistentlyWithTheCompletedFutureContract() {
        Throwable failure = new IllegalArgumentException("failed future");
        CompletedFutureFailureAccess future = CompletedFutureFailureAccess.failed(failure);

        Throwable result = InternalFutures.tryInternalFastPathGetFailure(future);

        assertThat(result).isSameAs(failure);
        assertThat(future.isDone()).isTrue();
        assertThat(future.isCancelled()).isFalse();
        assertThatThrownBy(future::get)
                .isInstanceOf(ExecutionException.class)
                .hasCauseReference(failure);
    }

    @Test
    void doesNotTreatCancellationAsAFailure() {
        CompletedFutureFailureAccess future = CompletedFutureFailureAccess.cancelled();

        Throwable result = InternalFutures.tryInternalFastPathGetFailure(future);

        assertThat(result).isNull();
        assertThat(future.isDone()).isTrue();
        assertThat(future.isCancelled()).isTrue();
        assertThatThrownBy(future::get)
                .isInstanceOf(CancellationException.class);
    }

    private static final class CountingFailureAccess extends InternalFutureFailureAccess {
        private final Throwable failure;
        private final RuntimeException exceptionToThrow;
        private final AtomicInteger invocationCount = new AtomicInteger();

        private CountingFailureAccess(Throwable failure, RuntimeException exceptionToThrow) {
            this.failure = failure;
            this.exceptionToThrow = exceptionToThrow;
        }

        @Override
        protected Throwable tryInternalFastPathGetFailure() {
            invocationCount.incrementAndGet();
            if (exceptionToThrow != null) {
                throw exceptionToThrow;
            }
            return failure;
        }

        private int invocationCount() {
            return invocationCount.get();
        }
    }

    private static final class CompletedFutureFailureAccess extends InternalFutureFailureAccess implements Future<Object> {
        private final Throwable failure;
        private final boolean cancelled;

        private CompletedFutureFailureAccess(Throwable failure, boolean cancelled) {
            this.failure = failure;
            this.cancelled = cancelled;
        }

        private static CompletedFutureFailureAccess failed(Throwable failure) {
            return new CompletedFutureFailureAccess(failure, false);
        }

        private static CompletedFutureFailureAccess cancelled() {
            return new CompletedFutureFailureAccess(null, true);
        }

        @Override
        protected Throwable tryInternalFastPathGetFailure() {
            return failure;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return false;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public boolean isDone() {
            return true;
        }

        @Override
        public Object get() throws ExecutionException {
            if (cancelled) {
                throw new CancellationException("cancelled");
            }
            if (failure != null) {
                throw new ExecutionException(failure);
            }
            return null;
        }

        @Override
        public Object get(long timeout, TimeUnit unit) throws ExecutionException, TimeoutException {
            return get();
        }
    }
}
