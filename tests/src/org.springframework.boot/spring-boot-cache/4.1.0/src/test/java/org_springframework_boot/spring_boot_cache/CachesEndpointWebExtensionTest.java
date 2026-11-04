/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_cache;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse;
import org.springframework.boot.cache.actuate.endpoint.CachesEndpoint;
import org.springframework.boot.cache.actuate.endpoint.CachesEndpoint.CacheEntryDescriptor;
import org.springframework.boot.cache.actuate.endpoint.CachesEndpointWebExtension;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import static org.assertj.core.api.Assertions.assertThat;

public class CachesEndpointWebExtensionTest {
    @Test
    void returnsCacheDescriptionAndClearsAnExistingCache() {
        ConcurrentMapCacheManager manager = new ConcurrentMapCacheManager("products");
        manager.getCache("products").put("item", "value");
        CachesEndpointWebExtension extension = new CachesEndpointWebExtension(
                new CachesEndpoint(Map.of("local", manager)));

        WebEndpointResponse<CacheEntryDescriptor> response = extension.cache("products", null);
        assertThat(response.getStatus()).isEqualTo(WebEndpointResponse.STATUS_OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getName()).isEqualTo("products");
        assertThat(response.getBody().getCacheManager()).isEqualTo("local");

        WebEndpointResponse<Void> cleared = extension.clearCache("products", "local");
        assertThat(cleared.getStatus()).isEqualTo(WebEndpointResponse.STATUS_NO_CONTENT);
        assertThat(cleared.getBody()).isNull();
        assertThat(manager.getCache("products").get("item")).isNull();
    }

    @Test
    void reportsMissingCachesWithoutCreatingThem() {
        ConcurrentMapCacheManager manager = new ConcurrentMapCacheManager("products");
        CachesEndpointWebExtension extension = new CachesEndpointWebExtension(
                new CachesEndpoint(Map.of("local", manager)));

        WebEndpointResponse<CacheEntryDescriptor> response = extension.cache("unknown", null);
        assertThat(response.getStatus()).isEqualTo(WebEndpointResponse.STATUS_NOT_FOUND);
        assertThat(response.getBody()).isNull();
        assertThat(extension.clearCache("unknown", null).getStatus()).isEqualTo(WebEndpointResponse.STATUS_NOT_FOUND);
        assertThat(manager.getCacheNames()).containsExactly("products");
    }

    @Test
    void rejectsAmbiguousNamesWithoutClearingEitherCache() {
        ConcurrentMapCacheManager first = new ConcurrentMapCacheManager("products");
        ConcurrentMapCacheManager second = new ConcurrentMapCacheManager("products");
        first.getCache("products").put("item", "first-value");
        second.getCache("products").put("item", "second-value");
        CachesEndpointWebExtension extension = new CachesEndpointWebExtension(
                new CachesEndpoint(Map.of("first", first, "second", second)));

        assertThat(extension.cache("products", null).getStatus()).isEqualTo(WebEndpointResponse.STATUS_BAD_REQUEST);
        assertThat(extension.clearCache("products", null).getStatus()).isEqualTo(WebEndpointResponse.STATUS_BAD_REQUEST);
        assertThat(first.getCache("products").get("item", String.class)).isEqualTo("first-value");
        assertThat(second.getCache("products").get("item", String.class)).isEqualTo("second-value");
        assertThat(extension.cache("products", "second").getStatus()).isEqualTo(WebEndpointResponse.STATUS_OK);
    }
}
