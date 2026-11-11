/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_micrometer_metrics;

import java.time.Duration;
import java.util.Map;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Counter;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.Test;

import org.springframework.boot.micrometer.metrics.autoconfigure.export.prometheus.PrometheusMetricsExportAutoConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

public class PrometheusPropertiesConfigAdapterTest {

    @Test
    void defaultConfigurationExportsCounterWithDescription() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            configure(context, Map.of());
            PrometheusConfig config = context.getBean(PrometheusConfig.class);
            PrometheusMeterRegistry registry = context.getBean(PrometheusMeterRegistry.class);

            assertThat(config.prefix()).isEqualTo("management.prometheus.metrics.export");
            assertThat(config.step()).isEqualTo(Duration.ofMinutes(1));
            assertThat(config.descriptions()).isTrue();
            Counter.builder("orders.processed").description("Completed orders").register(registry).increment(4.0);

            assertThat(registry.scrape()).contains("orders_processed_total 4.0")
                    .contains("# HELP orders_processed_total Completed orders");
        }
    }

    @Test
    void boundPropertiesConfigureExporterAndSuppressDescriptions() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            configure(context, Map.of("management.prometheus.metrics.export.step", "15s",
                    "management.prometheus.metrics.export.descriptions", false,
                    "management.prometheus.metrics.export.properties[io.prometheus.exporter.includeCreatedTimestamps]",
                    "false"));
            PrometheusConfig config = context.getBean(PrometheusConfig.class);
            PrometheusMeterRegistry registry = context.getBean(PrometheusMeterRegistry.class);

            assertThat(config.step()).isEqualTo(Duration.ofSeconds(15));
            assertThat(config.descriptions()).isFalse();
            assertThat(config.prometheusProperties()).containsEntry("io.prometheus.exporter.includeCreatedTimestamps", "false");
            Counter.builder("orders.processed").description("Completed orders").register(registry).increment(7.0);

            assertThat(registry.scrape()).contains("orders_processed_total 7.0")
                    .doesNotContain("# HELP orders_processed_total Completed orders");
        }
    }

    private static void configure(AnnotationConfigApplicationContext context, Map<String, Object> properties) {
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("prometheus-export", properties));
        context.registerBean(Clock.class, () -> Clock.SYSTEM);
        context.register(PrometheusMetricsExportAutoConfiguration.class);
        context.refresh();
    }

}
