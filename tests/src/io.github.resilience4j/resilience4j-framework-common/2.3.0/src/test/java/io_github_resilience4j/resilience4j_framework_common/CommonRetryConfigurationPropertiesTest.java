/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_github_resilience4j.resilience4j_framework_common;

import io.github.resilience4j.common.CompositeCustomizer;
import io.github.resilience4j.common.retry.configuration.CommonRetryConfigurationProperties;
import io.github.resilience4j.common.retry.configuration.RetryConfigCustomizer;
import io.github.resilience4j.core.IntervalBiFunction;
import io.github.resilience4j.core.functions.Either;
import io.github.resilience4j.retry.RetryConfig;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

public class CommonRetryConfigurationPropertiesTest {

    @Test
    void createsConfigWithPredicateConsumerAndIntervalFunctionClasses() {
        AttemptRecorder.reset();
        CommonRetryConfigurationProperties properties = new CommonRetryConfigurationProperties();
        CommonRetryConfigurationProperties.InstanceProperties shared =
                new CommonRetryConfigurationProperties.InstanceProperties()
                        .setMaxAttempts(4)
                        .setWaitDuration(Duration.ofMillis(10))
                        .setRetryExceptionPredicate(RetryOnlyIllegalState.class)
                        .setResultPredicate(RetryOnlyMarkedResult.class)
                        .setConsumeResultBeforeRetryAttempt(AttemptRecorder.class)
                        .setFailAfterMaxAttempts(true);
        shared.setIntervalBiFunction(FixedInterval.class);
        properties.getConfigs().put("shared", shared);
        CommonRetryConfigurationProperties.InstanceProperties instance =
                new CommonRetryConfigurationProperties.InstanceProperties()
                        .setBaseConfig("shared");

        RetryConfig config = properties.createRetryConfig(
                instance,
                new CompositeCustomizer<>(List.of(
                        RetryConfigCustomizer.of("orders", builder -> builder.maxAttempts(5)))),
                "orders");

        assertThat(config.getMaxAttempts()).isEqualTo(5);
        assertThat(config.isFailAfterMaxAttempts()).isTrue();
        assertThat(config.getExceptionPredicate().test(new IllegalStateException())).isTrue();
        assertThat(config.getExceptionPredicate().test(new IllegalArgumentException())).isFalse();
        Predicate<Object> resultPredicate = config.getResultPredicate();
        assertThat(resultPredicate.test("retry")).isTrue();
        assertThat(resultPredicate.test("success")).isFalse();
        assertThat(config.<Object>getIntervalBiFunction().apply(3, Either.right("value"))).isEqualTo(21L);

        config.<Object>getConsumeResultBeforeRetryAttempt().accept(2, "retry");
        assertThat(AttemptRecorder.attempt()).isEqualTo(2);
        assertThat(AttemptRecorder.result()).isEqualTo("retry");
    }

    public static class RetryOnlyIllegalState implements Predicate<Throwable> {
        @Override
        public boolean test(Throwable throwable) {
            return throwable instanceof IllegalStateException;
        }
    }

    public static class RetryOnlyMarkedResult implements Predicate<Object> {
        @Override
        public boolean test(Object result) {
            return "retry".equals(result);
        }
    }

    public static class AttemptRecorder implements BiConsumer<Integer, Object> {
        private static final AtomicInteger LAST_ATTEMPT = new AtomicInteger();
        private static final AtomicReference<Object> LAST_RESULT = new AtomicReference<>();

        @Override
        public void accept(Integer attempt, Object result) {
            LAST_ATTEMPT.set(attempt);
            LAST_RESULT.set(result);
        }

        static void reset() {
            LAST_ATTEMPT.set(0);
            LAST_RESULT.set(null);
        }

        static int attempt() {
            return LAST_ATTEMPT.get();
        }

        static Object result() {
            return LAST_RESULT.get();
        }
    }

    public static class FixedInterval implements IntervalBiFunction<Object> {
        @Override
        public Long apply(Integer attempt, Either<Throwable, Object> either) {
            return attempt * 7L;
        }
    }
}
