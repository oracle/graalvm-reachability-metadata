/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micrometer.micrometer_core;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

public class ProcessorMetricsTest {
    @Test
    void bindsProcessorGaugesAndReadsCpuUsageValues() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ProcessorMetrics metrics = new ProcessorMetrics(Collections.singletonList(Tag.of("scenario", "processor")));

        metrics.bindTo(registry);

        Gauge cpuCount = registry.get("system.cpu.count").tag("scenario", "processor").gauge();
        Gauge systemCpuUsage = registry.get("system.cpu.usage").tag("scenario", "processor").gauge();
        Gauge processCpuUsage = registry.get("process.cpu.usage").tag("scenario", "processor").gauge();

        assertThat(cpuCount.value()).isFinite().isGreaterThan(0.0);
        assertCpuUsageValue(systemCpuUsage.value());
        assertCpuUsageValue(processCpuUsage.value());
    }

    private static void assertCpuUsageValue(double value) {
        assertThat(value).isFinite().isBetween(-1.0, 1.0);
    }
}
