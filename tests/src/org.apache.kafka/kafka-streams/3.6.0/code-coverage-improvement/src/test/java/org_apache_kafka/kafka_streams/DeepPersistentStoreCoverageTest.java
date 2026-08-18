/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_streams;

import org.apache.kafka.common.metrics.Metrics;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.LogContext;
import org.apache.kafka.common.utils.Time;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.Windowed;
import org.apache.kafka.streams.kstream.internals.SessionWindow;
import org.apache.kafka.streams.processor.StateStoreContext;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.SessionStore;
import org.apache.kafka.streams.state.Stores;
import org.apache.kafka.streams.state.TimestampedKeyValueStore;
import org.apache.kafka.streams.state.TimestampedWindowStore;
import org.apache.kafka.streams.state.ValueAndTimestamp;
import org.apache.kafka.streams.state.VersionedKeyValueStore;
import org.apache.kafka.streams.state.VersionedRecord;
import org.apache.kafka.streams.state.WindowStore;
import org.apache.kafka.streams.state.internals.KeyValueStoreBuilder;
import org.apache.kafka.streams.state.internals.SessionStoreBuilder;
import org.apache.kafka.streams.state.internals.TimestampedKeyValueStoreBuilder;
import org.apache.kafka.streams.state.internals.TimestampedWindowStoreBuilder;
import org.apache.kafka.streams.state.internals.ThreadCache;
import org.apache.kafka.streams.state.internals.VersionedKeyValueStoreBuilder;
import org.apache.kafka.streams.state.internals.WindowStoreBuilder;
import org.apache.kafka.streams.processor.internals.metrics.StreamsMetricsImpl;
import org.apache.kafka.test.InternalMockProcessorContext;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Drives persistent, cached, timestamped, session, window, and versioned stores via public builders. */
public class DeepPersistentStoreCoverageTest {
    @Test
    void shouldDriveCachedPersistentKeyValueFamilies() {
        StoreResources resources = new StoreResources("deep-kv");
        try {
            KeyValueStore<String, String> store = new KeyValueStoreBuilder<>(
                    Stores.persistentKeyValueStore("deep-kv"), Serdes.String(), Serdes.String(), Time.SYSTEM)
                    .withCachingEnabled().withLoggingEnabled(Map.of()).build();
            init(store, resources.context);
            store.put("one", "1");
            store.put("two", "2");
            store.putAll(List.of(KeyValue.pair("three", "3")));
            assertThat(store.get("one")).isEqualTo("1");
            assertThat(store.all().hasNext()).isTrue();
            assertThat(store.range("one", "two").hasNext()).isTrue();
            assertThat(store.reverseRange("one", "two").hasNext()).isTrue();
            assertThat(store.reverseAll().hasNext()).isTrue();
            assertThat(store.prefixScan("o", Serdes.String().serializer()).hasNext()).isTrue();
            store.flush();
            assertThat(store.approximateNumEntries()).isEqualTo(3L);
            assertThat(store.all().hasNext()).isTrue();
            assertThat(store.delete("one")).isEqualTo("1");
            store.close();

            TimestampedKeyValueStore<String, String> timestamped = new TimestampedKeyValueStoreBuilder<>(
                    Stores.persistentKeyValueStore("deep-timestamped-kv"), Serdes.String(), Serdes.String(), Time.SYSTEM)
                    .withCachingEnabled().withLoggingEnabled(Map.of()).build();
            init(timestamped, resources.context);
            timestamped.put("key", ValueAndTimestamp.make("value", 10L));
            timestamped.putAll(List.of(KeyValue.pair("other", ValueAndTimestamp.make("other", 11L))));
            assertThat(timestamped.get("key").value()).isEqualTo("value");
            assertThat(timestamped.all().hasNext()).isTrue();
            assertThat(timestamped.reverseAll().hasNext()).isTrue();
            assertThat(timestamped.prefixScan("o", Serdes.String().serializer()).hasNext()).isTrue();
            assertThat(timestamped.approximateNumEntries()).isGreaterThanOrEqualTo(0L);
            timestamped.flush();
            timestamped.close();
        } finally {
            resources.close();
        }
    }

    @Test
    void shouldDriveCachedPersistentSessionAndWindowFamilies() {
        StoreResources resources = new StoreResources("deep-windows");
        try {
            SessionStore<String, String> session = new SessionStoreBuilder<>(
                    Stores.persistentSessionStore("deep-session", Duration.ofMinutes(2)),
                    Serdes.String(), Serdes.String(), Time.SYSTEM)
                    .withCachingEnabled().withLoggingEnabled(Map.of()).build();
            init(session, resources.context);
            session.put(new Windowed<>("key", new SessionWindow(10L, 20L)), "session");
            assertThat(session.fetch("key").hasNext()).isTrue();
            assertThat(session.backwardFetch("key").hasNext()).isTrue();
            assertThat(session.fetch("key", "key").hasNext()).isTrue();
            assertThat(session.backwardFetch("key", "key").hasNext()).isTrue();
            assertThat(session.findSessions("key", 0L, 30L).hasNext()).isTrue();
            assertThat(session.backwardFindSessions("key", 0L, 30L).hasNext()).isTrue();
            assertThat(session.fetchSession("key", 10L, 20L)).isEqualTo("session");
            session.flush();
            session.remove(new Windowed<>("key", new SessionWindow(10L, 20L)));
            session.close();

            WindowStore<String, String> window = new WindowStoreBuilder<>(
                    Stores.persistentWindowStore("deep-window", Duration.ofMinutes(2), Duration.ofSeconds(10), false),
                    Serdes.String(), Serdes.String(), Time.SYSTEM)
                    .withCachingEnabled().withLoggingEnabled(Map.of()).build();
            init(window, resources.context);
            window.put("key", "first", 100L);
            window.put("key", "second", 110L);
            assertThat(window.fetch("key", 50L, 150L).hasNext()).isTrue();
            assertThat(window.backwardFetch("key", 50L, 150L).hasNext()).isTrue();
            assertThat(window.fetch("key", java.time.Instant.ofEpochMilli(50L), java.time.Instant.ofEpochMilli(150L)).hasNext()).isTrue();
            assertThat(window.backwardFetch("key", java.time.Instant.ofEpochMilli(50L), java.time.Instant.ofEpochMilli(150L)).hasNext()).isTrue();
            assertThat(window.fetch("key", "key", 50L, 150L).hasNext()).isTrue();
            assertThat(window.backwardFetch("key", "key", 50L, 150L).hasNext()).isTrue();
            assertThat(window.fetchAll(50L, 150L).hasNext()).isTrue();
            assertThat(window.backwardFetchAll(50L, 150L).hasNext()).isTrue();
            assertThat(window.fetchAll(java.time.Instant.ofEpochMilli(50L), java.time.Instant.ofEpochMilli(150L)).hasNext()).isTrue();
            assertThat(window.backwardFetchAll(java.time.Instant.ofEpochMilli(50L), java.time.Instant.ofEpochMilli(150L)).hasNext()).isTrue();
            assertThat(window.all().hasNext()).isTrue();
            assertThat(window.backwardAll().hasNext()).isTrue();
            window.flush();
            window.close();

            WindowStore<String, String> inMemoryWindow = new WindowStoreBuilder<>(
                    Stores.inMemoryWindowStore("deep-memory-window", Duration.ofMinutes(2), Duration.ofSeconds(10), false),
                    Serdes.String(), Serdes.String(), Time.SYSTEM).build();
            init(inMemoryWindow, resources.context);
            inMemoryWindow.put("key", "first", 100L);
            inMemoryWindow.put("key", "second", 100L);
            org.apache.kafka.streams.state.KeyValueIterator<Windowed<String>, String> all =
                    inMemoryWindow.fetchAll(50L, 150L);
            assertThat(all.hasNext()).isTrue();
            all.next();
            all.close();
            inMemoryWindow.close();

            TimestampedWindowStore<String, String> timestampedWindow = new TimestampedWindowStoreBuilder<>(
                    Stores.persistentWindowStore("deep-timestamped-window", Duration.ofMinutes(2), Duration.ofSeconds(10), false),
                    Serdes.String(), Serdes.String(), Time.SYSTEM)
                    .withCachingEnabled().withLoggingEnabled(Map.of()).build();
            init(timestampedWindow, resources.context);
            timestampedWindow.put("key", ValueAndTimestamp.make("value", 120L), 120L);
            assertThat(timestampedWindow.fetch("key", 50L, 150L).hasNext()).isTrue();
            assertThat(timestampedWindow.backwardFetch("key", 50L, 150L).hasNext()).isTrue();
            assertThat(timestampedWindow.fetch("key", java.time.Instant.ofEpochMilli(50L), java.time.Instant.ofEpochMilli(150L)).hasNext()).isTrue();
            assertThat(timestampedWindow.backwardFetch("key", java.time.Instant.ofEpochMilli(50L), java.time.Instant.ofEpochMilli(150L)).hasNext()).isTrue();
            assertThat(timestampedWindow.fetchAll(50L, 150L).hasNext()).isTrue();
            assertThat(timestampedWindow.backwardFetchAll(50L, 150L).hasNext()).isTrue();
            assertThat(timestampedWindow.all().hasNext()).isTrue();
            assertThat(timestampedWindow.backwardAll().hasNext()).isTrue();
            timestampedWindow.flush();
            timestampedWindow.close();
        } finally {
            resources.close();
        }
    }

    @Test
    void shouldWriteAndReadMultipleVersionsThroughVersionedStore() {
        StoreResources resources = new StoreResources("deep-versioned");
        try {
            VersionedKeyValueStore<String, String> store = new VersionedKeyValueStoreBuilder<>(
                    Stores.persistentVersionedKeyValueStore("deep-versioned", Duration.ofMinutes(5)),
                    Serdes.String(), Serdes.String(), Time.SYSTEM).build();
            init(store, resources.context);
            assertThat(store.put("key", "old", 10L)).isEqualTo(VersionedKeyValueStore.PUT_RETURN_CODE_VALID_TO_UNDEFINED);
            assertThat(store.put("key", "new", 20L)).isEqualTo(VersionedKeyValueStore.PUT_RETURN_CODE_VALID_TO_UNDEFINED);
            assertThat(store.put("key", "middle", 15L)).isEqualTo(20L);
            VersionedRecord<String> at16 = store.get("key", 16L);
            assertThat(at16.value()).isEqualTo("middle");
            assertThat(at16.timestamp()).isEqualTo(15L);
            assertThat(store.get("key").value()).isEqualTo("new");
            assertThat(store.delete("key", 15L).value()).isEqualTo("middle");
            store.flush();
            store.close();
        } finally {
            resources.close();
        }
    }

    private static void init(org.apache.kafka.streams.processor.StateStore store, InternalMockProcessorContext<?, ?> context) {
        store.init((StateStoreContext) context, store);
    }

    private static final class StoreResources {
        private final Metrics metrics = new Metrics();
        private final StreamsMetricsImpl streamsMetrics;
        private final ThreadCache cache;
        private final InternalMockProcessorContext<?, ?> context;

        private StoreResources(String name) {
            deleteDirectory(Path.of("build", name));
            streamsMetrics = new StreamsMetricsImpl(metrics, name, name + "-thread", Time.SYSTEM);
            cache = new ThreadCache(new LogContext(name + " "), 1024L, streamsMetrics);
            context = new InternalMockProcessorContext<>(new File("build/" + name), Serdes.String(), Serdes.String(),
                    NativeCoverageFixtures.recordCollector(), cache);
            context.initialize();
            context.setTime(1L);
        }

        private void close() {
            metrics.close();
        }

        private static void deleteDirectory(Path directory) {
            if (Files.exists(directory)) {
                try (java.util.stream.Stream<Path> paths = Files.walk(directory)) {
                    paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (java.io.IOException exception) {
                            throw new RuntimeException(exception);
                        }
                    });
                } catch (java.io.IOException exception) {
                    throw new RuntimeException(exception);
                }
            }
        }
    }
}
