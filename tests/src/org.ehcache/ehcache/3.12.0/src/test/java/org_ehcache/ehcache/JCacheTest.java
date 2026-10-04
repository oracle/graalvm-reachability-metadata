/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_ehcache.ehcache;

import org.ehcache.jsr107.EhcacheCachingProvider;
import org.junit.jupiter.api.Test;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.configuration.CompleteConfiguration;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.spi.CachingProvider;

import static org.assertj.core.api.Assertions.assertThat;

class JCacheTest {
    @Test
    void testJCacheOrigin() {
        CachingProvider cachingProvider = Caching.getCachingProvider(EhcacheCachingProvider.class.getName());
        CacheManager cacheManager = cachingProvider.getCacheManager();
        CompleteConfiguration<String, String> config = new MutableConfiguration<String, String>()
                .setTypes(String.class, String.class)
                .setStatisticsEnabled(true)
                .setReadThrough(false)
                .setManagementEnabled(true)
                .setStoreByValue(false)
                .setWriteThrough(false);
        Cache<String, String> cache = cacheManager.createCache("example", config);
        cache.put("world", "Hello World");
        assertThat(cache.get("world")).isEqualTo("Hello World");
        assertThat(cacheManager.getCache("example", String.class, String.class)).isNotNull();
    }
}
