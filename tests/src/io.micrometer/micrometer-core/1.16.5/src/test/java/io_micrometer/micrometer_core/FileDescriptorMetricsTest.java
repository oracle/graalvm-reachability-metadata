/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micrometer.micrometer_core;

import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.system.FileDescriptorMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FileDescriptorMetricsTest {

    @Test
    void reportsOpenAndMaximumFileDescriptors() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        try {
            new FileDescriptorMetrics(Tags.of("application", "files")).bindTo(registry);

            double open = registry.get("process.files.open").tag("application", "files").gauge().value();
            double maximum = registry.get("process.files.max").tag("application", "files").gauge().value();
            assertThat(open).isGreaterThan(0.0);
            assertThat(maximum).isGreaterThanOrEqualTo(open);
        } finally {
            registry.close();
        }
    }
}
