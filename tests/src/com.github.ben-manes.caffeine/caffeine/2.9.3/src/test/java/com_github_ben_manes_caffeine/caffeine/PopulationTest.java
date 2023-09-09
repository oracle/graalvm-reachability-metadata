/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_ben_manes_caffeine.caffeine;

import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.testing.FakeTicker;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class PopulationTest {
    @Test
    void testManual() {
        Cache<String, String> cache = Caffeine.newBuilder().expireAfterWrite(10, TimeUnit.MINUTES).maximumSize(10_000).build();
        assertThat(cache.getIfPresent("Hello")).isNull();
        assertThat(cache.get("Hello", k -> "World")).isEqualTo("World");
        cache.put("Hello", "World");
        assertThat(cache.getIfPresent("Hello")).isEqualTo("World");
        cache.invalidate("Hello");
        assertThat(cache.getIfPresent("Hello")).isNull();
    }

    @Test
    void testExpireAfterWrite() {
        FakeTicker ticker = new FakeTicker();
        Cache<String, String> cache = Caffeine.newBuilder().ticker(ticker::read).expireAfterWrite(100, TimeUnit.MINUTES).build();
        cache.put("Aloha", "Universe");
        assertEquals("Universe", cache.getIfPresent("Aloha"));
        ticker.advance(Duration.ofMinutes(200));
        assertNull(cache.getIfPresent("Aloha"));
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testAsynchronousManual() throws ExecutionException, InterruptedException {
        AsyncCache<String, String> cache = Caffeine.newBuilder().expireAfterWrite(10, TimeUnit.MINUTES).maximumSize(10_000).buildAsync();
        assertThat(cache.getIfPresent("Hello")).isNull();
        assertThat(cache.get("Hello", k -> "World").get()).isEqualTo("World");
        cache.put("Hello", CompletableFuture.supplyAsync(() -> "World"));
        assertThat(cache.getIfPresent("Hello").get()).isEqualTo("World");
        cache.synchronous().invalidate("Hello");
        assertThat(cache.getIfPresent("Hello")).isNull();
    }

    @Test
    void testAsynchronouslyLoading() throws ExecutionException, InterruptedException {
        AsyncLoadingCache<String, String> firstCache = Caffeine.newBuilder().maximumSize(10_000).expireAfterWrite(10, TimeUnit.MINUTES)
                .buildAsync((key, executor) ->
                        key.equals("Hello") ? CompletableFuture.supplyAsync(() -> "World") : CompletableFuture.supplyAsync(() -> "Universe"));
        assertThat(firstCache.get("Hello").get()).isEqualTo("World");
        assertThat(firstCache.getAll(List.of("Hi", "Aloha")).get()).isEqualTo(Map.of("Hi", "Universe", "Aloha", "Universe"));
        AsyncLoadingCache<String, String> secondCache = Caffeine.newBuilder().maximumSize(10_000).expireAfterWrite(10, TimeUnit.MINUTES)
                .buildAsync(key -> key.equals("Hello") ? "World" : "Universe");
        assertThat(secondCache.get("Hello").get()).isEqualTo("World");
        assertThat(secondCache.getAll(List.of("Hi", "Aloha")).get()).isEqualTo(Map.of("Hi", "Universe", "Aloha", "Universe"));
    }
}
