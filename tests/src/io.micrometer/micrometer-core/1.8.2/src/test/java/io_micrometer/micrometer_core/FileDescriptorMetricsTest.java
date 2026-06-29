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
import io.micrometer.core.instrument.binder.system.FileDescriptorMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FileDescriptorMetricsTest {
    @Test
    void bindsAndSamplesFileDescriptorGauges() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        try {
            FileDescriptorMetrics fileDescriptorMetrics = new FileDescriptorMetrics(
                    Collections.singleton(Tag.of("source", "test")));

            fileDescriptorMetrics.bindTo(registry);

            Gauge openFiles = registry.find("process.files.open").tag("source", "test").gauge();
            Gauge maxFiles = registry.find("process.files.max").tag("source", "test").gauge();

            assertThat(openFiles).isNotNull();
            assertThat(maxFiles).isNotNull();

            double openFileCount = openFiles.value();
            double maxFileCount = maxFiles.value();

            assertThat(Double.isNaN(openFileCount)).isFalse();
            assertThat(Double.isNaN(maxFileCount)).isFalse();
            assertThat(openFileCount).isGreaterThanOrEqualTo(0.0);
            assertThat(maxFileCount).isGreaterThanOrEqualTo(openFileCount);
        } finally {
            registry.close();
        }
    }
}
