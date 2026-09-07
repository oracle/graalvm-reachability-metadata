/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.google.common.cache.Cache;
import org.testcontainers.shaded.com.google.common.cache.CacheBuilder;

import static org.assertj.core.api.Assertions.assertThat;

public class CacheStriped64Anonymous1Test {
    @Test
    void recordsCacheStatisticsWithStripedCounters() {
        Cache<String, String> cache = CacheBuilder.newBuilder().recordStats().build();
        cache.put("key", "value");

        assertThat(cache.getIfPresent("key")).isEqualTo("value");
        assertThat(cache.getIfPresent("missing")).isNull();
        assertThat(cache.stats().hitCount()).isEqualTo(1);
        assertThat(cache.stats().missCount()).isEqualTo(1);
    }
}
