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
import java.util.concurrent.TimeUnit;

import com.hazelcast.aggregation.Aggregator;
import com.hazelcast.core.EntryListener;
import com.hazelcast.core.EntryView;
import com.hazelcast.core.ExecutionCallback;
import com.hazelcast.core.ICompletableFuture;
import com.hazelcast.core.IMap;
import com.hazelcast.map.EntryProcessor;
import com.hazelcast.map.MapInterceptor;
import com.hazelcast.map.QueryCache;
import com.hazelcast.map.listener.MapListener;
import com.hazelcast.map.listener.MapPartitionLostListener;
import com.hazelcast.mapreduce.JobTracker;
import com.hazelcast.mapreduce.aggregation.Aggregation;
import com.hazelcast.mapreduce.aggregation.Supplier;
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
    void bindsMetricsForHazelcastThreeIMap() {
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

    private static final class TestIMap implements IMap<String, String> {
        private final String name;
        private final Map<String, String> entries = new HashMap<>();

        private TestIMap(String name) {
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
        public ICompletableFuture<String> getAsync(String key) {
            return unsupported();
        }

        @Override
        public ICompletableFuture<String> putAsync(String key, String value) {
            return unsupported();
        }

        @Override
        public ICompletableFuture<String> putAsync(String key, String value, long ttl, TimeUnit ttlUnit) {
            return unsupported();
        }

        @Override
        public ICompletableFuture<String> putAsync(String key, String value, long ttl, TimeUnit ttlUnit, long maxIdle,
                TimeUnit maxIdleUnit) {
            return unsupported();
        }

        @Override
        public ICompletableFuture<Void> setAsync(String key, String value) {
            return unsupported();
        }

        @Override
        public ICompletableFuture<Void> setAsync(String key, String value, long ttl, TimeUnit ttlUnit) {
            return unsupported();
        }

        @Override
        public ICompletableFuture<Void> setAsync(String key, String value, long ttl, TimeUnit ttlUnit, long maxIdle,
                TimeUnit maxIdleUnit) {
            return unsupported();
        }

        @Override
        public ICompletableFuture<String> removeAsync(String key) {
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
        public String addLocalEntryListener(MapListener listener) {
            return unsupported();
        }

        @Override
        public String addLocalEntryListener(EntryListener listener) {
            return unsupported();
        }

        @Override
        public String addLocalEntryListener(MapListener listener, Predicate<String, String> predicate,
                boolean includeValue) {
            return unsupported();
        }

        @Override
        public String addLocalEntryListener(EntryListener listener, Predicate<String, String> predicate,
                boolean includeValue) {
            return unsupported();
        }

        @Override
        public String addLocalEntryListener(MapListener listener, Predicate<String, String> predicate, String key,
                boolean includeValue) {
            return unsupported();
        }

        @Override
        public String addLocalEntryListener(EntryListener listener, Predicate<String, String> predicate, String key,
                boolean includeValue) {
            return unsupported();
        }

        @Override
        public String addInterceptor(MapInterceptor interceptor) {
            return unsupported();
        }

        @Override
        public void removeInterceptor(String id) {
        }

        @Override
        public String addEntryListener(MapListener listener, boolean includeValue) {
            return unsupported();
        }

        @Override
        public String addEntryListener(EntryListener listener, boolean includeValue) {
            return unsupported();
        }

        @Override
        public boolean removeEntryListener(String id) {
            return false;
        }

        @Override
        public String addPartitionLostListener(MapPartitionLostListener listener) {
            return unsupported();
        }

        @Override
        public boolean removePartitionLostListener(String id) {
            return false;
        }

        @Override
        public String addEntryListener(MapListener listener, String key, boolean includeValue) {
            return unsupported();
        }

        @Override
        public String addEntryListener(EntryListener listener, String key, boolean includeValue) {
            return unsupported();
        }

        @Override
        public String addEntryListener(MapListener listener, Predicate<String, String> predicate,
                boolean includeValue) {
            return unsupported();
        }

        @Override
        public String addEntryListener(EntryListener listener, Predicate<String, String> predicate,
                boolean includeValue) {
            return unsupported();
        }

        @Override
        public String addEntryListener(MapListener listener, Predicate<String, String> predicate, String key,
                boolean includeValue) {
            return unsupported();
        }

        @Override
        public String addEntryListener(EntryListener listener, Predicate<String, String> predicate, String key,
                boolean includeValue) {
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
        public Set<String> keySet(Predicate predicate) {
            return entries.keySet();
        }

        @Override
        public Set<Map.Entry<String, String>> entrySet(Predicate predicate) {
            return entries.entrySet();
        }

        @Override
        public Collection<String> values(Predicate predicate) {
            return entries.values();
        }

        @Override
        public Set<String> localKeySet() {
            return new HashSet<>(entries.keySet());
        }

        @Override
        public Set<String> localKeySet(Predicate predicate) {
            return localKeySet();
        }

        @Override
        public void addIndex(String attribute, boolean ordered) {
        }

        @Override
        public LocalMapStats getLocalMapStats() {
            return null;
        }

        @Override
        public Object executeOnKey(String key, EntryProcessor entryProcessor) {
            return unsupported();
        }

        @Override
        public Map<String, Object> executeOnKeys(Set<String> keys, EntryProcessor entryProcessor) {
            return unsupported();
        }

        @Override
        public void submitToKey(String key, EntryProcessor entryProcessor, ExecutionCallback callback) {
            unsupported();
        }

        @Override
        public ICompletableFuture submitToKey(String key, EntryProcessor entryProcessor) {
            return unsupported();
        }

        @Override
        public Map<String, Object> executeOnEntries(EntryProcessor entryProcessor) {
            return unsupported();
        }

        @Override
        public Map<String, Object> executeOnEntries(EntryProcessor entryProcessor, Predicate predicate) {
            return unsupported();
        }

        @Override
        public <R> R aggregate(Aggregator<Map.Entry<String, String>, R> aggregator) {
            return unsupported();
        }

        @Override
        public <R> R aggregate(Aggregator<Map.Entry<String, String>, R> aggregator,
                Predicate<String, String> predicate) {
            return unsupported();
        }

        @Override
        public <R> Collection<R> project(Projection<Map.Entry<String, String>, R> projection) {
            return unsupported();
        }

        @Override
        public <R> Collection<R> project(Projection<Map.Entry<String, String>, R> projection,
                Predicate<String, String> predicate) {
            return unsupported();
        }

        @Override
        public <SuppliedValue, Result> Result aggregate(Supplier<String, String, SuppliedValue> supplier,
                Aggregation<String, SuppliedValue, Result> aggregation) {
            return unsupported();
        }

        @Override
        public <SuppliedValue, Result> Result aggregate(Supplier<String, String, SuppliedValue> supplier,
                Aggregation<String, SuppliedValue, Result> aggregation, JobTracker jobTracker) {
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
