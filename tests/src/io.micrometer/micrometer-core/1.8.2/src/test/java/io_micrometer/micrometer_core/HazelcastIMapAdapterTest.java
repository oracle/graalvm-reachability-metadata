/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micrometer.micrometer_core;

import java.lang.reflect.Proxy;

import com.hazelcast.core.IMap;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.HazelcastCacheMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class HazelcastIMapAdapterTest {
    private static final String CACHE_NAME = "hazelcast-cache";

    @Test
    void bindsLegacyHazelcastIMapThroughPublicCacheBinder() {
        MeterRegistry registry = new SimpleMeterRegistry();
        IMap<String, String> cache = hazelcastMapWithoutLocalStats();

        Object monitoredCache = HazelcastCacheMetrics.monitor(registry, cache, "scenario", "legacy");

        assertThat(monitoredCache).isSameAs(cache);
        assertThat(registry.find("cache.gets").tag("cache", CACHE_NAME).tag("result", "hit").functionCounter()
                .count()).isEqualTo(0.0);
        assertThat(registry.find("cache.puts").tag("cache", CACHE_NAME).functionCounter().count()).isEqualTo(0.0);
        assertThat(registry.find("cache.size").tag("cache", CACHE_NAME).gauge()).isNull();
    }

    @SuppressWarnings("unchecked")
    private static IMap<String, String> hazelcastMapWithoutLocalStats() {
        return (IMap<String, String>) Proxy.newProxyInstance(HazelcastIMapAdapterTest.class.getClassLoader(),
                new Class<?>[] { IMap.class }, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getName":
                            return CACHE_NAME;
                        case "getLocalMapStats":
                            return null;
                        case "hashCode":
                            return System.identityHashCode(proxy);
                        case "equals":
                            return proxy == args[0];
                        case "toString":
                            return CACHE_NAME;
                        default:
                            return defaultValue(method.getReturnType());
                    }
                });
    }

    private static Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == char.class) {
            return '\0';
        }
        if (returnType == byte.class) {
            return (byte) 0;
        }
        if (returnType == short.class) {
            return (short) 0;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == long.class) {
            return 0L;
        }
        if (returnType == float.class) {
            return 0F;
        }
        if (returnType == double.class) {
            return 0D;
        }
        return null;
    }
}
