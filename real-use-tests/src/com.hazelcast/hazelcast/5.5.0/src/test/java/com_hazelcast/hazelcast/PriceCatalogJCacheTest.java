/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_hazelcast.hazelcast;

import com.hazelcast.cache.HazelcastCachingProvider;
import com.hazelcast.cache.ICache;
import com.hazelcast.config.Config;
import com.hazelcast.config.JoinConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.configuration.CompleteConfiguration;
import javax.cache.configuration.FactoryBuilder;
import javax.cache.configuration.MutableCacheEntryListenerConfiguration;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.event.CacheEntryCreatedListener;
import javax.cache.event.CacheEntryEvent;
import javax.cache.event.CacheEntryRemovedListener;
import javax.cache.event.CacheEntryUpdatedListener;
import javax.cache.event.EventType;
import javax.cache.integration.CacheLoader;
import javax.cache.processor.EntryProcessor;
import javax.cache.processor.MutableEntry;
import javax.cache.spi.CachingProvider;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises Hazelcast as a JCache (JSR-107) provider that caches a shop's price catalog.
 *
 * The application resolves Hazelcast's member caching provider through `javax.cache.Caching`
 * and creates a cache whose loader and listener are configured only by class. A miss reads the
 * price from the catalog through the loader, a discount is applied atomically with an entry
 * processor, and every created, updated, and removed entry is reported to a synchronous
 * listener. The cache is then unwrapped to `ICache` to read its Hazelcast statistics.
 */
class PriceCatalogJCacheTest {

    private static final String MEMBER_PROVIDER = "com.hazelcast.cache.HazelcastMemberCachingProvider";

    private static final String CACHE_NAME = "prices";

    private HazelcastInstance member;

    private CachingProvider provider;

    private CacheManager cacheManager;

    @BeforeEach
    void startShop() {
        CatalogLoader.LOADS.set(0);
        PriceAuditListener.EVENTS.clear();

        Config config = new Config();
        config.setClusterName("prices-" + UUID.randomUUID());
        config.setProperty("hazelcast.phone.home.enabled", "false");
        config.getNetworkConfig().setPort(5701).setPortAutoIncrement(true);
        JoinConfig join = config.getNetworkConfig().getJoin();
        join.getMulticastConfig().setEnabled(false);
        join.getTcpIpConfig().setEnabled(false);
        join.getAutoDetectionConfig().setEnabled(false);
        member = Hazelcast.newHazelcastInstance(config);

        provider = Caching.getCachingProvider(MEMBER_PROVIDER);
        cacheManager = provider.getCacheManager(provider.getDefaultURI(), provider.getDefaultClassLoader(),
                HazelcastCachingProvider.propertiesByInstanceItself(member));
    }

    @AfterEach
    void stopShop() {
        if (cacheManager != null) {
            cacheManager.close();
        }
        if (provider != null) {
            provider.close();
        }
        if (member != null) {
            member.shutdown();
        }
    }

    @Test
    void cacheLoadsDiscountsAndAuditsPrices() throws InterruptedException {
        MutableConfiguration<String, Integer> configuration = new MutableConfiguration<String, Integer>()
                .setTypes(String.class, Integer.class)
                .setReadThrough(true)
                .setCacheLoaderFactory(FactoryBuilder.factoryOf(CatalogLoader.class))
                .setStatisticsEnabled(true)
                .addCacheEntryListenerConfiguration(new MutableCacheEntryListenerConfiguration<>(
                        FactoryBuilder.factoryOf(PriceAuditListener.class), null, true, true));
        Cache<String, Integer> prices = cacheManager.createCache(CACHE_NAME, configuration);

        assertThat(cacheManager.getCacheNames()).contains(CACHE_NAME);
        assertThat(prices.getConfiguration(CompleteConfiguration.class).isReadThrough()).isTrue();

        assertThat(prices.get("LAMP")).isEqualTo(4500);
        assertThat(prices.get("LAMP")).isEqualTo(4500);
        assertThat(prices.get("UNKNOWN")).isNull();
        assertThat(CatalogLoader.LOADS.get()).isEqualTo(2);
        assertThat(nextEvent()).isEqualTo(new AuditEntry(EventType.CREATED, "LAMP", null, 4500));

        prices.put("DESK", 18000);
        assertThat(nextEvent()).isEqualTo(new AuditEntry(EventType.CREATED, "DESK", null, 18000));
        assertThat(prices.putIfAbsent("DESK", 1)).isFalse();

        int discounted = prices.invoke("DESK", new Discount(), 25);
        assertThat(discounted).isEqualTo(13500);
        assertThat(prices.get("DESK")).isEqualTo(13500);
        assertThat(nextEvent()).isEqualTo(new AuditEntry(EventType.UPDATED, "DESK", 18000, 13500));

        assertThat(prices.replace("LAMP", 4500, 3900)).isTrue();
        assertThat(nextEvent()).isEqualTo(new AuditEntry(EventType.UPDATED, "LAMP", 4500, 3900));
        assertThat(prices.replace("LAMP", 4500, 1)).isFalse();

        Map<String, Integer> basket = prices.getAll(Set.of("LAMP", "DESK"));
        assertThat(basket).containsOnly(Map.entry("LAMP", 3900), Map.entry("DESK", 13500));

        assertThat(prices.remove("LAMP")).isTrue();
        assertThat(nextEvent()).isEqualTo(new AuditEntry(EventType.REMOVED, "LAMP", 3900, null));
        assertThat(prices.containsKey("LAMP")).isFalse();
        assertThat(PriceAuditListener.EVENTS).isEmpty();

        ICache<String, Integer> hazelcastCache = prices.unwrap(ICache.class);
        assertThat(hazelcastCache.size()).isEqualTo(1);
        assertThat(hazelcastCache.getLocalCacheStatistics().getCachePuts()).isGreaterThanOrEqualTo(2);
        assertThat(hazelcastCache.getLocalCacheStatistics().getCacheRemovals()).isEqualTo(1);

        cacheManager.destroyCache(CACHE_NAME);
        assertThat(cacheManager.getCacheNames()).doesNotContain(CACHE_NAME);
    }

    private static AuditEntry nextEvent() throws InterruptedException {
        AuditEntry entry = PriceAuditListener.EVENTS.poll(30, TimeUnit.SECONDS);
        assertThat(entry).as("cache entry event").isNotNull();
        return entry;
    }

    /**
     * One change to a cached price as the audit listener saw it.
     */
    record AuditEntry(EventType type, String sku, Integer oldPrice, Integer newPrice) {
    }

    /**
     * Reads list prices, in cents, from the shop's catalog on a cache miss.
     */
    public static final class CatalogLoader implements CacheLoader<String, Integer>, Serializable {

        static final AtomicInteger LOADS = new AtomicInteger();

        private static final Map<String, Integer> CATALOG = Map.of("LAMP", 4500, "CHAIR", 7900);

        @Override
        public Integer load(String sku) {
            LOADS.incrementAndGet();
            return CATALOG.get(sku);
        }

        @Override
        public Map<String, Integer> loadAll(Iterable<? extends String> skus) {
            Map<String, Integer> prices = new HashMap<>();
            for (String sku : skus) {
                Integer price = load(sku);
                if (price != null) {
                    prices.put(sku, price);
                }
            }
            return prices;
        }
    }

    /**
     * Records every price change so the shop can audit it.
     */
    public static final class PriceAuditListener implements CacheEntryCreatedListener<String, Integer>,
            CacheEntryUpdatedListener<String, Integer>, CacheEntryRemovedListener<String, Integer>, Serializable {

        static final BlockingQueue<AuditEntry> EVENTS = new LinkedBlockingQueue<>();

        @Override
        public void onCreated(Iterable<CacheEntryEvent<? extends String, ? extends Integer>> events) {
            record(events);
        }

        @Override
        public void onUpdated(Iterable<CacheEntryEvent<? extends String, ? extends Integer>> events) {
            record(events);
        }

        @Override
        public void onRemoved(Iterable<CacheEntryEvent<? extends String, ? extends Integer>> events) {
            record(events);
        }

        private static void record(Iterable<CacheEntryEvent<? extends String, ? extends Integer>> events) {
            for (CacheEntryEvent<? extends String, ? extends Integer> event : events) {
                Integer newPrice = event.getEventType() == EventType.REMOVED ? null : event.getValue();
                EVENTS.add(new AuditEntry(event.getEventType(), event.getKey(), event.getOldValue(), newPrice));
            }
        }
    }

    /**
     * Lowers a cached price by the given percentage and returns the new price.
     */
    static final class Discount implements EntryProcessor<String, Integer, Integer>, Serializable {

        @Override
        public Integer process(MutableEntry<String, Integer> entry, Object... arguments) {
            int percent = (Integer) arguments[0];
            int price = entry.getValue() * (100 - percent) / 100;
            entry.setValue(price);
            return price;
        }
    }
}
