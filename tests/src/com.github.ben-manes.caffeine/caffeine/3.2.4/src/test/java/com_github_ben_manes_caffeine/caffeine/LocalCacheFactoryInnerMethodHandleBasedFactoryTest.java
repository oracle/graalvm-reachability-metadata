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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.ConcurrentMap;

import static org.assertj.core.api.Assertions.assertThat;

public class LocalCacheFactoryInnerMethodHandleBasedFactoryTest {
    @Test
    void constructsFactoryForBoundedCacheImplementation() throws Throwable {
        Cache<String, String> cache = Caffeine.newBuilder()
                .maximumSize(10)
                .build();
        Class<?> boundedCacheClass = cache.asMap().getClass();
        Class<?> fallbackFactoryClass = Class.forName(
                "com.github.benmanes.caffeine.cache.LocalCacheFactory$MethodHandleBasedFactory");

        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(fallbackFactoryClass,
                MethodHandles.lookup());
        MethodHandle constructor = lookup.findConstructor(fallbackFactoryClass,
                MethodType.methodType(void.class, Class.class));
        Object factory = constructor.invoke(boundedCacheClass);
        MethodHandle newInstance = lookup.findVirtual(fallbackFactoryClass, "newInstance", MethodType.methodType(
                Class.forName("com.github.benmanes.caffeine.cache.BoundedLocalCache"), Caffeine.class,
                Class.forName("com.github.benmanes.caffeine.cache.AsyncCacheLoader"), boolean.class));

        Object boundedCache = newInstance.invoke(factory, Caffeine.newBuilder().maximumSize(10), null, false);

        assertThat(boundedCache).isInstanceOf(ConcurrentMap.class);
        @SuppressWarnings("unchecked")
        ConcurrentMap<Object, Object> entries = (ConcurrentMap<Object, Object>) boundedCache;
        assertThat(entries.put("Hello", "World")).isNull();
        assertThat(entries.get("Hello")).isEqualTo("World");
    }
}
