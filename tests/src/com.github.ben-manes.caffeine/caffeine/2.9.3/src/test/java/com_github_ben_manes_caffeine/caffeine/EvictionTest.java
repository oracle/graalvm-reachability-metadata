/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_ben_manes_caffeine.caffeine;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.github.benmanes.caffeine.cache.LoadingCache;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.concurrent.TimeUnit;

import static java.time.temporal.ChronoUnit.MILLIS;
import static org.assertj.core.api.Assertions.assertThat;

public class EvictionTest {
    @Test
    void testSizeBased() {
        LoadingCache<String, String> firstGraphs = Caffeine.newBuilder().maximumSize(10_000).build(key -> key.equals("Hello") ? "World" : "Universe");
        assertThat(firstGraphs.get("Hello")).isEqualTo("World");
        LoadingCache<String, String> secondGraphs = Caffeine.newBuilder().maximumWeight(10_000).weigher((String key, String graph) -> graph.length())
                .build(key -> key.equals("Hello") ? "World" : "Universe");
        assertThat(secondGraphs.get("Hello")).isEqualTo("World");
    }

    @Test
    void testTimeBased() {
        LoadingCache<String, String> firstGraphs = Caffeine.newBuilder().expireAfterAccess(5, TimeUnit.MINUTES)
                .build(key -> key.equals("Hello") ? "World" : "Universe");
        assertThat(firstGraphs.get("Hello")).isEqualTo("World");
        LoadingCache<String, String> secondGraphs = Caffeine.newBuilder().expireAfterWrite(10, TimeUnit.MINUTES)
                .build(key -> key.equals("Hello") ? "World" : "Universe");
        assertThat(secondGraphs.get("Hello")).isEqualTo("World");
        LoadingCache<String, String> thirdGraphs = Caffeine.newBuilder().expireAfter(new Expiry<String, String>() {
            public long expireAfterCreate(String key, String graph, long currentTime) {
                long seconds = ZonedDateTime.now().plusHours(5).minus(System.currentTimeMillis(), MILLIS).toEpochSecond();
                return TimeUnit.SECONDS.toNanos(seconds);
            }

            public long expireAfterUpdate(String key, String graph, long currentTime, long currentDuration) {
                return currentDuration;
            }

            public long expireAfterRead(String key, String graph, long currentTime, long currentDuration) {
                return currentDuration;
            }
        }).build(key -> key.equals("Hello") ? "World" : "Universe");
        assertThat(thirdGraphs.get("Hello")).isEqualTo("World");
    }

    @Test
    void testReferenceBased() {
        LoadingCache<String, String> firstGraphs = Caffeine.newBuilder().weakKeys().weakValues().build(key -> key.equals("Hello") ? "World" : "Universe");
        assertThat(firstGraphs.get("Hello")).isEqualTo("World");
        LoadingCache<String, String> secondGraphs = Caffeine.newBuilder().softValues().build(key -> key.equals("Hello") ? "World" : "Universe");
        assertThat(secondGraphs.get("Hello")).isEqualTo("World");
    }
}
