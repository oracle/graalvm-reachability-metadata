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
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.HazelcastCacheMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import static org.assertj.core.api.Assertions.assertThat;

public class HazelcastIMapAdapterTest {
    private static final String CACHE_NAME = "orders-cache";

    @Test
    void bindsHazelcast3MapMetricsThroughPublicApi() {
        NearCacheStats nearCacheStats = proxy(NearCacheStats.class, new NearCacheStatsHandler());
        LocalMapStats localMapStats = proxy(LocalMapStats.class, new LocalMapStatsHandler(nearCacheStats));
        Object cache = proxy(IMap.class, new IMapHandler(localMapStats));
        MeterRegistry registry = new SimpleMeterRegistry();

        Object monitored = HazelcastCacheMetrics.monitor(registry, cache, "region", "primary");

        assertThat(monitored).isSameAs(cache);
        assertThat(registry.get("cache.size").tag("cache", CACHE_NAME).gauge().value()).isEqualTo(12.0);
        assertThat(registry.get("cache.gets").tag("cache", CACHE_NAME).tag("result", "hit").functionCounter().count())
                .isEqualTo(34.0);
        assertThat(registry.get("cache.puts").tag("cache", CACHE_NAME).functionCounter().count()).isEqualTo(56.0);
        assertThat(registry.get("cache.entries").tag("cache", CACHE_NAME).tag("ownership", "backup").gauge().value())
                .isEqualTo(7.0);
        assertThat(registry.get("cache.near.requests").tag("cache", CACHE_NAME).tag("result", "hit").gauge().value())
                .isEqualTo(3.0);
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> interfaceType, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(interfaceType.getClassLoader(), new Class<?>[] { interfaceType }, handler);
    }

    private static final class IMapHandler implements InvocationHandler {
        private final LocalMapStats localMapStats;

        private IMapHandler(LocalMapStats localMapStats) {
            this.localMapStats = localMapStats;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String name = method.getName();
            if ("getName".equals(name)) {
                return CACHE_NAME;
            }
            if ("getLocalMapStats".equals(name)) {
                return localMapStats;
            }
            return handleObjectMethod(proxy, method, args);
        }
    }

    private static final class LocalMapStatsHandler implements InvocationHandler {
        private final NearCacheStats nearCacheStats;

        private LocalMapStatsHandler(NearCacheStats nearCacheStats) {
            this.nearCacheStats = nearCacheStats;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "getOwnedEntryCount":
                    return 12L;
                case "getHits":
                    return 34L;
                case "getPutOperationCount":
                    return 56L;
                case "getBackupEntryCount":
                    return 7L;
                case "getBackupEntryMemoryCost":
                    return 800L;
                case "getOwnedEntryMemoryCost":
                    return 900L;
                case "getGetOperationCount":
                    return 11L;
                case "getTotalGetLatency":
                    return 22L;
                case "getTotalPutLatency":
                    return 33L;
                case "getRemoveOperationCount":
                    return 44L;
                case "getTotalRemoveLatency":
                    return 55L;
                case "getNearCacheStats":
                    return nearCacheStats;
                default:
                    return handleObjectMethod(proxy, method, args);
            }
        }
    }

    private static final class NearCacheStatsHandler implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "getHits":
                    return 3L;
                case "getMisses":
                    return 4L;
                case "getEvictions":
                    return 5L;
                case "getPersistenceCount":
                    return 6L;
                default:
                    return handleObjectMethod(proxy, method, args);
            }
        }
    }

    private static Object handleObjectMethod(Object proxy, Method method, Object[] args) {
        switch (method.getName()) {
            case "equals":
                return proxy == args[0];
            case "hashCode":
                return System.identityHashCode(proxy);
            case "toString":
                return proxy.getClass().getInterfaces()[0].getSimpleName() + "Proxy";
            default:
                throw new UnsupportedOperationException(method.toString());
        }
    }
}
