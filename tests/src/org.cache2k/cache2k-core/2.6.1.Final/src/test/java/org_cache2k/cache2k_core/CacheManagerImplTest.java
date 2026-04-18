/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_cache2k.cache2k_core;

import static org.assertj.core.api.Assertions.assertThat;

import org.cache2k.Cache;
import org.cache2k.Cache2kBuilder;
import org.cache2k.CacheManager;
import org.junit.jupiter.api.Test;

public class CacheManagerImplTest {

    @Test
    void createsManagerBackedCacheViaPublicApi() {
        String managerName = getClass().getSimpleName() + "-manager";
        String cacheName = getClass().getSimpleName() + "-cache";
        CacheManager manager = CacheManager.getInstance(getClass().getClassLoader(), managerName);
        try {
            assertThat(manager.getName()).isEqualTo(managerName);
            try (Cache<String, Integer> cache = Cache2kBuilder.of(String.class, Integer.class)
                .manager(manager)
                .name(cacheName)
                .build()) {
                cache.put("answer", 42);

                assertThat(cache.get("answer")).isEqualTo(42);
                assertThat(cache.getCacheManager()).isSameAs(manager);
                assertThat(manager.getActiveCaches()).contains(cache);
            }
            assertThat(manager.getActiveCaches()).isEmpty();
        } finally {
            manager.close();
        }
    }
}
