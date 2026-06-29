/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micrometer.micrometer_core;

import java.util.Collections;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ProcessorMetricsTest {
    @Test
    void bindsAndSamplesProcessorGauges() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        try {
            ProcessorMetrics processorMetrics = new ProcessorMetrics(Collections.singleton(Tag.of("source", "test")));

            processorMetrics.bindTo(registry);

            Gauge processorCount = registry.find("system.cpu.count").tag("source", "test").gauge();
            Gauge systemCpuUsage = registry.find("system.cpu.usage").tag("source", "test").gauge();
            Gauge processCpuUsage = registry.find("process.cpu.usage").tag("source", "test").gauge();

            assertThat(processorCount).isNotNull();
            assertThat(systemCpuUsage).isNotNull();
            assertThat(processCpuUsage).isNotNull();

            assertThat(processorCount.value()).isGreaterThanOrEqualTo(1.0);
            assertThat(Double.isNaN(systemCpuUsage.value())).isFalse();
            assertThat(Double.isNaN(processCpuUsage.value())).isFalse();
        } finally {
            registry.close();
        }
    }
}
