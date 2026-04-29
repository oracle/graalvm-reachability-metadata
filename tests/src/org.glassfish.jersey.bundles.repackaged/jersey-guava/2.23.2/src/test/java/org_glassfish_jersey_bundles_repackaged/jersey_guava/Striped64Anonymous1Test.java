/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jersey_bundles_repackaged.jersey_guava;

import jersey.repackaged.com.google.common.cache.AbstractCache.SimpleStatsCounter;
import jersey.repackaged.com.google.common.cache.CacheStats;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Striped64Anonymous1Test {
    @Test
    void simpleStatsCounterRecordsCacheStatisticsWithLongAddables() {
        SimpleStatsCounter counter = new SimpleStatsCounter();

        counter.recordHits(4);
        counter.recordMisses(2);
        counter.recordLoadSuccess(10L);
        counter.recordLoadException(5L);
        counter.recordEviction();

        CacheStats stats = counter.snapshot();

        assertThat(stats.hitCount()).isEqualTo(4L);
        assertThat(stats.missCount()).isEqualTo(2L);
        assertThat(stats.loadSuccessCount()).isEqualTo(1L);
        assertThat(stats.loadExceptionCount()).isEqualTo(1L);
        assertThat(stats.totalLoadTime()).isEqualTo(15L);
        assertThat(stats.evictionCount()).isEqualTo(1L);
    }
}
