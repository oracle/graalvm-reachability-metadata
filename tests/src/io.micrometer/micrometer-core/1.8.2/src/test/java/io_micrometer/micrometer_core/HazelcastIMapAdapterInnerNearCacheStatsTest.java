/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micrometer.micrometer_core;

import java.util.Collections;
import java.util.Map;

import com.hazelcast.internal.json.JsonObject;
import com.hazelcast.monitor.LocalIndexStats;
import com.hazelcast.monitor.LocalMapStats;
import com.hazelcast.monitor.NearCacheStats;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.cache.HazelcastCacheMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class HazelcastIMapAdapterInnerNearCacheStatsTest {
    @Test
    void bindsMetricsFromHazelcastNearCacheStats() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        StatsIMap cache = new StatsIMap("customers", new TestLocalMapStats(new TestNearCacheStats()));

        HazelcastCacheMetrics.monitor(registry, cache);

        Gauge hits = registry.find("cache.near.requests")
                .tag("cache", "customers")
                .tag("result", "hit")
                .gauge();
        Gauge misses = registry.find("cache.near.requests")
                .tag("cache", "customers")
                .tag("result", "miss")
                .gauge();
        Gauge evictions = registry.find("cache.near.evictions")
                .tag("cache", "customers")
                .gauge();
        Gauge persistences = registry.find("cache.near.persistences")
                .tag("cache", "customers")
                .gauge();

        assertThat(hits.value()).isEqualTo(13.0);
        assertThat(misses.value()).isEqualTo(5.0);
        assertThat(evictions.value()).isEqualTo(2.0);
        assertThat(persistences.value()).isEqualTo(1.0);
    }

    private static final class StatsIMap extends HazelcastIMapAdapterTest.TestIMap {
        private final LocalMapStats localMapStats;

        private StatsIMap(String name, LocalMapStats localMapStats) {
            super(name);
            this.localMapStats = localMapStats;
        }

        @Override
        public LocalMapStats getLocalMapStats() {
            return localMapStats;
        }
    }

    private static final class TestLocalMapStats implements LocalMapStats {
        private final NearCacheStats nearCacheStats;

        private TestLocalMapStats(NearCacheStats nearCacheStats) {
            this.nearCacheStats = nearCacheStats;
        }

        @Override
        public long getOwnedEntryCount() {
            return 0;
        }

        @Override
        public long getBackupEntryCount() {
            return 0;
        }

        @Override
        public int getBackupCount() {
            return 0;
        }

        @Override
        public long getOwnedEntryMemoryCost() {
            return 0;
        }

        @Override
        public long getBackupEntryMemoryCost() {
            return 0;
        }

        @Override
        public long getCreationTime() {
            return 0;
        }

        @Override
        public long getLastAccessTime() {
            return 0;
        }

        @Override
        public long getLastUpdateTime() {
            return 0;
        }

        @Override
        public long getHits() {
            return 0;
        }

        @Override
        public long getLockedEntryCount() {
            return 0;
        }

        @Override
        public long getDirtyEntryCount() {
            return 0;
        }

        @Override
        public long getPutOperationCount() {
            return 0;
        }

        @Override
        public long getSetOperationCount() {
            return 0;
        }

        @Override
        public long getGetOperationCount() {
            return 0;
        }

        @Override
        public long getRemoveOperationCount() {
            return 0;
        }

        @Override
        public long getTotalPutLatency() {
            return 0;
        }

        @Override
        public long getTotalSetLatency() {
            return 0;
        }

        @Override
        public long getTotalGetLatency() {
            return 0;
        }

        @Override
        public long getTotalRemoveLatency() {
            return 0;
        }

        @Override
        public long getMaxPutLatency() {
            return 0;
        }

        @Override
        public long getMaxSetLatency() {
            return 0;
        }

        @Override
        public long getMaxGetLatency() {
            return 0;
        }

        @Override
        public long getMaxRemoveLatency() {
            return 0;
        }

        @Override
        public long getEventOperationCount() {
            return 0;
        }

        @Override
        public long getOtherOperationCount() {
            return 0;
        }

        @Override
        public long total() {
            return 0;
        }

        @Override
        public long getHeapCost() {
            return 0;
        }

        @Override
        public long getMerkleTreesCost() {
            return 0;
        }

        @Override
        public NearCacheStats getNearCacheStats() {
            return nearCacheStats;
        }

        @Override
        public long getQueryCount() {
            return 0;
        }

        @Override
        public long getIndexedQueryCount() {
            return 0;
        }

        @Override
        public Map<String, LocalIndexStats> getIndexStats() {
            return Collections.emptyMap();
        }

        @Override
        public JsonObject toJson() {
            return new JsonObject();
        }

        @Override
        public void fromJson(JsonObject json) {
        }
    }

    private static final class TestNearCacheStats implements NearCacheStats {
        @Override
        public long getCreationTime() {
            return 0;
        }

        @Override
        public long getOwnedEntryCount() {
            return 0;
        }

        @Override
        public long getOwnedEntryMemoryCost() {
            return 0;
        }

        @Override
        public long getHits() {
            return 13;
        }

        @Override
        public long getMisses() {
            return 5;
        }

        @Override
        public double getRatio() {
            return 0;
        }

        @Override
        public long getEvictions() {
            return 2;
        }

        @Override
        public long getExpirations() {
            return 0;
        }

        @Override
        public long getInvalidations() {
            return 0;
        }

        @Override
        public long getPersistenceCount() {
            return 1;
        }

        @Override
        public long getLastPersistenceTime() {
            return 0;
        }

        @Override
        public long getLastPersistenceDuration() {
            return 0;
        }

        @Override
        public long getLastPersistenceWrittenBytes() {
            return 0;
        }

        @Override
        public long getLastPersistenceKeyCount() {
            return 0;
        }

        @Override
        public String getLastPersistenceFailure() {
            return null;
        }

        @Override
        public JsonObject toJson() {
            return new JsonObject();
        }

        @Override
        public void fromJson(JsonObject json) {
        }
    }
}
