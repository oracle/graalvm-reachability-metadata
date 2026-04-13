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
}
