/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_failsafe_failsafe;

import dev.failsafe.Failsafe;
import dev.failsafe.FailsafeExecutor;
import dev.failsafe.Fallback;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/// Exercises Failsafe's asynchronous fallback subsystem while a recommendation service falls back to a cached suggestion after a primary failure.
public class AsyncFallbackRecommendationTest {

    @Test
    @Timeout(60)
    void servesAnAsynchronousRecommendationAfterThePrimaryServiceFails() throws Exception {
        ExecutorService fallbackExecutor = Executors.newSingleThreadExecutor();
        try {
            RecommendationService recommendations = new RecommendationService();
            Fallback<String> fallback = Fallback.ofStage(() ->
                    CompletableFuture.supplyAsync(() -> "cached:sku-42", fallbackExecutor));
            FailsafeExecutor<String> failsafe = Failsafe.with(fallback);

            CompletableFuture<String> recommendation = failsafe.getStageAsync(
                    () -> recommendations.find("sku-42"));

            assertThat(recommendation.get(10, TimeUnit.SECONDS)).isEqualTo("cached:sku-42");
            assertThat(recommendations.primaryCalls()).isEqualTo(1);
        } finally {
            fallbackExecutor.shutdownNow();
            assertThat(fallbackExecutor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static final class RecommendationService {
        private final AtomicInteger primaryCalls = new AtomicInteger();

        CompletableFuture<String> find(String sku) {
            primaryCalls.incrementAndGet();
            CompletableFuture<String> response = new CompletableFuture<>();
            response.completeExceptionally(new IllegalStateException("recommendation service unavailable"));
            return response;
        }

        int primaryCalls() {
            return primaryCalls.get();
        }
    }
}
