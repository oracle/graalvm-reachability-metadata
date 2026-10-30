/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_ehcache.ehcache;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.config.CacheConfiguration;
import net.sf.ehcache.config.Configuration;
import net.sf.ehcache.config.DiskStoreConfiguration;
import net.sf.ehcache.config.FactoryConfiguration;
import net.sf.ehcache.store.MemoryStoreEvictionPolicy;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class TestSupport {
    private TestSupport() {
    }

    static Cache cache(String name, int capacity, MemoryStoreEvictionPolicy policy) {
        Cache cache = new Cache(name, capacity, policy, false, null, true, 0, 0, false, 120, null);
        cache.initialise();
        return cache;
    }

    static Cache persistentCache(Path directory, String name) {
        Cache cache = new Cache(name, 2, MemoryStoreEvictionPolicy.LRU, true,
                directory.toString(), true, 0, 0, true, 120, null);
        cache.initialise();
        return cache;
    }

    static Configuration configuration(Path directory) {
        Configuration configuration = new Configuration();
        DiskStoreConfiguration disk = new DiskStoreConfiguration();
        disk.setPath(directory.toString());
        configuration.addDiskStore(disk);
        CacheConfiguration defaults = new CacheConfiguration();
        defaults.setMaxElementsInMemory(20);
        defaults.setEternal(true);
        defaults.setOverflowToDisk(false);
        configuration.addDefaultCache(defaults);
        return configuration;
    }

    static CacheManager manager(Path directory) {
        return new CacheManager(configuration(directory));
    }

    static CacheManager replicatingManager(Path directory, String peerUrl) {
        Configuration configuration = configuration(directory);
        FactoryConfiguration provider = new FactoryConfiguration();
        provider.setClass("net.sf.ehcache.distribution.RMICacheManagerPeerProviderFactory");
        provider.setProperties("peerDiscovery=manual,rmiUrls=" + peerUrl);
        configuration.addCacheManagerPeerProviderFactory(provider);
        CacheManager manager = new CacheManager(configuration);
        manager.addCache("replicated");
        return manager;
    }

    static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(50);
        }
        assertTrue(condition.getAsBoolean(), "Operation did not complete within 15 seconds");
    }
}
