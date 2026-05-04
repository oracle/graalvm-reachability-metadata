/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micrometer.micrometer_core;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ProcessorMetricsTest {
    @Test
    void bindsAndSamplesProcessorGauges() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        try {
            ProcessorMetrics metrics = new ProcessorMetrics(Tags.of("area", "processor"));

            metrics.bindTo(registry);

            Gauge processorCount = registry.get("system.cpu.count").tag("area", "processor").gauge();
            Gauge systemCpuUsage = registry.get("system.cpu.usage").tag("area", "processor").gauge();
            Gauge processCpuUsage = registry.get("process.cpu.usage").tag("area", "processor").gauge();

            assertThat(processorCount.value()).isGreaterThan(0.0);
            assertThat(systemCpuUsage.value()).isNotNaN().isBetween(-1.0, 1.0);
            assertThat(processCpuUsage.value()).isNotNaN().isBetween(-1.0, 1.0);
        } finally {
            registry.close();
        }
    }
}
