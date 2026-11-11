/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micrometer.micrometer_core;

import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ProcessorMetricsTest {

    @Test
    void reportsProcessorCountLoadAndProcessTime() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        try {
            new ProcessorMetrics().bindTo(registry);

            assertThat(registry.get("system.cpu.count").gauge().value())
                    .isEqualTo(Runtime.getRuntime().availableProcessors());
            assertThat(registry.get("system.cpu.usage").gauge().value()).isBetween(-1.0, 1.0);
            assertThat(registry.get("process.cpu.usage").gauge().value()).isBetween(-1.0, 1.0);
            assertThat(registry.get("process.cpu.time").functionCounter().count()).isGreaterThanOrEqualTo(0.0);
        } finally {
            registry.close();
        }
    }
}
