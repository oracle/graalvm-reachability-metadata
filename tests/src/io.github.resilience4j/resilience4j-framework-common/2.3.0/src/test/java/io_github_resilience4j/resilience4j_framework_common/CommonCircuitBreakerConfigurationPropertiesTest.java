/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_github_resilience4j.resilience4j_framework_common;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.common.CompositeCustomizer;
import io.github.resilience4j.common.circuitbreaker.configuration.CircuitBreakerConfigCustomizer;
import io.github.resilience4j.common.circuitbreaker.configuration.CommonCircuitBreakerConfigurationProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class CommonCircuitBreakerConfigurationPropertiesTest {

    @Test
    void createsConfigWithPredicateClassesBaseConfigAndCustomizer() {
        CommonCircuitBreakerConfigurationProperties properties = new CommonCircuitBreakerConfigurationProperties();
        properties.getConfigs().put("shared", new CommonCircuitBreakerConfigurationProperties.InstanceProperties()
                .setFailureRateThreshold(40.0f)
                .setMinimumNumberOfCalls(4)
                .setSlidingWindowSize(8)
                .setSlowCallRateThreshold(60.0f)
                .setSlowCallDurationThreshold(Duration.ofMillis(30))
                .setWaitDurationInOpenState(Duration.ofMillis(50))
                .setRecordFailurePredicate(throwablePredicateClass(RecordOnlyIllegalState.class))
                .setRecordResultPredicate(objectPredicateClass(RecordOnlyFailedResult.class))
                .setIgnoreExceptionPredicate(throwablePredicateClass(IgnoreOnlyIllegalArgument.class)));
        CommonCircuitBreakerConfigurationProperties.InstanceProperties instance =
                new CommonCircuitBreakerConfigurationProperties.InstanceProperties()
                        .setBaseConfig("shared")
                        .setPermittedNumberOfCallsInHalfOpenState(2)
                        .setAutomaticTransitionFromOpenToHalfOpenEnabled(true)
                        .setWritableStackTraceEnabled(false);

        CircuitBreakerConfig config = properties.createCircuitBreakerConfig(
                "orders",
                instance,
                new CompositeCustomizer<>(List.of(
                        CircuitBreakerConfigCustomizer.of("orders", builder -> builder.failureRateThreshold(35.0f)))));

        assertThat(config.getFailureRateThreshold()).isEqualTo(35.0f);
        assertThat(config.getMinimumNumberOfCalls()).isEqualTo(4);
        assertThat(config.getSlidingWindowSize()).isEqualTo(8);
        assertThat(config.getPermittedNumberOfCallsInHalfOpenState()).isEqualTo(2);
        assertThat(config.getSlowCallRateThreshold()).isEqualTo(60.0f);
        assertThat(config.getSlowCallDurationThreshold()).isEqualTo(Duration.ofMillis(30));
        assertThat(config.isAutomaticTransitionFromOpenToHalfOpenEnabled()).isTrue();
        assertThat(config.isWritableStackTraceEnabled()).isFalse();
        assertThat(config.getRecordExceptionPredicate().test(new IllegalStateException())).isTrue();
        assertThat(config.getRecordExceptionPredicate().test(new IllegalArgumentException())).isFalse();
        assertThat(config.getRecordResultPredicate().test("failed")).isTrue();
        assertThat(config.getRecordResultPredicate().test("ok")).isFalse();
        assertThat(config.getIgnoreExceptionPredicate().test(new IllegalArgumentException())).isTrue();
        assertThat(config.getIgnoreExceptionPredicate().test(new IllegalStateException())).isFalse();
    }

    @Test
    void rejectsConflictingOpenStateBackoffPolicies() {
        CommonCircuitBreakerConfigurationProperties properties = new CommonCircuitBreakerConfigurationProperties();
        CommonCircuitBreakerConfigurationProperties.InstanceProperties instance =
                new CommonCircuitBreakerConfigurationProperties.InstanceProperties()
                        .setWaitDurationInOpenState(Duration.ofMillis(10))
                        .setEnableExponentialBackoff(true)
                        .setEnableRandomizedWait(true);

        assertThatThrownBy(() -> properties.createCircuitBreakerConfig(
                "orders",
                instance,
                new CompositeCustomizer<>(List.of())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Exponential backoff")
                .hasMessageContaining("randomized");
    }

    @SuppressWarnings("unchecked")
    private static Class<Predicate<Throwable>> throwablePredicateClass(
            Class<? extends Predicate<Throwable>> predicateClass) {
        return (Class<Predicate<Throwable>>) (Class<?>) predicateClass;
    }

    @SuppressWarnings("unchecked")
    private static Class<Predicate<Object>> objectPredicateClass(Class<? extends Predicate<Object>> predicateClass) {
        return (Class<Predicate<Object>>) (Class<?>) predicateClass;
    }

    public static class RecordOnlyIllegalState implements Predicate<Throwable> {
        @Override
        public boolean test(Throwable throwable) {
            return throwable instanceof IllegalStateException;
        }
    }

    public static class RecordOnlyFailedResult implements Predicate<Object> {
        @Override
        public boolean test(Object result) {
            return "failed".equals(result);
        }
    }

    public static class IgnoreOnlyIllegalArgument implements Predicate<Throwable> {
        @Override
        public boolean test(Throwable throwable) {
            return throwable instanceof IllegalArgumentException;
        }
    }
}
