/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_cache2k.cache2k_core;

import org.cache2k.Cache;
import org.cache2k.Cache2kBuilder;
import org.cache2k.CacheManager;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CacheManagerImplTest {

    @Test
    void managerTracksCachesCreatedViaPublicApi() {
        String suffix = Long.toUnsignedString(System.nanoTime());
        String managerName = "manager" + suffix;
        String cacheName = "cache" + suffix;

        CacheManager manager = CacheManager.getInstance(managerName);
        Cache<String, Integer> cache = null;
        try {
            assertThat(manager.getName()).isEqualTo(managerName);
            assertThat(manager.isClosed()).isFalse();

            cache = Cache2kBuilder.of(String.class, Integer.class)
                .manager(manager)
                .name(cacheName)
                .build();

            cache.put("one", 1);

            assertThat(cache.get("one")).isEqualTo(1);
            assertThat(cache.getCacheManager()).isSameAs(manager);
            assertThat(manager.getCache(cacheName)).isSameAs(cache);
            assertThat(manager.getActiveCaches()).contains(cache);
        } finally {
            if (!manager.isClosed()) {
                manager.close();
            }
        }

        assertThat(manager.isClosed()).isTrue();
        assertThat(cache).isNotNull();
        assertThat(cache.isClosed()).isTrue();
    }
}
