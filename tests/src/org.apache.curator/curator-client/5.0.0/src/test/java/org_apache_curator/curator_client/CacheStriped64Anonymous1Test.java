/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_curator.curator_client;

import org.apache.curator.shaded.com.google.common.cache.Cache;
import org.apache.curator.shaded.com.google.common.cache.CacheBuilder;
import org.apache.curator.shaded.com.google.common.cache.CacheStats;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CacheStriped64Anonymous1Test {
    @Test
    void recordedCacheStatisticsUseConcurrentLongCounters() {
        Cache<String, String> cache = CacheBuilder.newBuilder().recordStats().build();
        cache.put("present", "value");

        assertThat(cache.getIfPresent("present")).isEqualTo("value");
        assertThat(cache.getIfPresent("missing")).isNull();

        CacheStats stats = cache.stats();
        assertThat(stats.hitCount()).isEqualTo(1);
        assertThat(stats.missCount()).isEqualTo(1);
    }
}
