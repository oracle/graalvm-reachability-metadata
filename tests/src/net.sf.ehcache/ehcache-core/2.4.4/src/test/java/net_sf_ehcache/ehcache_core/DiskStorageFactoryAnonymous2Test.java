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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class DiskStorageFactoryAnonymous2Test {
    private static final String CACHE_NAME = "disk-storage-factory-context-loader-fallback";

    @TempDir
    Path diskStoreDirectory;

    @Test
    void fallsBackToObjectInputStreamResolutionWhenContextClassLoaderCannotLoadElement() {
        writePersistentEntry();

        CacheManager cacheManager = newCacheManager();
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            Cache cache = cacheManager.getCache(CACHE_NAME);
            Thread.currentThread().setContextClassLoader(null);
            Element element = cache.get("disk-key");
            Thread.currentThread().setContextClassLoader(originalClassLoader);

            assertThat(element).isNotNull();
            assertThat(element.getObjectValue()).isEqualTo("disk-value");
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
            cacheManager.shutdown();
        }
    }

    private void writePersistentEntry() {
        CacheManager cacheManager = newCacheManager();
        try {
            Cache cache = cacheManager.getCache(CACHE_NAME);
            cache.put(new Element("disk-key", "disk-value"));
            cache.flush();
        } finally {
            cacheManager.shutdown();
        }
    }

    private CacheManager newCacheManager() {
        Configuration configuration = new Configuration()
                .name(CACHE_NAME + "-manager")
                .updateCheck(false)
                .diskStore(new DiskStoreConfiguration().path(diskStoreDirectory.toString()))
                .cache(new CacheConfiguration(CACHE_NAME, 1)
                        .overflowToDisk(true)
                        .diskPersistent(true)
                        .eternal(true)
                        .diskExpiryThreadIntervalSeconds(120)
                        .memoryStoreEvictionPolicy(MemoryStoreEvictionPolicy.FIFO));
        return new CacheManager(configuration);
    }
}
