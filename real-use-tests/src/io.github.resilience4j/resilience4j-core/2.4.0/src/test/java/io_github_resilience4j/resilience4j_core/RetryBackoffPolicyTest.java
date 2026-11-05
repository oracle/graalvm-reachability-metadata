/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_github_resilience4j.resilience4j_core;

import io.github.resilience4j.core.IntervalFunction;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Calculates retry backoff intervals with Resilience4j's interval-function subsystem.
 *
 * A remote service client grows the delay between failed attempts and caps it at its configured
 * maximum, preventing a burst of retries while keeping the retry policy bounded.
 */
class RetryBackoffPolicyTest {

    @Test
    void growsAndCapsRetryDelaysForAClient() {
        IntervalFunction retryDelay = IntervalFunction.ofExponentialBackoff(
                Duration.ofMillis(100), 2.0, Duration.ofMillis(500));

        List<Long> retryDelays = List.of(
                retryDelay.apply(1),
                retryDelay.apply(2),
                retryDelay.apply(3),
                retryDelay.apply(4));

        assertThat(retryDelays).containsExactly(100L, 200L, 400L, 500L);
    }
}
