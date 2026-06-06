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
import com.oracle.bmc.circuitbreaker.NoCircuitBreakerConfiguration;
import com.oracle.bmc.circuitbreaker.OciCircuitBreaker;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
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
    void defaultConfigurationExposesDocumentedValues() {
        CircuitBreakerConfiguration configuration = new CircuitBreakerConfiguration();
        NoCircuitBreakerConfiguration noCircuitBreakerConfiguration =
                new NoCircuitBreakerConfiguration();

        assertThat(configuration.getFailureRateThreshold())
                .isEqualTo(CircuitBreakerConfiguration.DEFAULT_FAILURE_RATE_THRESHOLD);
        assertThat(configuration.getSlowCallRateThreshold())
                .isEqualTo(CircuitBreakerConfiguration.DEFAULT_SLOW_CALL_RATE_THRESHOLD);
        assertThat(configuration.getWaitDurationInOpenState())
                .isEqualTo(
                        Duration.ofSeconds(
                                CircuitBreakerConfiguration.DEFAULT_WAIT_DURATION_IN_OPEN_STATE));
        assertThat(configuration.getPermittedNumberOfCallsInHalfOpenState())
                .isEqualTo(
                        CircuitBreakerConfiguration.DEFAULT_PERMITTED_CALLS_IN_HALF_OPEN_STATE);
        assertThat(configuration.getMinimumNumberOfCalls())
                .isEqualTo(CircuitBreakerConfiguration.DEFAULT_MINIMUM_NUMBER_OF_CALLS);
        assertThat(configuration.getSlidingWindowSize())
                .isEqualTo(CircuitBreakerConfiguration.DEFAULT_SLIDING_WINDOW_SIZE);
        assertThat(configuration.getSlowCallDurationThreshold())
                .isEqualTo(
                        Duration.ofMinutes(
                                CircuitBreakerConfiguration
                                        .DEFAULT_SLOW_CALL_DURATION_THRESHOLD));
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
        assertThat(noCircuitBreakerConfiguration.getRecordHttpStatuses())
                .isEqualTo(configuration.getRecordHttpStatuses());
        assertThat(CircuitBreakerFactory.build(null)).isNull();
    }

    @Test
    void builderCreatesConfigurationAndFactoryMapsItToCircuitBreakerConfig() {
        CircuitBreakerConfiguration configuration =
                CircuitBreakerConfiguration.builder()
                        .failureRateThreshold(40)
                        .slowCallRateThreshold(60)
                        .waitDurationInOpenState(Duration.ofSeconds(3))
                        .permittedNumberOfCallsInHalfOpenState(2)
                        .minimumNumberOfCalls(4)
                        .slidingWindowSize(8)
                        .slowCallDurationThreshold(Duration.ofMillis(250))
                        .writableStackTraceEnabled(false)
                        .recordHttpStatuses(Set.of(409, 429))
                        .recordExceptions(List.of(CustomRuntimeException.class))
                        .recordProcessingFailures(false)
                        .numberOfRecordedHistoryResponses(3)
                        .build();

        assertThat(configuration.getFailureRateThreshold()).isEqualTo(40);
        assertThat(configuration.getSlowCallRateThreshold()).isEqualTo(60);
        assertThat(configuration.getWaitDurationInOpenState()).isEqualTo(Duration.ofSeconds(3));
        assertThat(configuration.getPermittedNumberOfCallsInHalfOpenState()).isEqualTo(2);
        assertThat(configuration.getMinimumNumberOfCalls()).isEqualTo(4);
        assertThat(configuration.getSlidingWindowSize()).isEqualTo(8);
        assertThat(configuration.getSlowCallDurationThreshold()).isEqualTo(Duration.ofMillis(250));
        assertThat(configuration.isWritableStackTraceEnabled()).isFalse();
        assertThat(configuration.getRecordHttpStatuses()).containsExactlyInAnyOrder(409, 429);
        assertThat(configuration.getRecordExceptions())
                .containsExactly(CustomRuntimeException.class);
        assertThat(configuration.isRecordProcessingFailures()).isFalse();
        assertThat(configuration.getNumberOfRecordedHistoryResponses()).isEqualTo(3);

        OciCircuitBreaker circuitBreaker =
                CircuitBreakerFactory.build(
                        configuration, throwable -> throwable instanceof CustomRuntimeException);
        OciCircuitBreaker.Config circuitBreakerConfig = circuitBreaker.getCircuitBreakerConfig();

        assertThat(circuitBreaker.getName()).isEqualTo("default");
        assertThat(circuitBreaker.getR4jCircuitBreaker()).isNotNull();
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(circuitBreaker.tryAcquirePermission()).isTrue();
        circuitBreaker.releasePermission();
        circuitBreaker.acquirePermission();
        circuitBreaker.onSuccess(1, TimeUnit.MILLISECONDS);
        circuitBreaker.onResult(1, TimeUnit.MILLISECONDS, "ok");
        long currentTimestamp = circuitBreaker.getCurrentTimestamp();
        assertThat(circuitBreaker.getCurrentTimestamp()).isGreaterThanOrEqualTo(currentTimestamp);
        assertThat(circuitBreaker.getTimestampUnit()).isEqualTo(TimeUnit.NANOSECONDS);

        assertThat(circuitBreakerConfig.getFailureRateThreshold()).isEqualTo(40.0f);
        assertThat(circuitBreakerConfig.getSlowCallRateThreshold()).isEqualTo(60.0f);
        assertThat(circuitBreakerConfig.getSlidingWindowSize()).isEqualTo(8);
        assertThat(circuitBreakerConfig.getMinimumNumberOfCalls()).isEqualTo(4);
        assertThat(circuitBreakerConfig.getPermittedNumberOfCallsInHalfOpenState()).isEqualTo(2);
        assertThat(circuitBreakerConfig.getSlidingWindowType())
                .isEqualTo(OciCircuitBreaker.Config.SlidingWindowType.TIME_BASED);
        assertThat(circuitBreakerConfig.getSlowCallDurationThreshold())
                .isEqualTo(Duration.ofMillis(250));
        assertThat(circuitBreakerConfig.isWritableStackTraceEnabled()).isFalse();
        assertThat(circuitBreakerConfig.getRecordExceptionPredicate())
                .accepts(new CustomRuntimeException("record"))
                .rejects(new IllegalArgumentException("ignore"));
    }

    @Test
    void circuitBreakerOpensAfterEnoughRecordedFailuresAndRejectsFurtherCalls() {
        OciCircuitBreaker circuitBreaker =
                CircuitBreakerFactory.build(
                        CircuitBreakerConfiguration.builder()
                                .failureRateThreshold(50)
                                .slowCallRateThreshold(100)
                                .waitDurationInOpenState(Duration.ofSeconds(30))
                                .minimumNumberOfCalls(2)
                                .slidingWindowSize(2)
                                .writableStackTraceEnabled(false)
                                .build());

        circuitBreaker.onError(1, TimeUnit.MILLISECONDS, new CustomRuntimeException("first"));
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        circuitBreaker.onError(1, TimeUnit.MILLISECONDS, new CustomRuntimeException("second"));
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(circuitBreaker.tryAcquirePermission()).isFalse();
        assertThatThrownBy(circuitBreaker::acquirePermission)
                .isInstanceOf(CallNotPermittedException.class)
                .hasMessageContaining("CircuitBreaker 'default' is OPEN");

        CallNotAllowedException exception = circuitBreaker.createCallNotAllowedException();
        assertThat(exception).hasMessageContaining("CircuitBreaker 'default' is OPEN");
        assertThat(exception.getStackTrace()).isEmpty();

        String rejectionMessage =
                circuitBreaker.circuitBreakerCallNotPermittedErrorMessage(
                        "https://iaas.example.com/instances");
        assertThat(rejectionMessage)
                .contains("CircuitBreaker has been OPEN for")
                .contains("all the requests sent in a window of 30 seconds will be rejected")
                .contains(
                        "URL which CircuitBreaker rejected is - "
                                + "https://iaas.example.com/instances");
    }

    @Test
    void slowSuccessfulCallsCanTripCircuitBreaker() {
        OciCircuitBreaker circuitBreaker =
                CircuitBreakerFactory.build(
                        CircuitBreakerConfiguration.builder()
                                .failureRateThreshold(100)
                                .slowCallRateThreshold(50)
                                .slowCallDurationThreshold(Duration.ofMillis(1))
                                .minimumNumberOfCalls(2)
                                .slidingWindowSize(2)
                                .build());

        circuitBreaker.onSuccess(2, TimeUnit.MILLISECONDS);
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        circuitBreaker.onSuccess(2, TimeUnit.MILLISECONDS);
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(circuitBreaker.tryAcquirePermission()).isFalse();
    }

    @Test
    void exceptionPredicateControlsWhichFailuresAreRecorded() {
        OciCircuitBreaker circuitBreaker =
                CircuitBreakerFactory.build(
                        CircuitBreakerConfiguration.builder()
                                .failureRateThreshold(50)
                                .slowCallRateThreshold(100)
                                .minimumNumberOfCalls(2)
                                .slidingWindowSize(2)
                                .build(),
                        throwable -> throwable instanceof RecordedRuntimeException);

        circuitBreaker.onError(1, TimeUnit.MILLISECONDS, new IgnoredRuntimeException("ignored"));
        circuitBreaker.onError(1, TimeUnit.MILLISECONDS, new IgnoredRuntimeException("ignored"));
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(circuitBreaker.tryAcquirePermission()).isTrue();

        circuitBreaker.onError(1, TimeUnit.MILLISECONDS, new RecordedRuntimeException("recorded"));
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        circuitBreaker.onError(1, TimeUnit.MILLISECONDS, new RecordedRuntimeException("recorded"));
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(circuitBreaker.tryAcquirePermission()).isFalse();
    }

    @Test
    void halfOpenProbeClosesCircuitBreakerAfterOpenStateWait() throws InterruptedException {
        OciCircuitBreaker circuitBreaker =
                CircuitBreakerFactory.build(
                        CircuitBreakerConfiguration.builder()
                                .failureRateThreshold(50)
                                .slowCallRateThreshold(100)
                                .waitDurationInOpenState(Duration.ofMillis(50))
                                .permittedNumberOfCallsInHalfOpenState(1)
                                .minimumNumberOfCalls(2)
                                .slidingWindowSize(2)
                                .build());

        circuitBreaker.onError(1, TimeUnit.MILLISECONDS, new CustomRuntimeException("first"));
        circuitBreaker.onError(1, TimeUnit.MILLISECONDS, new CustomRuntimeException("second"));
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        Thread.sleep(150);
        assertThat(circuitBreaker.tryAcquirePermission()).isTrue();
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);

        circuitBreaker.onSuccess(1, TimeUnit.MILLISECONDS);
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(circuitBreaker.tryAcquirePermission()).isTrue();
    }

    @Test
    void historyKeepsMostRecentFailuresAndFormatsDiagnosticMessage() {
        OciCircuitBreaker circuitBreaker =
                CircuitBreakerFactory.build(
                        CircuitBreakerConfiguration.builder()
                                .numberOfRecordedHistoryResponses(2)
                                .build());

        circuitBreaker.addToHistory(
                new CustomRuntimeException("first"), 500, linkedMessages("opc-request-id", "one"));
        circuitBreaker.addToHistory(
                new CustomRuntimeException("second"), 502, linkedMessages("opc-request-id", "two"));
        circuitBreaker.addToHistory(
                new CustomRuntimeException("third"),
                503,
                linkedMessages("opc-request-id", "three"));

        List<OciCircuitBreaker.ErrorHistoryItem> history = circuitBreaker.getHistory();
        assertThat(history).hasSize(2);
        assertThat(history.get(0).getStatus()).isEqualTo(502);
        assertThat(history.get(0).getThrowable()).hasMessage("second");
        assertThat(history.get(0).getMessages()).containsEntry("opc-request-id", "two");
        assertThat(history.get(1).getStatus()).isEqualTo(503);
        assertThatThrownBy(() -> history.add(null))
                .isInstanceOf(UnsupportedOperationException.class);

        assertThat(circuitBreaker.getHistoryAsString())
                .contains("1. opc-request-id: two; status: 502")
                .contains("2. opc-request-id: three; status: 503")
                .doesNotContain("one");
        assertThat(circuitBreaker.circuitBreakerCallNotPermittedErrorMessage(null))
                .contains("Here are the last 2 failed requests")
                .contains("opc-request-id: two; status: 502")
                .contains("opc-request-id: three; status: 503")
                .doesNotContain("URL which CircuitBreaker rejected is");
    }

    private static Map<String, String> linkedMessages(String key, String value) {
        Map<String, String> messages = new LinkedHashMap<>();
        messages.put(key, value);
        return messages;
    }

    private static final class CustomRuntimeException extends RuntimeException {
        private CustomRuntimeException(String message) {
            super(message);
        }
    }

    private static final class RecordedRuntimeException extends RuntimeException {
        private RecordedRuntimeException(String message) {
            super(message);
        }
    }

    private static final class IgnoredRuntimeException extends RuntimeException {
        private IgnoredRuntimeException(String message) {
            super(message);
        }
    }
}
