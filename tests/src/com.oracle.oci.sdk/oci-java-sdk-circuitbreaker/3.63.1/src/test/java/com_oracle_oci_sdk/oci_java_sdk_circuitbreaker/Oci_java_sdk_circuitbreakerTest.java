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
import io.github.resilience4j.circuitbreaker.CircuitBreaker.State;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

public class Oci_java_sdk_circuitbreakerTest {
    @Test
    void defaultConfigurationExposesDocumentedThresholdsAndStatusCodes() {
        CircuitBreakerConfiguration configuration = new CircuitBreakerConfiguration();

        assertThat(configuration.getFailureRateThreshold())
                .isEqualTo(CircuitBreakerConfiguration.DEFAULT_FAILURE_RATE_THRESHOLD)
                .isEqualTo(80);
        assertThat(configuration.getSlowCallRateThreshold())
                .isEqualTo(CircuitBreakerConfiguration.DEFAULT_SLOW_CALL_RATE_THRESHOLD)
                .isEqualTo(100);
        assertThat(configuration.getWaitDurationInOpenState()).isEqualTo(Duration.ofSeconds(30));
        assertThat(configuration.getPermittedNumberOfCallsInHalfOpenState()).isEqualTo(1);
        assertThat(configuration.getMinimumNumberOfCalls()).isEqualTo(10);
        assertThat(configuration.getSlidingWindowSize()).isEqualTo(120);
        assertThat(configuration.getSlowCallDurationThreshold()).isEqualTo(Duration.ofMinutes(60));
        assertThat(configuration.isWritableStackTraceEnabled()).isTrue();
        assertThat(configuration.getNumberOfRecordedHistoryResponses()).isEqualTo(5);
        assertThat(configuration.getRecordExceptions()).isEmpty();
        assertThat(configuration.isRecordProcessingFailures()).isTrue();
        assertThat(configuration.getRecordHttpStatuses())
                .containsExactlyInAnyOrder(
                        CircuitBreakerConfiguration.TOO_MANY_REQUESTS,
                        CircuitBreakerConfiguration.INTERNAL_SERVER_ERROR,
                        CircuitBreakerConfiguration.BAD_GATEWAY,
                        CircuitBreakerConfiguration.SERVICE_UNAVAILABLE,
                        CircuitBreakerConfiguration.GATEWAY_TIMEOUT);
    }

    @Test
    void builderOverridesEveryPublicConfigurationProperty() {
        Set<Integer> statuses = Set.of(429, 503);
        List<Class<? extends RuntimeException>> exceptions = List.of(IllegalStateException.class);

        CircuitBreakerConfiguration configuration =
                CircuitBreakerConfiguration.builder()
                        .failureRateThreshold(25)
                        .slowCallRateThreshold(75)
                        .waitDurationInOpenState(Duration.ofMillis(250))
                        .permittedNumberOfCallsInHalfOpenState(3)
                        .minimumNumberOfCalls(4)
                        .slidingWindowSize(8)
                        .slowCallDurationThreshold(Duration.ofMillis(125))
                        .writableStackTraceEnabled(false)
                        .recordHttpStatuses(statuses)
                        .recordExceptions(exceptions)
                        .recordProcessingFailures(false)
                        .numberOfRecordedHistoryResponses(2)
                        .build();

        assertThat(configuration.getFailureRateThreshold()).isEqualTo(25);
        assertThat(configuration.getSlowCallRateThreshold()).isEqualTo(75);
        assertThat(configuration.getWaitDurationInOpenState()).isEqualTo(Duration.ofMillis(250));
        assertThat(configuration.getPermittedNumberOfCallsInHalfOpenState()).isEqualTo(3);
        assertThat(configuration.getMinimumNumberOfCalls()).isEqualTo(4);
        assertThat(configuration.getSlidingWindowSize()).isEqualTo(8);
        assertThat(configuration.getSlowCallDurationThreshold()).isEqualTo(Duration.ofMillis(125));
        assertThat(configuration.isWritableStackTraceEnabled()).isFalse();
        assertThat(configuration.getRecordHttpStatuses()).isSameAs(statuses);
        assertThat(configuration.getRecordExceptions()).containsExactly(IllegalStateException.class);
        assertThat(configuration.isRecordProcessingFailures()).isFalse();
        assertThat(configuration.getNumberOfRecordedHistoryResponses()).isEqualTo(2);
        assertThat(CircuitBreakerConfiguration.builder().toString())
                .contains("CircuitBreakerConfiguration.CircuitBreakerConfigurationBuilder");
    }

    @Test
    void factoryBuildsCircuitBreakerUsingConfigurationAndPredicate() {
        CircuitBreakerConfiguration configuration =
                CircuitBreakerConfiguration.builder()
                        .failureRateThreshold(50)
                        .slowCallRateThreshold(60)
                        .waitDurationInOpenState(Duration.ofSeconds(1))
                        .permittedNumberOfCallsInHalfOpenState(2)
                        .minimumNumberOfCalls(2)
                        .slidingWindowSize(2)
                        .slowCallDurationThreshold(Duration.ofMillis(50))
                        .writableStackTraceEnabled(false)
                        .numberOfRecordedHistoryResponses(3)
                        .build();

        OciCircuitBreaker circuitBreaker =
                CircuitBreakerFactory.build(configuration, throwable -> throwable instanceof IllegalStateException);

        assertThat(circuitBreaker).isNotNull();
        assertThat(circuitBreaker.getName()).isEqualTo("default");
        assertThat(circuitBreaker.getState()).isEqualTo(State.CLOSED);
        assertThat(circuitBreaker.getR4jCircuitBreaker()).isNotNull();
        assertThat(circuitBreaker.tryAcquirePermission()).isTrue();
        circuitBreaker.releasePermission();
        assertThat(circuitBreaker.getCurrentTimestamp()).isPositive();
        assertThat(circuitBreaker.getTimestampUnit()).isEqualTo(TimeUnit.NANOSECONDS);
        assertThat(circuitBreaker.toString()).contains("OciCircuitBreakerImpl");

        OciCircuitBreaker.Config circuitBreakerConfig = circuitBreaker.getCircuitBreakerConfig();
        assertThat(circuitBreakerConfig.getFailureRateThreshold()).isEqualTo(50.0f);
        assertThat(circuitBreakerConfig.getSlowCallRateThreshold()).isEqualTo(60.0f);
        assertThat(circuitBreakerConfig.getSlidingWindowSize()).isEqualTo(2);
        assertThat(circuitBreakerConfig.getMinimumNumberOfCalls()).isEqualTo(2);
        assertThat(circuitBreakerConfig.getPermittedNumberOfCallsInHalfOpenState()).isEqualTo(2);
        assertThat(circuitBreakerConfig.getSlowCallDurationThreshold()).isEqualTo(Duration.ofMillis(50));
        assertThat(circuitBreakerConfig.getSlidingWindowType())
                .isEqualTo(OciCircuitBreaker.Config.SlidingWindowType.TIME_BASED);
        assertThat(circuitBreakerConfig.isWritableStackTraceEnabled()).isFalse();
        assertThat(circuitBreakerConfig.getRecordExceptionPredicate())
                .accepts(new IllegalStateException("recorded"))
                .rejects(new IllegalArgumentException("ignored"));
    }

    @Test
    void recordedFailuresOpenCircuitBreakerAndCreateCallNotAllowedException() {
        CircuitBreakerConfiguration configuration =
                CircuitBreakerConfiguration.builder()
                        .failureRateThreshold(50)
                        .minimumNumberOfCalls(2)
                        .slidingWindowSize(2)
                        .waitDurationInOpenState(Duration.ofSeconds(1))
                        .writableStackTraceEnabled(false)
                        .build();
        OciCircuitBreaker circuitBreaker = CircuitBreakerFactory.build(configuration);

        circuitBreaker.onError(1, TimeUnit.MILLISECONDS, new IllegalStateException("first"));
        assertThat(circuitBreaker.getState()).isEqualTo(State.CLOSED);
        circuitBreaker.onError(1, TimeUnit.MILLISECONDS, new IllegalStateException("second"));

        assertThat(circuitBreaker.getState()).isEqualTo(State.OPEN);
        assertThat(circuitBreaker.tryAcquirePermission()).isFalse();
        assertThatThrownBy(circuitBreaker::acquirePermission)
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("CircuitBreaker 'default' is OPEN");

        CallNotAllowedException exception = circuitBreaker.createCallNotAllowedException();
        assertThat(exception).hasMessageContaining("CircuitBreaker 'default' is OPEN");
        assertThat(exception.getStackTrace()).isEmpty();
        assertThat(CallNotAllowedException.createCallNotAllowedException("message", true))
                .hasMessage("message")
                .satisfies(created -> assertThat(created.getStackTrace()).isNotEmpty());
    }

    @Test
    void historyRecordsBoundedFailureDetailsAndBuildsRejectedCallMessage() {
        CircuitBreakerConfiguration configuration =
                CircuitBreakerConfiguration.builder().numberOfRecordedHistoryResponses(2).build();
        OciCircuitBreaker circuitBreaker = CircuitBreakerFactory.build(configuration);

        circuitBreaker.addToHistory(new IllegalArgumentException("too old"), 400, Map.of("opc-request-id", "old"));
        circuitBreaker.addToHistory(new IllegalStateException("kept one"), 500, Map.of("opc-request-id", "first"));
        circuitBreaker.addToHistory(new RuntimeException("kept two"), 503, Map.of("opc-request-id", "second"));

        List<OciCircuitBreaker.ErrorHistoryItem> history = circuitBreaker.getHistory();
        assertThat(history).hasSize(2);
        assertThat(history.get(0).getThrowable()).isInstanceOf(IllegalStateException.class).hasMessage("kept one");
        assertThat(history.get(0).getStatus()).isEqualTo(500);
        assertThat(history.get(0).getMessages()).containsEntry("opc-request-id", "first");
        assertThat(history.get(1).toString()).contains("opc-request-id: second", "status: 503");
        assertThat(circuitBreaker.getHistory()).isNotSameAs(history).containsExactlyElementsOf(history);

        assertThat(circuitBreaker.getHistoryAsString())
                .contains("1. opc-request-id: first; status: 500")
                .contains("2. opc-request-id: second; status: 503")
                .doesNotContain("old");
        assertThat(circuitBreaker.circuitBreakerCallNotPermittedErrorMessage("https://example.invalid/resource"))
                .contains("CircuitBreaker has been OPEN for")
                .contains("URL which CircuitBreaker rejected is - https://example.invalid/resource")
                .contains("The CircuitBreaker was opened because requests failed too frequently")
                .contains("last 2 failed requests");
    }

    @Test
    void noCircuitBreakerConfigurationRetainsBaseDefaultsAndEnumsAreAccessible() {
        NoCircuitBreakerConfiguration noCircuitBreakerConfiguration = new NoCircuitBreakerConfiguration();

        assertThat(noCircuitBreakerConfiguration.getFailureRateThreshold()).isEqualTo(80);
        assertThat(noCircuitBreakerConfiguration.getRecordHttpStatuses()).contains(500, 503);
        assertThat(CircuitBreakerFactory.build(noCircuitBreakerConfiguration)).isNotNull();
        assertThat(CircuitBreakerState.values())
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
    }
}
