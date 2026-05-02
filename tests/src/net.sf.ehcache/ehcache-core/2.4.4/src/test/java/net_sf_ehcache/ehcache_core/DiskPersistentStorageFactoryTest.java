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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class DiskPersistentStorageFactoryTest {
    private static final String CACHE_NAME = "disk-persistent-storage-factory-coverage";

    @TempDir
    Path diskStoreDirectory;

    @Test
    void reloadsMultipleEntriesFromPersistentDiskIndex() {
        writePersistentEntries();

        CacheManager cacheManager = newCacheManager();
        try {
            Cache cache = cacheManager.getCache(CACHE_NAME);

            assertThat(cache.get("first-key")).extracting(Element::getObjectValue).isEqualTo("first-value");
            assertThat(cache.get("second-key")).extracting(Element::getObjectValue).isEqualTo("second-value");
        } finally {
            cacheManager.shutdown();
        }
    }

    private void writePersistentEntries() {
        CacheManager cacheManager = newCacheManager();
        try {
            Cache cache = cacheManager.getCache(CACHE_NAME);
            cache.put(new Element("first-key", "first-value"));
            cache.put(new Element("second-key", "second-value"));
        } finally {
            cacheManager.shutdown();
        }
    }

    private CacheManager newCacheManager() {
        Configuration configuration = new Configuration()
                .name(CACHE_NAME + "-manager")
                .updateCheck(false)
                .diskStore(new DiskStoreConfiguration().path(diskStoreDirectory.toString()))
                .cache(new CacheConfiguration(CACHE_NAME, 2)
                        .overflowToDisk(true)
                        .diskPersistent(true)
                        .eternal(true)
                        .diskExpiryThreadIntervalSeconds(120));
        return new CacheManager(configuration);
    }
}
