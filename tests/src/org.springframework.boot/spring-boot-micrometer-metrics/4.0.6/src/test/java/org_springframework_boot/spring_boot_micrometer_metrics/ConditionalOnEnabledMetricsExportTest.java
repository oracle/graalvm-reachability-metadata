/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_micrometer_metrics;

import java.util.Map;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import org.springframework.boot.micrometer.metrics.autoconfigure.export.simple.SimpleMetricsExportAutoConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

public class ConditionalOnEnabledMetricsExportTest {

    @Test
    void enablesExporterByDefault() {
        assertExporterEnabled(Map.of());
    }

    @Test
    void disablesExporterWhenGlobalDefaultIsFalse() {
        assertExporterDisabled(Map.of("management.defaults.metrics.export.enabled", false));
    }

    @Test
    void exporterSettingOverridesDisabledGlobalDefault() {
        assertExporterEnabled(Map.of("management.defaults.metrics.export.enabled", false,
                "management.simple.metrics.export.enabled", true));
    }

    @Test
    void exporterSettingOverridesEnabledGlobalDefault() {
        assertExporterDisabled(Map.of("management.defaults.metrics.export.enabled", true,
                "management.simple.metrics.export.enabled", false));
    }

    private static void assertExporterEnabled(Map<String, Object> properties) {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            configure(context, properties);
            SimpleMeterRegistry registry = context.getBean(SimpleMeterRegistry.class);
            registry.counter("export.enabled.requests").increment(3.0);
            assertThat(registry.get("export.enabled.requests").counter().count()).isEqualTo(3.0);
        }
    }

    private static void assertExporterDisabled(Map<String, Object> properties) {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            configure(context, properties);
            assertThat(context.getBeansOfType(MeterRegistry.class)).isEmpty();
            assertThat(context.containsBean("simpleConfig")).isFalse();
        }
    }

    private static void configure(AnnotationConfigApplicationContext context, Map<String, Object> properties) {
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("metrics-export", properties));
        context.registerBean(Clock.class, () -> Clock.SYSTEM);
        context.register(SimpleMetricsExportAutoConfiguration.class);
        context.refresh();
    }

}
