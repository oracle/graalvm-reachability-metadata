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
import java.util.concurrent.atomic.AtomicBoolean;
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
    void standardStrategyUsesThrottlingBackoffForFailuresMarkedAsThrottling() {
        StandardRetryStrategy strategy = StandardRetryStrategy.builder()
            .maxAttempts(2)
            .retryOnExceptionInstanceOf(RuntimeException.class)
            .treatAsThrottling(ThrottlingFailure.class::isInstance)
            .backoffStrategy(BackoffStrategy.fixedDelayWithoutJitter(Duration.ofMillis(3)))
            .throttlingBackoffStrategy(BackoffStrategy.fixedDelayWithoutJitter(Duration.ofMillis(29)))
            .circuitBreakerEnabled(false)
            .build();

        RetryToken token = strategy.acquireInitialToken(AcquireInitialTokenRequest.create("standard-throttled"))
            .token();
        RefreshRetryTokenResponse retry = strategy.refreshRetryToken(
            refreshRequest(token, new ThrottlingFailure()).build());

        assertThat(retry.delay()).isEqualTo(Duration.ofMillis(29));
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
    void circuitBreakerCapacityIsIsolatedByRequestScope() {
        StandardRetryStrategy strategy = StandardRetryStrategy.builder()
            .maxAttempts(2)
            .retryOnExceptionInstanceOf(RuntimeException.class)
            .backoffStrategy(BackoffStrategy.retryImmediately())
            .circuitBreakerEnabled(true)
            .build();
        String exhaustedScope = "exhausted-scope";
        TokenAcquisitionFailedException blockedRetry = null;

        for (int attempt = 0; attempt < 200 && blockedRetry == null; attempt++) {
            RetryToken token = strategy.acquireInitialToken(AcquireInitialTokenRequest.create(exhaustedScope)).token();
            RuntimeException failure = new RuntimeException("scope failure " + attempt);
            try {
                strategy.refreshRetryToken(refreshRequest(token, failure).build());
            } catch (TokenAcquisitionFailedException e) {
                assertThat(e).hasCause(failure);
                blockedRetry = e;
            }
        }

        assertThat(blockedRetry).isNotNull()
            .hasMessageContaining("protect the caller");

        RetryToken independentScopeToken = strategy.acquireInitialToken(AcquireInitialTokenRequest.create(
            "independent-scope")).token();
        RefreshRetryTokenResponse retry = strategy.refreshRetryToken(
            refreshRequest(independentScopeToken, new RuntimeException("independent failure")).build());

        assertThat(retry.token()).isNotNull();
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
    void legacyStrategyDoesNotSpendCircuitBreakerCapacityForThrottlingFailures() {
        LegacyRetryStrategy strategy = LegacyRetryStrategy.builder()
            .maxAttempts(2)
            .retryOnExceptionInstanceOf(RuntimeException.class)
            .treatAsThrottling(ThrottlingFailure.class::isInstance)
            .backoffStrategy(BackoffStrategy.retryImmediately())
            .throttlingBackoffStrategy(BackoffStrategy.retryImmediately())
            .circuitBreakerEnabled(true)
            .build();
        String throttledScope = "legacy-throttling-capacity";

        for (int attempt = 0; attempt < 120; attempt++) {
            RetryToken token = strategy.acquireInitialToken(AcquireInitialTokenRequest.create(throttledScope)).token();
            RefreshRetryTokenResponse retry = strategy.refreshRetryToken(
                refreshRequest(token, new ThrottlingFailure()).build());
            assertThat(retry.token()).isNotNull();
        }

        TokenAcquisitionFailedException blockedRetry = null;
        for (int attempt = 0; attempt < 200 && blockedRetry == null; attempt++) {
            RetryToken token = strategy.acquireInitialToken(AcquireInitialTokenRequest.create("legacy-normal-capacity"))
                .token();
            RuntimeException failure = new RuntimeException("normal-" + attempt);
            try {
                strategy.refreshRetryToken(refreshRequest(token, failure).build());
            } catch (TokenAcquisitionFailedException e) {
                assertThat(e).hasCause(failure);
                blockedRetry = e;
            }
        }

        assertThat(blockedRetry).isNotNull()
            .hasMessageContaining("protect the caller")
            .satisfies(exception -> assertThat(exception.token()).isNotNull());
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
    void adaptiveStrategyRateLimitsInitialAttemptsWithinThrottledScope() {
        AdaptiveRetryStrategy strategy = AdaptiveRetryStrategy.builder()
            .maxAttempts(2)
            .retryOnExceptionInstanceOf(RuntimeException.class)
            .backoffStrategy(BackoffStrategy.retryImmediately())
            .throttlingBackoffStrategy(BackoffStrategy.retryImmediately())
            .treatAsThrottling(ThrottlingFailure.class::isInstance)
            .useClientDefaults(false)
            .build();
        String scope = "adaptive-throttled-initial-attempt";
        RetryToken firstAttemptToken = strategy.acquireInitialToken(AcquireInitialTokenRequest.create(scope)).token();

        RefreshRetryTokenResponse throttledRetry = strategy.refreshRetryToken(
            refreshRequest(firstAttemptToken, new ThrottlingFailure()).build());
        AcquireInitialTokenResponse nextInitialAttempt = strategy.acquireInitialToken(
            AcquireInitialTokenRequest.create(scope));

        assertThat(throttledRetry.token()).isNotNull();
        assertThat(nextInitialAttempt.token()).isNotNull();
        assertThat(nextInitialAttempt.delay()).isGreaterThan(Duration.ZERO);
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
    void customRetryPredicatesCanUseFailureStateAndAreAdditive() {
        RetryStrategy strategy = StandardRetryStrategy.builder()
            .maxAttempts(2)
            .retryOnException(failure -> failure instanceof IllegalStateException
                && failure.getMessage().contains("transient"))
            .retryOnException(failure -> failure instanceof UnsupportedOperationException)
            .backoffStrategy(BackoffStrategy.retryImmediately())
            .circuitBreakerEnabled(false)
            .build();

        assertRefreshSucceeds(strategy, new IllegalStateException("transient connection reset"));
        assertRefreshSucceeds(strategy, new UnsupportedOperationException("retryable by second predicate"));
        assertRefreshFails(strategy, new IllegalStateException("permanent validation failure"));
    }

    @Test
    void throttlingPredicateIsNotEvaluatedForNonRetryableFailures() {
        AtomicBoolean throttlingPredicateCalled = new AtomicBoolean(false);
        RetryStrategy strategy = StandardRetryStrategy.builder()
            .maxAttempts(2)
            .retryOnException(IllegalStateException.class)
            .treatAsThrottling(failure -> {
                throttlingPredicateCalled.set(true);
                return true;
            })
            .throttlingBackoffStrategy(BackoffStrategy.fixedDelayWithoutJitter(Duration.ofMillis(17)))
            .circuitBreakerEnabled(false)
            .build();
        RetryToken token = strategy.acquireInitialToken(AcquireInitialTokenRequest.create("non-retryable-throttling"))
            .token();
        IllegalArgumentException failure = new IllegalArgumentException("not retryable");

        assertThatExceptionOfType(TokenAcquisitionFailedException.class)
            .isThrownBy(() -> strategy.refreshRetryToken(refreshRequest(token, failure).build()))
            .withCause(failure)
            .withMessageContaining("non-retryable");
        assertThat(throttlingPredicateCalled).isFalse();
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

    @Test
    void responseFactoriesReturnProvidedTokensAndDelays() {
        RetryToken token = new ForeignRetryToken();

        AcquireInitialTokenResponse initialResponse = AcquireInitialTokenResponse.create(token, Duration.ofMillis(1));
        RefreshRetryTokenResponse retryResponse = RefreshRetryTokenResponse.create(token, Duration.ofMillis(2));
        RecordSuccessResponse successResponse = RecordSuccessResponse.create(token);

        assertThat(initialResponse.token()).isSameAs(token);
        assertThat(initialResponse.delay()).isEqualTo(Duration.ofMillis(1));
        assertThat(retryResponse.token()).isSameAs(token);
        assertThat(retryResponse.delay()).isEqualTo(Duration.ofMillis(2));
        assertThat(successResponse.token()).isSameAs(token);
    }

    @Test
    void tokenAcquisitionFailureConstructorsExposeMessageCauseAndOptionalToken() {
        RetryToken token = new ForeignRetryToken();
        RuntimeException cause = new RuntimeException("cause");

        TokenAcquisitionFailedException messageOnly = new TokenAcquisitionFailedException("message only");
        TokenAcquisitionFailedException messageAndCause = new TokenAcquisitionFailedException(
            "message and cause", cause);
        TokenAcquisitionFailedException messageTokenAndCause = new TokenAcquisitionFailedException(
            "message token and cause", token, cause);

        assertThat(messageOnly).hasMessage("message only").hasNoCause();
        assertThat(messageOnly.token()).isNull();
        assertThat(messageAndCause).hasMessage("message and cause").hasCause(cause);
        assertThat(messageAndCause.token()).isNull();
        assertThat(messageTokenAndCause).hasMessage("message token and cause").hasCause(cause);
        assertThat(messageTokenAndCause.token()).isSameAs(token);
    }

    @Test
    void retryPredicateInstanceConvenienceMethodsAcceptSubclassesInExceptionChain() {
        assertRefreshSucceeds(StandardRetryStrategy.builder()
            .maxAttempts(2)
            .retryOnExceptionInstanceOf(ExactFailure.class)
            .circuitBreakerEnabled(false)
            .build(), new ExactFailureSubclass());

        assertRefreshSucceeds(StandardRetryStrategy.builder()
            .maxAttempts(2)
            .retryOnExceptionOrCauseInstanceOf(ExactFailure.class)
            .circuitBreakerEnabled(false)
            .build(), new RuntimeException(new ExactFailureSubclass()));

        assertRefreshFails(StandardRetryStrategy.builder()
            .maxAttempts(2)
            .retryOnRootCause(ExactFailure.class)
            .circuitBreakerEnabled(false)
            .build(), new RuntimeException(new ExactFailureSubclass()));
    }

    @Test
    void defaultBuildersCreateUsableLegacyAndAdaptiveStrategies() {
        LegacyRetryStrategy legacy = DefaultRetryStrategy.legacyStrategyBuilder()
            .maxAttempts(2)
            .retryOnExceptionInstanceOf(RuntimeException.class)
            .backoffStrategy(BackoffStrategy.fixedDelayWithoutJitter(Duration.ofMillis(5)))
            .throttlingBackoffStrategy(BackoffStrategy.fixedDelayWithoutJitter(Duration.ofMillis(11)))
            .treatAsThrottling(ThrottlingFailure.class::isInstance)
            .circuitBreakerEnabled(false)
            .build();
        AdaptiveRetryStrategy adaptive = DefaultRetryStrategy.adaptiveStrategyBuilder()
            .maxAttempts(2)
            .retryOnExceptionInstanceOf(RuntimeException.class)
            .backoffStrategy(BackoffStrategy.retryImmediately())
            .throttlingBackoffStrategy(BackoffStrategy.retryImmediately())
            .treatAsThrottling(ThrottlingFailure.class::isInstance)
            .useClientDefaults(false)
            .build();

        assertThat(legacy.maxAttempts()).isEqualTo(2);
        assertThat(legacy.refreshRetryToken(refreshRequest(
            legacy.acquireInitialToken(AcquireInitialTokenRequest.create("default-legacy")).token(),
            new ThrottlingFailure()).build()).delay()).isEqualTo(Duration.ofMillis(11));
        assertThat(adaptive.maxAttempts()).isEqualTo(2);
        assertThat(adaptive.useClientDefaults()).isFalse();
        assertThat(adaptive.refreshRetryToken(refreshRequest(
            adaptive.acquireInitialToken(AcquireInitialTokenRequest.create("default-adaptive")).token(),
            new RuntimeException("retryable")).build()).token()).isNotNull();
    }

    @Test
    void recordingSuccessAfterInitialAttemptReturnsANewToken() {
        StandardRetryStrategy strategy = StandardRetryStrategy.builder()
            .maxAttempts(2)
            .retryOnExceptionInstanceOf(RuntimeException.class)
            .backoffStrategy(BackoffStrategy.retryImmediately())
            .circuitBreakerEnabled(false)
            .build();
        RetryToken initialToken = strategy.acquireInitialToken(AcquireInitialTokenRequest.create("initial-success"))
            .token();

        RetryToken successToken = strategy.recordSuccess(RecordSuccessRequest.create(initialToken)).token();

        assertThat(successToken).isNotNull();
        assertThat(successToken).isNotSameAs(initialToken);
    }

    @Test
    void standardStrategyToBuilderCopiesConfigurationAndUseClientDefaults() {
        StandardRetryStrategy original = StandardRetryStrategy.builder()
            .maxAttempts(4)
            .useClientDefaults(false)
            .retryOnExceptionInstanceOf(RuntimeException.class)
            .backoffStrategy(BackoffStrategy.fixedDelayWithoutJitter(Duration.ofMillis(6)))
            .throttlingBackoffStrategy(BackoffStrategy.fixedDelayWithoutJitter(Duration.ofMillis(18)))
            .treatAsThrottling(ThrottlingFailure.class::isInstance)
            .circuitBreakerEnabled(false)
            .build();

        StandardRetryStrategy copied = original.toBuilder()
            .maxAttempts(3)
            .build();

        assertThat(original.maxAttempts()).isEqualTo(4);
        assertThat(original.useClientDefaults()).isFalse();
        assertThat(copied.maxAttempts()).isEqualTo(3);
        assertThat(copied.useClientDefaults()).isFalse();
        assertThat(refreshDelay(copied, new RuntimeException("normal"), "standard-copy-normal"))
            .isEqualTo(Duration.ofMillis(6));
        assertThat(refreshDelay(copied, new ThrottlingFailure(), "standard-copy-throttled"))
            .isEqualTo(Duration.ofMillis(18));
    }

    @Test
    void legacyStrategyToBuilderCopiesConfigurationAndUseClientDefaults() {
        LegacyRetryStrategy original = LegacyRetryStrategy.builder()
            .maxAttempts(4)
            .useClientDefaults(false)
            .retryOnExceptionInstanceOf(RuntimeException.class)
            .backoffStrategy(BackoffStrategy.fixedDelayWithoutJitter(Duration.ofMillis(5)))
            .throttlingBackoffStrategy(BackoffStrategy.fixedDelayWithoutJitter(Duration.ofMillis(21)))
            .treatAsThrottling(ThrottlingFailure.class::isInstance)
            .circuitBreakerEnabled(false)
            .build();

        LegacyRetryStrategy copied = original.toBuilder()
            .maxAttempts(2)
            .build();

        assertThat(original.maxAttempts()).isEqualTo(4);
        assertThat(original.useClientDefaults()).isFalse();
        assertThat(copied.maxAttempts()).isEqualTo(2);
        assertThat(copied.useClientDefaults()).isFalse();
        assertThat(refreshDelay(copied, new RuntimeException("normal"), "legacy-copy-normal"))
            .isEqualTo(Duration.ofMillis(5));
        assertThat(refreshDelay(copied, new ThrottlingFailure(), "legacy-copy-throttled"))
            .isEqualTo(Duration.ofMillis(21));
    }

    @Test
    void adaptiveStrategyRecordsSuccessAfterThrottledRetry() {
        AdaptiveRetryStrategy strategy = AdaptiveRetryStrategy.builder()
            .maxAttempts(3)
            .retryOnExceptionInstanceOf(RuntimeException.class)
            .backoffStrategy(BackoffStrategy.retryImmediately())
            .throttlingBackoffStrategy(BackoffStrategy.retryImmediately())
            .treatAsThrottling(ThrottlingFailure.class::isInstance)
            .useClientDefaults(false)
            .build();
        RetryToken initialToken = strategy.acquireInitialToken(AcquireInitialTokenRequest.create("adaptive-success"))
            .token();
        RefreshRetryTokenResponse retry = strategy.refreshRetryToken(
            refreshRequest(initialToken, new ThrottlingFailure()).build());

        RecordSuccessResponse success = strategy.recordSuccess(RecordSuccessRequest.create(retry.token()));

        assertThat(success.token()).isNotNull();
        assertThat(success.token()).isNotSameAs(retry.token());
    }

    @Test
    void defaultStrategyBuildersProvideConfiguredRetryBehaviorWithoutCustomBackoff() {
        StandardRetryStrategy standard = DefaultRetryStrategy.standardStrategyBuilder()
            .retryOnExceptionInstanceOf(RuntimeException.class)
            .treatAsThrottling(ThrottlingFailure.class::isInstance)
            .circuitBreakerEnabled(false)
            .build();
        LegacyRetryStrategy legacy = DefaultRetryStrategy.legacyStrategyBuilder()
            .retryOnExceptionInstanceOf(RuntimeException.class)
            .treatAsThrottling(ThrottlingFailure.class::isInstance)
            .circuitBreakerEnabled(false)
            .build();
        AdaptiveRetryStrategy adaptive = DefaultRetryStrategy.adaptiveStrategyBuilder()
            .retryOnExceptionInstanceOf(RuntimeException.class)
            .treatAsThrottling(ThrottlingFailure.class::isInstance)
            .useClientDefaults(false)
            .build();

        assertThat(standard.maxAttempts()).isEqualTo(3);
        assertThat(standard.useClientDefaults()).isTrue();
        assertThat(refreshDelay(standard, new RuntimeException("standard default"), "default-standard-normal"))
            .isBetween(Duration.ZERO, Duration.ofMillis(99));
        assertThat(refreshDelay(standard, new ThrottlingFailure(), "default-standard-throttled"))
            .isBetween(Duration.ZERO, Duration.ofMillis(999));

        assertThat(legacy.maxAttempts()).isEqualTo(4);
        assertThat(legacy.useClientDefaults()).isTrue();
        assertThat(refreshDelay(legacy, new RuntimeException("legacy default"), "default-legacy-normal"))
            .isBetween(Duration.ZERO, Duration.ofMillis(99));
        assertThat(refreshDelay(legacy, new ThrottlingFailure(), "default-legacy-throttled"))
            .isBetween(Duration.ofMillis(250), Duration.ofMillis(500));

        assertThat(adaptive.maxAttempts()).isEqualTo(3);
        assertThat(adaptive.useClientDefaults()).isFalse();
        assertThat(adaptive.acquireInitialToken(AcquireInitialTokenRequest.create("default-adaptive-initial"))
            .delay()).isEqualTo(Duration.ZERO);
    }

    @Test
    void retryStrategyInterfaceBuilderCanCopyAndRebuildStrategies() {
        RetryStrategy original = StandardRetryStrategy.builder()
            .maxAttempts(2)
            .retryOnExceptionInstanceOf(RuntimeException.class)
            .backoffStrategy(BackoffStrategy.retryImmediately())
            .circuitBreakerEnabled(false)
            .build();

        RetryStrategy rebuilt = original.toBuilder()
            .maxAttempts(3)
            .build();

        assertThat(original.maxAttempts()).isEqualTo(2);
        assertThat(rebuilt.maxAttempts()).isEqualTo(3);
        assertThat(rebuilt.refreshRetryToken(refreshRequest(
            rebuilt.acquireInitialToken(AcquireInitialTokenRequest.create("rebuilt-interface")).token(),
            new RuntimeException("retryable through rebuilt strategy")).build()).delay()).isEqualTo(Duration.ZERO);
    }

    @Test
    void factoryMethodsRejectNullTokensAndDelays() {
        RetryToken token = new ForeignRetryToken();

        assertThatThrownBy(() -> RecordSuccessRequest.create(null))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> AcquireInitialTokenResponse.create(null, Duration.ZERO))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> AcquireInitialTokenResponse.create(token, null))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> RefreshRetryTokenResponse.create(null, Duration.ZERO))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> RefreshRetryTokenResponse.create(token, null))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> RecordSuccessResponse.create(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void backoffStrategiesExposeReadableConfigurationStrings() {
        assertThat(BackoffStrategy.retryImmediately().toString())
            .contains("Immediately");
        assertThat(BackoffStrategy.fixedDelay(Duration.ofMillis(12)).toString())
            .contains("FixedDelayWithJitter", "delay=PT0.012S");
        assertThat(BackoffStrategy.fixedDelayWithoutJitter(Duration.ofMillis(12)).toString())
            .contains("FixedDelayWithoutJitter", "delay=PT0.012S");
        assertThat(BackoffStrategy.exponentialDelay(Duration.ofMillis(10), Duration.ofMillis(30)).toString())
            .contains("ExponentialDelayWithJitter", "baseDelay=PT0.01S", "maxDelay=PT0.03S");
        assertThat(BackoffStrategy.exponentialDelayHalfJitter(Duration.ofMillis(10), Duration.ofMillis(30)).toString())
            .contains("ExponentialDelayWithHalfJitter", "baseDelay=PT0.01S", "maxDelay=PT0.03S");
        assertThat(BackoffStrategy.exponentialDelayWithoutJitter(Duration.ofMillis(10), Duration.ofMillis(30))
            .toString()).contains("ExponentialDelayWithoutJitter", "baseDelay=PT0.01S", "maxDelay=PT0.03S");
    }

    @Test
    void strategiesAndTokensExposeReadableDiagnosticStrings() {
        StandardRetryStrategy strategy = StandardRetryStrategy.builder()
            .maxAttempts(2)
            .useClientDefaults(false)
            .retryOnExceptionInstanceOf(RuntimeException.class)
            .backoffStrategy(BackoffStrategy.fixedDelayWithoutJitter(Duration.ofMillis(4)))
            .throttlingBackoffStrategy(BackoffStrategy.fixedDelayWithoutJitter(Duration.ofMillis(14)))
            .treatAsThrottling(ThrottlingFailure.class::isInstance)
            .circuitBreakerEnabled(false)
            .build();

        RetryToken initialToken = strategy.acquireInitialToken(AcquireInitialTokenRequest.create("diagnostic-scope"))
            .token();
        RefreshRetryTokenResponse retry = strategy.refreshRetryToken(
            refreshRequest(initialToken, new RuntimeException("diagnostic failure")).build());

        assertThat(strategy.toString())
            .contains("BaseRetryStrategy", "maxAttempts=2", "circuitBreakerEnabled=false", "useClientDefaults=false")
            .contains("backoffStrategy", "throttlingBackoffStrategy");
        assertThat(initialToken.toString())
            .contains("StandardRetryToken", "scope=diagnostic-scope", "attempt=1", "capacityAcquired=0");
        assertThat(retry.token().toString())
            .contains("StandardRetryToken", "scope=diagnostic-scope", "attempt=2", "diagnostic failure");
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

    private static Duration refreshDelay(RetryStrategy strategy, Throwable failure, String scope) {
        RetryToken token = strategy.acquireInitialToken(AcquireInitialTokenRequest.create(scope)).token();
        return strategy.refreshRetryToken(refreshRequest(token, failure).build()).delay();
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
