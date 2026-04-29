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
    public void createsManagerAndTracksCacheLifecycle() {
        String managerName = "CacheManagerImplTest-" + System.nanoTime();
        CacheManager manager = CacheManager.getInstance(managerName);
        try {
            assertThat(manager.getClass().getName()).isEqualTo("org.cache2k.core.CacheManagerImpl");
            assertThat(manager.getName()).isEqualTo(managerName);
            assertThat(manager.isDefaultManager()).isFalse();
            assertThat(manager.isClosed()).isFalse();

            Cache<Integer, String> cache = Cache2kBuilder.of(Integer.class, String.class)
                    .manager(manager)
                    .name("cacheLifecycle-" + System.nanoTime())
                    .entryCapacity(10)
                    .build();
            try {
                cache.put(1, "one");

                assertThat(cache.peek(1)).isEqualTo("one");
                assertThat(cache.getCacheManager()).isSameAs(manager);
                assertThat(manager.getActiveCaches()).contains(cache);
                assertThat(manager.<Integer, String>getCache(cache.getName())).isSameAs(cache);
            } finally {
                cache.close();
            }

            assertThat(cache.isClosed()).isTrue();
            assertThat(manager.getActiveCaches()).doesNotContain(cache);
        } finally {
            manager.close();
        }

        assertThat(manager.isClosed()).isTrue();
    }
}
