/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_streams;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.apache.kafka.streams.kstream.internals.SessionWindow;

import org.apache.kafka.common.metrics.Metrics;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.LogContext;
import org.apache.kafka.common.utils.Time;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.Windowed;
import org.apache.kafka.streams.processor.StateStoreContext;
import org.apache.kafka.streams.processor.TaskId;
import org.apache.kafka.streams.processor.internals.metrics.StreamsMetricsImpl;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.SessionStore;
import org.apache.kafka.streams.state.Stores;
import org.apache.kafka.streams.state.TimestampedKeyValueStore;
import org.apache.kafka.streams.state.TimestampedWindowStore;
import org.apache.kafka.streams.state.ValueAndTimestamp;
import org.apache.kafka.streams.state.WindowStore;
import org.apache.kafka.streams.state.WindowStoreIterator;
import org.apache.kafka.streams.state.internals.ThreadCache;
import org.apache.kafka.streams.state.internals.metrics.RocksDBMetricsRecorder;
import org.apache.kafka.test.InternalMockProcessorContext;
import org.apache.kafka.test.StreamsTestUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StateStoreInternalsCoverageTest {
    @TempDir
    File stateDirectory;

    @Test
    void rocksDbRecorderPublishesEmptyIntervalRatiosAndAverages() {
        Metrics metricsRegistry = new Metrics();
        StreamsMetricsImpl metrics = new StreamsMetricsImpl(
                metricsRegistry, "coverage-thread", "coverage-process", Time.SYSTEM);
        RocksDBMetricsRecorder recorder = new RocksDBMetricsRecorder("metrics", "rocks-metrics");
        recorder.init(metrics, new TaskId(0, 0));

        recorder.record(Time.SYSTEM.milliseconds());

        assertThat(metricsRegistry.metrics()).isNotEmpty();
        metricsRegistry.close();
    }

    @Test
    void inMemoryKeyValueStoresDriveOrderedAndTimestampedOperations() {
        InternalMockProcessorContext<Object, Object> context = new InternalMockProcessorContext<>();
        context.initialize();
        KeyValueStore<String, String> store = Stores.keyValueStoreBuilder(
                Stores.inMemoryKeyValueStore("ordered"), Serdes.String(), Serdes.String())
                .withLoggingDisabled().build();
        store.init((StateStoreContext) context, store);
        store.putAll(List.of(KeyValue.pair("alpha", "one"), KeyValue.pair("beta", "two")));

        assertThat(values(store.all())).containsExactly("one", "two");
        assertThat(values(store.reverseAll())).containsExactly("two", "one");
        assertThat(values(store.range("alpha", "beta"))).containsExactly("one", "two");
        assertThat(values(store.reverseRange("alpha", "beta"))).containsExactly("two", "one");
        assertThat(values(store.prefixScan("al", Serdes.String().serializer()))).containsExactly("one");
        assertThat(store.delete("alpha")).isEqualTo("one");
        store.close();

        TimestampedKeyValueStore<String, String> timestamped = Stores.timestampedKeyValueStoreBuilder(
                Stores.inMemoryKeyValueStore("timestamped"), Serdes.String(), Serdes.String())
                .withLoggingDisabled().build();
        timestamped.init((StateStoreContext) context, timestamped);
        timestamped.putAll(List.of(
                KeyValue.pair("alpha", ValueAndTimestamp.make("one", 1L)),
                KeyValue.pair("beta", ValueAndTimestamp.make("two", 2L))));
        assertThat(timestamped.get("alpha").value()).isEqualTo("one");
        assertThat(timestamped.approximateNumEntries()).isEqualTo(2L);
        assertThat(values(timestamped.range("alpha", "beta"))).extracting(ValueAndTimestamp::value)
                .containsExactly("one", "two");
        assertThat(values(timestamped.reverseRange("alpha", "beta"))).extracting(ValueAndTimestamp::value)
                .containsExactly("two", "one");
        assertThat(values(timestamped.all())).extracting(ValueAndTimestamp::value)
                .containsExactly("one", "two");
        assertThat(values(timestamped.reverseAll())).extracting(ValueAndTimestamp::value)
                .containsExactly("two", "one");
        assertThat(values(timestamped.prefixScan("al", Serdes.String().serializer())))
                .extracting(ValueAndTimestamp::value).containsExactly("one");
        assertThat(timestamped.delete("alpha").value()).isEqualTo("one");
        timestamped.close();
    }

    @Test
    void persistentStoresDriveRocksDbScansAndBackwardSegmentQueries() {
        InternalMockProcessorContext<Object, Object> context = new InternalMockProcessorContext<>(
                stateDirectory, new StreamsConfig(StreamsTestUtils.getStreamsConfig()));
        context.initialize();

        KeyValueStore<String, String> keyValues = Stores.keyValueStoreBuilder(
                Stores.persistentKeyValueStore("rocks-key-values"), Serdes.String(), Serdes.String())
                .withLoggingDisabled().build();
        keyValues.init((StateStoreContext) context, keyValues);
        keyValues.putAll(List.of(KeyValue.pair("alpha", "one"), KeyValue.pair("beta", "two")));
        assertThat(values(keyValues.all())).containsExactly("one", "two");
        assertThat(values(keyValues.prefixScan("al", Serdes.String().serializer())))
                .containsExactly("one");
        keyValues.close();

        TimestampedKeyValueStore<String, String> timestamped = Stores.timestampedKeyValueStoreBuilder(
                Stores.persistentTimestampedKeyValueStore("rocks-timestamped"),
                Serdes.String(), Serdes.String()).withLoggingDisabled().build();
        timestamped.init((StateStoreContext) context, timestamped);
        timestamped.putAll(List.of(
                KeyValue.pair("alpha", ValueAndTimestamp.make("one", 1L)),
                KeyValue.pair("beta", ValueAndTimestamp.make("two", 2L))));
        assertThat(timestamped.get("alpha").value()).isEqualTo("one");
        assertThat(timestamped.approximateNumEntries()).isEqualTo(2L);
        assertThat(values(timestamped.range("alpha", "beta"))).extracting(ValueAndTimestamp::value)
                .containsExactly("one", "two");
        assertThat(values(timestamped.reverseRange("alpha", "beta"))).extracting(ValueAndTimestamp::value)
                .containsExactly("two", "one");
        assertThat(values(timestamped.all())).extracting(ValueAndTimestamp::value)
                .containsExactly("one", "two");
        assertThat(values(timestamped.prefixScan("be", Serdes.String().serializer())))
                .extracting(ValueAndTimestamp::value).containsExactly("two");
        assertThat(timestamped.delete("alpha").value()).isEqualTo("one");
        timestamped.close();

        TimestampedWindowStore<String, String> timestampedWindows = Stores.timestampedWindowStoreBuilder(
                Stores.persistentTimestampedWindowStore("rocks-timestamped-windows",
                        Duration.ofMinutes(2), Duration.ofSeconds(10), true),
                Serdes.String(), Serdes.String()).withLoggingDisabled().build();
        timestampedWindows.init((StateStoreContext) context, timestampedWindows);
        timestampedWindows.put("alpha", ValueAndTimestamp.make("early", 1L), 1L);
        timestampedWindows.put("alpha", ValueAndTimestamp.make("late", 11_000L), 11_000L);
        assertThat(windowValues(timestampedWindows.fetch("alpha", Instant.EPOCH,
                Instant.ofEpochMilli(20_000L)))).containsExactly("early", "late");
        assertThat(entryValues(timestampedWindows.all())).containsExactly("early", "late");
        timestampedWindows.close();

        org.apache.kafka.streams.state.WindowStore<String, String> windows = Stores.windowStoreBuilder(
                Stores.persistentWindowStore("rocks-windows", Duration.ofMinutes(2),
                        Duration.ofSeconds(10), true), Serdes.String(), Serdes.String())
                .withLoggingDisabled().build();
        windows.init((StateStoreContext) context, windows);
        windows.put("alpha", "early", 1L);
        windows.put("alpha", "late", 11_000L);
        assertThat(stringWindowValues(windows.fetch("alpha", 0L, 20_000L)))
                .containsExactly("early", "late");
        assertThat(stringWindowValues(windows.backwardFetch("alpha", 0L, 20_000L)))
                .containsExactly("late", "early");
        assertThat(values(windows.all())).containsExactly("early", "late");
        windows.close();

        SessionStore<String, String> sessions = Stores.sessionStoreBuilder(
                Stores.persistentSessionStore("rocks-sessions", Duration.ofMinutes(2)),
                Serdes.String(), Serdes.String()).withLoggingDisabled().build();
        sessions.init((StateStoreContext) context, sessions);
        Windowed<String> first = new Windowed<>("alpha", new SessionWindow(1L, 2L));
        Windowed<String> second = new Windowed<>("alpha", new SessionWindow(10L, 12L));
        sessions.put(first, "first");
        sessions.put(second, "second");
        assertThat(values(sessions.findSessions("alpha", 0L, 20L)))
                .containsExactly("first", "second");
        assertThat(values(sessions.backwardFindSessions("alpha", 0L, 20L)))
                .containsExactly("second", "first");
        sessions.remove(first);
        sessions.close();
    }

    @Test
    void cachedPersistentStoresMergeCacheAndRocksDbForForwardAndBackwardQueries() {
        StreamsMetricsImpl metrics = new StreamsMetricsImpl(
                new Metrics(), "coverage-thread", "coverage-process", Time.SYSTEM);
        ThreadCache cache = new ThreadCache(new LogContext(), 1024 * 1024L, metrics);
        InternalMockProcessorContext<Object, Object> context = new InternalMockProcessorContext<>(
                stateDirectory, Serdes.String(), Serdes.String(), null, cache);
        context.initialize();

        KeyValueStore<String, String> keyValues = Stores.keyValueStoreBuilder(
                Stores.persistentKeyValueStore("cached-key-values"), Serdes.String(), Serdes.String())
                .withLoggingDisabled().withCachingEnabled().build();
        keyValues.init((StateStoreContext) context, keyValues);
        keyValues.put("alpha", "cached");
        keyValues.put("beta", "rocks");
        keyValues.flush();
        keyValues.put("alpha", "updated");
        keyValues.put("gamma", "new");
        assertThat(values(keyValues.all())).containsExactly("updated", "rocks", "new");
        assertThat(values(keyValues.reverseAll())).containsExactly("new", "rocks", "updated");
        assertThat(values(keyValues.range("alpha", "gamma")))
                .containsExactly("updated", "rocks", "new");
        assertThat(values(keyValues.prefixScan("al", Serdes.String().serializer())))
                .containsExactly("updated");
        keyValues.close();

        WindowStore<String, String> windows = Stores.windowStoreBuilder(
                Stores.persistentWindowStore("cached-windows", Duration.ofMinutes(2),
                        Duration.ofSeconds(10), true), Serdes.String(), Serdes.String())
                .withLoggingDisabled().withCachingEnabled().build();
        windows.init((StateStoreContext) context, windows);
        windows.put("alpha", "first", 1L);
        windows.flush();
        windows.put("alpha", "second", 2L);
        windows.put("beta", "third", 3L);
        // Exact fetch still drives the cache/store lookup; this duplicate-retaining store does not
        // expose sequence-numbered records through the legacy exact-timestamp overload.
        assertThat(windows.fetch("alpha", 1L)).isNull();
        assertThat(stringWindowValues(windows.fetch("alpha", 0L, 10L)))
                .containsExactly("first", "second");
        assertThat(stringWindowValues(windows.backwardFetch("alpha", 0L, 10L)))
                .containsExactly("second", "first");
        assertThat(values(windows.fetch("a", "z", 0L, 10L)))
                .containsExactly("first", "second", "third");
        assertThat(values(windows.backwardFetch("a", "z", 0L, 10L)))
                .containsExactly("third", "second", "first");
        assertThat(values(windows.fetchAll(Instant.EPOCH, Instant.ofEpochMilli(10L))))
                .containsExactly("first", "second", "third");
        assertThat(values(windows.backwardFetchAll(Instant.EPOCH, Instant.ofEpochMilli(10L))))
                .containsExactly("third", "second", "first");
        assertThat(values(windows.all())).containsExactly("first", "second", "third");
        assertThat(values(windows.backwardAll())).containsExactly("third", "second", "first");
        windows.close();

        SessionStore<String, String> sessions = Stores.sessionStoreBuilder(
                Stores.persistentSessionStore("cached-sessions", Duration.ofMinutes(2)),
                Serdes.String(), Serdes.String()).withLoggingDisabled().withCachingEnabled().build();
        sessions.init((StateStoreContext) context, sessions);
        Windowed<String> early = new Windowed<>("alpha", new SessionWindow(1L, 2L));
        Windowed<String> late = new Windowed<>("alpha", new SessionWindow(10L, 12L));
        sessions.put(early, "early");
        sessions.flush();
        sessions.put(late, "late");
        assertThat(sessions.fetchSession("alpha", 1L, 2L)).isEqualTo("early");
        assertThat(values(sessions.fetch("alpha"))).containsExactly("early", "late");
        assertThat(values(sessions.backwardFetch("alpha"))).containsExactly("late", "early");
        assertThat(values(sessions.fetch("a", "z"))).containsExactly("early", "late");
        assertThat(values(sessions.backwardFetch("a", "z"))).containsExactly("late", "early");
        assertThat(values(sessions.findSessions("alpha", 0L, 20L)))
                .containsExactly("early", "late");
        assertThat(values(sessions.backwardFindSessions("alpha", 0L, 20L)))
                .containsExactly("late", "early");
        assertThat(values(sessions.findSessions("a", "z", 0L, 20L)))
                .containsExactly("early", "late");
        assertThat(values(sessions.backwardFindSessions("a", "z", 0L, 20L)))
                .containsExactly("late", "early");
        sessions.remove(early);
        sessions.close();
    }

    @Test
    void inMemoryStoresDriveReverseSegmentIterationAndDuplicateWindows() {
        InternalMockProcessorContext<Object, Object> context = new InternalMockProcessorContext<>();
        context.initialize();
        WindowStore<String, String> windows = Stores.windowStoreBuilder(
                Stores.inMemoryWindowStore("duplicate-windows", Duration.ofMillis(20),
                        Duration.ofMillis(5), true), Serdes.String(), Serdes.String())
                .withLoggingDisabled().build();
        windows.init((StateStoreContext) context, windows);
        windows.put("alpha", "first", 1L);
        windows.put("alpha", "duplicate", 1L);
        windows.put("beta", "later", 6L);
        assertThat(stringWindowValues(windows.backwardFetch("alpha", 0L, 10L)))
                .containsExactly("duplicate", "first");
        assertThat(values(windows.backwardFetch("a", "z", 0L, 10L)))
                .containsExactly("later", "duplicate", "first");
        windows.close();

        SessionStore<String, String> sessions = Stores.sessionStoreBuilder(
                Stores.inMemorySessionStore("expiring-sessions", Duration.ofMillis(5)),
                Serdes.String(), Serdes.String()).withLoggingDisabled().build();
        sessions.init((StateStoreContext) context, sessions);
        sessions.put(new Windowed<>("alpha", new SessionWindow(1L, 2L)), "expired");
        sessions.put(new Windowed<>("beta", new SessionWindow(20L, 21L)), "current");
        assertThat(values(sessions.backwardFindSessions("a", "z", 0L, 30L)))
                .containsExactly("current");
        sessions.close();
    }

    @Test
    void timestampedWindowBuilderDrivesCachingLoggingFlushAndCloseWrappers() {
        StreamsMetricsImpl metrics = new StreamsMetricsImpl(
                new Metrics(), "builder-thread", "builder-process", Time.SYSTEM);
        ThreadCache cache = new ThreadCache(new LogContext(), 1024 * 1024L, metrics);
        InternalMockProcessorContext<Object, Object> context = new InternalMockProcessorContext<>(
                stateDirectory, Serdes.String(), Serdes.String(), null, cache);
        context.initialize();

        TimestampedWindowStore<String, String> cached = Stores.timestampedWindowStoreBuilder(
                Stores.persistentTimestampedWindowStore("cached-timestamped-windows",
                        Duration.ofMinutes(2), Duration.ofSeconds(10), false),
                Serdes.String(), Serdes.String()).withLoggingDisabled().withCachingEnabled().build();
        cached.init((StateStoreContext) context, cached);
        cached.put("key", ValueAndTimestamp.make("cached", 1L), 1L);
        cached.flush();
        assertThat(entryValues(cached.all())).containsExactly("cached");
        cached.close();

        TimestampedWindowStore<String, String> logged = Stores.timestampedWindowStoreBuilder(
                Stores.inMemoryWindowStore("logged-timestamped-windows",
                        Duration.ofMinutes(2), Duration.ofSeconds(10), false),
                Serdes.String(), Serdes.String()).withLoggingEnabled(Map.of()).build();
        logged.init((StateStoreContext) context, logged);
        assertThatThrownBy(() -> logged.put("key", ValueAndTimestamp.make("logged", 2L), 2L))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("No RecordCollector specified");
        assertThat(logged.name()).isEqualTo("logged-timestamped-windows");
        assertThat(logged.persistent()).isFalse();
        logged.flush();
        logged.close();
    }

    @Test
    void timestampedWindowAndSessionBuildersInitializeAndServePublicQueries() {
        InternalMockProcessorContext<Object, Object> context = new InternalMockProcessorContext<>();
        context.initialize();
        TimestampedWindowStore<String, String> windows = Stores.timestampedWindowStoreBuilder(
                Stores.inMemoryWindowStore("windows", Duration.ofMinutes(1), Duration.ofSeconds(10), false),
                Serdes.String(), Serdes.String()).withLoggingDisabled().build();
        windows.init((StateStoreContext) context, windows);
        windows.put("key", ValueAndTimestamp.make("early", 1L), 1L);
        windows.put("key", ValueAndTimestamp.make("late", 2L), 2L);
        Instant start = Instant.ofEpochMilli(0L);
        Instant end = Instant.ofEpochMilli(3L);
        assertThat(windowValues(windows.fetch("key", start, end))).containsExactly("early", "late");
        assertThat(windowValues(windows.backwardFetch("key", start, end))).containsExactly("late", "early");
        assertThat(entryValues(windows.all())).containsExactly("early", "late");
        assertThat(entryValues(windows.backwardAll())).containsExactly("late", "early");
        assertThat(entryValues(windows.fetchAll(start, end))).containsExactly("early", "late");
        assertThat(entryValues(windows.backwardFetchAll(start, end))).containsExactly("late", "early");
        windows.close();

        SessionStore<String, String> sessions = Stores.sessionStoreBuilder(
                Stores.inMemorySessionStore("sessions", Duration.ofMinutes(1)),
                Serdes.String(), Serdes.String()).withLoggingDisabled().build();
        sessions.init((StateStoreContext) context, sessions);
        sessions.close();
    }

    private static <K, V> List<V> values(KeyValueIterator<K, V> iterator) {
        try (iterator) {
            return iteratorToValues(iterator);
        }
    }

    private static <K, V> List<V> iteratorToValues(KeyValueIterator<K, V> iterator) {
        java.util.ArrayList<V> values = new java.util.ArrayList<>();
        iterator.forEachRemaining(entry -> values.add(entry.value));
        return values;
    }

    private static List<String> stringWindowValues(WindowStoreIterator<String> iterator) {
        try (iterator) {
            java.util.ArrayList<String> values = new java.util.ArrayList<>();
            iterator.forEachRemaining(entry -> values.add(entry.value));
            return values;
        }
    }

    private static List<String> windowValues(WindowStoreIterator<ValueAndTimestamp<String>> iterator) {
        try (iterator) {
            java.util.ArrayList<String> values = new java.util.ArrayList<>();
            iterator.forEachRemaining(entry -> values.add(entry.value.value()));
            return values;
        }
    }

    private static List<String> entryValues(
            KeyValueIterator<Windowed<String>, ValueAndTimestamp<String>> iterator) {
        return values(iterator).stream().map(ValueAndTimestamp::value).toList();
    }
}
