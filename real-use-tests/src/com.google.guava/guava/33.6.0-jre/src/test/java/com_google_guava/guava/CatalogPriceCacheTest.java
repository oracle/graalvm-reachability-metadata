/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_guava.guava;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.base.Ticker;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.CacheStats;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalCause;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.UncheckedExecutionException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * In-memory caching with `CacheBuilder` and `LoadingCache`: loading, bulk loading, expiry,
 * size-bounded eviction, refresh, removal listeners, and statistics.
 *
 * A storefront caches product prices that it otherwise reads from a slow pricing backend. The
 * cache must load prices on demand, drop entries after a time-to-live, stay within a fixed
 * size, report evictions to an audit log, and expose hit and miss counts to monitoring.
 */
public class CatalogPriceCacheTest {

    @Test
    void loadsPricesOnDemandAndServesRepeatsFromTheCache() throws Exception {
        PricingBackend backend = new PricingBackend();
        LoadingCache<String, Long> prices = CacheBuilder.newBuilder()
                .maximumSize(100)
                .recordStats()
                .build(backend.loader());

        assertEquals(1999L, prices.get("sku-keyboard"));
        assertEquals(1999L, prices.get("sku-keyboard"));
        assertEquals(4999L, prices.getUnchecked("sku-monitor"));

        assertEquals(2, backend.calls.get());
        CacheStats stats = prices.stats();
        assertEquals(1, stats.hitCount());
        assertEquals(2, stats.missCount());
        assertEquals(2, stats.loadSuccessCount());
        assertEquals(1.0 / 3.0, stats.hitRate(), 1e-9);
        assertEquals(2, prices.size());
    }

    @Test
    void loadsAWholeCartInOneBackendRoundTrip() throws Exception {
        PricingBackend backend = new PricingBackend();
        LoadingCache<String, Long> prices = CacheBuilder.newBuilder().build(backend.loader());
        prices.get("sku-mouse");

        ImmutableMap<String, Long> cart = prices.getAll(List.of("sku-mouse", "sku-keyboard", "sku-monitor"));

        assertEquals(ImmutableMap.of("sku-mouse", 999L, "sku-keyboard", 1999L, "sku-monitor", 4999L), cart);
        assertEquals(List.of("sku-mouse", "sku-keyboard", "sku-monitor"), List.copyOf(cart.keySet()));
        assertEquals(1, backend.calls.get());
        assertEquals(1, backend.bulkCalls.get());
        assertEquals(3, prices.asMap().size());
    }

    @Test
    void expiresPricesAfterTheirTimeToLive() throws Exception {
        PricingBackend backend = new PricingBackend();
        ManualClock clock = new ManualClock();
        List<String> audit = new ArrayList<>();
        RemovalListener<String, Long> auditLog = (RemovalNotification<String, Long> removal) ->
                audit.add(removal.getKey() + ":" + removal.getCause());
        LoadingCache<String, Long> prices = CacheBuilder.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(10))
                .ticker(clock)
                .removalListener(auditLog)
                .build(backend.loader());

        prices.get("sku-keyboard");
        clock.advance(Duration.ofMinutes(9));
        assertEquals(1999L, prices.get("sku-keyboard"));
        assertEquals(1, backend.calls.get());

        backend.reprice("sku-keyboard", 1799L);
        clock.advance(Duration.ofMinutes(2));
        assertNull(prices.getIfPresent("sku-keyboard"));
        assertEquals(1799L, prices.get("sku-keyboard"));
        assertEquals(2, backend.calls.get());
        assertEquals(List.of("sku-keyboard:" + RemovalCause.EXPIRED), audit);
    }

    @Test
    void evictsTheLeastRecentlyUsedPriceWhenFull() throws Exception {
        PricingBackend backend = new PricingBackend();
        List<RemovalNotification<String, Long>> evicted = new ArrayList<>();
        RemovalListener<String, Long> recorder = evicted::add;
        LoadingCache<String, Long> prices = CacheBuilder.newBuilder()
                .maximumSize(2)
                .concurrencyLevel(1)
                .removalListener(recorder)
                .build(backend.loader());

        prices.get("sku-mouse");
        prices.get("sku-keyboard");
        prices.get("sku-mouse");
        prices.get("sku-monitor");

        assertEquals(1, evicted.size());
        assertEquals("sku-keyboard", evicted.get(0).getKey());
        assertEquals(1999L, evicted.get(0).getValue());
        assertEquals(RemovalCause.SIZE, evicted.get(0).getCause());
        assertTrue(evicted.get(0).wasEvicted());
        assertEquals(2, prices.size());
        assertNull(prices.getIfPresent("sku-keyboard"));
    }

    @Test
    void refreshesAndInvalidatesPricesOnRequest() throws Exception {
        PricingBackend backend = new PricingBackend();
        List<String> audit = new ArrayList<>();
        LoadingCache<String, Long> prices = CacheBuilder.newBuilder()
                .removalListener((RemovalNotification<String, Long> removal) ->
                        audit.add(removal.getKey() + ":" + removal.getCause()))
                .build(backend.loader());

        prices.get("sku-monitor");
        backend.reprice("sku-monitor", 4499L);
        prices.refresh("sku-monitor");
        assertEquals(4499L, prices.get("sku-monitor"));

        prices.put("sku-cable", 199L);
        prices.invalidate("sku-cable");
        prices.invalidateAll();

        assertEquals(0, prices.size());
        assertEquals(List.of("sku-monitor:REPLACED", "sku-cable:EXPLICIT", "sku-monitor:EXPLICIT"), audit);
    }

    @Test
    void reportsUnknownProductsAsLoadFailures() {
        PricingBackend backend = new PricingBackend();
        LoadingCache<String, Long> prices = CacheBuilder.newBuilder().recordStats().build(backend.loader());

        UncheckedExecutionException failure =
                assertThrows(UncheckedExecutionException.class, () -> prices.getUnchecked("sku-unknown"));

        assertTrue(failure.getCause() instanceof IllegalArgumentException);
        assertEquals("no price for sku-unknown", failure.getCause().getMessage());
        assertEquals(1, prices.stats().loadExceptionCount());
        assertNull(prices.getIfPresent("sku-unknown"));
    }

    /** A slow pricing service the storefront would rather not call twice for the same product. */
    private static final class PricingBackend {

        private final Map<String, Long> catalog = new HashMap<>(Map.of(
                "sku-mouse", 999L,
                "sku-keyboard", 1999L,
                "sku-monitor", 4999L));
        private final AtomicInteger calls = new AtomicInteger();
        private final AtomicInteger bulkCalls = new AtomicInteger();

        void reprice(String sku, long cents) {
            catalog.put(sku, cents);
        }

        CacheLoader<String, Long> loader() {
            return new CacheLoader<>() {
                @Override
                public Long load(String sku) {
                    calls.incrementAndGet();
                    Long cents = catalog.get(sku);
                    if (cents == null) {
                        throw new IllegalArgumentException("no price for " + sku);
                    }
                    return cents;
                }

                @Override
                public Map<String, Long> loadAll(Iterable<? extends String> skus) {
                    bulkCalls.incrementAndGet();
                    Map<String, Long> found = new HashMap<>();
                    for (String sku : skus) {
                        found.put(sku, catalog.get(sku));
                    }
                    return found;
                }
            };
        }
    }

    /** A clock the test advances by hand so expiry does not depend on wall time. */
    private static final class ManualClock extends Ticker {

        private long nanos;

        void advance(Duration duration) {
            nanos += duration.toNanos();
        }

        @Override
        public long read() {
            return nanos;
        }
    }
}
