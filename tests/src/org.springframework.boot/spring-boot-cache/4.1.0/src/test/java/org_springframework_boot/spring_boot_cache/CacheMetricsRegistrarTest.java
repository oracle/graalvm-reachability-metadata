/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_cache;

import java.util.List;

import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.cache.metrics.CacheMetricsRegistrar;
import org.springframework.boot.cache.metrics.CaffeineCacheMeterBinderProvider;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.concurrent.ConcurrentMapCache;

import static org.assertj.core.api.Assertions.assertThat;

public class CacheMetricsRegistrarTest {
    @Test
    void registersSupportedCacheWithDefaultAndSuppliedTags() {
        CaffeineCache cache = new CaffeineCache("products",
                Caffeine.newBuilder().recordStats().executor(Runnable::run).build());
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        try {
            CacheMetricsRegistrar registrar = new CacheMetricsRegistrar(registry,
                    List.of(new CaffeineCacheMeterBinderProvider()));

            assertThat(registrar.bindCacheToRegistry(cache, Tag.of("cacheManager", "local"))).isTrue();
            cache.put("item", "value");
            assertThat(cache.get("item", String.class)).isEqualTo("value");
            assertThat(cache.get("missing")).isNull();

            assertThat(registry.get("cache.gets")
                    .tags("cache", "products", "name", "products", "cacheManager", "local", "result", "hit")
                    .functionCounter().count()).isEqualTo(1.0);
            assertThat(registry.get("cache.gets")
                    .tags("cache", "products", "name", "products", "cacheManager", "local", "result", "miss")
                    .functionCounter().count()).isEqualTo(1.0);
        } finally {
            registry.close();
        }
    }

    @Test
    void leavesUnsupportedCachesAndRegistryUnchanged() {
        ConcurrentMapCache cache = new ConcurrentMapCache("products");
        cache.put("item", "value");
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        try {
            CacheMetricsRegistrar registrar = new CacheMetricsRegistrar(registry,
                    List.of(new CaffeineCacheMeterBinderProvider()));

            assertThat(registrar.bindCacheToRegistry(cache)).isFalse();
            assertThat(registry.getMeters()).isEmpty();
            assertThat(cache.get("item", String.class)).isEqualTo("value");
        } finally {
            registry.close();
        }
    }

    @Test
    void returnsFalseWhenNoProviderIsConfigured() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        try {
            CacheMetricsRegistrar registrar = new CacheMetricsRegistrar(registry, List.of());

            assertThat(registrar.bindCacheToRegistry(new ConcurrentMapCache("products"))).isFalse();
            assertThat(registry.getMeters()).isEmpty();
        } finally {
            registry.close();
        }
    }
}
