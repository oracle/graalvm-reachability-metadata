/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_micrometer_metrics;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.CountingMode;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import org.springframework.boot.micrometer.metrics.autoconfigure.export.simple.SimpleMetricsExportAutoConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

public class PropertiesConfigAdapterTest {

    @Test
    void boundPropertiesProvideConfigurationToAutoConfiguredRegistry() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            Map<String, Object> properties = Map.of("management.simple.metrics.export.step", "20s",
                    "management.simple.metrics.export.mode", "cumulative");
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("simple-export", properties));
            context.registerBean(Clock.class, () -> Clock.SYSTEM);
            context.register(SimpleMetricsExportAutoConfiguration.class);
            context.refresh();

            SimpleConfig config = context.getBean(SimpleConfig.class);
            SimpleMeterRegistry registry = context.getBean(SimpleMeterRegistry.class);
            assertThat(config.step()).isEqualTo(Duration.ofSeconds(20));
            assertThat(config.mode()).isEqualTo(CountingMode.CUMULATIVE);

            Timer timer = registry.timer("configured.processing");
            timer.record(Duration.ofMillis(125));
            timer.record(Duration.ofMillis(375));

            assertThat(timer.count()).isEqualTo(2);
            assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(500.0);
            assertThat(registry.get("configured.processing").timer()).isSameAs(timer);
        }
    }

}
