/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_ehcache.ehcache;

import org.ehcache.Cache;
import org.ehcache.CacheManager;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.config.units.MemoryUnit;
import org.ehcache.impl.serialization.LongSerializer;
import org.ehcache.impl.serialization.PlainJavaSerializer;
import org.ehcache.spi.serialization.Serializer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SerializationConfigurationTest {
    @Test
    void testClassConfiguredSerializers() {
        CacheConfigurationBuilder<Long, MemoryUnit> configuration = CacheConfigurationBuilder
                .newCacheConfigurationBuilder(Long.class, MemoryUnit.class,
                        ResourcePoolsBuilder.newResourcePoolsBuilder().offheap(1, MemoryUnit.MB))
                .withKeySerializer(LongSerializer.class)
                .withValueSerializer(plainJavaSerializerClass())
                .withValueSerializingCopier();

        try (CacheManager cacheManager = CacheManagerBuilder.newCacheManagerBuilder()
                .withCache("serialized", configuration)
                .build(true)) {
            Cache<Long, MemoryUnit> cache = cacheManager.getCache("serialized", Long.class, MemoryUnit.class);
            cache.put(1L, MemoryUnit.GB);

            assertThat(cache.get(1L)).isEqualTo(MemoryUnit.GB);
            assertThat(cache.replace(1L, MemoryUnit.GB, MemoryUnit.MB)).isTrue();
            assertThat(cache.get(1L)).isEqualTo(MemoryUnit.MB);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Class<? extends Serializer<MemoryUnit>> plainJavaSerializerClass() {
        return (Class) PlainJavaSerializer.class;
    }
}
