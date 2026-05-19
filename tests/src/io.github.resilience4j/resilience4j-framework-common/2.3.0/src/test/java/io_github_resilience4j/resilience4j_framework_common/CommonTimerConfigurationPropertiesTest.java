/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_github_resilience4j.resilience4j_framework_common;

import io.github.resilience4j.common.CompositeCustomizer;
import io.github.resilience4j.common.micrometer.configuration.CommonTimerConfigurationProperties;
import io.github.resilience4j.common.micrometer.configuration.TimerConfigCustomizer;
import io.github.resilience4j.common.micrometer.monitoring.endpoint.TimerEndpointResponse;
import io.github.resilience4j.common.micrometer.monitoring.endpoint.TimerEventDTO;
import io.github.resilience4j.common.micrometer.monitoring.endpoint.TimerEventDTOFactory;
import io.github.resilience4j.common.micrometer.monitoring.endpoint.TimerEventsEndpointResponse;
import io.github.resilience4j.micrometer.TimerConfig;
import io.github.resilience4j.micrometer.event.TimerOnSuccessEvent;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

public class CommonTimerConfigurationPropertiesTest {

    @Test
    void createsTimerConfigWithFailureTagResolverClassAndCustomizer() {
        CommonTimerConfigurationProperties properties = new CommonTimerConfigurationProperties();
        properties.getConfigs().put("shared", new CommonTimerConfigurationProperties.InstanceProperties()
                .setMetricNames("shared.timer")
                .setOnFailureTagResolver(ExceptionSimpleNameResolver.class));
        CommonTimerConfigurationProperties.InstanceProperties instance =
                new CommonTimerConfigurationProperties.InstanceProperties()
                        .setBaseConfig("shared");

        TimerConfig config = properties.createTimerConfig(
                instance,
                new CompositeCustomizer<>(List.of(
                        TimerConfigCustomizer.of("orders", builder -> builder.metricNames("orders.timer")))),
                "orders");

        assertThat(config.getMetricNames()).isEqualTo("orders.timer");
        assertThat(config.getOnFailureTagResolver().apply(new IllegalArgumentException("bad")))
                .isEqualTo("IllegalArgumentException");
    }

    @Test
    void mapsTimerEventsAndEndpointResponses() {
        TimerOnSuccessEvent event = new TimerOnSuccessEvent("ordersTimer", Duration.ofMillis(17));
        TimerEventDTO dto = TimerEventDTOFactory.createTimerEventDTO(event);

        assertThat(dto.getTimerName()).isEqualTo("ordersTimer");
        assertThat(dto.getType()).isEqualTo(event.getEventType());
        assertThat(dto.getOperationDuration()).isEqualTo(Duration.ofMillis(17));
        assertThat(dto.getCreationTime()).isNotBlank();
        assertThat(new TimerEndpointResponse(List.of("ordersTimer")).getTimers()).containsExactly("ordersTimer");
        assertThat(new TimerEventsEndpointResponse(List.of(dto)).getTimerEvents()).containsExactly(dto);
    }

    public static class ExceptionSimpleNameResolver implements Function<Throwable, String> {
        @Override
        public String apply(Throwable throwable) {
            return throwable.getClass().getSimpleName();
        }
    }
}
