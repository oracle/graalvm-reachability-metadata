/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package software_amazon_awssdk.retries;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.retries.AdaptiveRetryStrategy;
import software.amazon.awssdk.retries.DefaultRetryStrategy;
import software.amazon.awssdk.retries.LegacyRetryStrategy;
import software.amazon.awssdk.retries.StandardRetryStrategy;
import software.amazon.awssdk.retries.api.AcquireInitialTokenRequest;
import software.amazon.awssdk.retries.api.AcquireInitialTokenResponse;
import software.amazon.awssdk.retries.api.BackoffStrategy;
import software.amazon.awssdk.retries.api.RecordSuccessRequest;
import software.amazon.awssdk.retries.api.RecordSuccessResponse;
import software.amazon.awssdk.retries.api.RefreshRetryTokenRequest;
import software.amazon.awssdk.retries.api.RefreshRetryTokenResponse;
import software.amazon.awssdk.retries.api.RetryStrategy;
import software.amazon.awssdk.retries.api.RetryToken;
import software.amazon.awssdk.retries.api.TokenAcquisitionFailedException;

public class RetriesTest {
    private static final String SCOPE = "test-scope";

    @Test
    void standardStrategyRetriesMatchingFailuresAndRecordsSuccess() {
        StandardRetryStrategy strategy = StandardRetryStrategy.builder()
            .maxAttempts(3)
            .retryOnExceptionInstanceOf(IllegalStateException.class)
            .backoffStrategy(BackoffStrategy.fixedDelayWithoutJitter(Duration.ofMillis(7)))
            .throttlingBackoffStrategy(BackoffStrategy.fixedDelayWithoutJitter(Duration.ofMillis(31)))
            .circuitBreakerEnabled(false)
            .build();

        AcquireInitialTokenResponse initial = strategy.acquireInitialToken(AcquireInitialTokenRequest.create(SCOPE));
        assertThat(initial.delay()).isEqualTo(Duration.ZERO);
        assertThat(initial.token()).isNotNull();

        RefreshRetryTokenResponse retry = strategy.refreshRetryToken(
            refreshRequest(initial.token(), new IllegalStateException("retryable"))
                .suggestedDelay(Duration.ofMillis(2))
                .build());
        assertThat(retry.delay()).isEqualTo(Duration.ofMillis(7));
        assertThat(retry.token()).isNotSameAs(initial.token());

        RecordSuccessResponse success = strategy.recordSuccess(RecordSuccessRequest.create(retry.token()));
        assertThat(success.token()).isNotNull();
        assertThat(success.token()).isNotSameAs(retry.token());
    }

    @Test
    void suggestedDelayOverridesComputedBackoffWhenItIsLonger() {
        RetryStrategy strategy = DefaultRetryStrategy.standardStrategyBuilder()
            .maxAttempts(2)
            .retryOnExceptionInstanceOf(RuntimeException.class)
            .backoffStrategy(BackoffStrategy.fixedDelayWithoutJitter(Duration.ofMillis(10)))
            .circuitBreakerEnabled(false)
            .build();
        RetryToken token = strategy.acquireInitialToken(AcquireInitialTokenRequest.create(SCOPE)).token();

        RefreshRetryTokenResponse retry = strategy.refreshRetryToken(
            refreshRequest(token, new RuntimeException("retryable"))
                .suggestedDelay(Duration.ofMillis(25))
                .build());

        assertThat(retry.delay()).isEqualTo(Duration.ofMillis(25));
    }

    @Test
    void nonRetryableFailureIncludesFailedTokenAndCause() {
        RetryStrategy strategy = StandardRetryStrategy.builder()
            .maxAttempts(3)
            .retryOnException(IllegalArgumentException.class)
            .circuitBreakerEnabled(false)
            .build();
        RetryToken token = strategy.acquireInitialToken(AcquireInitialTokenRequest.create(SCOPE)).token();
        IllegalStateException failure = new IllegalStateException("not configured for retry");

        assertThatExceptionOfType(TokenAcquisitionFailedException.class)
            .isThrownBy(() -> strategy.refreshRetryToken(refreshRequest(token, failure).build()))
            .withCause(failure)
            .satisfies(exception -> assertThat(exception.token()).isNotNull())
            .withMessageContaining("non-retryable");
    }

    @Test
    void maxAttemptsLimitStopsFurtherRefreshes() {
        RetryStrategy strategy = StandardRetryStrategy.builder()
            .maxAttempts(2)
            .retryOnExceptionInstanceOf(RuntimeException.class)
            .backoffStrategy(BackoffStrategy.retryImmediately())
            .circuitBreakerEnabled(false)
            .build();
        RetryToken initialToken = strategy.acquireInitialToken(AcquireInitialTokenRequest.create(SCOPE)).token();
        RefreshRetryTokenResponse secondAttempt = strategy.refreshRetryToken(
            refreshRequest(initialToken, new RuntimeException("first failure")).build());
        RuntimeException finalFailure = new RuntimeException("second failure");

        assertThatExceptionOfType(TokenAcquisitionFailedException.class)
            .isThrownBy(() -> strategy.refreshRetryToken(refreshRequest(secondAttempt.token(), finalFailure).build()))
            .withCause(finalFailure)
            .satisfies(exception -> assertThat(exception.token()).isNotNull())
            .withMessageContaining("Retries have been exhausted");
    }

    @Test
    void doNotRetryStrategyAllowsOnlyTheInitialAttempt() {
        StandardRetryStrategy strategy = DefaultRetryStrategy.doNotRetry();
        RetryToken token = strategy.acquireInitialToken(AcquireInitialTokenRequest.create(SCOPE)).token();

        assertThat(strategy.maxAttempts()).isEqualTo(1);
        assertThatThrownBy(() -> strategy.refreshRetryToken(
            refreshRequest(token, new RuntimeException("failure")).build()))
            .isInstanceOf(TokenAcquisitionFailedException.class);
    }

    @Test
    void circuitBreakerBlocksRetriesWhenScopedCapacityIsExhaustedAndSuccessRestoresCapacity() {
        StandardRetryStrategy strategy = StandardRetryStrategy.builder()
            .maxAttempts(2)
            .retryOnExceptionInstanceOf(RuntimeException.class)
            .backoffStrategy(BackoffStrategy.retryImmediately())
            .circuitBreakerEnabled(true)
            .build();
        String scope = "circuit-breaker-scope";
        RetryToken tokenThatAcquiredCapacity = null;
        TokenAcquisitionFailedException blockedRetry = null;

        for (int attempt = 0; attempt < 200 && blockedRetry == null; attempt++) {
            RetryToken initialToken = strategy.acquireInitialToken(AcquireInitialTokenRequest.create(scope)).token();
            RuntimeException failure = new RuntimeException("retryable-" + attempt);
            try {
                tokenThatAcquiredCapacity = strategy.refreshRetryToken(refreshRequest(initialToken, failure).build())
                    .token();
            } catch (TokenAcquisitionFailedException e) {
                assertThat(e).hasCause(failure);
                blockedRetry = e;
            }
        }

        assertThat(tokenThatAcquiredCapacity).isNotNull();
        assertThat(blockedRetry).isNotNull()
            .hasMessageContaining("protect the caller")
            .satisfies(exception -> assertThat(exception.token()).isNotNull());

        RetryToken restoredCapacityToken = strategy.recordSuccess(RecordSuccessRequest.create(tokenThatAcquiredCapacity))
            .token();
        assertThat(restoredCapacityToken).isNotNull();

        RetryToken initialTokenAfterSuccess = strategy.acquireInitialToken(AcquireInitialTokenRequest.create(scope))
            .token();
        RefreshRetryTokenResponse retryAfterSuccess = strategy.refreshRetryToken(
            refreshRequest(initialTokenAfterSuccess, new RuntimeException("after success")).build());
        assertThat(retryAfterSuccess.token()).isNotNull();
    }

    @Test
    void legacyStrategyUsesThrottlingBackoffForFailuresMarkedAsThrottling() {
        LegacyRetryStrategy strategy = LegacyRetryStrategy.builder()
            .maxAttempts(3)
            .retryOnExceptionInstanceOf(RuntimeException.class)
            .treatAsThrottling(ThrottlingFailure.class::isInstance)
            .backoffStrategy(BackoffStrategy.fixedDelayWithoutJitter(Duration.ofMillis(3)))
            .throttlingBackoffStrategy(BackoffStrategy.fixedDelayWithoutJitter(Duration.ofMillis(8)))
            .circuitBreakerEnabled(false)
            .build();

        RetryToken normalToken = strategy.acquireInitialToken(AcquireInitialTokenRequest.create("normal")).token();
        RefreshRetryTokenResponse normalRetry = strategy.refreshRetryToken(
            refreshRequest(normalToken, new RuntimeException("normal")).build());
        assertThat(normalRetry.delay()).isEqualTo(Duration.ofMillis(3));

        RetryToken throttledToken = strategy.acquireInitialToken(AcquireInitialTokenRequest.create("throttled"))
            .token();
        RefreshRetryTokenResponse throttledRetry = strategy.refreshRetryToken(
            refreshRequest(throttledToken, new ThrottlingFailure()).build());
        assertThat(throttledRetry.delay()).isEqualTo(Duration.ofMillis(8));
    }

    @Test
    void adaptiveStrategyAppliesConfiguredBackoffAndCanBeCopied() {
        AdaptiveRetryStrategy strategy = AdaptiveRetryStrategy.builder()
            .maxAttempts(4)
            .retryOnExceptionInstanceOf(RuntimeException.class)
            .backoffStrategy(BackoffStrategy.fixedDelayWithoutJitter(Duration.ofMillis(4)))
            .throttlingBackoffStrategy(BackoffStrategy.fixedDelayWithoutJitter(Duration.ofMillis(9)))
            .treatAsThrottling(ThrottlingFailure.class::isInstance)
            .useClientDefaults(false)
            .build();

        assertThat(strategy.maxAttempts()).isEqualTo(4);
        assertThat(strategy.useClientDefaults()).isFalse();
        RetryToken token = strategy.acquireInitialToken(AcquireInitialTokenRequest.create(SCOPE)).token();
        RefreshRetryTokenResponse retry = strategy.refreshRetryToken(
            refreshRequest(token, new RuntimeException("normal")).build());
        assertThat(retry.delay()).isGreaterThanOrEqualTo(Duration.ofMillis(4));

        AdaptiveRetryStrategy copied = strategy.toBuilder().maxAttempts(2).build();
        assertThat(copied.maxAttempts()).isEqualTo(2);
        assertThat(copied.useClientDefaults()).isFalse();
    }

    @Test
    void adaptiveStrategyAddsRateLimiterDelayAfterThrottlingFailures() {
        AdaptiveRetryStrategy strategy = AdaptiveRetryStrategy.builder()
            .maxAttempts(2)
            .retryOnExceptionInstanceOf(RuntimeException.class)
            .backoffStrategy(BackoffStrategy.retryImmediately())
            .throttlingBackoffStrategy(BackoffStrategy.retryImmediately())
            .treatAsThrottling(ThrottlingFailure.class::isInstance)
            .useClientDefaults(false)
            .build();

        RetryToken token = strategy.acquireInitialToken(AcquireInitialTokenRequest.create("adaptive-rate-limited"))
            .token();
        RefreshRetryTokenResponse retry = strategy.refreshRetryToken(
            refreshRequest(token, new ThrottlingFailure()).build());

        assertThat(retry.token()).isNotNull();
        assertThat(retry.delay()).isGreaterThan(Duration.ZERO);
        assertThat(strategy.acquireInitialToken(AcquireInitialTokenRequest.create("adaptive-independent"))
            .delay()).isEqualTo(Duration.ZERO);
    }

    @Test
    void retryPredicateConvenienceMethodsHandleExactTypeCauseAndRootCause() {
        assertRefreshSucceeds(StandardRetryStrategy.builder()
            .maxAttempts(2)
            .retryOnException(ExactFailure.class)
            .circuitBreakerEnabled(false)
            .build(), new ExactFailure());

        assertRefreshFails(StandardRetryStrategy.builder()
            .maxAttempts(2)
            .retryOnException(ExactFailure.class)
            .circuitBreakerEnabled(false)
            .build(), new ExactFailureSubclass());

        assertRefreshSucceeds(StandardRetryStrategy.builder()
            .maxAttempts(2)
            .retryOnExceptionOrCause(ExactFailure.class)
            .circuitBreakerEnabled(false)
            .build(), new RuntimeException(new ExactFailure()));

        assertRefreshSucceeds(StandardRetryStrategy.builder()
            .maxAttempts(2)
            .retryOnRootCauseInstanceOf(ExactFailure.class)
            .circuitBreakerEnabled(false)
            .build(), new RuntimeException(new IllegalStateException(new ExactFailureSubclass())));
    }

    @Test
    void requestFactoriesAndCopyBuilderPreserveValues() {
        RetryToken token = StandardRetryStrategy.builder()
            .maxAttempts(2)
            .retryOnExceptionInstanceOf(RuntimeException.class)
            .circuitBreakerEnabled(false)
            .build()
            .acquireInitialToken(AcquireInitialTokenRequest.create(SCOPE))
            .token();
        RuntimeException originalFailure = new RuntimeException("original");
        RefreshRetryTokenRequest original = RefreshRetryTokenRequest.builder()
            .token(token)
            .failure(originalFailure)
            .suggestedDelay(Duration.ofMillis(6))
            .build();

        RefreshRetryTokenRequest copied = original.toBuilder()
            .suggestedDelay(Duration.ofMillis(12))
            .build();

        assertThat(AcquireInitialTokenRequest.create(SCOPE).scope()).isEqualTo(SCOPE);
        assertThat(original.token()).isSameAs(token);
        assertThat(original.failure()).isSameAs(originalFailure);
        assertThat(original.suggestedDelay()).contains(Duration.ofMillis(6));
        assertThat(copied.token()).isSameAs(token);
        assertThat(copied.failure()).isSameAs(originalFailure);
        assertThat(copied.suggestedDelay()).contains(Duration.ofMillis(12));
    }

    @Test
    void backoffStrategiesComputeExpectedDelaysAndRanges() {
        assertThat(BackoffStrategy.retryImmediately().computeDelay(1)).isEqualTo(Duration.ZERO);
        assertThat(BackoffStrategy.retryImmediately().computeDelay(10)).isEqualTo(Duration.ZERO);

        BackoffStrategy fixed = BackoffStrategy.fixedDelayWithoutJitter(Duration.ofMillis(13));
        assertThat(fixed.computeDelay(1)).isEqualTo(Duration.ofMillis(13));
        assertThat(fixed.computeDelay(4)).isEqualTo(Duration.ofMillis(13));

        BackoffStrategy fixedWithJitter = BackoffStrategy.fixedDelay(Duration.ofMillis(20));
        assertThat(fixedWithJitter.computeDelay(1)).isBetween(Duration.ZERO, Duration.ofMillis(19));
        assertThat(fixedWithJitter.computeDelay(4)).isBetween(Duration.ZERO, Duration.ofMillis(19));

        BackoffStrategy exponential = BackoffStrategy.exponentialDelayWithoutJitter(
            Duration.ofMillis(5), Duration.ofMillis(18));
        assertThat(exponential.computeDelay(1)).isEqualTo(Duration.ZERO);
        assertThat(exponential.computeDelay(2)).isEqualTo(Duration.ofMillis(5));
        assertThat(exponential.computeDelay(3)).isEqualTo(Duration.ofMillis(10));
        assertThat(exponential.computeDelay(4)).isEqualTo(Duration.ofMillis(18));
        assertThat(exponential.computeDelay(40)).isEqualTo(Duration.ofMillis(18));

        BackoffStrategy exponentialWithJitter = BackoffStrategy.exponentialDelay(
            Duration.ofMillis(10), Duration.ofMillis(100));
        assertThat(exponentialWithJitter.computeDelay(1)).isEqualTo(Duration.ZERO);
        assertThat(exponentialWithJitter.computeDelay(2)).isBetween(Duration.ZERO, Duration.ofMillis(9));

        BackoffStrategy halfJitter = BackoffStrategy.exponentialDelayHalfJitter(
            Duration.ofMillis(10), Duration.ofMillis(100));
        assertThat(halfJitter.computeDelay(1)).isEqualTo(Duration.ZERO);
        assertThat(halfJitter.computeDelay(2)).isBetween(Duration.ofMillis(5), Duration.ofMillis(10));
    }

    @Test
    void invalidArgumentsAreRejectedByPublicFactoriesAndBuilders() {
        assertThatThrownBy(() -> AcquireInitialTokenRequest.create(null))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> RefreshRetryTokenRequest.builder().failure(new RuntimeException()).build())
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> RefreshRetryTokenRequest.builder()
            .token(new ForeignRetryToken())
            .failure(new RuntimeException())
            .suggestedDelay(Duration.ofMillis(-1))
            .build())
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BackoffStrategy.retryImmediately().computeDelay(0))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BackoffStrategy.fixedDelayWithoutJitter(Duration.ZERO))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StandardRetryStrategy.builder().maxAttempts(0).build())
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void tokensFromForeignStrategiesAreRejected() {
        RetryToken foreignToken = new ForeignRetryToken();
        RetryStrategy strategy = StandardRetryStrategy.builder()
            .maxAttempts(2)
            .retryOnExceptionInstanceOf(RuntimeException.class)
            .circuitBreakerEnabled(false)
            .build();

        assertThatThrownBy(() -> strategy.refreshRetryToken(
            refreshRequest(foreignToken, new RuntimeException("failure")).build()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("RetryToken is of unexpected class");
        assertThatThrownBy(() -> strategy.recordSuccess(RecordSuccessRequest.create(foreignToken)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("RetryToken is of unexpected class");
    }

    private static RefreshRetryTokenRequest.Builder refreshRequest(RetryToken token, Throwable failure) {
        return RefreshRetryTokenRequest.builder()
            .token(token)
            .failure(failure);
    }

    private static void assertRefreshSucceeds(RetryStrategy strategy, Throwable failure) {
        RetryToken token = strategy.acquireInitialToken(
            AcquireInitialTokenRequest.create("success-" + failure.getClass().getName()))
            .token();
        assertThat(strategy.refreshRetryToken(refreshRequest(token, failure).build()).token()).isNotNull();
    }

    private static void assertRefreshFails(RetryStrategy strategy, Throwable failure) {
        RetryToken token = strategy.acquireInitialToken(
            AcquireInitialTokenRequest.create("failure-" + failure.getClass().getName()))
            .token();
        assertThatThrownBy(() -> strategy.refreshRetryToken(refreshRequest(token, failure).build()))
            .isInstanceOf(TokenAcquisitionFailedException.class);
    }

    private static final class ForeignRetryToken implements RetryToken {
    }

    private static class ExactFailure extends RuntimeException {
    }

    private static final class ExactFailureSubclass extends ExactFailure {
    }

    private static final class ThrottlingFailure extends RuntimeException {
    }
}
