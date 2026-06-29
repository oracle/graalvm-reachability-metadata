/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micrometer.micrometer_core;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.hazelcast.internal.json.JsonObject;
import com.hazelcast.monitor.LocalIndexStats;
import com.hazelcast.monitor.LocalMapStats;
import com.hazelcast.monitor.NearCacheStats;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.cache.HazelcastCacheMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class HazelcastIMapAdapterInnerLocalMapStatsTest {
    @Test
    void bindsMetricsFromHazelcastLocalMapStats() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        StatsIMap cache = new StatsIMap("orders", new TestLocalMapStats());

        HazelcastCacheMetrics.monitor(registry, cache);

        Gauge ownedEntries = registry.find("cache.entries")
                .tag("cache", "orders")
                .tag("ownership", "owned")
                .gauge();
        Gauge backupMemory = registry.find("cache.entry.memory")
                .tag("cache", "orders")
                .tag("ownership", "backup")
                .gauge();
        FunctionCounter hitCounter = registry.find("cache.gets")
                .tag("cache", "orders")
                .tag("result", "hit")
                .functionCounter();
        FunctionCounter putCounter = registry.find("cache.puts")
                .tag("cache", "orders")
                .functionCounter();
        FunctionTimer getLatency = registry.find("cache.gets.latency")
                .tag("cache", "orders")
                .functionTimer();

        assertThat(ownedEntries.value()).isEqualTo(3.0);
        assertThat(backupMemory.value()).isEqualTo(64.0);
        assertThat(hitCounter.count()).isEqualTo(11.0);
        assertThat(putCounter.count()).isEqualTo(2.0);
        assertThat(getLatency.count()).isEqualTo(4.0);
        assertThat(getLatency.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(25.0);
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
        @Override
        public long getOwnedEntryCount() {
            return 3;
        }

        @Override
        public long getBackupEntryCount() {
            return 1;
        }

        @Override
        public int getBackupCount() {
            return 0;
        }

        @Override
        public long getOwnedEntryMemoryCost() {
            return 128;
        }

        @Override
        public long getBackupEntryMemoryCost() {
            return 64;
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
            return 11;
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
            return 2;
        }

        @Override
        public long getSetOperationCount() {
            return 0;
        }

        @Override
        public long getGetOperationCount() {
            return 4;
        }

        @Override
        public long getRemoveOperationCount() {
            return 1;
        }

        @Override
        public long getTotalPutLatency() {
            return 12;
        }

        @Override
        public long getTotalSetLatency() {
            return 0;
        }

        @Override
        public long getTotalGetLatency() {
            return 25;
        }

        @Override
        public long getTotalRemoveLatency() {
            return 7;
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
            return getPutOperationCount() + getGetOperationCount() + getRemoveOperationCount();
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
            return null;
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
}
