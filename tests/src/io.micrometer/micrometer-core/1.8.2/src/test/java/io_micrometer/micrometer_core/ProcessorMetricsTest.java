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
    void bindsProcessorGaugesFromPlatformOperatingSystemBean() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        try {
            new ProcessorMetrics(Collections.singleton(Tag.of("source", "platform"))).bindTo(registry);

            Gauge processorCount = registry.get("system.cpu.count").tag("source", "platform").gauge();
            Gauge systemCpuUsage = registry.get("system.cpu.usage").tag("source", "platform").gauge();
            Gauge processCpuUsage = registry.get("process.cpu.usage").tag("source", "platform").gauge();

            assertThat(processorCount.value()).isGreaterThan(0.0d);
            assertSupportedCpuLoadValue(systemCpuUsage.value());
            assertSupportedCpuLoadValue(processCpuUsage.value());
        } finally {
            registry.close();
        }
    }

    private void assertSupportedCpuLoadValue(double value) {
        assertThat(Double.isInfinite(value)).isFalse();
    }
}
