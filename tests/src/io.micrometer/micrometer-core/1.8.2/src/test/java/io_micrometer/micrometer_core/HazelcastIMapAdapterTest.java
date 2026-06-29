/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micrometer.micrometer_core;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;

import com.hazelcast.aggregation.Aggregator;
import com.hazelcast.config.IndexConfig;
import com.hazelcast.core.EntryListener;
import com.hazelcast.core.EntryView;
import com.hazelcast.core.ExecutionCallback;
import com.hazelcast.map.EntryProcessor;
import com.hazelcast.map.IMap;
import com.hazelcast.map.MapInterceptor;
import com.hazelcast.map.QueryCache;
import com.hazelcast.map.listener.MapListener;
import com.hazelcast.map.listener.MapPartitionLostListener;
import com.hazelcast.monitor.LocalMapStats;
import com.hazelcast.projection.Projection;
import com.hazelcast.query.Predicate;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.cache.HazelcastCacheMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class HazelcastIMapAdapterTest {
    @Test
    void bindsMetricsForHazelcastIMap() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        TestIMap cache = new TestIMap("orders");

        Object monitored = HazelcastCacheMetrics.monitor(registry, cache,
                Collections.singleton(Tag.of("node", "test")));

        assertThat(monitored).isSameAs(cache);
        assertThat(registry.find("cache.gets").tag("cache", "orders").tag("result", "hit").functionCounter())
                .isNotNull();
        assertThat(registry.find("cache.puts").tag("cache", "orders").functionCounter()).isNotNull();
        assertThat(registry.find("cache.entries").tag("cache", "orders").tag("ownership", "owned").gauge())
                .isNotNull();
    }

    static class TestIMap implements IMap<String, String> {
        private final String name;
        private final Map<String, String> entries = new HashMap<>();

        TestIMap(String name) {
            this.name = name;
        }

        @Override
        public String getPartitionKey() {
            return name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getServiceName() {
            return "hz:impl:mapService";
        }

        @Override
        public void destroy() {
            entries.clear();
        }

        @Override
        public void putAll(Map<? extends String, ? extends String> map) {
            entries.putAll(map);
        }

        @Override
        public boolean containsKey(Object key) {
            return entries.containsKey(key);
        }

        @Override
        public boolean containsValue(Object value) {
            return entries.containsValue(value);
        }

        @Override
        public String get(Object key) {
            return entries.get(key);
        }

        @Override
        public String put(String key, String value) {
            return entries.put(key, value);
        }

        @Override
        public String remove(Object key) {
            return entries.remove(key);
        }

        @Override
        public boolean remove(Object key, Object value) {
            return entries.remove(key, value);
        }

        @Override
        public void removeAll(Predicate<String, String> predicate) {
            unsupported();
        }

        @Override
        public void delete(Object key) {
            entries.remove(key);
        }

        @Override
        public void flush() {
        }

        @Override
        public Map<String, String> getAll(Set<String> keys) {
            Map<String, String> selected = new HashMap<>();
            for (String key : keys) {
                if (entries.containsKey(key)) {
                    selected.put(key, entries.get(key));
                }
            }
            return selected;
        }

        @Override
        public void loadAll(boolean replaceExistingValues) {
        }

        @Override
        public void loadAll(Set<String> keys, boolean replaceExistingValues) {
        }

        @Override
        public void clear() {
            entries.clear();
        }

        @Override
        public CompletionStage<String> getAsync(String key) {
            return unsupported();
        }

        @Override
        public CompletionStage<String> putAsync(String key, String value) {
            return unsupported();
        }

        @Override
        public CompletionStage<String> putAsync(String key, String value, long ttl, TimeUnit ttlUnit) {
            return unsupported();
        }

        @Override
        public CompletionStage<String> putAsync(String key, String value, long ttl, TimeUnit ttlUnit, long maxIdle,
                TimeUnit maxIdleUnit) {
            return unsupported();
        }

        @Override
        public CompletionStage<Void> setAsync(String key, String value) {
            return unsupported();
        }

        @Override
        public CompletionStage<Void> setAsync(String key, String value, long ttl, TimeUnit ttlUnit) {
            return unsupported();
        }

        @Override
        public CompletionStage<Void> setAsync(String key, String value, long ttl, TimeUnit ttlUnit, long maxIdle,
                TimeUnit maxIdleUnit) {
            return unsupported();
        }

        @Override
        public CompletionStage<String> removeAsync(String key) {
            return unsupported();
        }

        @Override
        public boolean tryRemove(String key, long timeout, TimeUnit timeunit) {
            return entries.remove(key) != null;
        }

        @Override
        public boolean tryPut(String key, String value, long timeout, TimeUnit timeunit) {
            return entries.putIfAbsent(key, value) == null;
        }

        @Override
        public String put(String key, String value, long ttl, TimeUnit ttlUnit) {
            return entries.put(key, value);
        }

        @Override
        public String put(String key, String value, long ttl, TimeUnit ttlUnit, long maxIdle, TimeUnit maxIdleUnit) {
            return entries.put(key, value);
        }

        @Override
        public void putTransient(String key, String value, long ttl, TimeUnit ttlUnit) {
            entries.put(key, value);
        }

        @Override
        public void putTransient(String key, String value, long ttl, TimeUnit ttlUnit, long maxIdle,
                TimeUnit maxIdleUnit) {
            entries.put(key, value);
        }

        @Override
        public String putIfAbsent(String key, String value) {
            return entries.putIfAbsent(key, value);
        }

        @Override
        public String putIfAbsent(String key, String value, long ttl, TimeUnit ttlUnit) {
            return entries.putIfAbsent(key, value);
        }

        @Override
        public String putIfAbsent(String key, String value, long ttl, TimeUnit ttlUnit, long maxIdle,
                TimeUnit maxIdleUnit) {
            return entries.putIfAbsent(key, value);
        }

        @Override
        public boolean replace(String key, String oldValue, String newValue) {
            return entries.replace(key, oldValue, newValue);
        }

        @Override
        public String replace(String key, String value) {
            return entries.replace(key, value);
        }

        @Override
        public void set(String key, String value) {
            entries.put(key, value);
        }

        @Override
        public void set(String key, String value, long ttl, TimeUnit ttlUnit) {
            entries.put(key, value);
        }

        @Override
        public void set(String key, String value, long ttl, TimeUnit ttlUnit, long maxIdle, TimeUnit maxIdleUnit) {
            entries.put(key, value);
        }

        @Override
        public void lock(String key) {
        }

        @Override
        public void lock(String key, long leaseTime, TimeUnit timeUnit) {
        }

        @Override
        public boolean isLocked(String key) {
            return false;
        }

        @Override
        public boolean tryLock(String key) {
            return true;
        }

        @Override
        public boolean tryLock(String key, long time, TimeUnit timeunit) {
            return true;
        }

        @Override
        public boolean tryLock(String key, long time, TimeUnit timeunit, long leaseTime, TimeUnit leaseTimeunit) {
            return true;
        }

        @Override
        public void unlock(String key) {
        }

        @Override
        public void forceUnlock(String key) {
        }

        @Override
        public UUID addLocalEntryListener(MapListener listener) {
            return unsupported();
        }

        @Override
        public UUID addLocalEntryListener(EntryListener<String, String> listener) {
            return unsupported();
        }

        @Override
        public UUID addLocalEntryListener(MapListener listener, Predicate<String, String> predicate,
                boolean includeValue) {
            return unsupported();
        }

        @Override
        public UUID addLocalEntryListener(EntryListener<String, String> listener,
                Predicate<String, String> predicate, boolean includeValue) {
            return unsupported();
        }

        @Override
        public UUID addLocalEntryListener(MapListener listener, Predicate<String, String> predicate, String key,
                boolean includeValue) {
            return unsupported();
        }

        @Override
        public UUID addLocalEntryListener(EntryListener<String, String> listener,
                Predicate<String, String> predicate, String key, boolean includeValue) {
            return unsupported();
        }

        @Override
        public String addInterceptor(MapInterceptor interceptor) {
            return unsupported();
        }

        @Override
        public boolean removeInterceptor(String id) {
            return false;
        }

        @Override
        public UUID addEntryListener(MapListener listener, boolean includeValue) {
            return unsupported();
        }

        @Override
        public UUID addEntryListener(EntryListener<String, String> listener, boolean includeValue) {
            return unsupported();
        }

        @Override
        public boolean removeEntryListener(UUID id) {
            return false;
        }

        @Override
        public UUID addPartitionLostListener(MapPartitionLostListener listener) {
            return unsupported();
        }

        @Override
        public boolean removePartitionLostListener(UUID id) {
            return false;
        }

        @Override
        public UUID addEntryListener(MapListener listener, String key, boolean includeValue) {
            return unsupported();
        }

        @Override
        public UUID addEntryListener(EntryListener<String, String> listener, String key, boolean includeValue) {
            return unsupported();
        }

        @Override
        public UUID addEntryListener(MapListener listener, Predicate<String, String> predicate, boolean includeValue) {
            return unsupported();
        }

        @Override
        public UUID addEntryListener(EntryListener<String, String> listener,
                Predicate<String, String> predicate, boolean includeValue) {
            return unsupported();
        }

        @Override
        public UUID addEntryListener(MapListener listener, Predicate<String, String> predicate, String key,
                boolean includeValue) {
            return unsupported();
        }

        @Override
        public UUID addEntryListener(EntryListener<String, String> listener,
                Predicate<String, String> predicate, String key, boolean includeValue) {
            return unsupported();
        }

        @Override
        public EntryView<String, String> getEntryView(String key) {
            return unsupported();
        }

        @Override
        public boolean evict(String key) {
            return entries.remove(key) != null;
        }

        @Override
        public void evictAll() {
            entries.clear();
        }

        @Override
        public Set<String> keySet() {
            return entries.keySet();
        }

        @Override
        public Collection<String> values() {
            return entries.values();
        }

        @Override
        public Set<Map.Entry<String, String>> entrySet() {
            return entries.entrySet();
        }

        @Override
        public Set<String> keySet(Predicate<String, String> predicate) {
            return entries.keySet();
        }

        @Override
        public Set<Map.Entry<String, String>> entrySet(Predicate<String, String> predicate) {
            return entries.entrySet();
        }

        @Override
        public Collection<String> values(Predicate<String, String> predicate) {
            return entries.values();
        }

        @Override
        public Set<String> localKeySet() {
            return new HashSet<>(entries.keySet());
        }

        @Override
        public Set<String> localKeySet(Predicate<String, String> predicate) {
            return localKeySet();
        }

        @Override
        public void addIndex(IndexConfig indexConfig) {
        }

        @Override
        public LocalMapStats getLocalMapStats() {
            return null;
        }

        @Override
        public <R> R executeOnKey(String key, EntryProcessor<String, String, R> entryProcessor) {
            return unsupported();
        }

        @Override
        public <R> Map<String, R> executeOnKeys(Set<String> keys,
                EntryProcessor<String, String, R> entryProcessor) {
            return unsupported();
        }

        @Override
        public <R> void submitToKey(String key, EntryProcessor<String, String, R> entryProcessor,
                ExecutionCallback<? super R> callback) {
            unsupported();
        }

        @Override
        public <R> CompletionStage<R> submitToKey(String key, EntryProcessor<String, String, R> entryProcessor) {
            return unsupported();
        }

        @Override
        public <R> Map<String, R> executeOnEntries(EntryProcessor<String, String, R> entryProcessor) {
            return unsupported();
        }

        @Override
        public <R> Map<String, R> executeOnEntries(EntryProcessor<String, String, R> entryProcessor,
                Predicate<String, String> predicate) {
            return unsupported();
        }

        @Override
        public <R> R aggregate(Aggregator<? super Map.Entry<String, String>, R> aggregator) {
            return unsupported();
        }

        @Override
        public <R> R aggregate(Aggregator<? super Map.Entry<String, String>, R> aggregator,
                Predicate<String, String> predicate) {
            return unsupported();
        }

        @Override
        public <R> Collection<R> project(Projection<? super Map.Entry<String, String>, R> projection) {
            return unsupported();
        }

        @Override
        public <R> Collection<R> project(Projection<? super Map.Entry<String, String>, R> projection,
                Predicate<String, String> predicate) {
            return unsupported();
        }

        @Override
        public QueryCache<String, String> getQueryCache(String name) {
            return unsupported();
        }

        @Override
        public QueryCache<String, String> getQueryCache(String name, Predicate<String, String> predicate,
                boolean includeValue) {
            return unsupported();
        }

        @Override
        public QueryCache<String, String> getQueryCache(String name, MapListener listener,
                Predicate<String, String> predicate, boolean includeValue) {
            return unsupported();
        }

        @Override
        public boolean setTtl(String key, long ttl, TimeUnit timeunit) {
            return entries.containsKey(key);
        }

        @Override
        public String computeIfPresent(String key,
                BiFunction<? super String, ? super String, ? extends String> remappingFunction) {
            return entries.computeIfPresent(key, remappingFunction);
        }

        @Override
        public String computeIfAbsent(String key, Function<? super String, ? extends String> mappingFunction) {
            return entries.computeIfAbsent(key, mappingFunction);
        }

        @Override
        public String compute(String key,
                BiFunction<? super String, ? super String, ? extends String> remappingFunction) {
            return entries.compute(key, remappingFunction);
        }

        @Override
        public String merge(String key, String value,
                BiFunction<? super String, ? super String, ? extends String> remappingFunction) {
            return entries.merge(key, value, remappingFunction);
        }

        @Override
        public int size() {
            return entries.size();
        }

        @Override
        public boolean isEmpty() {
            return entries.isEmpty();
        }

        private static <T> T unsupported() {
            throw new UnsupportedOperationException("This test IMap only supports Micrometer metric binding methods");
        }
    }
}
