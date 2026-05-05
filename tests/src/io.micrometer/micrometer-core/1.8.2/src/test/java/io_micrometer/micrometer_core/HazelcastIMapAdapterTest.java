/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micrometer.micrometer_core;

import com.hazelcast.map.IMap;
import com.hazelcast.monitor.LocalMapStats;
import com.hazelcast.monitor.NearCacheStats;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.cache.HazelcastCacheMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class HazelcastIMapAdapterTest {
    @Test
    void monitorsHazelcastThreeIMapMetrics() {
        NearCacheStats nearCacheStats = proxy(NearCacheStats.class, values(
                "getHits", 7L,
                "getMisses", 8L,
                "getEvictions", 9L,
                "getPersistenceCount", 10L));
        LocalMapStats localMapStats = proxy(LocalMapStats.class, values(
                "getOwnedEntryCount", 11L,
                "getHits", 12L,
                "getPutOperationCount", 13L,
                "getBackupEntryCount", 14L,
                "getBackupEntryMemoryCost", 15L,
                "getOwnedEntryMemoryCost", 16L,
                "getGetOperationCount", 17L,
                "getNearCacheStats", nearCacheStats,
                "getTotalGetLatency", 18L,
                "getTotalPutLatency", 19L,
                "getRemoveOperationCount", 20L,
                "getTotalRemoveLatency", 21L));
        IMap<Object, Object> cache = proxy(IMap.class, values(
                "getName", "hazelcast-cache",
                "getLocalMapStats", localMapStats));

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        Object monitoredCache = HazelcastCacheMetrics.monitor(registry, cache, "region", "primary");

        assertThat(monitoredCache).isSameAs(cache);
        assertGauge(registry, "cache.size", 11.0);
        assertCounter(registry, "cache.gets", 12.0, "result", "hit");
        assertCounter(registry, "cache.puts", 13.0);
        assertGauge(registry, "cache.entries", 14.0, "ownership", "backup");
        assertGauge(registry, "cache.entry.memory", 16.0, "ownership", "owned");
        assertCounter(registry, "cache.partition.gets", 17.0);
        assertFunctionTimer(registry, "cache.gets.latency", 17.0, 18.0);
        assertFunctionTimer(registry, "cache.puts.latency", 13.0, 19.0);
        assertFunctionTimer(registry, "cache.removals.latency", 20.0, 21.0);
        assertGauge(registry, "cache.near.requests", 7.0, "result", "hit");
        assertGauge(registry, "cache.near.requests", 8.0, "result", "miss");
        assertGauge(registry, "cache.near.evictions", 9.0);
        assertGauge(registry, "cache.near.persistences", 10.0);
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Map<String, Object> values) {
        InvocationHandler handler = new StubInvocationHandler(type, values);
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
    }

    private static Map<String, Object> values(Object... pairs) {
        Map<String, Object> values = new HashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            values.put((String) pairs[index], pairs[index + 1]);
        }
        return values;
    }

    private static void assertGauge(SimpleMeterRegistry registry, String name, double value, String... tags) {
        Gauge gauge = registry.find(name).tags(tagsWithCacheName(tags)).gauge();
        assertThat(gauge).isNotNull();
        assertThat(gauge.value()).isEqualTo(value);
    }

    private static void assertCounter(SimpleMeterRegistry registry, String name, double value, String... tags) {
        FunctionCounter counter = registry.find(name).tags(tagsWithCacheName(tags)).functionCounter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(value);
    }

    private static void assertFunctionTimer(SimpleMeterRegistry registry, String name, double count, double totalTime) {
        FunctionTimer timer = registry.find(name).tags(tagsWithCacheName()).functionTimer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(count);
        assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(totalTime);
    }

    private static String[] tagsWithCacheName(String... tags) {
        String[] allTags = new String[tags.length + 4];
        allTags[0] = "cache";
        allTags[1] = "hazelcast-cache";
        allTags[2] = "region";
        allTags[3] = "primary";
        System.arraycopy(tags, 0, allTags, 4, tags.length);
        return allTags;
    }

    private static final class StubInvocationHandler implements InvocationHandler {
        private final Class<?> type;
        private final Map<String, Object> values;

        private StubInvocationHandler(Class<?> type, Map<String, Object> values) {
            this.type = type;
            this.values = values;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String methodName = method.getName();
            if (method.getDeclaringClass() == Object.class) {
                return invokeObjectMethod(proxy, methodName, args);
            }
            if (values.containsKey(methodName)) {
                return values.get(methodName);
            }
            throw new UnsupportedOperationException(type.getName() + "#" + methodName + " is not used by this test");
        }

        private Object invokeObjectMethod(Object proxy, String methodName, Object[] args) {
            if ("toString".equals(methodName)) {
                return type.getSimpleName() + values;
            }
            if ("hashCode".equals(methodName)) {
                return System.identityHashCode(proxy);
            }
            if ("equals".equals(methodName)) {
                return proxy == args[0];
            }
            throw new UnsupportedOperationException(type.getName() + "#" + methodName + " is not used by this test");
        }
    }
}
