/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_retry.spring_retry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.classify.BinaryExceptionClassifier;
import org.springframework.classify.PatternMatcher;
import org.springframework.classify.PatternMatchingClassifier;
import org.springframework.classify.SubclassClassifier;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.RetryStatistics;
import org.springframework.retry.backoff.BackOffContext;
import org.springframework.retry.backoff.BackOffPolicy;
import org.springframework.retry.backoff.BackOffPolicyBuilder;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.backoff.Sleeper;
import org.springframework.retry.policy.MapRetryContextCache;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.stats.DefaultStatisticsRepository;
import org.springframework.retry.stats.ExponentialAverageRetryStatistics;
import org.springframework.retry.support.DefaultRetryState;
import org.springframework.retry.support.RetrySynchronizationManager;
import org.springframework.retry.support.RetryTemplate;

public class Spring_retryTest {
    @Test
    void retryTemplateRetriesSelectedExceptionsAndNotifiesListener() {
        RecordingRetryListener listener = new RecordingRetryListener();
        RetryTemplate retryTemplate = RetryTemplate.builder()
                .maxAttempts(3)
                .noBackoff()
                .retryOn(IllegalStateException.class)
                .withListener(listener)
                .build();
        AtomicInteger attempts = new AtomicInteger();

        String result = retryTemplate.execute(context -> {
            assertThat(RetrySynchronizationManager.getContext()).isSameAs(context);
            int attempt = attempts.incrementAndGet();
            assertThat(context.getRetryCount()).isEqualTo(attempt - 1);
            if (attempt < 3) {
                throw new IllegalStateException("temporary failure " + attempt);
            }
            return "ok after " + context.getRetryCount() + " retries";
        });

        assertThat(result).isEqualTo("ok after 2 retries");
        assertThat(attempts).hasValue(3);
        assertThat(RetrySynchronizationManager.getContext()).isNull();
        assertThat(listener.events).containsExactly(
                "open:0",
                "error:1:IllegalStateException",
                "error:2:IllegalStateException",
                "success:2:ok after 2 retries",
                "close:2:none");

        AtomicInteger fatalAttempts = new AtomicInteger();
        Throwable thrown = catchThrowable(() -> retryTemplate.execute(context -> {
            fatalAttempts.incrementAndGet();
            throw new IllegalArgumentException("non retryable");
        }));

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("non retryable");
        assertThat(fatalAttempts).hasValue(1);
    }

    @Test
    void exhaustedRetryInvokesRecoveryWithLastThrowable() {
        RetryTemplate retryTemplate = RetryTemplate.builder()
                .maxAttempts(2)
                .noBackoff()
                .retryOn(TemporaryServiceException.class)
                .build();
        AtomicInteger attempts = new AtomicInteger();

        String result = retryTemplate.execute(context -> {
            attempts.incrementAndGet();
            throw new TemporaryServiceException("service unavailable");
        }, context -> "recovered after " + context.getRetryCount() + " attempts from "
                + context.getLastThrowable().getClass().getSimpleName());

        assertThat(result).isEqualTo("recovered after 2 attempts from TemporaryServiceException");
        assertThat(attempts).hasValue(2);
    }

    @Test
    void retryPoliciesExposeAttemptStateAndExceptionClassification() {
        Map<Class<? extends Throwable>, Boolean> retryableExceptions = new LinkedHashMap<>();
        retryableExceptions.put(IllegalStateException.class, true);
        retryableExceptions.put(IllegalArgumentException.class, false);
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(2, retryableExceptions, true);

        RetryContext retryableContext = retryPolicy.open(null);
        assertThat(retryPolicy.canRetry(retryableContext)).isTrue();
        retryPolicy.registerThrowable(retryableContext, new IllegalStateException("first"));
        assertThat(retryableContext.getRetryCount()).isEqualTo(1);
        assertThat(retryPolicy.canRetry(retryableContext)).isTrue();
        retryPolicy.registerThrowable(retryableContext, new IllegalStateException("second"));
        assertThat(retryableContext.getRetryCount()).isEqualTo(2);
        assertThat(retryPolicy.canRetry(retryableContext)).isFalse();
        retryPolicy.close(retryableContext);

        RetryContext nonRetryableContext = retryPolicy.open(null);
        retryPolicy.registerThrowable(nonRetryableContext, new IllegalArgumentException("fatal"));
        assertThat(retryPolicy.canRetry(nonRetryableContext)).isFalse();
        retryPolicy.close(nonRetryableContext);
    }

    @Test
    void backOffPoliciesUseConfiguredSleeperWithoutRealWaiting() {
        RecordingSleeper exponentialSleeper = new RecordingSleeper();
        ExponentialBackOffPolicy exponentialBackOffPolicy = new ExponentialBackOffPolicy();
        exponentialBackOffPolicy.setInitialInterval(5);
        exponentialBackOffPolicy.setMultiplier(3.0);
        exponentialBackOffPolicy.setMaxInterval(20);
        exponentialBackOffPolicy.setSleeper(exponentialSleeper);

        BackOffContext exponentialContext = exponentialBackOffPolicy.start(null);
        exponentialBackOffPolicy.backOff(exponentialContext);
        exponentialBackOffPolicy.backOff(exponentialContext);
        exponentialBackOffPolicy.backOff(exponentialContext);

        assertThat(exponentialSleeper.periods).containsExactly(5L, 15L, 20L);

        RecordingSleeper fixedSleeper = new RecordingSleeper();
        BackOffPolicy fixedBackOffPolicy = BackOffPolicyBuilder.newBuilder()
                .delay(7)
                .sleeper(fixedSleeper)
                .build();
        BackOffContext fixedContext = fixedBackOffPolicy.start(null);
        fixedBackOffPolicy.backOff(fixedContext);
        fixedBackOffPolicy.backOff(fixedContext);

        assertThat(fixedSleeper.periods).containsExactly(7L, 7L);
    }

    @Test
    void classifiersMatchThrowableHierarchiesCausesAndPatterns() {
        SubclassClassifier<Throwable, String> subclassClassifier = new SubclassClassifier<>("other");
        subclassClassifier.add(IOException.class, "io");

        assertThat(subclassClassifier.classify(new FileNotFoundException("missing"))).isEqualTo("io");
        assertThat(subclassClassifier.classify(new IllegalStateException("state"))).isEqualTo("other");

        BinaryExceptionClassifier binaryClassifier = BinaryExceptionClassifier.builder()
                .retryOn(IOException.class)
                .traversingCauses()
                .build();

        assertThat(binaryClassifier.classify(new IllegalStateException(new IOException("nested")))).isTrue();
        assertThat(binaryClassifier.classify(new IllegalArgumentException("fatal"))).isFalse();

        assertThat(PatternMatcher.match("invoice.*", "invoice.created")).isTrue();
        assertThat(PatternMatcher.match("invoice.*", "payment.created")).isFalse();

        PatternMatchingClassifier<String> patternClassifier = new PatternMatchingClassifier<>(Map.of(
                "invoice.*", "invoice-route",
                "payment.*", "payment-route",
                "*", "default-route"));

        assertThat(patternClassifier.classify("invoice.created")).isEqualTo("invoice-route");
        assertThat(patternClassifier.classify("unknown.created")).isEqualTo("default-route");
    }

    @Test
    void statisticsRepositoryAccumulatesNamedRetryOutcomes() {
        DefaultStatisticsRepository repository = new DefaultStatisticsRepository();

        repository.addStarted("checkout");
        repository.addStarted("checkout");
        repository.addError("checkout");
        repository.addRecovery("checkout");
        repository.addComplete("checkout");
        repository.addAbort("checkout");

        RetryStatistics statistics = repository.findOne("checkout");
        assertThat(statistics.getName()).isEqualTo("checkout");
        assertThat(statistics.getStartedCount()).isEqualTo(2);
        assertThat(statistics.getErrorCount()).isEqualTo(1);
        assertThat(statistics.getRecoveryCount()).isEqualTo(1);
        assertThat(statistics.getCompleteCount()).isEqualTo(1);
        assertThat(statistics.getAbortCount()).isEqualTo(1);
        assertThat(repository.findAll()).extracting(RetryStatistics::getName).containsExactly("checkout");

        ExponentialAverageRetryStatistics rollingStatistics = new ExponentialAverageRetryStatistics("payments");
        rollingStatistics.setWindow(1_000_000);
        rollingStatistics.incrementStartedCount();
        rollingStatistics.incrementStartedCount();
        rollingStatistics.incrementErrorCount();
        rollingStatistics.incrementRecoveryCount();
        rollingStatistics.incrementCompleteCount();

        assertThat(rollingStatistics.getRollingStartedCount()).isEqualTo(2);
        assertThat(rollingStatistics.getRollingErrorCount()).isEqualTo(1);
        assertThat(rollingStatistics.getRollingRecoveryCount()).isEqualTo(1);
        assertThat(rollingStatistics.getRollingCompleteCount()).isEqualTo(1);
        assertThat(rollingStatistics.getRollingErrorRate()).isPositive();
    }

    @Test
    void retryStateAndContextCacheTrackKeysAndRollbackRules() {
        DefaultRetryState retryState = new DefaultRetryState("order-42", false,
                throwable -> throwable instanceof OptimisticLockingFailure);
        MapRetryContextCache contextCache = new MapRetryContextCache(2);
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(1);
        RetryContext context = retryPolicy.open(null);

        contextCache.put(retryState.getKey(), context);

        assertThat(retryState.getKey()).isEqualTo("order-42");
        assertThat(retryState.isForceRefresh()).isFalse();
        assertThat(retryState.rollbackFor(new OptimisticLockingFailure())).isTrue();
        assertThat(retryState.rollbackFor(new IllegalStateException())).isFalse();
        assertThat(contextCache.containsKey("order-42")).isTrue();
        assertThat(contextCache.get("order-42")).isSameAs(context);

        contextCache.remove("order-42");

        assertThat(contextCache.containsKey("order-42")).isFalse();
        retryPolicy.close(context);
    }

    private static final class RecordingRetryListener implements RetryListener {
        private final List<String> events = new ArrayList<>();

        @Override
        public <T, E extends Throwable> boolean open(RetryContext context, RetryCallback<T, E> callback) {
            events.add("open:" + context.getRetryCount());
            return true;
        }

        @Override
        public <T, E extends Throwable> void onError(RetryContext context, RetryCallback<T, E> callback,
                Throwable throwable) {
            events.add("error:" + context.getRetryCount() + ":" + throwable.getClass().getSimpleName());
        }

        @Override
        public <T, E extends Throwable> void onSuccess(RetryContext context, RetryCallback<T, E> callback, T result) {
            events.add("success:" + context.getRetryCount() + ":" + result);
        }

        @Override
        public <T, E extends Throwable> void close(RetryContext context, RetryCallback<T, E> callback,
                Throwable throwable) {
            String failure = throwable == null ? "none" : throwable.getClass().getSimpleName();
            events.add("close:" + context.getRetryCount() + ":" + failure);
        }
    }

    private static final class RecordingSleeper implements Sleeper {
        private final List<Long> periods = new ArrayList<>();

        @Override
        public void sleep(long backOffPeriod) {
            periods.add(backOffPeriod);
        }
    }

    private static final class TemporaryServiceException extends RuntimeException {
        private TemporaryServiceException(String message) {
            super(message);
        }
    }

    private static final class OptimisticLockingFailure extends RuntimeException {
    }
}
