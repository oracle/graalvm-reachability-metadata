/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micrometer.micrometer_core;

import com.hazelcast.core.IMap;
import com.hazelcast.monitor.LocalMapStats;
import com.hazelcast.monitor.NearCacheStats;
import io.micrometer.core.instrument.binder.cache.HazelcastCacheMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class HazelcastIMapAdapterTest {
    @Test
    void bindsHazelcast3IMapMetricsThroughFallbackPackage() {
        Assumptions.assumeFalse(
                isNativeImageRuntime(),
                "Micrometer's Hazelcast 3 adapter resolves getName() with a native-image-incompatible method handle"
        );

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        IMap<Object, Object> cache = createIMap("hazelcast-cache");

        Object monitoredCache = HazelcastCacheMetrics.monitor(registry, cache, "node", "local");

        assertThat(monitoredCache).isSameAs(cache);
        assertThat(registry.get("cache.size").tag("cache", "hazelcast-cache").gauge().value()).isEqualTo(4.0);
        assertThat(registry.get("cache.gets").tag("cache", "hazelcast-cache").tag("result", "hit")
                .functionCounter().count()).isEqualTo(7.0);
        assertThat(registry.get("cache.puts").tag("cache", "hazelcast-cache").functionCounter().count())
                .isEqualTo(3.0);
        assertThat(registry.get("cache.entries").tag("cache", "hazelcast-cache").tag("ownership", "backup")
                .gauge().value()).isEqualTo(2.0);
        assertThat(registry.get("cache.entry.memory").tag("cache", "hazelcast-cache").tag("ownership", "owned")
                .gauge().value()).isEqualTo(128.0);
        assertThat(registry.get("cache.near.requests").tag("cache", "hazelcast-cache").tag("result", "hit")
                .gauge().value()).isEqualTo(11.0);
        assertThat(registry.get("cache.near.evictions").tag("cache", "hazelcast-cache").gauge().value())
                .isEqualTo(13.0);
        assertThat(registry.get("cache.gets.latency").tag("cache", "hazelcast-cache").functionTimer()
                .totalTime(TimeUnit.MILLISECONDS)).isEqualTo(25.0);
    }

    @SuppressWarnings("unchecked")
    private IMap<Object, Object> createIMap(String name) {
        LocalMapStats localMapStats = createLocalMapStats();
        InvocationHandler handler = (Object proxy, Method method, Object[] args) -> {
            String methodName = method.getName();
            if ("getName".equals(methodName)) {
                return name;
            }
            if ("getLocalMapStats".equals(methodName)) {
                return localMapStats;
            }
            return defaultValue(proxy, method, args);
        };
        return (IMap<Object, Object>) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {IMap.class },
                handler);
    }

    private LocalMapStats createLocalMapStats() {
        NearCacheStats nearCacheStats = createNearCacheStats();
        InvocationHandler handler = (Object proxy, Method method, Object[] args) -> {
            String methodName = method.getName();
            if ("getNearCacheStats".equals(methodName)) {
                return nearCacheStats;
            }
            if ("getOwnedEntryCount".equals(methodName)) {
                return 4L;
            }
            if ("getHits".equals(methodName)) {
                return 7L;
            }
            if ("getPutOperationCount".equals(methodName)) {
                return 3L;
            }
            if ("getBackupEntryCount".equals(methodName)) {
                return 2L;
            }
            if ("getBackupEntryMemoryCost".equals(methodName)) {
                return 64L;
            }
            if ("getOwnedEntryMemoryCost".equals(methodName)) {
                return 128L;
            }
            if ("getGetOperationCount".equals(methodName)) {
                return 5L;
            }
            if ("getTotalGetLatency".equals(methodName)) {
                return 25L;
            }
            if ("getTotalPutLatency".equals(methodName)) {
                return 30L;
            }
            if ("getRemoveOperationCount".equals(methodName)) {
                return 1L;
            }
            if ("getTotalRemoveLatency".equals(methodName)) {
                return 10L;
            }
            return defaultValue(proxy, method, args);
        };
        return (LocalMapStats) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {LocalMapStats.class }, handler);
    }

    private NearCacheStats createNearCacheStats() {
        InvocationHandler handler = (Object proxy, Method method, Object[] args) -> {
            String methodName = method.getName();
            if ("getHits".equals(methodName)) {
                return 11L;
            }
            if ("getMisses".equals(methodName)) {
                return 12L;
            }
            if ("getEvictions".equals(methodName)) {
                return 13L;
            }
            if ("getPersistenceCount".equals(methodName)) {
                return 14L;
            }
            return defaultValue(proxy, method, args);
        };
        return (NearCacheStats) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {NearCacheStats.class }, handler);
    }

    private Object defaultValue(Object proxy, Method method, Object[] args) {
        String methodName = method.getName();
        if ("toString".equals(methodName)) {
            return "Hazelcast IMap test proxy";
        }
        if ("hashCode".equals(methodName)) {
            return System.identityHashCode(proxy);
        }
        if ("equals".equals(methodName)) {
            return proxy == args[0];
        }
        Class<?> returnType = method.getReturnType();
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (boolean.class.equals(returnType)) {
            return false;
        }
        if (char.class.equals(returnType)) {
            return '\0';
        }
        if (byte.class.equals(returnType)) {
            return (byte) 0;
        }
        if (short.class.equals(returnType)) {
            return (short) 0;
        }
        if (int.class.equals(returnType)) {
            return 0;
        }
        if (long.class.equals(returnType)) {
            return 0L;
        }
        if (float.class.equals(returnType)) {
            return 0.0f;
        }
        if (double.class.equals(returnType)) {
            return 0.0d;
        }
        return null;
    }

    private static boolean isNativeImageRuntime() {
        return "runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"));
    }
}
