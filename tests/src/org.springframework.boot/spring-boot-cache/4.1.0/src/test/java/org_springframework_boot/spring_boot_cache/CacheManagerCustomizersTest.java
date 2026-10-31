/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_cache;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.cache.autoconfigure.CacheManagerCustomizer;
import org.springframework.boot.cache.autoconfigure.CacheManagerCustomizers;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class CacheManagerCustomizersTest {
    @Test
    void appliesOnlyCustomizersForTheGivenManagerType() {
        CacheManagerCustomizers customizers = new CacheManagerCustomizers(
                List.of(new LocalCacheCustomizer(), new CaffeineCustomizer()));
        ConcurrentMapCacheManager local = new ConcurrentMapCacheManager();
        CaffeineCacheManager caffeine = new CaffeineCacheManager();

        assertThat(customizers.customize(local)).isSameAs(local);
        assertThat(local.getCacheNames()).containsExactly("local-products");
        local.getCache("local-products").put("item", "value");
        assertThat(local.getCache("local-products").get("item", String.class)).isEqualTo("value");
        assertThrows(IllegalArgumentException.class, () -> local.getCache("local-products").put("empty", null));

        assertThat(customizers.customize(caffeine)).isSameAs(caffeine);
        assertThat(caffeine.getCacheNames()).containsExactly("fast-products");
        caffeine.getCache("fast-products").put("item", "cached-value");
        assertThat(caffeine.getCache("fast-products").get("item", String.class)).isEqualTo("cached-value");
        assertThat(caffeine.getCache("other")).isNull();
    }

    @Test
    void acceptsNoCustomizersWithoutChangingCacheBehavior() {
        ConcurrentMapCacheManager manager = new ConcurrentMapCacheManager("products");
        manager.getCache("products").put("item", "value");

        assertThat(new CacheManagerCustomizers(null).customize(manager)).isSameAs(manager);
        assertThat(manager.getCache("products").get("item", String.class)).isEqualTo("value");
        assertThat(manager.isAllowNullValues()).isTrue();
    }

    public static class LocalCacheCustomizer implements CacheManagerCustomizer<ConcurrentMapCacheManager> {
        @Override
        public void customize(ConcurrentMapCacheManager manager) {
            manager.setCacheNames(List.of("local-products"));
            manager.setAllowNullValues(false);
        }
    }

    public static class CaffeineCustomizer implements CacheManagerCustomizer<CaffeineCacheManager> {
        @Override
        public void customize(CaffeineCacheManager manager) {
            manager.setCacheSpecification("maximumSize=20,recordStats");
            manager.setCacheNames(List.of("fast-products"));
        }
    }
}
