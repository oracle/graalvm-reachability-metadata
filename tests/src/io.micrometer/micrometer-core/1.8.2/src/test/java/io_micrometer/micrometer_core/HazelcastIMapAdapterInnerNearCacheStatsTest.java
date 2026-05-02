/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micrometer.micrometer_core;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import com.hazelcast.map.IMap;
import com.hazelcast.map.LocalMapStats;
import com.hazelcast.nearcache.NearCacheStats;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.HazelcastCacheMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class HazelcastIMapAdapterInnerNearCacheStatsTest {
    private static final String CACHE_NAME = "hazelcast-cache-with-near-cache-stats";

    @Test
    void bindsHazelcastNearCacheStatsThroughPublicCacheBinder() {
        MeterRegistry registry = new SimpleMeterRegistry();
        NearCacheStats nearCacheStats = nearCacheStats();
        LocalMapStats localMapStats = localMapStats(nearCacheStats);
        IMap<String, String> cache = hazelcastMap(localMapStats);

        Object monitoredCache = HazelcastCacheMetrics.monitor(registry, cache, "scenario", "near-cache-stats");

        assertThat(monitoredCache).isSameAs(cache);
        assertThat(registry.find("cache.near.requests").tag("cache", CACHE_NAME).tag("result", "hit").gauge()
                .value()).isEqualTo(29.0);
        assertThat(registry.find("cache.near.requests").tag("cache", CACHE_NAME).tag("result", "miss").gauge()
                .value()).isEqualTo(7.0);
        assertThat(registry.find("cache.near.evictions").tag("cache", CACHE_NAME).gauge().value()).isEqualTo(3.0);
        assertThat(registry.find("cache.near.persistences").tag("cache", CACHE_NAME).gauge().value())
                .isEqualTo(2.0);
    }

    @SuppressWarnings("unchecked")
    private static IMap<String, String> hazelcastMap(LocalMapStats localMapStats) {
        return (IMap<String, String>) Proxy.newProxyInstance(HazelcastIMapAdapterInnerNearCacheStatsTest.class
                .getClassLoader(), new Class<?>[] { IMap.class },
                (proxy, method, args) -> handleMapInvocation(proxy, method, args, localMapStats));
    }

    private static LocalMapStats localMapStats(NearCacheStats nearCacheStats) {
        return (LocalMapStats) Proxy.newProxyInstance(HazelcastIMapAdapterInnerNearCacheStatsTest.class
                .getClassLoader(), new Class<?>[] { LocalMapStats.class },
                (proxy, method, args) -> handleLocalMapStatsInvocation(proxy, method, args, nearCacheStats));
    }

    private static NearCacheStats nearCacheStats() {
        return (NearCacheStats) Proxy.newProxyInstance(HazelcastIMapAdapterInnerNearCacheStatsTest.class
                .getClassLoader(), new Class<?>[] { NearCacheStats.class },
                (proxy, method, args) -> handleNearCacheStatsInvocation(proxy, method, args));
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

    private static Object handleLocalMapStatsInvocation(Object proxy, Method method, Object[] args,
            NearCacheStats nearCacheStats) {
        switch (method.getName()) {
            case "getOwnedEntryCount":
                return 41L;
            case "getHits":
                return 19L;
            case "getPutOperationCount":
                return 5L;
            case "getBackupEntryCount":
                return 4L;
            case "getBackupEntryMemoryCost":
                return 2048L;
            case "getOwnedEntryMemoryCost":
                return 8192L;
            case "getGetOperationCount":
                return 36L;
            case "getTotalGetLatency":
                return 44L;
            case "getTotalPutLatency":
                return 21L;
            case "getRemoveOperationCount":
                return 6L;
            case "getTotalRemoveLatency":
                return 13L;
            case "getNearCacheStats":
                return nearCacheStats;
            case "hashCode":
                return System.identityHashCode(proxy);
            case "equals":
                return proxy == args[0];
            case "toString":
                return "local-map-stats-with-near-cache";
            default:
                return defaultValue(method.getReturnType());
        }
    }

    private static Object handleNearCacheStatsInvocation(Object proxy, Method method, Object[] args) {
        switch (method.getName()) {
            case "getHits":
                return 29L;
            case "getMisses":
                return 7L;
            case "getEvictions":
                return 3L;
            case "getPersistenceCount":
                return 2L;
            case "hashCode":
                return System.identityHashCode(proxy);
            case "equals":
                return proxy == args[0];
            case "toString":
                return "near-cache-stats";
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
