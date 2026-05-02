/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micrometer.micrometer_core;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.TimeUnit;

import com.hazelcast.map.IMap;
import com.hazelcast.map.LocalMapStats;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.HazelcastCacheMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class HazelcastIMapAdapterInnerLocalMapStatsTest {
    private static final String CACHE_NAME = "hazelcast-cache-with-local-stats";

    @Test
    void bindsHazelcastLocalMapStatsThroughPublicCacheBinder() {
        MeterRegistry registry = new SimpleMeterRegistry();
        LocalMapStats localMapStats = localMapStats();
        IMap<String, String> cache = hazelcastMap(localMapStats);

        Object monitoredCache = HazelcastCacheMetrics.monitor(registry, cache, "scenario", "local-stats");

        assertThat(monitoredCache).isSameAs(cache);
        assertThat(registry.find("cache.size").tag("cache", CACHE_NAME).gauge().value()).isEqualTo(17.0);
        assertThat(registry.find("cache.gets").tag("cache", CACHE_NAME).tag("result", "hit").functionCounter()
                .count()).isEqualTo(11.0);
        assertThat(registry.find("cache.puts").tag("cache", CACHE_NAME).functionCounter().count()).isEqualTo(5.0);
        assertThat(registry.find("cache.entries").tag("cache", CACHE_NAME).tag("ownership", "backup").gauge()
                .value()).isEqualTo(3.0);
        assertThat(registry.find("cache.entries").tag("cache", CACHE_NAME).tag("ownership", "owned").gauge()
                .value()).isEqualTo(17.0);
        assertThat(registry.find("cache.entry.memory").tag("cache", CACHE_NAME).tag("ownership", "backup").gauge()
                .value()).isEqualTo(3072.0);
        assertThat(registry.find("cache.entry.memory").tag("cache", CACHE_NAME).tag("ownership", "owned").gauge()
                .value()).isEqualTo(4096.0);
        assertThat(registry.find("cache.partition.gets").tag("cache", CACHE_NAME).functionCounter().count())
                .isEqualTo(13.0);
        assertThat(registry.find("cache.gets.latency").tag("cache", CACHE_NAME).functionTimer().count())
                .isEqualTo(13.0);
        assertThat(registry.find("cache.gets.latency").tag("cache", CACHE_NAME).functionTimer()
                .totalTime(TimeUnit.MILLISECONDS)).isEqualTo(21.0);
        assertThat(registry.find("cache.puts.latency").tag("cache", CACHE_NAME).functionTimer().count())
                .isEqualTo(5.0);
        assertThat(registry.find("cache.puts.latency").tag("cache", CACHE_NAME).functionTimer()
                .totalTime(TimeUnit.MILLISECONDS)).isEqualTo(34.0);
        assertThat(registry.find("cache.removals.latency").tag("cache", CACHE_NAME).functionTimer().count())
                .isEqualTo(2.0);
        assertThat(registry.find("cache.removals.latency").tag("cache", CACHE_NAME).functionTimer()
                .totalTime(TimeUnit.MILLISECONDS)).isEqualTo(55.0);
        assertThat(registry.find("cache.near.requests").tag("cache", CACHE_NAME).gauge()).isNull();
    }

    @SuppressWarnings("unchecked")
    private static IMap<String, String> hazelcastMap(LocalMapStats localMapStats) {
        return (IMap<String, String>) Proxy.newProxyInstance(HazelcastIMapAdapterInnerLocalMapStatsTest.class
                .getClassLoader(), new Class<?>[] { IMap.class },
                (proxy, method, args) -> handleMapInvocation(proxy, method, args, localMapStats));
    }

    private static LocalMapStats localMapStats() {
        return (LocalMapStats) Proxy.newProxyInstance(HazelcastIMapAdapterInnerLocalMapStatsTest.class
                .getClassLoader(), new Class<?>[] { LocalMapStats.class },
                (proxy, method, args) -> handleLocalMapStatsInvocation(proxy, method, args));
    }

    private static Object handleMapInvocation(Object proxy, Method method, Object[] args, LocalMapStats localMapStats) {
        switch (method.getName()) {
            case "getName":
                return CACHE_NAME;
            case "getLocalMapStats":
                return localMapStats;
            case "hashCode":
                return System.identityHashCode(proxy);
            case "equals":
                return proxy == args[0];
            case "toString":
                return CACHE_NAME;
            default:
                return defaultValue(method.getReturnType());
        }
    }

    private static Object handleLocalMapStatsInvocation(Object proxy, Method method, Object[] args) {
        switch (method.getName()) {
            case "getOwnedEntryCount":
                return 17L;
            case "getHits":
                return 11L;
            case "getPutOperationCount":
                return 5L;
            case "getBackupEntryCount":
                return 3L;
            case "getBackupEntryMemoryCost":
                return 3072L;
            case "getOwnedEntryMemoryCost":
                return 4096L;
            case "getGetOperationCount":
                return 13L;
            case "getTotalGetLatency":
                return 21L;
            case "getTotalPutLatency":
                return 34L;
            case "getRemoveOperationCount":
                return 2L;
            case "getTotalRemoveLatency":
                return 55L;
            case "getNearCacheStats":
                return null;
            case "hashCode":
                return System.identityHashCode(proxy);
            case "equals":
                return proxy == args[0];
            case "toString":
                return "local-map-stats";
            default:
                return defaultValue(method.getReturnType());
        }
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
