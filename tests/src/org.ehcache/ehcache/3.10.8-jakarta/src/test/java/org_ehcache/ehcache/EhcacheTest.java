/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_ehcache.ehcache;

import org.ehcache.Cache;
import org.ehcache.CacheManager;
import org.ehcache.PersistentCacheManager;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.config.builders.ExpiryPolicyBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.config.units.EntryUnit;
import org.ehcache.config.units.MemoryUnit;
import org.ehcache.xml.XmlConfiguration;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class EhcacheTest {
    @Test
    void testProgrammaticConfiguration() {
        try (CacheManager cacheManager = CacheManagerBuilder.newCacheManagerBuilder()
                .withCache("preConfigured", CacheConfigurationBuilder.newCacheConfigurationBuilder(
                        Long.class, String.class, ResourcePoolsBuilder.heap(10))
                ).build(true)) {
            assertThat(cacheManager.getCache("preConfigured", Long.class, String.class)).isNotNull();
            Cache<Long, String> myCache = cacheManager.createCache("myCache",
                    CacheConfigurationBuilder.newCacheConfigurationBuilder(Long.class, String.class, ResourcePoolsBuilder.heap(10)));
            myCache.put(1L, "da one!");
            assertThat(myCache.get(1L)).isEqualTo("da one!");
            assertDoesNotThrow(() -> cacheManager.removeCache("preConfigured"));
        }
    }

    @Test
    void testXMLConfiguration() {
        URL myUrl = getClass().getResource("/my-config.xml");
        assertThat(myUrl).isNotNull();
        try (CacheManager myCacheManager = CacheManagerBuilder.newCacheManager(new XmlConfiguration(myUrl))) {
            myCacheManager.init();
            assertThat(myCacheManager.getCache("foo", String.class, String.class)).isNotNull();
            assertThat(myCacheManager.getCache("bar", Number.class, String.class)).isNotNull();
            assertThat(myCacheManager.getCache("simpleCache", Long.class, String.class)).isNotNull();
        }
    }

    @Test
    void testStorageTiers() {
        String dirPathString = "src/test/resources/testStorageTiersData/";
        PersistentCacheManager persistentCacheManager = CacheManagerBuilder.newCacheManagerBuilder()
                .with(CacheManagerBuilder.persistence(dirPathString))
                .withCache("threeTieredCache", CacheConfigurationBuilder.newCacheConfigurationBuilder(Long.class, String.class,
                        ResourcePoolsBuilder.newResourcePoolsBuilder()
                                .heap(10, EntryUnit.ENTRIES).offheap(1, MemoryUnit.MB).disk(20, MemoryUnit.MB, true))).build(true);
        Cache<Long, String> threeTieredCache = persistentCacheManager.getCache("threeTieredCache", Long.class, String.class);
        threeTieredCache.put(1L, "stillAvailableAfterRestart");
        assertThat(threeTieredCache.get(1L)).isEqualTo("stillAvailableAfterRestart");
        persistentCacheManager.close();
        assertDoesNotThrow(persistentCacheManager::destroy);
        assertDoesNotThrow(() -> Files.delete(Paths.get(dirPathString)));
    }

    @Test
    void testDataFreshness() {
        try (CacheManager cacheManager = CacheManagerBuilder.newCacheManagerBuilder().build(true)) {
            Cache<Long, String> myCache = cacheManager.createCache("myCache",
                    CacheConfigurationBuilder.newCacheConfigurationBuilder(Long.class, String.class, ResourcePoolsBuilder.heap(100))
                            .withExpiry(ExpiryPolicyBuilder.timeToLiveExpiration(Duration.ofSeconds(20)))
                            .build());
            myCache.put(1L, "da one!");
            assertThat(myCache.get(1L)).isEqualTo("da one!");
        }
    }
}
