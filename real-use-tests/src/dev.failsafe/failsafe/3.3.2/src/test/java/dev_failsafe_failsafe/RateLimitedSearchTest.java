/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_failsafe_failsafe;

import dev.failsafe.Failsafe;
import dev.failsafe.FailsafeExecutor;
import dev.failsafe.RateLimitExceededException;
import dev.failsafe.RateLimiter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Exercises Failsafe's rate limiter subsystem while a product search client limits request admission.
public class RateLimitedSearchTest {

    @Test
    @Timeout(60)
    void admitsOnlyOneSearchDuringTheConfiguredPeriod() {
        RateLimiter<String> rateLimiter = RateLimiter.<String>smoothBuilder(1, Duration.ofMinutes(1))
                .withMaxWaitTime(Duration.ZERO)
                .build();
        FailsafeExecutor<String> failsafe = Failsafe.with(rateLimiter);
        SearchClient search = new SearchClient();

        assertThat(failsafe.get(() -> search.find("keyboard"))).isEqualTo("results:keyboard");
        assertThatThrownBy(() -> failsafe.get(() -> search.find("monitor")))
                .isInstanceOf(RateLimitExceededException.class);
        assertThat(search.calls()).isEqualTo(1);
    }

    private static final class SearchClient {
        private final AtomicInteger calls = new AtomicInteger();

        String find(String query) {
            calls.incrementAndGet();
            return "results:" + query;
        }

        int calls() {
            return calls.get();
        }
    }
}
