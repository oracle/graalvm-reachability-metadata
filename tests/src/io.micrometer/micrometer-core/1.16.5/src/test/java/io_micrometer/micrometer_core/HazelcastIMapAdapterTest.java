/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micrometer.micrometer_core;

import io.micrometer.core.instrument.binder.cache.HazelcastCacheMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class HazelcastIMapAdapterTest {

    @Test
    void monitorsMapByItsDistributedObjectName() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        try (HazelcastFixture cache = new HazelcastFixture(false)) {
            cache.map.put("order", "pending");
            assertThat(cache.map.get("order")).isEqualTo("pending");
            assertThat(HazelcastCacheMetrics.monitor(registry, cache.map)).isSameAs(cache.map);

            assertThat(registry.get("cache.size").tag("cache", "orders").gauge().value()).isEqualTo(1.0);
            assertThat(registry.get("cache.puts").tag("cache", "orders").functionCounter().count()).isEqualTo(1.0);
        } finally {
            registry.close();
        }
    }
}
