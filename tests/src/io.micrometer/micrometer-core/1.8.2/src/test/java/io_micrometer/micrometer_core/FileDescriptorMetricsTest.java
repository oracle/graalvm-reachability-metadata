/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micrometer.micrometer_core;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.system.FileDescriptorMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

public class FileDescriptorMetricsTest {
    @Test
    void bindsFileDescriptorGaugesAndReadsTheirValues() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        FileDescriptorMetrics metrics = new FileDescriptorMetrics(
                Collections.singletonList(Tag.of("scenario", "file-descriptors")));

        metrics.bindTo(registry);

        Gauge openFiles = registry.get("process.files.open").tag("scenario", "file-descriptors").gauge();
        Gauge maxFiles = registry.get("process.files.max").tag("scenario", "file-descriptors").gauge();

        double openFileCount = openFiles.value();
        double maxFileCount = maxFiles.value();

        assertThat(openFileCount).isFinite().isGreaterThanOrEqualTo(0.0);
        assertThat(maxFileCount).isFinite().isGreaterThan(0.0);
        assertThat(maxFileCount).isGreaterThanOrEqualTo(openFileCount);
    }
}
