/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_retry.spring_retry;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.retry.support.RetryTemplate;

import static org.assertj.core.api.Assertions.assertThat;

public class Spring_retryTest {
    @Test
    void retriesTransientFailuresAndReturnsTheSuccessfulResult() {
        RetryTemplate template = RetryTemplate.builder()
                .maxAttempts(3)
                .noBackoff()
                .retryOn(IllegalStateException.class)
                .build();
        AtomicInteger attempts = new AtomicInteger();

        String result = template.execute(context -> {
            int attempt = attempts.incrementAndGet();
            assertThat(context.getRetryCount()).isEqualTo(attempt - 1);
            if (attempt < 3) {
                throw new IllegalStateException("Service temporarily unavailable");
            }
            return "invoice-42";
        });

        assertThat(result).isEqualTo("invoice-42");
        assertThat(attempts.get()).isEqualTo(3);
    }
}
