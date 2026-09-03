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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class DiskPersistentStorageFactoryTest {
    @TempDir
    Path diskStoreDirectory;

    @BeforeAll
    static void disableUpdateChecks() {
        System.setProperty("net.sf.ehcache.skipUpdateCheck", "true");
    }

    @Test
    void reloadsMultipleEntriesFromPersistentDiskIndex() {
        String cacheName = "persistent-cache-" + Long.toUnsignedString(System.nanoTime());
        String diskStorePath = diskStoreDirectory.toAbsolutePath().toString();

        CacheManager firstManager = new CacheManager(configuration(cacheName, diskStorePath));
        try {
            Cache cache = firstManager.getCache(cacheName);
            cache.put(new Element("first", "alpha"));
            cache.put(new Element("second", "bravo"));
            cache.flush();

            assertThat(cache.get("first").getValue()).isEqualTo("alpha");
            assertThat(cache.get("second").getValue()).isEqualTo("bravo");
        } finally {
            firstManager.shutdown();
        }

        CacheManager secondManager = new CacheManager(configuration(cacheName, diskStorePath));
        try {
            Cache reloadedCache = secondManager.getCache(cacheName);

            assertThat(reloadedCache.get("first").getValue()).isEqualTo("alpha");
            assertThat(reloadedCache.get("second").getValue()).isEqualTo("bravo");
        } finally {
            secondManager.shutdown();
        }
    }

    private static Configuration configuration(String cacheName, String diskStorePath) {
        CacheConfiguration cacheConfiguration = new CacheConfiguration(cacheName, 2)
                .eternal(true)
                .overflowToDisk(false)
                .diskPersistent(true)
                .maxElementsOnDisk(10);

        return new Configuration()
                .name("manager-" + cacheName)
                .updateCheck(false)
                .diskStore(new DiskStoreConfiguration().path(diskStorePath))
                .cache(cacheConfiguration);
    }
}
