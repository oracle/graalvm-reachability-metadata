/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_github_resilience4j.resilience4j_framework_common;

import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.event.BulkheadOnCallPermittedEvent;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.event.CircuitBreakerOnErrorEvent;
import io.github.resilience4j.circuitbreaker.event.CircuitBreakerOnSuccessEvent;
import io.github.resilience4j.common.CompositeCustomizer;
import io.github.resilience4j.common.bulkhead.configuration.BulkheadConfigCustomizer;
import io.github.resilience4j.common.bulkhead.configuration.CommonBulkheadConfigurationProperties;
import io.github.resilience4j.common.bulkhead.monitoring.endpoint.BulkheadEndpointResponse;
import io.github.resilience4j.common.bulkhead.monitoring.endpoint.BulkheadEventDTO;
import io.github.resilience4j.common.bulkhead.monitoring.endpoint.BulkheadEventDTOFactory;
import io.github.resilience4j.common.bulkhead.monitoring.endpoint.BulkheadEventsEndpointResponse;
import io.github.resilience4j.common.circuitbreaker.monitoring.endpoint.CircuitBreakerDetails;
import io.github.resilience4j.common.circuitbreaker.monitoring.endpoint.CircuitBreakerEndpointResponse;
import io.github.resilience4j.common.circuitbreaker.monitoring.endpoint.CircuitBreakerEventDTO;
import io.github.resilience4j.common.circuitbreaker.monitoring.endpoint.CircuitBreakerEventDTOFactory;
import io.github.resilience4j.common.circuitbreaker.monitoring.endpoint.CircuitBreakerEventsEndpointResponse;
import io.github.resilience4j.common.circuitbreaker.monitoring.endpoint.CircuitBreakerHystrixStreamEventsDTO;
import io.github.resilience4j.common.circuitbreaker.monitoring.endpoint.CircuitBreakerUpdateStateResponse;
import io.github.resilience4j.common.circuitbreaker.monitoring.endpoint.UpdateState;
import io.github.resilience4j.common.ratelimiter.configuration.CommonRateLimiterConfigurationProperties;
import io.github.resilience4j.common.ratelimiter.configuration.RateLimiterConfigCustomizer;
import io.github.resilience4j.common.ratelimiter.monitoring.endpoint.RateLimiterEndpointResponse;
import io.github.resilience4j.common.ratelimiter.monitoring.endpoint.RateLimiterEventDTO;
import io.github.resilience4j.common.ratelimiter.monitoring.endpoint.RateLimiterEventsEndpointResponse;
import io.github.resilience4j.common.retry.monitoring.endpoint.RetryEndpointResponse;
import io.github.resilience4j.common.retry.monitoring.endpoint.RetryEventDTO;
import io.github.resilience4j.common.retry.monitoring.endpoint.RetryEventDTOFactory;
import io.github.resilience4j.common.retry.monitoring.endpoint.RetryEventsEndpointResponse;
import io.github.resilience4j.common.timelimiter.configuration.CommonTimeLimiterConfigurationProperties;
import io.github.resilience4j.common.timelimiter.configuration.TimeLimiterConfigCustomizer;
import io.github.resilience4j.common.timelimiter.monitoring.endpoint.TimeLimiterEndpointResponse;
import io.github.resilience4j.common.timelimiter.monitoring.endpoint.TimeLimiterEventDTO;
import io.github.resilience4j.common.timelimiter.monitoring.endpoint.TimeLimiterEventsEndpointResponse;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.event.RateLimiterOnSuccessEvent;
import io.github.resilience4j.retry.event.RetryOnRetryEvent;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.event.TimeLimiterOnSuccessEvent;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class Resilience4j_framework_commonTest {

    @Test
    void createsCoreModuleConfigsFromPropertiesAndCustomizers() {
        CommonRateLimiterConfigurationProperties rateLimiterProperties = new CommonRateLimiterConfigurationProperties();
        rateLimiterProperties.setTags(Map.of("component", "payments"));
        CommonRateLimiterConfigurationProperties.InstanceProperties limiter =
                new CommonRateLimiterConfigurationProperties.InstanceProperties()
                        .setLimitForPeriod(2)
                        .setLimitRefreshPeriod(Duration.ofMillis(40))
                        .setTimeoutDuration(Duration.ofMillis(5))
                        .setWritableStackTraceEnabled(false);
        RateLimiterConfig rateLimiterConfig = rateLimiterProperties.createRateLimiterConfig(
                limiter,
                new CompositeCustomizer<>(List.of(
                        RateLimiterConfigCustomizer.of("api", builder -> builder.limitForPeriod(3)))),
                "api");

        assertThat(rateLimiterProperties.getTags()).containsEntry("component", "payments");
        assertThat(rateLimiterConfig.getLimitForPeriod()).isEqualTo(3);
        assertThat(rateLimiterConfig.getLimitRefreshPeriod()).isEqualTo(Duration.ofMillis(40));
        assertThat(rateLimiterConfig.getTimeoutDuration()).isEqualTo(Duration.ofMillis(5));
        assertThat(rateLimiterConfig.isWritableStackTraceEnabled()).isFalse();

        CommonBulkheadConfigurationProperties bulkheadProperties = new CommonBulkheadConfigurationProperties();
        CommonBulkheadConfigurationProperties.InstanceProperties bulkhead =
                new CommonBulkheadConfigurationProperties.InstanceProperties()
                        .setMaxConcurrentCalls(2)
                        .setMaxWaitDuration(Duration.ZERO)
                        .setWritableStackTraceEnabled(false);
        BulkheadConfig bulkheadConfig = bulkheadProperties.createBulkheadConfig(
                bulkhead,
                new CompositeCustomizer<>(List.of(
                        BulkheadConfigCustomizer.of("api", builder -> builder.maxConcurrentCalls(4)))),
                "api");

        assertThat(bulkheadConfig.getMaxConcurrentCalls()).isEqualTo(4);
        assertThat(bulkheadConfig.getMaxWaitDuration()).isEqualTo(Duration.ZERO);
        assertThat(bulkheadConfig.isWritableStackTraceEnabled()).isFalse();

        CommonTimeLimiterConfigurationProperties timeLimiterProperties = new CommonTimeLimiterConfigurationProperties();
        CommonTimeLimiterConfigurationProperties.InstanceProperties timeLimiter =
                new CommonTimeLimiterConfigurationProperties.InstanceProperties()
                        .setTimeoutDuration(Duration.ofMillis(80))
                        .setCancelRunningFuture(false);
        TimeLimiterConfig timeLimiterConfig = timeLimiterProperties.createTimeLimiterConfig(
                "api",
                timeLimiter,
                new CompositeCustomizer<>(List.of(
                        TimeLimiterConfigCustomizer.of(
                                "api", builder -> builder.timeoutDuration(Duration.ofMillis(90))))));

        assertThat(timeLimiterConfig.getTimeoutDuration()).isEqualTo(Duration.ofMillis(90));
        assertThat(timeLimiterConfig.shouldCancelRunningFuture()).isFalse();
    }

    @Test
    void exposesCompositeCustomizerByInstanceName() {
        RateLimiterConfigCustomizer apiCustomizer = RateLimiterConfigCustomizer.of(
                "api", builder -> builder.limitForPeriod(8));
        RateLimiterConfigCustomizer adminCustomizer = RateLimiterConfigCustomizer.of(
                "admin", builder -> builder.limitForPeriod(1));
        CompositeCustomizer<RateLimiterConfigCustomizer> customizers = new CompositeCustomizer<>(
                List.of(apiCustomizer, adminCustomizer));

        assertThat(customizers.instanceNames()).containsExactlyInAnyOrder("api", "admin");
        assertThat(customizers.getCustomizer("api")).containsSame(apiCustomizer);
        assertThat(customizers.getCustomizer("missing")).isEmpty();
    }

    @Test
    void mapsCoreEventsToMonitoringDtos() {
        CircuitBreakerOnErrorEvent circuitBreakerEvent = new CircuitBreakerOnErrorEvent(
                "orders", Duration.ofMillis(12), new IllegalStateException("boom"));
        CircuitBreakerEventDTO circuitBreakerDto =
                CircuitBreakerEventDTOFactory.createCircuitBreakerEventDTO(circuitBreakerEvent);
        assertThat(circuitBreakerDto.getCircuitBreakerName()).isEqualTo("orders");
        assertThat(circuitBreakerDto.getType()).isEqualTo(circuitBreakerEvent.getEventType());
        assertThat(circuitBreakerDto.getDurationInMs()).isEqualTo(12L);
        assertThat(circuitBreakerDto.getErrorMessage()).contains("boom");
        assertThat(circuitBreakerDto.getCreationTime()).isNotBlank();

        RetryOnRetryEvent retryEvent = new RetryOnRetryEvent(
                "retryOrders", 2, new RuntimeException("retry"), 25L);
        RetryEventDTO retryDto = RetryEventDTOFactory.createRetryEventDTO(retryEvent);
        assertThat(retryDto.getRetryName()).isEqualTo("retryOrders");
        assertThat(retryDto.getType()).isEqualTo(retryEvent.getEventType());
        assertThat(retryDto.getNumberOfAttempts()).isEqualTo(2);
        assertThat(retryDto.getErrorMessage()).contains("retry");

        RateLimiterOnSuccessEvent rateLimiterEvent = new RateLimiterOnSuccessEvent("limiter", 2);
        RateLimiterEventDTO rateLimiterDto = RateLimiterEventDTO.createRateLimiterEventDTO(rateLimiterEvent);
        assertThat(rateLimiterDto.getRateLimiterName()).isEqualTo("limiter");
        assertThat(rateLimiterDto.getType()).isEqualTo(rateLimiterEvent.getEventType());

        BulkheadOnCallPermittedEvent bulkheadEvent = new BulkheadOnCallPermittedEvent("bulkhead");
        BulkheadEventDTO bulkheadDto = BulkheadEventDTOFactory.createBulkheadEventDTO(bulkheadEvent);
        assertThat(bulkheadDto.getBulkheadName()).isEqualTo("bulkhead");
        assertThat(bulkheadDto.getType()).isEqualTo(bulkheadEvent.getEventType());

        TimeLimiterOnSuccessEvent timeLimiterEvent = new TimeLimiterOnSuccessEvent("timeout");
        TimeLimiterEventDTO timeLimiterDto = TimeLimiterEventDTO.createTimeLimiterEventDTO(timeLimiterEvent);
        assertThat(timeLimiterDto.getTimeLimiterName()).isEqualTo("timeout");
        assertThat(timeLimiterDto.getType()).isEqualTo(timeLimiterEvent.getEventType());
    }

    @Test
    void exposesCircuitBreakerHystrixStreamEventPayload() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(35.0f)
                .slowCallRateThreshold(65.0f)
                .build();
        CircuitBreaker circuitBreaker = CircuitBreaker.of("orders", config);
        circuitBreaker.onSuccess(15, TimeUnit.MILLISECONDS);
        CircuitBreaker.Metrics metrics = circuitBreaker.getMetrics();
        CircuitBreakerOnSuccessEvent recentEvent = new CircuitBreakerOnSuccessEvent(
                circuitBreaker.getName(), Duration.ofMillis(15));

        CircuitBreakerHystrixStreamEventsDTO dto = new CircuitBreakerHystrixStreamEventsDTO(
                recentEvent, circuitBreaker.getState(), metrics, config);

        assertThat(dto.getCircuitBreakerRecentEvent()).isSameAs(recentEvent);
        assertThat(dto.getCurrentState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(dto.getMetrics()).isSameAs(metrics);
        assertThat(dto.getMetrics().getNumberOfBufferedCalls()).isEqualTo(1);
        assertThat(dto.getFailureRateThreshold()).isEqualTo(35.0f);
        assertThat(dto.getSlowCallRateThreshold()).isEqualTo(65.0f);
    }

    @Test
    void monitoringEndpointResponsesRetainPayloads() {
        CircuitBreakerDetails details = new CircuitBreakerDetails();
        details.setFailureRate("25.0%");
        details.setSlowCallRate("0.0%");
        details.setFailureRateThreshold("50.0%");
        details.setSlowCallRateThreshold("100.0%");
        details.setBufferedCalls(4);
        details.setFailedCalls(1);
        details.setSlowCalls(0);
        details.setSlowFailedCalls(0);
        details.setNotPermittedCalls(3L);
        details.setState(CircuitBreaker.State.CLOSED);

        CircuitBreakerEndpointResponse circuitBreakers = new CircuitBreakerEndpointResponse(Map.of("orders", details));
        assertThat(circuitBreakers.getCircuitBreakers()).containsEntry("orders", details);
        assertThat(circuitBreakers.getCircuitBreakers().get("orders").getFailedCalls()).isEqualTo(1);

        CircuitBreakerUpdateStateResponse stateResponse = new CircuitBreakerUpdateStateResponse();
        stateResponse.setCircuitBreakerName("orders");
        stateResponse.setCurrentState(UpdateState.FORCE_OPEN.name());
        stateResponse.setMessage("updated");
        CircuitBreakerUpdateStateResponse equalStateResponse = new CircuitBreakerUpdateStateResponse();
        equalStateResponse.setCircuitBreakerName("orders");
        equalStateResponse.setCurrentState(UpdateState.FORCE_OPEN.name());
        equalStateResponse.setMessage("updated");
        assertThat(stateResponse).isEqualTo(equalStateResponse).hasSameHashCodeAs(equalStateResponse);
        assertThat(stateResponse.toString()).contains("orders", "updated");

        BulkheadEndpointResponse bulkheadEndpoint = new BulkheadEndpointResponse(List.of("bulkhead"));
        assertThat(bulkheadEndpoint.getBulkheads()).containsExactly("bulkhead");
        RateLimiterEndpointResponse rateLimiterEndpoint = new RateLimiterEndpointResponse(List.of("limiter"));
        assertThat(rateLimiterEndpoint.getRateLimiters()).containsExactly("limiter");
        RetryEndpointResponse retryEndpoint = new RetryEndpointResponse(List.of("retry"));
        assertThat(retryEndpoint.getRetries()).containsExactly("retry");
        TimeLimiterEndpointResponse timeLimiterEndpoint = new TimeLimiterEndpointResponse(List.of("timeout"));
        assertThat(timeLimiterEndpoint.getTimeLimiters()).containsExactly("timeout");

        CircuitBreakerEventDTO circuitBreakerEvent = CircuitBreakerEventDTOFactory.createCircuitBreakerEventDTO(
                new CircuitBreakerOnErrorEvent("orders", Duration.ofMillis(1), new RuntimeException("event")));
        BulkheadEventDTO bulkheadEvent = BulkheadEventDTOFactory.createBulkheadEventDTO(
                new BulkheadOnCallPermittedEvent("bulkhead"));
        RetryEventDTO retryEvent = RetryEventDTOFactory.createRetryEventDTO(
                new RetryOnRetryEvent("retry", 1, new RuntimeException("retry"), 1L));
        assertThat(new CircuitBreakerEventsEndpointResponse(List.of(circuitBreakerEvent)).getCircuitBreakerEvents())
                .containsExactly(circuitBreakerEvent);
        assertThat(new BulkheadEventsEndpointResponse(List.of(bulkheadEvent)).getBulkheadEvents())
                .containsExactly(bulkheadEvent);
        assertThat(new RateLimiterEventsEndpointResponse(List.of(new RateLimiterEventDTO())).getRateLimiterEvents())
                .hasSize(1);
        assertThat(new RetryEventsEndpointResponse(List.of(retryEvent)).getRetryEvents()).containsExactly(retryEvent);
        assertThat(new TimeLimiterEventsEndpointResponse(List.of(new TimeLimiterEventDTO())).getTimeLimiterEvents())
                .hasSize(1);
    }
}
