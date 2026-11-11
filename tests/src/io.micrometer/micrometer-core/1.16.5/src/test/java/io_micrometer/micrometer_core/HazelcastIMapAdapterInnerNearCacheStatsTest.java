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

public class HazelcastIMapAdapterInnerNearCacheStatsTest {

    @Test
    void reportsNearCacheHitsMissesAndMaintenanceCounts() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        try (HazelcastFixture cache = new HazelcastFixture(true)) {
            cache.map.put("order", "ready");
            assertThat(cache.map.get("order")).isEqualTo("ready");
            assertThat(cache.map.get("order")).isEqualTo("ready");
            assertThat(cache.map.get("missing")).isNull();
            HazelcastCacheMetrics.monitor(registry, cache.map);

            assertThat(registry.get("cache.near.requests").tag("result", "hit").gauge().value())
                    .isGreaterThanOrEqualTo(1.0);
            assertThat(registry.get("cache.near.requests").tag("result", "miss").gauge().value())
                    .isGreaterThanOrEqualTo(1.0);
            assertThat(registry.get("cache.near.evictions").gauge().value()).isZero();
            assertThat(registry.get("cache.near.persistences").gauge().value()).isZero();
        } finally {
            registry.close();
        }
    }
}
