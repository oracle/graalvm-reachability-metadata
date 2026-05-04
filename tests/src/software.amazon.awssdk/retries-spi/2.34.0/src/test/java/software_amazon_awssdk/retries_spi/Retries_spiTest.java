/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package software_amazon_awssdk.retries_spi;

import java.time.Duration;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Retries_spiTest {
    private static final Duration FIFTY_MILLIS = Duration.ofMillis(50);
    private static final Duration HUNDRED_MILLIS = Duration.ofMillis(100);
    private static final Duration TWO_HUNDRED_MILLIS = Duration.ofMillis(200);
    private static final Duration ONE_SECOND = Duration.ofSeconds(1);

    @Test
    void requestAndResponseFactoriesPreserveTokenScopeAndDelay() {
        RetryToken token = new TestRetryToken("initial");

        AcquireInitialTokenRequest initialRequest = AcquireInitialTokenRequest.create("service-operation");
        AcquireInitialTokenResponse initialResponse = AcquireInitialTokenResponse.create(token, FIFTY_MILLIS);
        RecordSuccessRequest successRequest = RecordSuccessRequest.create(token);
        RecordSuccessResponse successResponse = RecordSuccessResponse.create(token);
        RefreshRetryTokenResponse refreshResponse = RefreshRetryTokenResponse.create(token, HUNDRED_MILLIS);

        assertThat(initialRequest.scope()).isEqualTo("service-operation");
        assertThat(initialResponse.token()).isSameAs(token);
        assertThat(initialResponse.delay()).isEqualTo(FIFTY_MILLIS);
        assertThat(successRequest.token()).isSameAs(token);
        assertThat(successResponse.token()).isSameAs(token);
        assertThat(refreshResponse.token()).isSameAs(token);
        assertThat(refreshResponse.delay()).isEqualTo(HUNDRED_MILLIS);
    }

    @Test
    void factoriesRejectMissingRequiredValues() {
        RetryToken token = new TestRetryToken("token");

        assertThatThrownBy(() -> AcquireInitialTokenRequest.create(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> AcquireInitialTokenResponse.create(null, Duration.ZERO))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> AcquireInitialTokenResponse.create(token, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> RecordSuccessRequest.create(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> RecordSuccessResponse.create(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> RefreshRetryTokenResponse.create(null, Duration.ZERO))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> RefreshRetryTokenResponse.create(token, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void refreshRetryTokenResponseFactoryRejectsNegativeDelay() {
        RetryToken token = new TestRetryToken("token");
        Duration negativeDelay = Duration.ofMillis(-1);

        assertThatThrownBy(() -> RefreshRetryTokenResponse.create(token, negativeDelay))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refreshRetryTokenRequestBuilderRequiresTokenAndFailureAndDefaultsDelayToZero() {
        RetryToken token = new TestRetryToken("retry");
        IllegalStateException failure = new IllegalStateException("transient failure");

        RefreshRetryTokenRequest request = RefreshRetryTokenRequest.builder()
                .token(token)
                .failure(failure)
                .build();

        assertThat(request.token()).isSameAs(token);
        assertThat(request.failure()).isSameAs(failure);
        assertThat(request.suggestedDelay()).contains(Duration.ZERO);
    }

    @Test
    void refreshRetryTokenRequestBuilderSupportsCopyAndMutation() {
        RetryToken firstToken = new TestRetryToken("first");
        RetryToken secondToken = new TestRetryToken("second");
        RuntimeException firstFailure = new RuntimeException("first failure");
        RuntimeException secondFailure = new RuntimeException("second failure");

        RefreshRetryTokenRequest original = RefreshRetryTokenRequest.builder()
                .token(firstToken)
                .suggestedDelay(FIFTY_MILLIS)
                .failure(firstFailure)
                .build();
        RefreshRetryTokenRequest copied = original.toBuilder()
                .token(secondToken)
                .suggestedDelay(TWO_HUNDRED_MILLIS)
                .failure(secondFailure)
                .build();

        assertThat(original.token()).isSameAs(firstToken);
        assertThat(original.suggestedDelay()).contains(FIFTY_MILLIS);
        assertThat(original.failure()).isSameAs(firstFailure);
        assertThat(copied.token()).isSameAs(secondToken);
        assertThat(copied.suggestedDelay()).contains(TWO_HUNDRED_MILLIS);
        assertThat(copied.failure()).isSameAs(secondFailure);
    }

    @Test
    void refreshRetryTokenRequestSupportsConsumerBasedMutationAndCopy() {
        RetryToken originalToken = new TestRetryToken("original");
        RetryToken copiedToken = new TestRetryToken("copied");
        RuntimeException originalFailure = new RuntimeException("original failure");
        RuntimeException copiedFailure = new RuntimeException("copied failure");

        RefreshRetryTokenRequest original = RefreshRetryTokenRequest.builder()
                .token(originalToken)
                .failure(originalFailure)
                .applyMutation(builder -> builder.suggestedDelay(FIFTY_MILLIS))
                .build();
        RefreshRetryTokenRequest copied = original.copy(builder -> builder
                .token(copiedToken)
                .suggestedDelay(HUNDRED_MILLIS)
                .failure(copiedFailure));

        assertThat(original.token()).isSameAs(originalToken);
        assertThat(original.suggestedDelay()).contains(FIFTY_MILLIS);
        assertThat(original.failure()).isSameAs(originalFailure);
        assertThat(copied.token()).isSameAs(copiedToken);
        assertThat(copied.suggestedDelay()).contains(HUNDRED_MILLIS);
        assertThat(copied.failure()).isSameAs(copiedFailure);
    }

    @Test
    void refreshRetryTokenRequestRejectsInvalidBuilderInputs() {
        RetryToken token = new TestRetryToken("retry");
        RuntimeException failure = new RuntimeException("failure");

        assertThatThrownBy(() -> RefreshRetryTokenRequest.builder()
                .failure(failure)
                .build()).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> RefreshRetryTokenRequest.builder()
                .token(token)
                .build()).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> RefreshRetryTokenRequest.builder()
                .token(token)
                .failure(failure)
                .suggestedDelay(Duration.ofMillis(-1))
                .build()).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RefreshRetryTokenRequest.builder()
                .token(token)
                .failure(failure)
                .suggestedDelay(null)
                .build()).isInstanceOf(NullPointerException.class);
    }

    @Test
    void immediateAndFixedBackoffStrategiesComputeExpectedDelays() {
        BackoffStrategy immediate = BackoffStrategy.retryImmediately();
        BackoffStrategy fixedWithoutJitter = BackoffStrategy.fixedDelayWithoutJitter(HUNDRED_MILLIS);
        BackoffStrategy fixedWithJitter = BackoffStrategy.fixedDelay(ONE_SECOND);

        assertThat(immediate.computeDelay(1)).isEqualTo(Duration.ZERO);
        assertThat(immediate.computeDelay(10)).isEqualTo(Duration.ZERO);
        assertThat(fixedWithoutJitter.computeDelay(1)).isEqualTo(HUNDRED_MILLIS);
        assertThat(fixedWithoutJitter.computeDelay(5)).isEqualTo(HUNDRED_MILLIS);
        assertThat(fixedWithJitter.computeDelay(1))
                .isGreaterThanOrEqualTo(Duration.ZERO)
                .isLessThan(ONE_SECOND);
    }

    @Test
    void exponentialBackoffWithoutJitterStartsAtZeroAndCapsAtMaximumDelay() {
        BackoffStrategy strategy = BackoffStrategy.exponentialDelayWithoutJitter(HUNDRED_MILLIS, ONE_SECOND);

        assertThat(strategy.computeDelay(1)).isEqualTo(Duration.ZERO);
        assertThat(strategy.computeDelay(2)).isEqualTo(HUNDRED_MILLIS);
        assertThat(strategy.computeDelay(3)).isEqualTo(TWO_HUNDRED_MILLIS);
        assertThat(strategy.computeDelay(4)).isEqualTo(Duration.ofMillis(400));
        assertThat(strategy.computeDelay(20)).isEqualTo(ONE_SECOND);
    }

    @Test
    void jitteredExponentialBackoffStaysWithinDocumentedRanges() {
        BackoffStrategy fullJitter = BackoffStrategy.exponentialDelay(HUNDRED_MILLIS, ONE_SECOND);
        BackoffStrategy halfJitter = BackoffStrategy.exponentialDelayHalfJitter(HUNDRED_MILLIS, ONE_SECOND);

        assertThat(fullJitter.computeDelay(1)).isEqualTo(Duration.ZERO);
        assertThat(halfJitter.computeDelay(1)).isEqualTo(Duration.ZERO);
        assertThat(fullJitter.computeDelay(3))
                .isGreaterThanOrEqualTo(Duration.ZERO)
                .isLessThan(TWO_HUNDRED_MILLIS);
        assertThat(halfJitter.computeDelay(3))
                .isGreaterThanOrEqualTo(HUNDRED_MILLIS)
                .isLessThanOrEqualTo(TWO_HUNDRED_MILLIS);
    }

    @Test
    void jitteredExponentialBackoffUsesMaximumDelayAsTheCappedRange() {
        BackoffStrategy fullJitter = BackoffStrategy.exponentialDelay(HUNDRED_MILLIS, TWO_HUNDRED_MILLIS);
        BackoffStrategy halfJitter = BackoffStrategy.exponentialDelayHalfJitter(HUNDRED_MILLIS, TWO_HUNDRED_MILLIS);

        assertThat(fullJitter.computeDelay(10))
                .isGreaterThanOrEqualTo(Duration.ZERO)
                .isLessThan(TWO_HUNDRED_MILLIS);
        assertThat(halfJitter.computeDelay(10))
                .isGreaterThanOrEqualTo(HUNDRED_MILLIS)
                .isLessThanOrEqualTo(TWO_HUNDRED_MILLIS);
    }

    @Test
    void backoffStrategiesCapVeryLargeDelaysToSupportedJitterRange() {
        Duration veryLargeDelay = Duration.ofDays(365);
        Duration ceiling = Duration.ofMillis(Integer.MAX_VALUE);
        BackoffStrategy fixedWithJitter = BackoffStrategy.fixedDelay(veryLargeDelay);
        BackoffStrategy exponentialWithoutJitter = BackoffStrategy.exponentialDelayWithoutJitter(
                veryLargeDelay, veryLargeDelay);
        BackoffStrategy exponentialWithJitter = BackoffStrategy.exponentialDelay(veryLargeDelay, veryLargeDelay);
        BackoffStrategy exponentialWithHalfJitter = BackoffStrategy.exponentialDelayHalfJitter(
                veryLargeDelay, veryLargeDelay);

        assertThat(fixedWithJitter.computeDelay(1))
                .isGreaterThanOrEqualTo(Duration.ZERO)
                .isLessThan(ceiling);
        assertThat(exponentialWithoutJitter.computeDelay(2)).isEqualTo(ceiling);
        assertThat(exponentialWithJitter.computeDelay(2))
                .isGreaterThanOrEqualTo(Duration.ZERO)
                .isLessThan(ceiling);
        assertThat(exponentialWithHalfJitter.computeDelay(2))
                .isGreaterThanOrEqualTo(Duration.ofMillis(Integer.MAX_VALUE / 2))
                .isLessThanOrEqualTo(ceiling);
    }

    @Test
    void backoffStrategiesValidatePositiveAttemptsAndPositiveDurations() {
        BackoffStrategy immediate = BackoffStrategy.retryImmediately();
        BackoffStrategy fixed = BackoffStrategy.fixedDelayWithoutJitter(HUNDRED_MILLIS);
        BackoffStrategy exponential = BackoffStrategy.exponentialDelayWithoutJitter(HUNDRED_MILLIS, ONE_SECOND);

        assertThatThrownBy(() -> immediate.computeDelay(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> fixed.computeDelay(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> exponential.computeDelay(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BackoffStrategy.fixedDelayWithoutJitter(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BackoffStrategy.fixedDelay(Duration.ofMillis(-1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BackoffStrategy.exponentialDelayWithoutJitter(Duration.ZERO, ONE_SECOND))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BackoffStrategy.exponentialDelay(HUNDRED_MILLIS, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BackoffStrategy.exponentialDelayHalfJitter(HUNDRED_MILLIS, Duration.ofMillis(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void backoffStrategiesExposeReadableDescriptions() {
        assertThat(BackoffStrategy.retryImmediately().toString()).isEqualTo("(Immediately)");
        assertThat(BackoffStrategy.fixedDelayWithoutJitter(HUNDRED_MILLIS).toString())
                .contains("FixedDelayWithoutJitter", "delay", "PT0.1S");
        assertThat(BackoffStrategy.fixedDelay(HUNDRED_MILLIS).toString())
                .contains("FixedDelayWithJitter", "delay", "PT0.1S");
        assertThat(BackoffStrategy.exponentialDelayWithoutJitter(HUNDRED_MILLIS, ONE_SECOND).toString())
                .contains("ExponentialDelayWithoutJitter", "baseDelay", "PT0.1S", "maxDelay", "PT1S");
        assertThat(BackoffStrategy.exponentialDelay(HUNDRED_MILLIS, ONE_SECOND).toString())
                .contains("ExponentialDelayWithJitter", "baseDelay", "PT0.1S", "maxDelay", "PT1S");
        assertThat(BackoffStrategy.exponentialDelayHalfJitter(HUNDRED_MILLIS, ONE_SECOND).toString())
                .contains("ExponentialDelayWithHalfJitter", "baseDelay", "PT0.1S", "maxDelay", "PT1S");
    }

    @Test
    void retryStrategyCanCoordinateTokenLifecycleThroughTheSpiRequests() {
        TestRetryStrategy strategy = new TestRetryStrategyBuilder()
                .maxAttempts(4)
                .backoffStrategy(BackoffStrategy.retryImmediately())
                .throttlingBackoffStrategy(BackoffStrategy.fixedDelayWithoutJitter(FIFTY_MILLIS))
                .treatAsThrottling(IllegalStateException.class::isInstance)
                .build();

        AcquireInitialTokenResponse initial = strategy.acquireInitialToken(AcquireInitialTokenRequest.create("orders"));
        RefreshRetryTokenResponse refreshed = strategy.refreshRetryToken(RefreshRetryTokenRequest.builder()
                .token(initial.token())
                .suggestedDelay(TWO_HUNDRED_MILLIS)
                .failure(new IllegalStateException("throttled"))
                .build());
        RecordSuccessResponse success = strategy.recordSuccess(RecordSuccessRequest.create(refreshed.token()));

        assertThat(strategy.maxAttempts()).isEqualTo(4);
        assertThat(strategy.useClientDefaults()).isTrue();
        assertThat(initial.token()).isInstanceOf(TestRetryToken.class);
        assertThat(initial.delay()).isEqualTo(Duration.ZERO);
        assertThat(refreshed.token()).isSameAs(initial.token());
        assertThat(refreshed.delay()).isEqualTo(TWO_HUNDRED_MILLIS);
        assertThat(success.token()).isSameAs(initial.token());
    }

    @Test
    void retryStrategyBuilderDefaultPredicatesSupportExactAndAssignableMatches() {
        TestRetryStrategyBuilder builder = new TestRetryStrategyBuilder();

        builder.retryOnException(IllegalArgumentException.class);
        assertThat(builder.shouldRetry(new IllegalArgumentException("exact"))).isTrue();
        assertThat(builder.shouldRetry(new NumberFormatException("subclass"))).isFalse();

        builder.retryOnExceptionInstanceOf(IllegalArgumentException.class);
        assertThat(builder.shouldRetry(new NumberFormatException("subclass"))).isTrue();
        assertThat(builder.shouldRetry(new IllegalStateException("different"))).isFalse();
    }

    @Test
    void retryStrategyBuilderDefaultPredicatesInspectCausesAndRootCauses() {
        TestRetryStrategyBuilder builder = new TestRetryStrategyBuilder();
        IllegalArgumentException root = new IllegalArgumentException("root");
        RuntimeException middle = new RuntimeException("middle", root);
        IllegalStateException outer = new IllegalStateException("outer", middle);

        builder.retryOnExceptionOrCause(IllegalArgumentException.class);
        assertThat(builder.shouldRetry(new IllegalArgumentException("direct"))).isTrue();
        assertThat(builder.shouldRetry(outer)).isFalse();
        assertThat(builder.shouldRetry(new RuntimeException(
                "outer", new IllegalArgumentException("direct cause")))).isTrue();
        assertThat(builder.shouldRetry(new RuntimeException(
                "outer", new NumberFormatException("different direct cause")))).isFalse();

        builder.retryOnExceptionOrCauseInstanceOf(IllegalArgumentException.class);
        assertThat(builder.shouldRetry(new NumberFormatException("direct subclass"))).isTrue();
        assertThat(builder.shouldRetry(new RuntimeException(
                "outer", new NumberFormatException("subclass direct cause")))).isTrue();
        assertThat(builder.shouldRetry(new RuntimeException(
                "outer", new IllegalStateException("different direct cause")))).isFalse();

        builder.retryOnRootCause(IllegalArgumentException.class);
        assertThat(builder.shouldRetry(outer)).isTrue();
        assertThat(builder.shouldRetry(new RuntimeException(
                "outer", new NumberFormatException("subclass root")))).isFalse();

        builder.retryOnRootCauseInstanceOf(IllegalArgumentException.class);
        assertThat(builder.shouldRetry(new RuntimeException(
                "outer", new NumberFormatException("subclass root")))).isTrue();
    }

    @Test
    void retryStrategyBuilderUseClientDefaultsDefaultMethodIsChainable() {
        TestRetryStrategyBuilder builder = new TestRetryStrategyBuilder();

        TestRetryStrategyBuilder returnedBuilder = builder.useClientDefaults(false)
                .retryOnExceptionInstanceOf(IllegalArgumentException.class)
                .maxAttempts(2);
        TestRetryStrategy strategy = returnedBuilder.build();

        assertThat(returnedBuilder).isSameAs(builder);
        assertThat(strategy.maxAttempts()).isEqualTo(2);
        assertThat(strategy.useClientDefaults()).isTrue();
        assertThat(strategy.shouldRetry(new NumberFormatException("subclass"))).isTrue();
    }

    @Test
    void retryStrategyBuilderCanCopyAConfiguredStrategy() {
        TestRetryStrategy original = new TestRetryStrategyBuilder()
                .retryOnExceptionInstanceOf(IllegalArgumentException.class)
                .maxAttempts(3)
                .backoffStrategy(BackoffStrategy.fixedDelayWithoutJitter(FIFTY_MILLIS))
                .throttlingBackoffStrategy(BackoffStrategy.fixedDelayWithoutJitter(ONE_SECOND))
                .treatAsThrottling(IllegalStateException.class::isInstance)
                .build();

        TestRetryStrategy copied = original.toBuilder()
                .maxAttempts(5)
                .build();

        assertThat(original.maxAttempts()).isEqualTo(3);
        assertThat(copied.maxAttempts()).isEqualTo(5);
        assertThat(copied.shouldRetry(new IllegalArgumentException("retry"))).isTrue();
        assertThat(copied.isThrottling(new IllegalStateException("throttle"))).isTrue();
        assertThat(copied.acquireInitialToken(AcquireInitialTokenRequest.create("copy")).delay())
                .isEqualTo(FIFTY_MILLIS);
    }

    @Test
    void tokenAcquisitionFailedExceptionExposesMessageCauseAndOptionalToken() {
        RetryToken token = new TestRetryToken("failed-token");
        RuntimeException cause = new RuntimeException("cause");

        TokenAcquisitionFailedException messageOnly = new TokenAcquisitionFailedException("message only");
        TokenAcquisitionFailedException withCause = new TokenAcquisitionFailedException("with cause", cause);
        TokenAcquisitionFailedException withToken = new TokenAcquisitionFailedException("with token", token, cause);

        assertThat(messageOnly).hasMessage("message only");
        assertThat(messageOnly.token()).isNull();
        assertThat(withCause).hasMessage("with cause").hasCause(cause);
        assertThat(withCause.token()).isNull();
        assertThat(withToken).hasMessage("with token").hasCause(cause);
        assertThat(withToken.token()).isSameAs(token);
    }

    private static final class TestRetryToken implements RetryToken {
        private final String id;

        private TestRetryToken(String id) {
            this.id = id;
        }

        @Override
        public String toString() {
            return "TestRetryToken{" + "id='" + id + '\'' + '}';
        }
    }

    private static final class TestRetryStrategy implements RetryStrategy {
        private final Predicate<Throwable> retryOnException;
        private final int maxAttempts;
        private final BackoffStrategy backoffStrategy;
        private final BackoffStrategy throttlingBackoffStrategy;
        private final Predicate<Throwable> treatAsThrottling;

        private TestRetryStrategy(TestRetryStrategyBuilder builder) {
            this.retryOnException = builder.retryOnException;
            this.maxAttempts = builder.maxAttempts;
            this.backoffStrategy = builder.backoffStrategy;
            this.throttlingBackoffStrategy = builder.throttlingBackoffStrategy;
            this.treatAsThrottling = builder.treatAsThrottling;
        }

        @Override
        public AcquireInitialTokenResponse acquireInitialToken(AcquireInitialTokenRequest request) {
            TestRetryToken token = new TestRetryToken(request.scope());
            return AcquireInitialTokenResponse.create(token, backoffStrategy.computeDelay(1));
        }

        @Override
        public RefreshRetryTokenResponse refreshRetryToken(RefreshRetryTokenRequest request) {
            Duration delay = request.suggestedDelay().orElseGet(() -> selectBackoff(request.failure()).computeDelay(2));
            return RefreshRetryTokenResponse.create(request.token(), delay);
        }

        @Override
        public RecordSuccessResponse recordSuccess(RecordSuccessRequest request) {
            return RecordSuccessResponse.create(request.token());
        }

        @Override
        public int maxAttempts() {
            return maxAttempts;
        }

        @Override
        public TestRetryStrategyBuilder toBuilder() {
            return new TestRetryStrategyBuilder(this);
        }

        private boolean shouldRetry(Throwable failure) {
            return retryOnException.test(failure);
        }

        private boolean isThrottling(Throwable failure) {
            return treatAsThrottling.test(failure);
        }

        private BackoffStrategy selectBackoff(Throwable failure) {
            return isThrottling(failure) ? throttlingBackoffStrategy : backoffStrategy;
        }
    }

    private static final class TestRetryStrategyBuilder
            implements RetryStrategy.Builder<TestRetryStrategyBuilder, TestRetryStrategy> {
        private Predicate<Throwable> retryOnException = failure -> false;
        private int maxAttempts = 1;
        private BackoffStrategy backoffStrategy = BackoffStrategy.retryImmediately();
        private BackoffStrategy throttlingBackoffStrategy = BackoffStrategy.retryImmediately();
        private Predicate<Throwable> treatAsThrottling = failure -> false;

        private TestRetryStrategyBuilder() {
        }

        private TestRetryStrategyBuilder(TestRetryStrategy strategy) {
            this.retryOnException = strategy.retryOnException;
            this.maxAttempts = strategy.maxAttempts;
            this.backoffStrategy = strategy.backoffStrategy;
            this.throttlingBackoffStrategy = strategy.throttlingBackoffStrategy;
            this.treatAsThrottling = strategy.treatAsThrottling;
        }

        @Override
        public TestRetryStrategyBuilder retryOnException(Predicate<Throwable> retryOnException) {
            this.retryOnException = retryOnException;
            return this;
        }

        @Override
        public TestRetryStrategyBuilder maxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
            return this;
        }

        @Override
        public TestRetryStrategyBuilder backoffStrategy(BackoffStrategy backoffStrategy) {
            this.backoffStrategy = backoffStrategy;
            return this;
        }

        @Override
        public TestRetryStrategyBuilder throttlingBackoffStrategy(BackoffStrategy throttlingBackoffStrategy) {
            this.throttlingBackoffStrategy = throttlingBackoffStrategy;
            return this;
        }

        @Override
        public TestRetryStrategyBuilder treatAsThrottling(Predicate<Throwable> treatAsThrottling) {
            this.treatAsThrottling = treatAsThrottling;
            return this;
        }

        @Override
        public TestRetryStrategy build() {
            return new TestRetryStrategy(this);
        }

        private boolean shouldRetry(Throwable failure) {
            return retryOnException.test(failure);
        }
    }
}
