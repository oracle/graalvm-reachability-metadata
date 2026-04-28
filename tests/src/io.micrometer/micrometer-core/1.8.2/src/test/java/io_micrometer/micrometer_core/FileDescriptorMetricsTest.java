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
import io.micrometer.core.instrument.binder.system.FileDescriptorMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FileDescriptorMetricsTest {
    @Test
    void bindsFileDescriptorGaugesFromOperatingSystemMxBean() {
        MeterRegistry registry = new SimpleMeterRegistry();
        Tags tags = Tags.of("component", "file-descriptors");

        new FileDescriptorMetrics(tags).bindTo(registry);

        Gauge openFiles = registry.get("process.files.open").tags(tags).gauge();
        Gauge maxFiles = registry.get("process.files.max").tags(tags).gauge();

        assertThat(openFiles.getId().getBaseUnit()).isEqualTo("files");
        assertThat(maxFiles.getId().getBaseUnit()).isEqualTo("files");
        assertThat(openFiles.value()).isGreaterThanOrEqualTo(0.0);
        assertThat(maxFiles.value()).isGreaterThan(0.0);
    }
}
