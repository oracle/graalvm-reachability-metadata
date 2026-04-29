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

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class CacheManagerImplTest {

    @Test
    void createsNamedManagerAndTracksActiveCaches() {
        String managerName = uniqueName("manager");
        CacheManager manager = CacheManager.getInstance(managerName);
        try {
            assertThat(manager.getName()).isEqualTo(managerName);
            assertThat(manager.isDefaultManager()).isFalse();
            assertThat(manager.isClosed()).isFalse();
            assertThat(manager.getProperties()).isNotNull();

            Cache<String, Integer> cache = Cache2kBuilder.of(String.class, Integer.class)
                    .manager(manager)
                    .name(uniqueName("cache"))
                    .entryCapacity(4)
                    .build();
            try {
                cache.put("one", 1);
                cache.put("two", 2);

                assertThat(cache.getCacheManager()).isSameAs(manager);
                assertThat(cache.peek("one")).isEqualTo(1);
                assertThat(cache.containsKey("two")).isTrue();
                assertThat(manager.getCache(cache.getName())).isSameAs(cache);
                assertThat(activeCacheNames(manager)).containsExactly(cache.getName());
            } finally {
                cache.close();
            }

            assertThat(activeCacheNames(manager)).isEmpty();
        } finally {
            manager.close();
        }
        assertThat(manager.isClosed()).isTrue();
    }

    @Test
    void rejectsDuplicateCacheNamesThroughPublicBuilder() {
        String managerName = uniqueName("manager");
        String cacheName = uniqueName("cache");
        CacheManager manager = CacheManager.getInstance(managerName);
        Cache<String, String> cache = Cache2kBuilder.of(String.class, String.class)
                .manager(manager)
                .name(cacheName)
                .entryCapacity(2)
                .build();
        try {
            assertThatThrownBy(() -> Cache2kBuilder.of(String.class, String.class)
                    .manager(manager)
                    .name(cacheName)
                    .entryCapacity(2)
                    .build())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cache already created");
        } finally {
            cache.close();
            manager.close();
        }
    }

    private static List<String> activeCacheNames(CacheManager manager) {
        List<String> names = new ArrayList<>();
        for (Cache<?, ?> cache : manager.getActiveCaches()) {
            names.add(cache.getName());
        }
        return names;
    }

    private static String uniqueName(String label) {
        return CacheManagerImplTest.class.getSimpleName() + "-" + label + "-"
                + Long.toUnsignedString(System.nanoTime(), 36);
    }
}
