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
    void reportsOpenAndMaximumFileDescriptorsOfTheProcess() {
        MeterRegistry registry = new SimpleMeterRegistry();

        new FileDescriptorMetrics(Tags.of("host", "test")).bindTo(registry);

        Gauge open = registry.get("process.files.open").tag("host", "test").gauge();
        Gauge max = registry.get("process.files.max").tag("host", "test").gauge();
        assertThat(open.getId().getBaseUnit()).isEqualTo("files");
        assertThat(open.value()).isPositive();
        assertThat(max.value()).isGreaterThanOrEqualTo(open.value());
    }
}
