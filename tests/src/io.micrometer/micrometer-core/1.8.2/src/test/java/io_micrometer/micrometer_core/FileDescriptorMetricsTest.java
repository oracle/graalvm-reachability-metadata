/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micrometer.micrometer_core;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.system.FileDescriptorMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FileDescriptorMetricsTest {
    @Test
    void bindsUnixFileDescriptorGauges() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        try {
            FileDescriptorMetrics metrics = new FileDescriptorMetrics(Tags.of("area", "descriptor"));

            metrics.bindTo(registry);

            Gauge openFiles = registry.get("process.files.open").tag("area", "descriptor").gauge();
            Gauge maxFiles = registry.get("process.files.max").tag("area", "descriptor").gauge();

            assertThat(openFiles.value()).isNotNaN();
            assertThat(maxFiles.value()).isNotNaN();
        } finally {
            registry.close();
        }
    }
}
