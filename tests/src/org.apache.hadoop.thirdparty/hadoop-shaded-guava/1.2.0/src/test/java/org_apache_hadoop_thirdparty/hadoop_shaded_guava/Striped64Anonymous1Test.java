/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop_thirdparty.hadoop_shaded_guava;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.hadoop.thirdparty.com.google.common.cache.Cache;
import org.apache.hadoop.thirdparty.com.google.common.cache.CacheBuilder;
import org.apache.hadoop.thirdparty.com.google.common.cache.CacheStats;
import org.junit.jupiter.api.Test;

public class Striped64Anonymous1Test {
    @Test
    void cacheStatsUseCachePackageLongAdder() {
        Cache<String, String> cache = CacheBuilder.newBuilder()
                .recordStats()
                .build();

        cache.put("alpha", "one");

        assertThat(cache.getIfPresent("alpha")).isEqualTo("one");
        assertThat(cache.getIfPresent("missing")).isNull();

        CacheStats stats = cache.stats();
        assertThat(stats.requestCount()).isEqualTo(2);
        assertThat(stats.hitCount()).isEqualTo(1);
        assertThat(stats.missCount()).isEqualTo(1);
    }
}
