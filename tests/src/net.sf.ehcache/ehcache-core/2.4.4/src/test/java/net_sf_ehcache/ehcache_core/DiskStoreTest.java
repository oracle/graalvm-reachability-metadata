/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_ehcache.ehcache_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;
import net.sf.ehcache.config.CacheConfiguration;
import net.sf.ehcache.config.Configuration;
import net.sf.ehcache.config.DiskStoreConfiguration;
import net.sf.ehcache.store.MemoryStoreEvictionPolicy;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class DiskStoreTest {
    private static final String CACHE_NAME = "persistent-disk-store-cache";

    @TempDir
    Path diskStoreDirectory;

    @BeforeAll
    static void disableUpdateChecks() {
        System.setProperty("net.sf.ehcache.skipUpdateCheck", "true");
    }

    @Test
    void persistentClassicDiskStoreRestoresIndexAndLoadsElementFromDisk() {
        System.setProperty(Cache.NET_SF_EHCACHE_USE_CLASSIC_LRU, "true");
        try {
            String diskPath = diskStoreDirectory.toString();

            CacheManager firstManager = newCacheManager("disk-store-writer", diskPath);
            try {
                Cache cache = firstManager.getCache(CACHE_NAME);
                cache.put(new Element("first-key", "first-value"));
                cache.put(new Element("second-key", "second-value"));
                cache.flush();
            } finally {
                firstManager.shutdown();
            }

            CacheManager secondManager = newCacheManager("disk-store-reader", diskPath);
            try {
                Cache restoredCache = secondManager.getCache(CACHE_NAME);

                assertThat(restoredCache.get("first-key").getObjectValue()).isEqualTo("first-value");
                assertThat(restoredCache.get("second-key").getObjectValue()).isEqualTo("second-value");
            } finally {
                secondManager.shutdown();
            }
        } finally {
            System.clearProperty(Cache.NET_SF_EHCACHE_USE_CLASSIC_LRU);
        }
    }

    private static CacheManager newCacheManager(String managerName, String diskPath) {
        Configuration configuration = new Configuration()
                .name(managerName)
                .updateCheck(false)
                .diskStore(new DiskStoreConfiguration().path(diskPath))
                .cache(new CacheConfiguration(CACHE_NAME, 1)
                        .memoryStoreEvictionPolicy(MemoryStoreEvictionPolicy.LRU)
                        .overflowToDisk(true)
                        .diskPersistent(true)
                        .maxElementsOnDisk(10)
                        .eternal(true));
        return new CacheManager(configuration);
    }
}
