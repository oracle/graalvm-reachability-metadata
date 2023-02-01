/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_ben_manes_caffeine.caffeine;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class RemovalTest {
    @Test
    void testExplicitRemovals() {
        Cache<String, String> cache = Caffeine.newBuilder().expireAfterWrite(10, TimeUnit.MINUTES).maximumSize(10_000).build();
        cache.putAll(Map.of("Hello", "World", "Hi", "Universe", "Aloha", "Universe"));
        assertThat(cache.getAll(List.of("Hello", "Hi", "Aloha"), key -> null))
                .isEqualTo(Map.of("Hello", "World", "Hi", "Universe", "Aloha", "Universe"));
        cache.invalidate("Hello");
        assertThat(cache.getIfPresent("Hello")).isNull();
        cache.invalidateAll(List.of("Hi", "Aloha"));
        Stream.of("Hi", "Aloha").forEach(s -> assertThat(cache.getIfPresent(s)).isNull());
        cache.invalidateAll();
        Stream.of("Hello", "Hi", "Aloha").forEach(s -> assertThat(cache.getIfPresent(s)).isNull());
    }

    @Test
    void testRemovalListeners() {
        Cache<String, String> graphs = Caffeine.newBuilder()
                .evictionListener((String key, String graph, RemovalCause cause) -> System.out.printf("Key %s was evicted (%s)%n", key, cause))
                .removalListener((String key, String graph, RemovalCause cause) -> System.out.printf("Key %s was removed (%s)%n", key, cause))
                .build();
        graphs.put("Hello", "World");
        assertThat(graphs.getIfPresent("Hello")).isEqualTo("World");
        graphs.invalidate("Hello");
        assertThat(graphs.getIfPresent("Hello")).isNull();
    }
}
