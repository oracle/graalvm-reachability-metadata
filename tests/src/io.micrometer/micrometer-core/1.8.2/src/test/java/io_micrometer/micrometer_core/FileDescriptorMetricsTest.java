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
    void bindsFileDescriptorGaugesFromPlatformOperatingSystemBean() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        new FileDescriptorMetrics(Collections.singleton(Tag.of("source", "platform"))).bindTo(registry);

        Gauge openFiles = registry.find("process.files.open").tag("source", "platform").gauge();
        Gauge maxFiles = registry.find("process.files.max").tag("source", "platform").gauge();
        assertThat(openFiles).isNotNull();
        assertThat(maxFiles).isNotNull();
        assertThat(openFiles.value()).isFinite().isGreaterThanOrEqualTo(0.0d);
        assertThat(maxFiles.value()).isFinite().isGreaterThan(0.0d);
    }
}
