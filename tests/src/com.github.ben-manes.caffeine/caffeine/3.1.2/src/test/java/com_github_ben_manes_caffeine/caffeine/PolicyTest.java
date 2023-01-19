/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_ben_manes_caffeine.caffeine;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class PolicyTest {
    @Test
    void testSizeBased() {
        Cache<String, String> cache = Caffeine.newBuilder().expireAfterWrite(10, TimeUnit.MINUTES).maximumSize(10_000).build();
        cache.policy().eviction().ifPresent(eviction -> eviction.setMaximum(2 * eviction.getMaximum()));
        cache.put("Hello", "World");
        assertThat(cache.getIfPresent("Hello")).isEqualTo("World");
    }

    @Test
    void testTimeBased() {
        Cache<String, String> cache = Caffeine.newBuilder().expireAfterWrite(10, TimeUnit.MINUTES).maximumSize(10_000).build();
        cache.policy().expireAfterAccess().ifPresent(eviction -> eviction.setExpiresAfter(Duration.ofMinutes(5)));
        cache.policy().expireAfterWrite().ifPresent(eviction -> eviction.setExpiresAfter(Duration.ofMinutes(5)));
        cache.policy().expireVariably().ifPresent(eviction -> eviction.setExpiresAfter("Hello", Duration.ofMinutes(5)));
        cache.policy().refreshAfterWrite().ifPresent(refresh -> refresh.setRefreshesAfter(Duration.ofMinutes(5)));
        cache.put("Hello", "World");
        assertThat(cache.getIfPresent("Hello")).isEqualTo("World");
    }
}
