/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_micrometer_metrics_test;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;

import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.assertj.core.api.Assertions.assertThat;

@AutoConfigureMetrics
@SpringJUnitConfig(AutoConfigureMetricsSpringContextTest.TestConfiguration.class)
public class AutoConfigureMetricsSpringContextTest {

    @Test
    void annotationAddsInMemoryMetricsAndObservationRegistries(ApplicationContext context) {
        assertThat(context.getBeansOfType(MeterRegistry.class).values())
                .isNotEmpty()
                .anyMatch(SimpleMeterRegistry.class::isInstance);
        assertThat(context.getBean(ObservationRegistry.class)).isNotSameAs(ObservationRegistry.NOOP);
    }

    @Configuration(proxyBeanMethods = false)
    public static class TestConfiguration {
    }
}
