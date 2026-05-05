/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_ehcache.ehcache_core;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheEntry;
import net.sf.ehcache.CacheException;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;
import net.sf.ehcache.Statistics;
import net.sf.ehcache.Status;
import net.sf.ehcache.config.CacheConfiguration;
import net.sf.ehcache.config.CacheWriterConfiguration;
import net.sf.ehcache.config.Configuration;
import net.sf.ehcache.config.Searchable;
import net.sf.ehcache.event.CacheEventListener;
import net.sf.ehcache.loader.CacheLoader;
import net.sf.ehcache.management.ManagementService;
import net.sf.ehcache.search.Attribute;
import net.sf.ehcache.search.Direction;
import net.sf.ehcache.search.Query;
import net.sf.ehcache.search.Result;
import net.sf.ehcache.search.Results;
import net.sf.ehcache.store.MemoryStoreEvictionPolicy;
import net.sf.ehcache.writer.CacheWriter;
import org.junit.jupiter.api.Test;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class Ehcache_coreTest {
    private static final AtomicInteger MANAGER_IDS = new AtomicInteger();

    @Test
    void programmaticCacheManagerSupportsCoreElementOperations() throws Exception {
        CacheManager manager = newCacheManager("programmatic",
                new CacheConfiguration("users", 10)
                        .eternal(true)
                        .overflowToDisk(false)
                        .statistics(true));
        try {
            assertThat(manager.getStatus()).isEqualTo(Status.STATUS_ALIVE);
            assertThat(manager.getCacheNames()).containsExactly("users");

            manager.addCache("fromDefault");
            assertThat(manager.cacheExists("fromDefault")).isTrue();
            assertThat(manager.getCache("fromDefault").getCacheConfiguration().getMaxElementsInMemory()).isEqualTo(50);

            Cache cache = manager.getCache("users");
            cache.put(new Element("alice", "Alice"));
            assertThat(cache.get("alice").getObjectValue()).isEqualTo("Alice");
            assertThat(cache.isKeyInCache("alice")).isTrue();
            assertThat(cache.isValueInCache("Alice")).isTrue();

            Element existing = cache.putIfAbsent(new Element("alice", "Not stored"));
            assertThat(existing.getObjectValue()).isEqualTo("Alice");
            assertThat(cache.replace(new Element("alice", "Alice"), new Element("alice", "Bob"))).isTrue();
            Element replaced = cache.replace(new Element("alice", "Carol"));
            assertThat(replaced.getObjectValue()).isEqualTo("Bob");
            assertThat(cache.removeElement(new Element("alice", "Carol"))).isTrue();
            assertThat(cache.get("alice")).isNull();

            cache.put(new Element("one", 1));
            cache.put(new Element("two", 2));
            assertThat(cache.getKeys()).containsExactlyInAnyOrder("one", "two");
            manager.clearAll();
            assertThat(cache.getSize()).isZero();
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void eventNotificationsEvictionExpiryAndStatisticsAreObservable() throws Exception {
        CacheManager manager = newCacheManager("events",
                new CacheConfiguration("events", 2)
                        .memoryStoreEvictionPolicy(MemoryStoreEvictionPolicy.FIFO)
                        .eternal(false)
                        .timeToLiveSeconds(1)
                        .timeToIdleSeconds(0)
                        .overflowToDisk(false)
                        .statistics(true));
        try {
            Cache cache = manager.getCache("events");
            CountingCacheEventListener listener = new CountingCacheEventListener();
            cache.getCacheEventNotificationService().registerListener(listener);

            cache.put(new Element("first", "one"));
            cache.put(new Element("second", "two"));
            cache.put(new Element("third", "three"));
            assertThat(cache.getSize()).isEqualTo(2);
            assertThat(cache.get("first")).isNull();
            assertThat(listener.evicted.get()).isGreaterThanOrEqualTo(1);

            cache.put(new Element("second", "updated"));
            assertThat(cache.remove("second")).isTrue();
            cache.removeAll();

            cache.put(new Element("shortLived", "value"));
            Thread.sleep(1_200L);
            assertThat(cache.get("shortLived")).isNull();

            Statistics statistics = cache.getStatistics();
            assertThat(statistics.getCacheHits()).isGreaterThanOrEqualTo(0L);
            assertThat(statistics.getCacheMisses()).isGreaterThanOrEqualTo(2L);
            assertThat(statistics.getEvictionCount()).isGreaterThanOrEqualTo(1L);
            assertThat(listener.put.get()).isGreaterThanOrEqualTo(4);
            assertThat(listener.removed.get()).isEqualTo(1);
            assertThat(listener.removeAll.get()).isEqualTo(1);
            assertThat(listener.expired.get()).isGreaterThanOrEqualTo(1);
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void searchableCacheQueriesKeysValuesOrderingAndResultRanges() throws Exception {
        Searchable searchable = new Searchable();
        searchable.keys(true);
        searchable.values(true);
        CacheManager manager = newCacheManager("search",
                new CacheConfiguration("scores", 10)
                        .eternal(true)
                        .overflowToDisk(false)
                        .searchable(searchable)
                        .statistics(true));
        try {
            Cache cache = manager.getCache("scores");
            cache.put(new Element("alpha", 10));
            cache.put(new Element("beta", 20));
            cache.put(new Element("gamma", 30));
            cache.put(new Element("delta", 40));

            @SuppressWarnings("unchecked")
            Attribute<String> keyAttribute = Query.KEY;
            @SuppressWarnings("unchecked")
            Attribute<Integer> valueAttribute = Query.VALUE;
            Results results = cache.createQuery()
                    .includeKeys()
                    .includeValues()
                    .addCriteria(valueAttribute.ge(20))
                    .addOrderBy(keyAttribute, Direction.ASCENDING)
                    .maxResults(2)
                    .execute();
            try {
                assertThat(results.hasKeys()).isTrue();
                assertThat(results.hasValues()).isTrue();
                assertThat(results.size()).isEqualTo(2);

                List<Result> rows = results.all();
                assertThat(rows.get(0).getKey()).isEqualTo("beta");
                assertThat(rows.get(0).getValue()).isEqualTo(20);
                assertThat(rows.get(1).getKey()).isEqualTo("delta");
                assertThat(rows.get(1).getValue()).isEqualTo(40);
                assertThat(results.range(1, 1).get(0).getKey()).isEqualTo("delta");
            } finally {
                results.discard();
            }

            assertThat(cache.getStatistics().getAverageSearchTime()).isGreaterThanOrEqualTo(0L);
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void xmlConfigurationInputStreamBuildsUsableCaches() throws Exception {
        String managerName = uniqueName("xml");
        String xml = """
                <ehcache name="%s" updateCheck="false" monitoring="off" dynamicConfig="true">
                  <defaultCache maxElementsInMemory="10" eternal="false" timeToIdleSeconds="30"
                                timeToLiveSeconds="30" overflowToDisk="false" />
                  <cache name="xmlConfigured" maxElementsInMemory="3" eternal="true" overflowToDisk="false"
                         statistics="true" memoryStoreEvictionPolicy="LFU" />
                </ehcache>
                """.formatted(managerName);

        InputStream input = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
        CacheManager manager = new CacheManager(input);
        try {
            assertThat(manager.getName()).isEqualTo(managerName);
            assertThat(manager.getCacheNames()).containsExactly("xmlConfigured");

            Cache cache = manager.getCache("xmlConfigured");
            assertThat(cache.getMemoryStoreEvictionPolicy().getName()).isEqualTo("LFU");
            assertThat(cache.getCacheConfiguration().getMemoryStoreEvictionPolicy()).isEqualTo(MemoryStoreEvictionPolicy.LFU);
            cache.put(new Element("xmlKey", "xmlValue"));
            assertThat(cache.get("xmlKey").getObjectValue()).isEqualTo("xmlValue");
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void registeredLoadersWritersAndJmxManagementUsePublicExtensionPoints() throws Exception {
        CacheManager manager = newCacheManager("extensions",
                new CacheConfiguration("extensions", 20)
                        .eternal(true)
                        .overflowToDisk(false)
                        .cacheWriter(new CacheWriterConfiguration()
                                .writeMode(CacheWriterConfiguration.WriteMode.WRITE_THROUGH))
                        .statistics(true));
        ManagementService managementService = null;
        try {
            Cache cache = manager.getCache("extensions");
            CountingCacheLoader loader = new CountingCacheLoader();
            CountingCacheWriter writer = new CountingCacheWriter();
            cache.registerCacheLoader(loader);
            cache.registerCacheWriter(writer);

            Element loaded = cache.getWithLoader("loaded", loader, "argument");
            assertThat(loaded.getObjectValue()).isEqualTo("loaded:argument");
            assertThat(loader.loadWithArgument.get()).isEqualTo(1);
            assertThat(cache.get("loaded").getObjectValue()).isEqualTo("loaded:argument");

            cache.putWithWriter(new Element("written", "value"));
            assertThat(writer.writtenValues).containsEntry("written", "value");
            assertThat(cache.removeWithWriter("written")).isTrue();
            assertThat(writer.deletedKeys).containsEntry("written", "value");

            MBeanServer mBeanServer = java.lang.management.ManagementFactory.getPlatformMBeanServer();
            managementService = new ManagementService(manager, mBeanServer, true, true, true, true, true);
            managementService.init();
            ObjectName cacheManagerName = new ObjectName("net.sf.ehcache:type=CacheManager,name=" + manager.getName());
            assertThat(mBeanServer.isRegistered(cacheManagerName)).isTrue();
        } finally {
            if (managementService != null) {
                managementService.dispose();
            }
            manager.shutdown();
        }
    }

    private static CacheManager newCacheManager(
            String scenario, CacheConfiguration... cacheConfigurations) throws Exception {
        Configuration configuration = new Configuration()
                .name(uniqueName(scenario))
                .updateCheck(false)
                .monitoring(Configuration.Monitoring.OFF)
                .dynamicConfig(true)
                .defaultCache(new CacheConfiguration()
                        .maxElementsInMemory(50)
                        .eternal(false)
                        .timeToLiveSeconds(300)
                        .timeToIdleSeconds(300)
                        .overflowToDisk(false));
        for (CacheConfiguration cacheConfiguration : cacheConfigurations) {
            configuration.cache(cacheConfiguration);
        }
        return new CacheManager(configuration);
    }

    private static String uniqueName(String scenario) {
        return "ehcacheCore" + scenario + MANAGER_IDS.incrementAndGet();
    }

    private static final class CountingCacheEventListener implements CacheEventListener {
        private final AtomicInteger put = new AtomicInteger();
        private final AtomicInteger updated = new AtomicInteger();
        private final AtomicInteger removed = new AtomicInteger();
        private final AtomicInteger expired = new AtomicInteger();
        private final AtomicInteger evicted = new AtomicInteger();
        private final AtomicInteger removeAll = new AtomicInteger();

        @Override
        public void notifyElementRemoved(Ehcache cache, Element element) throws CacheException {
            removed.incrementAndGet();
        }

        @Override
        public void notifyElementPut(Ehcache cache, Element element) throws CacheException {
            put.incrementAndGet();
        }

        @Override
        public void notifyElementUpdated(Ehcache cache, Element element) throws CacheException {
            updated.incrementAndGet();
        }

        @Override
        public void notifyElementExpired(Ehcache cache, Element element) {
            expired.incrementAndGet();
        }

        @Override
        public void notifyElementEvicted(Ehcache cache, Element element) {
            evicted.incrementAndGet();
        }

        @Override
        public void notifyRemoveAll(Ehcache cache) {
            removeAll.incrementAndGet();
        }

        @Override
        public void dispose() {
        }

        @Override
        public Object clone() throws CloneNotSupportedException {
            return super.clone();
        }
    }

    private static final class CountingCacheLoader implements CacheLoader {
        private final AtomicInteger loadWithArgument = new AtomicInteger();

        @Override
        public Object load(Object key) throws CacheException {
            return key + ":loaded";
        }

        @Override
        public Map<Object, Object> loadAll(Collection keys) {
            Map<Object, Object> values = new HashMap<>();
            for (Object key : keys) {
                values.put(key, load(key));
            }
            return values;
        }

        @Override
        public Object load(Object key, Object argument) {
            loadWithArgument.incrementAndGet();
            return key + ":" + argument;
        }

        @Override
        public Map<Object, Object> loadAll(Collection keys, Object argument) {
            Map<Object, Object> values = new HashMap<>();
            for (Object key : keys) {
                values.put(key, load(key, argument));
            }
            return values;
        }

        @Override
        public String getName() {
            return "counting";
        }

        @Override
        public CacheLoader clone(Ehcache cache) throws CloneNotSupportedException {
            return this;
        }

        @Override
        public void init() {
        }

        @Override
        public void dispose() throws CacheException {
        }

        @Override
        public Status getStatus() {
            return Status.STATUS_ALIVE;
        }
    }

    private static final class CountingCacheWriter implements CacheWriter {
        private final Map<Object, Object> writtenValues = new HashMap<>();
        private final Map<Object, Object> deletedKeys = new HashMap<>();

        @Override
        public CacheWriter clone(Ehcache cache) throws CloneNotSupportedException {
            return this;
        }

        @Override
        public void init() {
        }

        @Override
        public void dispose() throws CacheException {
        }

        @Override
        public void write(Element element) throws CacheException {
            writtenValues.put(element.getObjectKey(), element.getObjectValue());
        }

        @Override
        public void writeAll(Collection<Element> elements) throws CacheException {
            for (Element element : elements) {
                write(element);
            }
        }

        @Override
        public void delete(CacheEntry entry) throws CacheException {
            Element element = entry.getElement();
            deletedKeys.put(entry.getKey(), element == null ? null : element.getObjectValue());
        }

        @Override
        public void deleteAll(Collection<CacheEntry> entries) throws CacheException {
            for (CacheEntry entry : entries) {
                delete(entry);
            }
        }
    }
}
