/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.cache.metrics.CaffeineCacheMeterBinderProvider;
import org.springframework.cache.caffeine.CaffeineCache;

import static org.assertj.core.api.Assertions.assertThat;

public class CaffeineCacheMeterBinderProviderTest {
    @Test
    void publishesCacheSizeHitsAndMissesWithConsumerTags() {
        CaffeineCache cache = new CaffeineCache("products",
                Caffeine.newBuilder().recordStats().executor(Runnable::run).build());
        CaffeineCacheMeterBinderProvider provider = new CaffeineCacheMeterBinderProvider();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        try {
            provider.getMeterBinder(cache, Tags.of("region", "local")).bindTo(registry);

            cache.put("item", "value");
            assertThat(cache.get("item", String.class)).isEqualTo("value");
            assertThat(cache.get("missing")).isNull();
            cache.getNativeCache().cleanUp();

            assertThat(registry.get("cache.size").tags("cache", "products", "region", "local").gauge().value())
                    .isEqualTo(1.0);
            assertThat(registry.get("cache.gets").tags("cache", "products", "region", "local", "result", "hit")
                    .functionCounter().count()).isEqualTo(1.0);
            assertThat(registry.get("cache.gets").tags("cache", "products", "region", "local", "result", "miss")
                    .functionCounter().count()).isEqualTo(1.0);

            cache.evict("item");
            cache.getNativeCache().cleanUp();
            assertThat(registry.get("cache.size").tags("cache", "products", "region", "local").gauge().value())
                    .isZero();
        } finally {
            registry.close();
        }
    }
}
