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
import net.sf.ehcache.Element;
import net.sf.ehcache.config.CacheConfiguration;
import net.sf.ehcache.store.DiskStore;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class DiskStoreTest {
    @TempDir
    Path diskStoreDirectory;

    @Test
    void persistsAndReloadsDiskStoreIndex() {
        String cacheName = "disk-store-index-coverage";
        Cache cache = newPersistentCache(cacheName);
        DiskStore diskStore = DiskStore.create(cache, diskStoreDirectory.toString());

        diskStore.put(new Element("disk-key", "disk-value"));
        diskStore.dispose();

        Cache reloadedCache = newPersistentCache(cacheName);
        DiskStore reloadedDiskStore = DiskStore.create(reloadedCache, diskStoreDirectory.toString());
        try {
            Element element = reloadedDiskStore.get("disk-key");

            assertThat(element).isNotNull();
            assertThat(element.getObjectValue()).isEqualTo("disk-value");
        } finally {
            reloadedDiskStore.dispose();
        }
    }

    private Cache newPersistentCache(String cacheName) {
        CacheConfiguration configuration = new CacheConfiguration(cacheName, 1)
                .overflowToDisk(true)
                .diskPersistent(true)
                .diskStorePath(diskStoreDirectory.toString())
                .eternal(true)
                .diskExpiryThreadIntervalSeconds(120);
        return new Cache(configuration);
    }
}
