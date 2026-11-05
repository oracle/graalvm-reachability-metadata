/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_modulith.spring_modulith_observability_api;

import io.micrometer.common.docs.KeyName;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.observability.ModulithMetrics;
import org.springframework.modulith.observability.ModulithMetrics.LowKeys;

import static org.assertj.core.api.Assertions.assertThat;

public class ModulithMetricsTest {

    @Test
    void recordsPublishedEventsUsingTheDocumentedCounterAndTagNames() {
        ModulithMetrics documentation = ModulithMetrics.ALL_EVENTS;
        assertThat(documentation.getType()).isEqualTo(Meter.Type.COUNTER);
        assertThat(documentation.getKeyNames()).extracting(KeyName::asString)
                .containsExactlyInAnyOrder("module.event.type", "module.identifier", "module.name");

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        try {
            Counter counter = Counter.builder(documentation.getName())
                    .tag(LowKeys.EVENT_TYPE.asString(), "StockReserved")
                    .tag(LowKeys.MODULE_IDENTIFIER.asString(), "inventory")
                    .tag(LowKeys.MODULE_NAME.asString(), "Inventory")
                    .register(registry);
            counter.increment();
            counter.increment();

            Counter recorded = registry.get("module.events.published")
                    .tags("module.event.type", "StockReserved", "module.identifier", "inventory",
                            "module.name", "Inventory").counter();
            assertThat(recorded).isSameAs(counter);
            assertThat(recorded.count()).isEqualTo(2.0);
        } finally {
            registry.close();
        }
    }
}
