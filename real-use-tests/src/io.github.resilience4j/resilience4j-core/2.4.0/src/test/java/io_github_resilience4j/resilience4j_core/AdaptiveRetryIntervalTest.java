/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_github_resilience4j.resilience4j_core;

import io.github.resilience4j.core.IntervalBiFunction;
import io.github.resilience4j.core.functions.Either;
import org.junit.jupiter.api.Test;

import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chooses retry delays from both attempt numbers and outcomes through Resilience4j's adaptive
 * interval-function subsystem.
 *
 * A checkout client retries timed-out requests with progressively longer delays, but does not
 * schedule another retry after a successful response.
 */
class AdaptiveRetryIntervalTest {

    @Test
    void schedulesTimeoutRetriesButStopsAfterSuccess() {
        IntervalBiFunction<String> retryInterval = (attempt, outcome) -> {
            if (outcome.isRight()) {
                return 0L;
            }
            return 100L * attempt;
        };
        Either<Throwable, String> timedOut = Either.left(new SocketTimeoutException("checkout"));
        Either<Throwable, String> completed = Either.right("confirmed");

        assertThat(retryInterval.apply(1, timedOut)).isEqualTo(100L);
        assertThat(retryInterval.apply(3, timedOut)).isEqualTo(300L);
        assertThat(retryInterval.apply(2, completed)).isZero();
    }
}
