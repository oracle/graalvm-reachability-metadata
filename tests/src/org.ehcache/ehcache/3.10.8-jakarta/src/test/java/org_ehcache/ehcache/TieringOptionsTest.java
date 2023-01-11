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
import org.ehcache.config.CacheConfiguration;
import org.ehcache.config.ResourceType;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.config.units.EntryUnit;
import org.ehcache.config.units.MemoryUnit;
import org.ehcache.impl.config.store.disk.OffHeapDiskStoreConfiguration;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@SuppressWarnings("deprecation")
public class TieringOptionsTest {
    @Test
    void testHeapTierAndOffHeapTier() {
        Stream.of(ResourcePoolsBuilder.newResourcePoolsBuilder().heap(10, EntryUnit.ENTRIES),
                        ResourcePoolsBuilder.heap(10),
                        ResourcePoolsBuilder.newResourcePoolsBuilder().heap(10, MemoryUnit.MB),
                        ResourcePoolsBuilder.newResourcePoolsBuilder().offheap(10, MemoryUnit.MB))
                .forEach(resourcePoolsBuilder -> {
                    try (CacheManager cacheManager = CacheManagerBuilder.newCacheManagerBuilder().build(true)) {
                        Cache<Long, String> myCache = cacheManager.createCache("myCache",
                                CacheConfigurationBuilder.newCacheConfigurationBuilder(Long.class, String.class, resourcePoolsBuilder));
                        myCache.put(1L, "da one!");
                        assertThat(myCache.get(1L)).isEqualTo("da one!");
                    }
                });
    }

    @Test
    void testByteSizedHeap() {
        CacheConfiguration<Long, String> usesConfiguredInCacheConfig = CacheConfigurationBuilder.newCacheConfigurationBuilder(Long.class, String.class,
                        ResourcePoolsBuilder.newResourcePoolsBuilder().heap(10, MemoryUnit.KB).offheap(10, MemoryUnit.MB))
                .withSizeOfMaxObjectGraph(1000).withSizeOfMaxObjectSize(1000, MemoryUnit.B).build();
        CacheConfiguration<Long, String> usesDefaultSizeOfEngineConfig = CacheConfigurationBuilder.newCacheConfigurationBuilder(Long.class, String.class,
                ResourcePoolsBuilder.newResourcePoolsBuilder().heap(10, MemoryUnit.KB)).build();
        try (CacheManager cacheManager = CacheManagerBuilder.newCacheManagerBuilder()
                .withDefaultSizeOfMaxObjectSize(500, MemoryUnit.B)
                .withDefaultSizeOfMaxObjectGraph(2000)
                .withCache("usesConfiguredInCache", usesConfiguredInCacheConfig)
                .withCache("usesDefaultSizeOfEngine", usesDefaultSizeOfEngineConfig)
                .build(true)) {
            assertThat(cacheManager.getCache("usesConfiguredInCache", Long.class, String.class)).isNotNull();
            assertThat(cacheManager.getCache("usesDefaultSizeOfEngine", Long.class, String.class)).isNotNull();
            Cache<Long, String> myCache = cacheManager.createCache("myCache",
                    CacheConfigurationBuilder.newCacheConfigurationBuilder(Long.class, String.class, ResourcePoolsBuilder.heap(10)));
            myCache.put(1L, "da one!");
            assertThat(myCache.get(1L)).isEqualTo("da one!");
        }
    }

    @Test
    void testSegmentsAndDestroyPersistentTiers() {
        String dirPathString = "src/test/resources/testSegmentsAndDestroyPersistentTiersData/";
        PersistentCacheManager persistentCacheManager = CacheManagerBuilder.newCacheManagerBuilder()
                .with(CacheManagerBuilder.persistence(dirPathString))
                .withCache("less-segments", CacheConfigurationBuilder.newCacheConfigurationBuilder(Long.class, String.class,
                                ResourcePoolsBuilder.newResourcePoolsBuilder().disk(10, MemoryUnit.MB))
                        .withService(new OffHeapDiskStoreConfiguration(2))).build(true);
        Cache<Long, String> threeTieredCache = persistentCacheManager.getCache("less-segments", Long.class, String.class);
        assertThat(threeTieredCache).isNotNull();
        threeTieredCache.put(1L, "da one!");
        assertThat(threeTieredCache.get(1L)).isEqualTo("da one!");
        persistentCacheManager.close();
        assertDoesNotThrow(() -> persistentCacheManager.destroyCache("less-segments"));
        assertDoesNotThrow(persistentCacheManager::destroy);
        assertDoesNotThrow(() -> Files.delete(Paths.get(dirPathString)));
    }

    @Test
    void testUpdateResourcePools() {
        try (CacheManager cacheManager = CacheManagerBuilder.newCacheManagerBuilder().build(true)) {
            Cache<Long, String> myCache = cacheManager.createCache("myCache",
                    CacheConfigurationBuilder.newCacheConfigurationBuilder(Long.class, String.class, ResourcePoolsBuilder.heap(10L)));
            myCache.put(1L, "da one!");
            assertThat(myCache.get(1L)).isEqualTo("da one!");
            myCache.getRuntimeConfiguration().updateResourcePools(ResourcePoolsBuilder.newResourcePoolsBuilder().heap(20L, EntryUnit.ENTRIES).build());
            assertThat(myCache.getRuntimeConfiguration().getResourcePools().getPoolForResource(ResourceType.Core.HEAP).getSize()).isEqualTo(20L);
        }
    }
}
