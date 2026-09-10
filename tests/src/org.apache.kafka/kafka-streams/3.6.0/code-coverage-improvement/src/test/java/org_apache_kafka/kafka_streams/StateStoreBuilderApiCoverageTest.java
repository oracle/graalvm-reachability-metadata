/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_streams;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.common.utils.Time;
import org.apache.kafka.common.utils.LogContext;
import org.apache.kafka.streams.kstream.Windowed;
import org.apache.kafka.streams.processor.StateStore;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.processor.StateStoreContext;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.SessionStore;
import org.apache.kafka.streams.state.StateSerdes;
import org.apache.kafka.streams.state.TimestampedKeyValueStore;
import org.apache.kafka.streams.state.TimestampedWindowStore;
import org.apache.kafka.streams.state.WindowStore;
import org.apache.kafka.streams.state.Stores;
import org.apache.kafka.streams.state.VersionedKeyValueStore;
import org.apache.kafka.streams.state.internals.InMemoryTimeOrderedKeyValueChangeBuffer;
import org.apache.kafka.test.InternalMockProcessorContext;
import org.apache.kafka.streams.state.internals.KeyValueStoreBuilder;
import org.apache.kafka.streams.state.internals.ListValueStoreBuilder;
import org.apache.kafka.streams.state.internals.RocksDBTimeOrderedKeyValueBuffer;
import org.apache.kafka.streams.state.internals.SessionStoreBuilder;
import org.apache.kafka.streams.state.internals.TimestampedKeyValueStoreBuilder;
import org.apache.kafka.streams.state.internals.TimestampedWindowStoreBuilder;
import org.apache.kafka.streams.state.internals.VersionedKeyValueStoreBuilder;
import org.apache.kafka.streams.state.internals.WindowStoreBuilder;
import org.apache.kafka.streams.processor.internals.metrics.StreamsMetricsImpl;
import org.apache.kafka.streams.state.internals.ThreadCache;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Builds and uses the state-store families that back the public window and query APIs. */
public class StateStoreBuilderApiCoverageTest {
    @Test
    void shouldBuildKeyValueSessionWindowAndVersionedStores() {
        KeyValueStoreBuilder<String, String> keyValueBuilder = new KeyValueStoreBuilder<>(
                Stores.inMemoryKeyValueStore("builder-kv"), Serdes.String(), Serdes.String(), Time.SYSTEM);
        KeyValueStore<String, String> keyValue = keyValueBuilder.build();
        assertThat(keyValue.name()).isEqualTo("builder-kv");
        InternalMockProcessorContext<?, ?> keyValueContext = new InternalMockProcessorContext<>(
                StateSerdes.withBuiltinTypes("context", String.class, String.class), NativeCoverageFixtures.recordCollector());
        keyValueContext.initialize();
        keyValueContext.setTime(1L);
        keyValue.init((ProcessorContext) keyValueContext, keyValue);
        keyValue.put("one", "1");
        keyValue.putAll(java.util.List.of(org.apache.kafka.streams.KeyValue.pair("two", "2")));
        assertThat(keyValue.get("one")).isEqualTo("1");
        assertThat(keyValue.delete("two")).isEqualTo("2");
        assertThat(keyValue.approximateNumEntries()).isEqualTo(1);
        assertThat(keyValue.all().hasNext()).isTrue();
        assertThat(keyValue.range("one", "two").hasNext()).isTrue();
        assertThat(keyValue.reverseRange("one", "two").hasNext()).isTrue();
        assertThat(keyValue.reverseAll().hasNext()).isTrue();
        assertThat(keyValue.prefixScan("o", Serdes.String().serializer()).hasNext()).isTrue();

        ListValueStoreBuilder<String, String> listBuilder = new ListValueStoreBuilder<>(
                Stores.inMemoryKeyValueStore("builder-list"), Serdes.String(), Serdes.String(), Time.SYSTEM);
        assertThat(listBuilder.build().name()).isEqualTo("builder-list");

        TimestampedKeyValueStoreBuilder<String, String> timestampedBuilder = new TimestampedKeyValueStoreBuilder<>(
                Stores.inMemoryKeyValueStore("builder-timestamped-kv"), Serdes.String(), Serdes.String(), Time.SYSTEM);
        TimestampedKeyValueStore<String, String> timestamped = timestampedBuilder.build();
        assertThat(timestamped.name()).isEqualTo("builder-timestamped-kv");

        SessionStoreBuilder<String, String> sessionBuilder = new SessionStoreBuilder<>(
                Stores.inMemorySessionStore("builder-session", Duration.ofMinutes(1)), Serdes.String(), Serdes.String(), Time.SYSTEM);
        SessionStore<String, String> session = sessionBuilder.build();
        assertThat(session.name()).isEqualTo("builder-session");
        InternalMockProcessorContext<?, ?> sessionContext = new InternalMockProcessorContext<>(
                StateSerdes.withBuiltinTypes("context", String.class, String.class), NativeCoverageFixtures.recordCollector());
        sessionContext.initialize();
        sessionContext.setTime(1L);
        session.init((ProcessorContext) sessionContext, session);
        session.put(new Windowed<>("key", new org.apache.kafka.streams.kstream.internals.SessionWindow(10L, 20L)), "value");
        assertThat(session.fetch("key").hasNext()).isTrue();
        assertThat(session.backwardFetch("key").hasNext()).isTrue();
        assertThat(session.fetch("key", "key").hasNext()).isTrue();
        assertThat(session.backwardFetch("key", "key").hasNext()).isTrue();
        assertThat(session.findSessions("key", 0L, 30L).hasNext()).isTrue();
        assertThat(session.backwardFindSessions("key", 0L, 30L).hasNext()).isTrue();
        assertThat(sessionBuilder.retentionPeriod()).isEqualTo(Duration.ofMinutes(1).toMillis());

        VersionedKeyValueStoreBuilder<String, String> versionedBuilder = new VersionedKeyValueStoreBuilder<>(
                Stores.persistentVersionedKeyValueStore("builder-versioned", Duration.ofMinutes(1)),
                Serdes.String(), Serdes.String(), Time.SYSTEM);
        VersionedKeyValueStore<String, String> versioned = versionedBuilder.build();
        assertThat(versioned.name()).isEqualTo("builder-versioned");
    }

    @Test
    void shouldExerciseCachingAndChangeLoggingStoreWrappers() {
        org.apache.kafka.streams.state.StoreBuilder<KeyValueStore<org.apache.kafka.common.utils.Bytes, byte[]>> cachedBuilder =
                new KeyValueStoreBuilder<>(Stores.inMemoryKeyValueStore("builder-cached"),
                        Serdes.Bytes(), Serdes.ByteArray(), Time.SYSTEM).withCachingEnabled();
        KeyValueStore<org.apache.kafka.common.utils.Bytes, byte[]> cached = cachedBuilder.build();

        StreamsMetricsImpl metrics = new StreamsMetricsImpl(new org.apache.kafka.common.metrics.Metrics(),
                "cached-client", "cached-thread", Time.SYSTEM);
        ThreadCache threadCache = new ThreadCache(new LogContext("cached "), 1024L, metrics);
        InternalMockProcessorContext<?, ?> context = new InternalMockProcessorContext<>(
                new java.io.File("build/api-cached"), Serdes.Bytes(), Serdes.ByteArray(),
                NativeCoverageFixtures.recordCollector(), threadCache);
        context.initialize();
        context.setTime(1L);
        cached.init((StateStoreContext) context, cached);
        assertThat(cached.approximateNumEntries()).isZero();
        org.apache.kafka.common.utils.Bytes key = org.apache.kafka.common.utils.Bytes.wrap(new byte[] {1});
        cached.put(key, new byte[] {2});
        Bytes secondKey = Bytes.wrap(new byte[] {2});
        cached.put(secondKey, new byte[] {3});
        assertThat(cached.get(key)).containsExactly(2);
        assertThat(cached.all().hasNext()).isTrue();
        assertThat(cached.range(key, secondKey).hasNext()).isTrue();
        assertThat(cached.reverseRange(key, secondKey).hasNext()).isTrue();
        assertThat(cached.reverseAll().hasNext()).isTrue();
        assertThat(cached.prefixScan(new byte[] {1}, Serdes.ByteArray().serializer()).hasNext()).isTrue();
        cached.flush();
        assertThat(cached.approximateNumEntries()).isEqualTo(2);
        assertThat(cached.delete(key)).containsExactly(2);

        org.apache.kafka.streams.state.StoreBuilder<SessionStore<String, byte[]>> loggingSessionBuilder = new SessionStoreBuilder<>(
                Stores.inMemorySessionStore("builder-logging-session", Duration.ofMinutes(1)),
                Serdes.String(), Serdes.ByteArray(), Time.SYSTEM).withLoggingEnabled(Map.of());
        SessionStore<String, byte[]> loggingSession = loggingSessionBuilder.build();
        assertThat(loggingSession.name()).isEqualTo("builder-logging-session");

        org.apache.kafka.streams.state.StoreBuilder<TimestampedKeyValueStore<String, String>> loggingTimestampedBuilder =
                new TimestampedKeyValueStoreBuilder<>(Stores.inMemoryKeyValueStore("builder-logging-timestamped"),
                        Serdes.String(), Serdes.String(), Time.SYSTEM).withLoggingEnabled(Map.of());
        assertThat(loggingTimestampedBuilder.build().name()).isEqualTo("builder-logging-timestamped");
    }

    @Test
    void shouldUseWindowStoreInstantFetchOverloads() {
        WindowStoreBuilder<String, String> windowBuilder = new WindowStoreBuilder<>(
                Stores.inMemoryWindowStore("builder-window", Duration.ofMinutes(2), Duration.ofSeconds(10), false),
                Serdes.String(), Serdes.String(), Time.SYSTEM);
        WindowStore<String, String> window = windowBuilder.build();
        assertThat(window.name()).isEqualTo("builder-window");
        InternalMockProcessorContext<?, ?> windowContext = new InternalMockProcessorContext<>(
                StateSerdes.withBuiltinTypes("context", String.class, String.class), NativeCoverageFixtures.recordCollector());
        windowContext.initialize();
        windowContext.setTime(1L);
        window.init((ProcessorContext) windowContext, window);
        window.put("key", "value", 100L);
        assertThat(window.fetch("key", 100L)).isEqualTo("value");
        assertThat(window.backwardFetch("key", 50L, 150L).hasNext()).isTrue();
        assertThat(window.fetch("key", "key", 50L, 150L).hasNext()).isTrue();
        assertThat(window.backwardFetch("key", "key", 50L, 150L).hasNext()).isTrue();
        assertThat(window.fetchAll(50L, 150L).hasNext()).isTrue();
        assertThat(window.backwardFetchAll(50L, 150L).hasNext()).isTrue();
        assertThat(window.all().hasNext()).isTrue();
        assertThat(window.backwardAll().hasNext()).isTrue();
        RecordingWindowStore recording = new RecordingWindowStore();
        recording.fetch("key", 100L, 200L);
        assertThat(recording.fetch("key", Instant.ofEpochMilli(50), Instant.ofEpochMilli(250))).isNull();
        assertThat(recording.lastStart).isEqualTo(50L);
        assertThat(recording.backwardFetch("key", Instant.ofEpochMilli(50), Instant.ofEpochMilli(250))).isNull();
        assertThat(recording.lastEnd).isEqualTo(250L);
        assertThat(recording.fetch("key", "key", Instant.ofEpochMilli(50), Instant.ofEpochMilli(250))).isNull();
        assertThat(recording.backwardFetch("key", "key", Instant.ofEpochMilli(50), Instant.ofEpochMilli(250))).isNull();
        assertThat(recording.fetchAll(Instant.ofEpochMilli(50), Instant.ofEpochMilli(250))).isNull();
        assertThat(recording.backwardFetchAll(Instant.ofEpochMilli(50), Instant.ofEpochMilli(250))).isNull();

        TimestampedWindowStoreBuilder<String, String> timestampedBuilder = new TimestampedWindowStoreBuilder<>(
                Stores.inMemoryWindowStore("builder-timestamped-window", Duration.ofMinutes(1), Duration.ofSeconds(5), false),
                Serdes.String(), Serdes.String(), Time.SYSTEM);
        TimestampedWindowStore<String, String> timestamped = timestampedBuilder.build();
        assertThat(timestamped.name()).isEqualTo("builder-timestamped-window");
        InternalMockProcessorContext<?, ?> timestampedContext = new InternalMockProcessorContext<>(
                StateSerdes.withBuiltinTypes("context", String.class, String.class), NativeCoverageFixtures.recordCollector());
        timestampedContext.initialize();
        timestampedContext.setTime(1L);
        timestamped.init((ProcessorContext) timestampedContext, timestamped);
        timestamped.put("key", org.apache.kafka.streams.state.ValueAndTimestamp.make("value", 100L), 100L);
        assertThat(timestamped.fetch("key", 100L)).isNotNull();
        assertThat(timestamped.backwardFetch("key", 50L, 150L).hasNext()).isTrue();
        assertThat(timestamped.fetchAll(50L, 150L).hasNext()).isTrue();
        assertThat(windowBuilder.retentionPeriod()).isEqualTo(Duration.ofMinutes(2).toMillis());

        WindowStoreBuilder<String, String> persistentBuilder = new WindowStoreBuilder<>(
                Stores.persistentWindowStore("builder-rocks-window", Duration.ofMinutes(2), Duration.ofSeconds(10), false),
                Serdes.String(), Serdes.String(), Time.SYSTEM);
        WindowStore<String, String> persistent = persistentBuilder.build();
        assertThat(persistent.name()).isEqualTo("builder-rocks-window");
        InternalMockProcessorContext<?, ?> persistentContext = new InternalMockProcessorContext<>(
                new java.io.File("build/api-rocks"), Serdes.String(), Serdes.String(),
                NativeCoverageFixtures.recordCollector(), null);
        persistentContext.initialize();
        persistentContext.setTime(1L);
        persistent.init((StateStoreContext) persistentContext, persistent);
        persistent.put("key", "value", 100L);
        assertThat(persistent.fetch("key", 100L)).isEqualTo("value");
        assertThat(persistent.backwardFetch("key", 50L, 150L).hasNext()).isTrue();
        assertThat(persistent.fetch("key", "key", 50L, 150L).hasNext()).isTrue();
        assertThat(persistent.backwardFetch("key", "key", 50L, 150L).hasNext()).isTrue();
        assertThat(persistent.fetchAll(50L, 150L).hasNext()).isTrue();
        assertThat(persistent.backwardFetchAll(50L, 150L).hasNext()).isTrue();
        assertThat(persistent.all().hasNext()).isTrue();
        assertThat(persistent.backwardAll().hasNext()).isTrue();

        TimestampedWindowStoreBuilder<String, String> persistentTimestampedBuilder = new TimestampedWindowStoreBuilder<>(
                Stores.persistentWindowStore("builder-rocks-timestamped", Duration.ofMinutes(1), Duration.ofSeconds(5), false),
                Serdes.String(), Serdes.String(), Time.SYSTEM);
        assertThat(persistentTimestampedBuilder.build().name()).isEqualTo("builder-rocks-timestamped");
    }

    private static final class RecordingWindowStore implements WindowStore<String, String> {
        private long lastStart;
        private long lastEnd;
        @Override public String name() {
            return "recording-window";
        }
        @Override public void init(ProcessorContext context, StateStore root) { }
        @Override public void init(StateStoreContext context, StateStore root) { }
        @Override public void flush() { }
        @Override public void close() { }
        @Override public boolean persistent() {
            return false;
        }
        @Override public boolean isOpen() {
            return true;
        }
        @Override public void put(String key, String value, long timestamp) { }
        @Override public String fetch(String key, long timestamp) {
            return null;
        }
        @Override public org.apache.kafka.streams.state.WindowStoreIterator<String> fetch(String key, long from, long to) {
            lastStart = from;
            lastEnd = to;
            return null;
        }
        @Override public org.apache.kafka.streams.state.KeyValueIterator<Windowed<String>, String> fetch(
                String fromKey, String toKey, long from, long to) {
            lastStart = from;
            lastEnd = to;
            return null;
        }
        @Override public org.apache.kafka.streams.state.KeyValueIterator<Windowed<String>, String> fetchAll(long from, long to) {
            lastStart = from;
            lastEnd = to;
            return null;
        }
        @Override public org.apache.kafka.streams.state.WindowStoreIterator<String> backwardFetch(String key, long from, long to) {
            lastStart = from;
            lastEnd = to;
            return null;
        }
        @Override public org.apache.kafka.streams.state.KeyValueIterator<Windowed<String>, String> backwardFetch(
                String fromKey, String toKey, long from, long to) {
            lastStart = from;
            lastEnd = to;
            return null;
        }
        @Override public org.apache.kafka.streams.state.KeyValueIterator<Windowed<String>, String> backwardFetchAll(long from, long to) {
            lastStart = from;
            lastEnd = to;
            return null;
        }
        @Override public org.apache.kafka.streams.state.KeyValueIterator<Windowed<String>, String> all() {
            return null;
        }
        @Override public org.apache.kafka.streams.state.KeyValueIterator<Windowed<String>, String> backwardAll() {
            return null;
        }
    }

    @Test
    void shouldBuildTimeOrderedBufferBuildersWithConfiguration() {
        InMemoryTimeOrderedKeyValueChangeBuffer.Builder<String, String> changeBuilder =
                new InMemoryTimeOrderedKeyValueChangeBuffer.Builder<>("change-buffer", Serdes.String(), Serdes.String());
        assertThat(changeBuilder.withCachingEnabled().withCachingDisabled().withLoggingEnabled(Map.of("retention.ms", "1"))
                .withLoggingDisabled().build().name()).isEqualTo("change-buffer");

        RocksDBTimeOrderedKeyValueBuffer.Builder<String, String> rocksBuilder =
                new RocksDBTimeOrderedKeyValueBuffer.Builder<>("rocks-buffer", Duration.ofMinutes(1), "api-task");
        assertThat(rocksBuilder.withCachingEnabled().withCachingDisabled().withLoggingEnabled(Map.of()).withLoggingDisabled()
                .build().name()).isEqualTo("rocks-buffer");
    }
}
