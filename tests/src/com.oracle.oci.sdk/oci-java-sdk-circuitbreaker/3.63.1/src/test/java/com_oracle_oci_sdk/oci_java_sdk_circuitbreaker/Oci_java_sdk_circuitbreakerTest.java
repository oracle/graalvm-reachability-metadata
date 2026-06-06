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
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

public class Oci_java_sdk_circuitbreakerTest {
    @Test
    void defaultConfigurationExposesDocumentedDefaults() {
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
        assertThat(configuration.isRecordProcessingFailures()).isTrue();
        assertThat(configuration.getRecordExceptions()).isEmpty();
        assertThat(configuration.getRecordHttpStatuses())
                .containsExactlyInAnyOrder(
                        CircuitBreakerConfiguration.TOO_MANY_REQUESTS,
                        CircuitBreakerConfiguration.INTERNAL_SERVER_ERROR,
                        CircuitBreakerConfiguration.BAD_GATEWAY,
                        CircuitBreakerConfiguration.SERVICE_UNAVAILABLE,
                        CircuitBreakerConfiguration.GATEWAY_TIMEOUT);
        assertThat(CircuitBreakerState.valueOf("OPEN")).isSameAs(CircuitBreakerState.OPEN);
        assertThat(CircuitBreakerState.values())
                .contains(
                        CircuitBreakerState.DISABLED,
                        CircuitBreakerState.CLOSED,
                        CircuitBreakerState.OPEN,
                        CircuitBreakerState.FORCED_OPEN,
                        CircuitBreakerState.HALF_OPEN,
                        CircuitBreakerState.UNKNOWN);
    }

    @Test
    void builderValuesArePropagatedToCircuitBreakerConfig() {
        CircuitBreakerConfiguration configuration = CircuitBreakerConfiguration.builder()
                .failureRateThreshold(25)
                .slowCallRateThreshold(40)
                .waitDurationInOpenState(Duration.ofMillis(750))
                .permittedNumberOfCallsInHalfOpenState(3)
                .minimumNumberOfCalls(4)
                .slidingWindowSize(6)
                .slowCallDurationThreshold(Duration.ofMillis(125))
                .writableStackTraceEnabled(false)
                .recordHttpStatuses(Set.of(418, 429))
                .recordExceptions(List.of(IllegalArgumentException.class))
                .recordProcessingFailures(false)
                .numberOfRecordedHistoryResponses(2)
                .build();

        assertThat(configuration.getFailureRateThreshold()).isEqualTo(25);
        assertThat(configuration.getSlowCallRateThreshold()).isEqualTo(40);
        assertThat(configuration.getWaitDurationInOpenState()).isEqualTo(Duration.ofMillis(750));
        assertThat(configuration.getPermittedNumberOfCallsInHalfOpenState()).isEqualTo(3);
        assertThat(configuration.getMinimumNumberOfCalls()).isEqualTo(4);
        assertThat(configuration.getSlidingWindowSize()).isEqualTo(6);
        assertThat(configuration.getSlowCallDurationThreshold()).isEqualTo(Duration.ofMillis(125));
        assertThat(configuration.isWritableStackTraceEnabled()).isFalse();
        assertThat(configuration.getRecordHttpStatuses()).containsExactlyInAnyOrder(418, 429);
        assertThat(configuration.getRecordExceptions()).containsExactly(IllegalArgumentException.class);
        assertThat(configuration.isRecordProcessingFailures()).isFalse();
        assertThat(configuration.getNumberOfRecordedHistoryResponses()).isEqualTo(2);

        OciCircuitBreaker circuitBreaker = CircuitBreakerFactory.build(
                configuration, RetryableFailure.class::isInstance);
        OciCircuitBreaker.Config config = circuitBreaker.getCircuitBreakerConfig();

        assertThat(circuitBreaker.getName()).isEqualTo("default");
        assertThat(circuitBreaker.getR4jCircuitBreaker()).isNotNull();
        assertThat(config.getFailureRateThreshold()).isEqualTo(25.0f);
        assertThat(config.getSlowCallRateThreshold()).isEqualTo(40.0f);
        assertThat(config.getPermittedNumberOfCallsInHalfOpenState()).isEqualTo(3);
        assertThat(config.getMinimumNumberOfCalls()).isEqualTo(4);
        assertThat(config.getSlidingWindowSize()).isEqualTo(6);
        assertThat(config.getSlidingWindowType()).isEqualTo(OciCircuitBreaker.Config.SlidingWindowType.TIME_BASED);
        assertThat(config.getSlowCallDurationThreshold()).isEqualTo(Duration.ofMillis(125));
        assertThat(config.isWritableStackTraceEnabled()).isFalse();
        assertThat(config.getRecordExceptionPredicate()).accepts(new RetryableFailure("retry"));
        assertThat(config.getRecordExceptionPredicate()).accepts(new IllegalArgumentException("configured"));
        assertThat(circuitBreaker.getTimestampUnit()).isNotNull();
        assertThat(circuitBreaker.getCurrentTimestamp()).isGreaterThanOrEqualTo(0L);
    }

    @Test
    void circuitBreakerRecordsSuccessesFailuresAndOpenStateRejections() {
        CircuitBreakerConfiguration configuration = CircuitBreakerConfiguration.builder()
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .minimumNumberOfCalls(2)
                .slidingWindowSize(2)
                .writableStackTraceEnabled(false)
                .build();
        OciCircuitBreaker successCircuitBreaker = CircuitBreakerFactory.build(configuration);

        assertThat(successCircuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(successCircuitBreaker.tryAcquirePermission()).isTrue();
        successCircuitBreaker.releasePermission();
        successCircuitBreaker.acquirePermission();
        successCircuitBreaker.onSuccess(1, TimeUnit.MILLISECONDS);
        successCircuitBreaker.acquirePermission();
        successCircuitBreaker.onResult(1, TimeUnit.MILLISECONDS, "ok");
        assertThat(successCircuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        OciCircuitBreaker failingCircuitBreaker = CircuitBreakerFactory.build(configuration);
        failingCircuitBreaker.acquirePermission();
        failingCircuitBreaker.onError(1, TimeUnit.MILLISECONDS, new RetryableFailure("first"));
        failingCircuitBreaker.acquirePermission();
        failingCircuitBreaker.onError(1, TimeUnit.MILLISECONDS, new RetryableFailure("second"));

        assertThat(failingCircuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(failingCircuitBreaker.tryAcquirePermission()).isFalse();

        CallNotAllowedException exception = failingCircuitBreaker.createCallNotAllowedException();
        assertThat(exception)
                .hasMessageContaining("CircuitBreaker 'default' is OPEN")
                .hasMessageContaining("does not permit further calls");
        assertThat(exception.getStackTrace()).isEmpty();
        assertThat(failingCircuitBreaker.circuitBreakerCallNotPermittedErrorMessage("https://example.invalid/service"))
                .contains("CircuitBreaker has been OPEN for")
                .contains("URL which CircuitBreaker rejected is - https://example.invalid/service")
                .contains("10 seconds will be rejected");
    }

    @Test
    void circuitBreakerAutomaticallyTransitionsToHalfOpenAndClosesAfterSuccessfulTrial()
            throws InterruptedException {
        CircuitBreakerConfiguration configuration = CircuitBreakerConfiguration.builder()
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofMillis(50))
                .permittedNumberOfCallsInHalfOpenState(1)
                .minimumNumberOfCalls(2)
                .slidingWindowSize(2)
                .writableStackTraceEnabled(false)
                .build();
        OciCircuitBreaker circuitBreaker = CircuitBreakerFactory.build(configuration);

        circuitBreaker.acquirePermission();
        circuitBreaker.onError(1, TimeUnit.MILLISECONDS, new RetryableFailure("first"));
        circuitBreaker.acquirePermission();
        circuitBreaker.onError(1, TimeUnit.MILLISECONDS, new RetryableFailure("second"));
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        awaitState(circuitBreaker, CircuitBreaker.State.HALF_OPEN, Duration.ofSeconds(2));
        assertThat(circuitBreaker.tryAcquirePermission()).isTrue();
        assertThat(circuitBreaker.tryAcquirePermission()).isFalse();

        circuitBreaker.onSuccess(1, TimeUnit.MILLISECONDS);

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(circuitBreaker.tryAcquirePermission()).isTrue();
        circuitBreaker.onSuccess(1, TimeUnit.MILLISECONDS);
    }

    @Test
    void errorHistoryIsBoundedAndRenderedInInsertionOrder() {
        CircuitBreakerConfiguration configuration = CircuitBreakerConfiguration.builder()
                .numberOfRecordedHistoryResponses(2)
                .build();
        OciCircuitBreaker circuitBreaker = CircuitBreakerFactory.build(configuration);

        circuitBreaker.addToHistory(new RetryableFailure("first"), 500, messages("opc-request-id", "request-1"));
        circuitBreaker.addToHistory(new RetryableFailure("second"), 502, messages("opc-request-id", "request-2"));
        circuitBreaker.addToHistory(new RetryableFailure("third"), 503, messages("opc-request-id", "request-3"));

        List<OciCircuitBreaker.ErrorHistoryItem> history = circuitBreaker.getHistory();
        assertThat(history).hasSize(2);
        assertThat(history.get(0).getThrowable()).hasMessage("second");
        assertThat(history.get(0).getStatus()).isEqualTo(502);
        assertThat(history.get(0).getMessages()).containsEntry("opc-request-id", "request-2");
        assertThat(history.get(0).toString())
                .contains("opc-request-id: request-2;")
                .contains("status: 502");
        assertThat(history.get(1).getThrowable()).hasMessage("third");
        assertThat(history.get(1).getStatus()).isEqualTo(503);
        assertThatThrownBy(() -> history.add(new OciCircuitBreaker.ErrorHistoryItem(
                        new RetryableFailure("other"), 504, messages("opc-request-id", "request-4"))))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(circuitBreaker.getHistoryAsString())
                .contains("1. opc-request-id: request-2; status: 502")
                .contains("2. opc-request-id: request-3; status: 503");
        assertThat(circuitBreaker.circuitBreakerCallNotPermittedErrorMessage(null))
                .doesNotContain("URL which CircuitBreaker rejected is")
                .contains("The CircuitBreaker was opened because requests failed too frequently")
                .contains("failed requests");
    }

    @Test
    void factoryHandlesNullAndNoCircuitBreakerConfiguration() {
        assertThat(CircuitBreakerFactory.build(null)).isNull();

        OciCircuitBreaker circuitBreaker = CircuitBreakerFactory.build(new NoCircuitBreakerConfiguration());

        assertThat(circuitBreaker).isNotNull();
        assertThat(circuitBreaker.getName()).isEqualTo("default");
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(circuitBreaker.getCircuitBreakerConfig().getSlidingWindowSize())
                .isEqualTo(CircuitBreakerConfiguration.DEFAULT_SLIDING_WINDOW_SIZE);
        assertThat(circuitBreaker.toString()).contains("OciCircuitBreakerImpl");
        assertThat(CallNotAllowedException.createCallNotAllowedException("blocked", true))
                .hasMessage("blocked");
    }

    private static void awaitState(
            OciCircuitBreaker circuitBreaker,
            CircuitBreaker.State expectedState,
            Duration timeout)
            throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        CircuitBreaker.State state = circuitBreaker.getState();
        while (state != expectedState && System.nanoTime() < deadline) {
            TimeUnit.MILLISECONDS.sleep(10);
            state = circuitBreaker.getState();
        }
        assertThat(state).isEqualTo(expectedState);
    }

    private static Map<String, String> messages(String key, String value) {
        Map<String, String> messages = new LinkedHashMap<>();
        messages.put(key, value);
        return messages;
    }

    private static final class RetryableFailure extends RuntimeException {
        private RetryableFailure(String message) {
            super(message);
        }
    }
}
