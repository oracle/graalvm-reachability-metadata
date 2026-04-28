/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micrometer.micrometer_core;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ProcessorMetricsTest {
    @Test
    void bindsProcessorGaugesFromOperatingSystemMxBean() {
        MeterRegistry registry = new SimpleMeterRegistry();
        Tags tags = Tags.of("component", "processor");

        new ProcessorMetrics(tags).bindTo(registry);

        Gauge cpuCount = registry.get("system.cpu.count").tags(tags).gauge();
        Gauge systemCpuUsage = registry.get("system.cpu.usage").tags(tags).gauge();
        Gauge processCpuUsage = registry.get("process.cpu.usage").tags(tags).gauge();

        assertThat(cpuCount.value()).isGreaterThan(0.0);
        assertThat(systemCpuUsage.value()).isBetween(-1.0, 1.0);
        assertThat(processCpuUsage.value()).isBetween(-1.0, 1.0);
    }
}
