/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_failsafe_failsafe;

import dev.failsafe.Failsafe;
import dev.failsafe.FailsafeExecutor;
import dev.failsafe.RetryPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/// Exercises Failsafe's CompletionStage integration subsystem while a catalog client retries an asynchronous lookup.
public class CompletionStageCatalogTest {

    @Test
    @Timeout(60)
    void retriesAnAsynchronousCatalogLookupAfterACompletedFailure() throws Exception {
        RetryPolicy<String> retryPolicy = RetryPolicy.<String>builder()
                .withMaxRetries(1)
                .build();
        FailsafeExecutor<String> failsafe = Failsafe.with(retryPolicy);
        CatalogClient catalog = new CatalogClient();

        CompletableFuture<String> lookup = failsafe.getStageAsync(
                () -> catalog.find("sku-42"));

        assertThat(lookup.get(10, TimeUnit.SECONDS)).isEqualTo("product:sku-42");
        assertThat(catalog.calls()).isEqualTo(2);
    }

    private static final class CatalogClient {
        private final AtomicInteger calls = new AtomicInteger();

        CompletionStage<String> find(String sku) {
            int call = calls.incrementAndGet();
            CompletableFuture<String> response = new CompletableFuture<>();
            if (call == 1) {
                response.completeExceptionally(new IllegalStateException("catalog unavailable"));
            } else {
                response.complete("product:" + sku);
            }
            return response;
        }

        int calls() {
            return calls.get();
        }
    }
}
