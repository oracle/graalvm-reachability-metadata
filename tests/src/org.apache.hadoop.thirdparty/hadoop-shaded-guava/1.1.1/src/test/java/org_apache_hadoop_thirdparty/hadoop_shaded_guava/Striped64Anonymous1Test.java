/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop_thirdparty.hadoop_shaded_guava;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.hadoop.thirdparty.com.google.common.cache.AbstractCache.SimpleStatsCounter;
import org.apache.hadoop.thirdparty.com.google.common.cache.CacheStats;
import org.junit.jupiter.api.Test;

public class Striped64Anonymous1Test {
    @Test
    void simpleStatsCounterRecordsValuesWithLongAdderBackedCounters() {
        SimpleStatsCounter counter = new SimpleStatsCounter();

        counter.recordHits(2);
        counter.recordMisses(3);
        counter.recordLoadSuccess(11);
        counter.recordLoadException(7);
        counter.recordEviction();

        CacheStats stats = counter.snapshot();
        assertThat(stats.hitCount()).isEqualTo(2);
        assertThat(stats.missCount()).isEqualTo(3);
        assertThat(stats.loadSuccessCount()).isEqualTo(1);
        assertThat(stats.loadExceptionCount()).isEqualTo(1);
        assertThat(stats.totalLoadTime()).isEqualTo(18);
        assertThat(stats.evictionCount()).isEqualTo(1);
    }
}
