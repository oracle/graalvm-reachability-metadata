/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_oci_sdk.oci_java_sdk_circuitbreaker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oracle.bmc.circuitbreaker.CallNotAllowedException;
import com.oracle.bmc.circuitbreaker.CircuitBreakerConfiguration;
import com.oracle.bmc.circuitbreaker.CircuitBreakerFactory;
import com.oracle.bmc.circuitbreaker.CircuitBreakerState;
import com.oracle.bmc.circuitbreaker.NoCircuitBreakerConfiguration;
import com.oracle.bmc.circuitbreaker.OciCircuitBreaker;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

public class Oci_java_sdk_circuitbreakerTest {
    @Test
    void defaultConfigurationExposesDocumentedThresholdsAndDefaults() {
        CircuitBreakerConfiguration configuration = new CircuitBreakerConfiguration();

        assertThat(configuration.getFailureRateThreshold())
                .isEqualTo(CircuitBreakerConfiguration.DEFAULT_FAILURE_RATE_THRESHOLD);
        assertThat(configuration.getSlowCallRateThreshold())
                .isEqualTo(CircuitBreakerConfiguration.DEFAULT_SLOW_CALL_RATE_THRESHOLD);
        assertThat(configuration.getWaitDurationInOpenState())
                .isEqualTo(Duration.ofSeconds(CircuitBreakerConfiguration.DEFAULT_WAIT_DURATION_IN_OPEN_STATE));
        assertThat(configuration.getPermittedNumberOfCallsInHalfOpenState())
                .isEqualTo(CircuitBreakerConfiguration.DEFAULT_PERMITTED_CALLS_IN_HALF_OPEN_STATE);
        assertThat(configuration.getMinimumNumberOfCalls())
                .isEqualTo(CircuitBreakerConfiguration.DEFAULT_MINIMUM_NUMBER_OF_CALLS);
        assertThat(configuration.getSlidingWindowSize())
                .isEqualTo(CircuitBreakerConfiguration.DEFAULT_SLIDING_WINDOW_SIZE);
        assertThat(configuration.getSlowCallDurationThreshold())
                .isEqualTo(Duration.ofMinutes(CircuitBreakerConfiguration.DEFAULT_SLOW_CALL_DURATION_THRESHOLD));
        assertThat(configuration.isWritableStackTraceEnabled())
                .isEqualTo(CircuitBreakerConfiguration.DEFAULT_WRITABLE_STACK_TRACE_ENABLED);
        assertThat(configuration.getNumberOfRecordedHistoryResponses())
                .isEqualTo(CircuitBreakerConfiguration.NUMBER_OF_RECORDED_HISTORY_RESPONSES);
        assertThat(configuration.getRecordHttpStatuses())
                .containsExactlyInAnyOrder(
                        CircuitBreakerConfiguration.TOO_MANY_REQUESTS,
                        CircuitBreakerConfiguration.INTERNAL_SERVER_ERROR,
                        CircuitBreakerConfiguration.BAD_GATEWAY,
                        CircuitBreakerConfiguration.SERVICE_UNAVAILABLE,
                        CircuitBreakerConfiguration.GATEWAY_TIMEOUT);
        assertThat(configuration.getRecordExceptions()).isEmpty();
        assertThat(configuration.isRecordProcessingFailures()).isTrue();
    }

    @Test
    void builderOverridesConfigurationAndKeepsDefaultValuesForUnsetFields() {
        Set<Integer> recordedStatuses = Set.of(418, 429);
        List<Class<? extends RuntimeException>> recordedExceptions = List.of(IllegalArgumentException.class);

        CircuitBreakerConfiguration configuration =
                CircuitBreakerConfiguration.builder()
                        .failureRateThreshold(25)
                        .slowCallRateThreshold(40)
                        .waitDurationInOpenState(Duration.ofMillis(250))
                        .permittedNumberOfCallsInHalfOpenState(3)
                        .minimumNumberOfCalls(4)
                        .slidingWindowSize(5)
                        .slowCallDurationThreshold(Duration.ofMillis(75))
                        .writableStackTraceEnabled(false)
                        .recordHttpStatuses(recordedStatuses)
                        .recordExceptions(recordedExceptions)
                        .recordProcessingFailures(false)
                        .numberOfRecordedHistoryResponses(2)
                        .build();

        assertThat(configuration.getFailureRateThreshold()).isEqualTo(25);
        assertThat(configuration.getSlowCallRateThreshold()).isEqualTo(40);
        assertThat(configuration.getWaitDurationInOpenState()).isEqualTo(Duration.ofMillis(250));
        assertThat(configuration.getPermittedNumberOfCallsInHalfOpenState()).isEqualTo(3);
        assertThat(configuration.getMinimumNumberOfCalls()).isEqualTo(4);
        assertThat(configuration.getSlidingWindowSize()).isEqualTo(5);
        assertThat(configuration.getSlowCallDurationThreshold()).isEqualTo(Duration.ofMillis(75));
        assertThat(configuration.isWritableStackTraceEnabled()).isFalse();
        assertThat(configuration.getRecordHttpStatuses()).isSameAs(recordedStatuses);
        assertThat(configuration.getRecordExceptions()).isSameAs(recordedExceptions);
        assertThat(configuration.isRecordProcessingFailures()).isFalse();
        assertThat(configuration.getNumberOfRecordedHistoryResponses()).isEqualTo(2);
        assertThat(CircuitBreakerConfiguration.builder().toString())
                .contains("CircuitBreakerConfigurationBuilder", "failureRateThreshold$value");
    }

    @Test
    void noCircuitBreakerConfigurationUsesStandardDefaults() {
        NoCircuitBreakerConfiguration configuration = new NoCircuitBreakerConfiguration();

        assertThat(configuration).isInstanceOf(CircuitBreakerConfiguration.class);
        assertThat(configuration.getFailureRateThreshold())
                .isEqualTo(CircuitBreakerConfiguration.DEFAULT_FAILURE_RATE_THRESHOLD);
        assertThat(configuration.getRecordHttpStatuses())
                .contains(CircuitBreakerConfiguration.INTERNAL_SERVER_ERROR);
    }

    @Test
    void factoryBuildsUsableCircuitBreakerWithPublicConfigurationView() {
        CircuitBreakerConfiguration configuration =
                CircuitBreakerConfiguration.builder()
                        .failureRateThreshold(60)
                        .slowCallRateThreshold(70)
                        .minimumNumberOfCalls(2)
                        .slidingWindowSize(4)
                        .permittedNumberOfCallsInHalfOpenState(1)
                        .slowCallDurationThreshold(Duration.ofMillis(50))
                        .writableStackTraceEnabled(true)
                        .build();

        OciCircuitBreaker circuitBreaker = CircuitBreakerFactory.build(configuration);
        OciCircuitBreaker.Config publicConfig = circuitBreaker.getCircuitBreakerConfig();

        assertThat(circuitBreaker.getName()).isEqualTo("default");
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(circuitBreaker.getTimestampUnit()).isNotNull();
        assertThat(circuitBreaker.getCurrentTimestamp()).isGreaterThanOrEqualTo(0L);
        assertThat(circuitBreaker.getR4jCircuitBreaker()).isNotNull();
        assertThat(circuitBreaker.tryAcquirePermission()).isTrue();
        circuitBreaker.releasePermission();
        circuitBreaker.onSuccess(1, TimeUnit.MILLISECONDS);
        circuitBreaker.onResult(1, TimeUnit.MILLISECONDS, "successful-result");

        assertThat(publicConfig.getFailureRateThreshold()).isEqualTo(60.0f);
        assertThat(publicConfig.getSlowCallRateThreshold()).isEqualTo(70.0f);
        assertThat(publicConfig.getMinimumNumberOfCalls()).isEqualTo(2);
        assertThat(publicConfig.getSlidingWindowSize()).isEqualTo(4);
        assertThat(publicConfig.getPermittedNumberOfCallsInHalfOpenState()).isEqualTo(1);
        assertThat(publicConfig.getSlidingWindowType())
                .isEqualTo(OciCircuitBreaker.Config.SlidingWindowType.TIME_BASED);
        assertThat(publicConfig.getSlowCallDurationThreshold()).isEqualTo(Duration.ofMillis(50));
        assertThat(publicConfig.isWritableStackTraceEnabled()).isTrue();
        assertThat(publicConfig.getRecordExceptionPredicate().test(new RuntimeException("recorded"))).isTrue();
        assertThat(circuitBreaker.toString()).contains("OciCircuitBreakerImpl", "r4jCircuitBreaker");
    }

    @Test
    void customRecordExceptionPredicateControlsFailureRecording() {
        CircuitBreakerConfiguration configuration =
                CircuitBreakerConfiguration.builder()
                        .failureRateThreshold(50)
                        .minimumNumberOfCalls(2)
                        .slidingWindowSize(2)
                        .build();

        OciCircuitBreaker circuitBreaker =
                CircuitBreakerFactory.build(configuration, throwable -> throwable instanceof IllegalStateException);

        assertThat(circuitBreaker.getCircuitBreakerConfig().getRecordExceptionPredicate())
                .accepts(new IllegalStateException("recorded"))
                .rejects(new IllegalArgumentException("ignored"));
    }

    @Test
    void circuitBreakerOpensAfterConfiguredRecordedFailures() {
        OciCircuitBreaker circuitBreaker = CircuitBreakerFactory.build(fastOpeningConfiguration(true));

        circuitBreaker.onError(1, TimeUnit.MILLISECONDS, new IllegalStateException("first"));
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        circuitBreaker.onError(1, TimeUnit.MILLISECONDS, new IllegalStateException("second"));

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(circuitBreaker.tryAcquirePermission()).isFalse();
        CallNotAllowedException exception = circuitBreaker.createCallNotAllowedException();
        assertThat(exception)
                .isInstanceOf(CallNotAllowedException.class)
                .hasMessageContaining("CircuitBreaker 'default'")
                .hasMessageContaining("does not permit further calls");
        assertThat(exception.getStackTrace()).isNotEmpty();
        assertThat(circuitBreaker.circuitBreakerCallNotPermittedErrorMessage("https://example.com/resource"))
                .contains(
                        "CircuitBreaker has been OPEN for",
                        "requests sent in a window of",
                        "URL which CircuitBreaker rejected is - https://example.com/resource");
    }

    @Test
    void circuitBreakerOpensAfterConfiguredSlowCalls() {
        OciCircuitBreaker circuitBreaker =
                CircuitBreakerFactory.build(
                        CircuitBreakerConfiguration.builder()
                                .slowCallRateThreshold(50)
                                .failureRateThreshold(100)
                                .minimumNumberOfCalls(2)
                                .slidingWindowSize(2)
                                .slowCallDurationThreshold(Duration.ofMillis(1))
                                .build());

        circuitBreaker.onSuccess(2, TimeUnit.MILLISECONDS);
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        circuitBreaker.onSuccess(2, TimeUnit.MILLISECONDS);

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(circuitBreaker.tryAcquirePermission()).isFalse();
    }

    @Test
    void acquirePermissionEnforcesHalfOpenProbeLimitAndReleaseRestoresPermit() {
        OciCircuitBreaker circuitBreaker =
                CircuitBreakerFactory.build(
                        CircuitBreakerConfiguration.builder()
                                .permittedNumberOfCallsInHalfOpenState(1)
                                .minimumNumberOfCalls(1)
                                .slidingWindowSize(2)
                                .build());

        CircuitBreaker r4jCircuitBreaker = circuitBreaker.getR4jCircuitBreaker();
        r4jCircuitBreaker.transitionToOpenState();
        r4jCircuitBreaker.transitionToHalfOpenState();
        circuitBreaker.acquirePermission();

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);
        assertThatThrownBy(circuitBreaker::acquirePermission)
                .isInstanceOf(CallNotPermittedException.class)
                .hasMessageContaining("CircuitBreaker 'default'")
                .hasMessageContaining("HALF_OPEN");

        circuitBreaker.releasePermission();
        circuitBreaker.acquirePermission();
        circuitBreaker.onSuccess(1, TimeUnit.MILLISECONDS);

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void disabledWritableStackTraceIsAppliedToCreatedCallNotAllowedException() {
        OciCircuitBreaker circuitBreaker = CircuitBreakerFactory.build(fastOpeningConfiguration(false));

        circuitBreaker.onError(1, TimeUnit.MILLISECONDS, new IllegalStateException("first"));
        circuitBreaker.onError(1, TimeUnit.MILLISECONDS, new IllegalStateException("second"));

        CallNotAllowedException exception = circuitBreaker.createCallNotAllowedException();

        assertThat(exception.getMessage())
                .contains("CircuitBreaker 'default'", "does not permit further calls");
        assertThat(exception.getStackTrace()).isEmpty();
        assertThat(CallNotAllowedException.createCallNotAllowedException("manual", false).getStackTrace())
                .isEmpty();
        assertThat(CallNotAllowedException.createCallNotAllowedException("manual", true).getStackTrace())
                .isNotEmpty();
    }

    @Test
    void errorHistoryIsBoundedOrderedAndExposedAsUnmodifiableSnapshot() {
        OciCircuitBreaker circuitBreaker =
                CircuitBreakerFactory.build(
                        CircuitBreakerConfiguration.builder()
                                .numberOfRecordedHistoryResponses(2)
                                .build());

        circuitBreaker.addToHistory(new IllegalStateException("first"), 500, messages("opc-request-id", "one"));
        circuitBreaker.addToHistory(new IllegalArgumentException("second"), 502, messages("opc-request-id", "two"));
        circuitBreaker.addToHistory(new RuntimeException("third"), 503, messages("opc-request-id", "three"));

        List<OciCircuitBreaker.ErrorHistoryItem> history = circuitBreaker.getHistory();

        assertThat(history).hasSize(2);
        assertThat(history.get(0).getThrowable()).hasMessage("second");
        assertThat(history.get(0).getStatus()).isEqualTo(502);
        assertThat(history.get(0).getMessages()).containsEntry("opc-request-id", "two");
        assertThat(history.get(1).toString()).contains("opc-request-id: three;", "status: 503");
        assertThat(circuitBreaker.getHistoryAsString())
                .contains("1. opc-request-id: two; status: 502", "2. opc-request-id: three; status: 503")
                .doesNotContain("one");
        assertThatThrownBy(() -> history.add(new OciCircuitBreaker.ErrorHistoryItem(null, 500, Map.of())))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void publicEnumsExposeExpectedValues() {
        assertThat(Arrays.asList(CircuitBreakerState.values()))
                .containsExactly(
                        CircuitBreakerState.DISABLED,
                        CircuitBreakerState.CLOSED,
                        CircuitBreakerState.OPEN,
                        CircuitBreakerState.FORCED_OPEN,
                        CircuitBreakerState.HALF_OPEN,
                        CircuitBreakerState.UNKNOWN);
        assertThat(CircuitBreakerState.valueOf("OPEN")).isEqualTo(CircuitBreakerState.OPEN);
        assertThat(OciCircuitBreaker.Config.SlidingWindowType.valueOf("TIME_BASED"))
                .isEqualTo(OciCircuitBreaker.Config.SlidingWindowType.TIME_BASED);
        assertThat(OciCircuitBreaker.Config.SlidingWindowType.values())
                .containsExactly(
                        OciCircuitBreaker.Config.SlidingWindowType.TIME_BASED,
                        OciCircuitBreaker.Config.SlidingWindowType.COUNT_BASED);
    }

    private static CircuitBreakerConfiguration fastOpeningConfiguration(boolean writableStackTraceEnabled) {
        return CircuitBreakerConfiguration.builder()
                .failureRateThreshold(50)
                .minimumNumberOfCalls(2)
                .slidingWindowSize(2)
                .waitDurationInOpenState(Duration.ofSeconds(5))
                .writableStackTraceEnabled(writableStackTraceEnabled)
                .build();
    }

    private static Map<String, String> messages(String key, String value) {
        Map<String, String> messages = new LinkedHashMap<>();
        messages.put(key, value);
        return messages;
    }
}
