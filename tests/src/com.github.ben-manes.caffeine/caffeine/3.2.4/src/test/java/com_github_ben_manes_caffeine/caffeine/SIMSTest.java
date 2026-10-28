/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_ben_manes_caffeine.caffeine;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Policy.Eviction;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class SIMSTest {
    @Test
    void cachesSoftValuesUntilInvalidated() {
        Cache<String, String> cache = Caffeine.newBuilder()
                .maximumSize(10)
                .softValues()
                .executor(Runnable::run)
                .build();
        AtomicInteger loads = new AtomicInteger();

        assertThat(cache.get("expression", key -> {
            loads.incrementAndGet();
            return "parsed";
        })).isEqualTo("parsed");
        assertThat(cache.get("expression", key -> {
            loads.incrementAndGet();
            return "reloaded";
        })).isEqualTo("parsed");
        assertThat(loads.get()).isEqualTo(1);
        assertThat(cache.getIfPresent("expression")).isEqualTo("parsed");

        cache.invalidate("expression");
        assertThat(cache.getIfPresent("expression")).isNull();
        assertThat(cache.get("expression", key -> {
            loads.incrementAndGet();
            return "reloaded";
        })).isEqualTo("reloaded");
        assertThat(loads.get()).isEqualTo(2);
        assertThat(cache.asMap()).containsEntry("expression", "reloaded");
    }

    @Test
    void evictsSoftValuesWhenSizeLimitIsExceededOrReduced() {
        Cache<String, String> cache = Caffeine.newBuilder()
                .maximumSize(2)
                .softValues()
                .executor(Runnable::run)
                .build();
        Map<String, String> entries = Map.of("first", "one", "second", "two", "third", "three");
        cache.putAll(entries);
        cache.cleanUp();

        assertThat(cache.estimatedSize()).isEqualTo(2);
        assertThat(cache.asMap()).hasSize(2);
        assertThat(entries).containsAllEntriesOf(cache.asMap());
        Eviction<String, String> eviction = cache.policy().eviction().orElseThrow();
        assertThat(eviction.isWeighted()).isFalse();
        assertThat(eviction.getMaximum()).isEqualTo(2);

        eviction.setMaximum(1);
        cache.cleanUp();
        assertThat(eviction.getMaximum()).isEqualTo(1);
        assertThat(cache.estimatedSize()).isEqualTo(1);
        assertThat(cache.asMap()).hasSize(1);

        eviction.setMaximum(0);
        cache.cleanUp();
        assertThat(eviction.getMaximum()).isZero();
        assertThat(cache.estimatedSize()).isZero();
        assertThat(cache.asMap()).isEmpty();
    }
}
