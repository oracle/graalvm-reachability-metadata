/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_ben_manes_caffeine.caffeine;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.CaffeineSpec;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.Scheduler;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.google.common.testing.FakeTicker;
import org.junit.jupiter.api.Test;

import java.lang.ref.Cleaner;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class CaffeineTest {
    @Test
    void testRefresh() {
        TestRefreshLoader testRefreshLoader = new TestRefreshLoader();
        LoadingCache<String, CompletableFuture<String>> cache = Caffeine.newBuilder()
                .refreshAfterWrite(Duration.ofMillis(100))
                .build(testRefreshLoader::load);
        cache.get("Hello");
        CompletableFuture<String> firstValue = cache.getIfPresent("Hello");
        assertThat(firstValue).isNotNull();
        assertEquals("World", firstValue.join());
        testRefreshLoader.setFlag(true);
        cache.refresh("Hello");
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            CompletableFuture<String> secondValue = cache.getIfPresent("Hello");
            assertThat(secondValue).isNotNull();
            assertEquals("Universe", secondValue.join());
        });
    }

    @Test
    void testStatistics() {
        Cache<String, String> graphs = Caffeine.newBuilder().maximumSize(10_000).recordStats().build();
        graphs.put("Hello", "World");
        assertThat(graphs.getIfPresent("Hello")).isEqualTo("World");
        CacheStats cacheStats = graphs.stats();
        assertThat(cacheStats.hitCount()).isEqualTo(1);
        assertThat(cacheStats.missCount()).isEqualTo(0);
        assertThat(cacheStats.evictionCount()).isEqualTo(0);
    }

    @Test
    void testSpecification() {
        CaffeineSpec spec = CaffeineSpec.parse("maximumWeight=1000, expireAfterWrite=10m");
        LoadingCache<String, String> graphs = Caffeine.from(spec).weigher((String key, String graph) -> graph.length())
                .build(key -> key.equals("Hello") ? "World" : "Universe");
        assertThat(graphs.get("Hello")).isEqualTo("World");
    }

    @Test
    void testCleanup() {
        FakeTicker ticker = new FakeTicker();
        LoadingCache<String, String> firstGraphs = Caffeine.newBuilder()
                .scheduler(Scheduler.systemScheduler())
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .ticker(ticker::read)
                .build(key -> key.equals("Hello") ? "World" : "Universe");
        firstGraphs.put("Hello", "World");
        assertNotEquals(0, firstGraphs.estimatedSize());
        assertThat(firstGraphs.getIfPresent("Hello")).isEqualTo("World");
        ticker.advance(10, TimeUnit.MINUTES);
        firstGraphs.cleanUp();
        assertEquals(0, firstGraphs.estimatedSize());
        Cache<String, String> secondGraphs = Caffeine.newBuilder().weakValues().build();
        Cleaner cleaner = Cleaner.create();
        cleaner.register("World", secondGraphs::cleanUp);
        secondGraphs.put("Hello", "World");
        assertThat(secondGraphs.getIfPresent("Hello")).isEqualTo("World");
    }

    @Test
    void testTesting() {
        FakeTicker ticker = new FakeTicker();
        Cache<String, String> cache = Caffeine.newBuilder()
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .executor(Runnable::run)
                .ticker(ticker::read)
                .maximumSize(10)
                .build();
        cache.put("Hello", "World");
        ticker.advance(30, TimeUnit.MINUTES);
        assertThat(cache.getIfPresent("Hello")).isNull();
    }
}
