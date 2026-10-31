/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_cache;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.cache.actuate.endpoint.CachesEndpoint;
import org.springframework.boot.cache.actuate.endpoint.CachesEndpoint.CacheEntryDescriptor;
import org.springframework.boot.cache.actuate.endpoint.NonUniqueCacheException;
import org.springframework.cache.Cache;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class CachesEndpointTest {
    @Test
    void listsCachesAndDescribesTheirNativeTargets() {
        ConcurrentMapCacheManager manager = new ConcurrentMapCacheManager("products", "prices");
        CachesEndpoint endpoint = new CachesEndpoint(Map.of("local", manager));

        assertThat(endpoint.caches().getCacheManagers()).containsOnlyKeys("local");
        assertThat(endpoint.caches().getCacheManagers().get("local").getCaches())
                .containsOnlyKeys("products", "prices");
        CacheEntryDescriptor entry = endpoint.cache("products", null);
        assertThat(entry).isNotNull();
        assertThat(entry.getName()).isEqualTo("products");
        assertThat(entry.getCacheManager()).isEqualTo("local");
        assertThat(entry.getTarget()).isEqualTo(manager.getCache("products").getNativeCache().getClass().getName());
        assertThat(endpoint.caches().getCacheManagers().get("local").getCaches().get("products").getTarget())
                .isEqualTo(entry.getTarget());
        assertThat(endpoint.cache("unknown", null)).isNull();
        assertThat(endpoint.cache("products", "unknown")).isNull();
        assertThat(manager.getCacheNames()).containsExactlyInAnyOrder("products", "prices");
    }

    @Test
    void requiresManagerForDuplicateNamesAndClearsOnlyTheSelectedCache() {
        ConcurrentMapCacheManager local = new ConcurrentMapCacheManager("products");
        ConcurrentMapCacheManager remote = new ConcurrentMapCacheManager("products");
        Cache localCache = local.getCache("products");
        Cache remoteCache = remote.getCache("products");
        localCache.put("item", "local-value");
        remoteCache.put("item", "remote-value");
        CachesEndpoint endpoint = new CachesEndpoint(Map.of("local", local, "remote", remote));

        NonUniqueCacheException lookup = assertThrows(NonUniqueCacheException.class,
                () -> endpoint.cache("products", null));
        assertThat(lookup.getCacheName()).isEqualTo("products");
        assertThat(lookup.getCacheManagerNames()).containsExactlyInAnyOrder("local", "remote");
        assertThrows(NonUniqueCacheException.class, () -> endpoint.clearCache("products", null));
        assertThat(localCache.get("item", String.class)).isEqualTo("local-value");
        assertThat(remoteCache.get("item", String.class)).isEqualTo("remote-value");

        assertThat(endpoint.cache("products", "remote").getCacheManager()).isEqualTo("remote");
        assertThat(endpoint.clearCache("products", "local")).isTrue();
        assertThat(localCache.get("item")).isNull();
        assertThat(remoteCache.get("item", String.class)).isEqualTo("remote-value");
        assertThat(endpoint.clearCache("unknown", "local")).isFalse();
        assertThat(endpoint.clearCache("products", "unknown")).isFalse();
    }

    @Test
    void clearsAllCachesAcrossManagers() {
        ConcurrentMapCacheManager first = new ConcurrentMapCacheManager("products", "prices");
        ConcurrentMapCacheManager second = new ConcurrentMapCacheManager("products");
        first.getCache("products").put("item", "value");
        first.getCache("prices").put("item", 12);
        second.getCache("products").put("item", "other-value");
        CachesEndpoint endpoint = new CachesEndpoint(Map.of("first", first, "second", second));

        endpoint.clearCaches();

        assertThat(first.getCache("products").get("item")).isNull();
        assertThat(first.getCache("prices").get("item")).isNull();
        assertThat(second.getCache("products").get("item")).isNull();
        assertThat(endpoint.caches().getCacheManagers()).containsOnlyKeys("first", "second");
    }
}
