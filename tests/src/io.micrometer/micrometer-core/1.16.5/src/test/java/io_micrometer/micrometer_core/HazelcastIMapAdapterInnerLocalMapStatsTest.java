/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micrometer.micrometer_core;

import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.binder.cache.HazelcastCacheMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class HazelcastIMapAdapterInnerLocalMapStatsTest {

    @Test
    void exposesStorageCountsHitsAndOperationTimings() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        try (HazelcastFixture cache = new HazelcastFixture(false)) {
            cache.map.put("first", "pending");
            cache.map.set("second", "ready");
            assertThat(cache.map.get("first")).isEqualTo("pending");
            assertThat(cache.map.remove("second")).isEqualTo("ready");
            HazelcastCacheMetrics.monitor(registry, cache.map);

            assertThat(registry.get("cache.entries").tag("ownership", "owned").gauge().value()).isEqualTo(1.0);
            assertThat(registry.get("cache.entries").tag("ownership", "backup").gauge().value()).isZero();
            assertThat(registry.get("cache.entry.memory").tag("ownership", "owned").gauge().value())
                    .isGreaterThan(0.0);
            assertThat(registry.get("cache.entry.memory").tag("ownership", "backup").gauge().value()).isZero();
            assertThat(registry.get("cache.gets").tag("result", "hit").functionCounter().count()).isEqualTo(1.0);
            assertThat(registry.get("cache.puts").functionCounter().count()).isEqualTo(2.0);
            assertThat(registry.get("cache.partition.gets").functionCounter().count()).isEqualTo(1.0);
            assertThat(registry.get("cache.gets.latency").functionTimer().count()).isEqualTo(1);
            assertThat(registry.get("cache.puts.latency").functionTimer().count()).isEqualTo(1);
            assertThat(registry.get("cache.removals.latency").functionTimer().count()).isEqualTo(1);
            assertThat(registry.get("cache.gets.latency").functionTimer().totalTime(TimeUnit.NANOSECONDS))
                    .isGreaterThanOrEqualTo(0.0);
            assertThat(registry.get("cache.puts.latency").functionTimer().totalTime(TimeUnit.NANOSECONDS))
                    .isGreaterThanOrEqualTo(0.0);
            assertThat(registry.get("cache.removals.latency").functionTimer().totalTime(TimeUnit.NANOSECONDS))
                    .isGreaterThanOrEqualTo(0.0);
        } finally {
            registry.close();
        }
    }
}
