/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_streams;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.Windowed;
import org.apache.kafka.streams.kstream.internals.SessionWindow;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyWindowStore;
import org.apache.kafka.streams.state.WindowStore;
import org.apache.kafka.streams.state.WindowStoreIterator;
import org.apache.kafka.streams.state.TimestampedWindowStore;
import org.apache.kafka.streams.state.Stores;
import org.apache.kafka.streams.state.ValueAndTimestamp;
import org.apache.kafka.streams.state.internals.CompositeReadOnlyKeyValueStore;
import org.apache.kafka.streams.state.internals.CompositeReadOnlySessionStore;
import org.apache.kafka.streams.state.internals.CompositeReadOnlyWindowStore;
import org.apache.kafka.streams.state.internals.StateStoreProvider;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies empty query results when no stream thread owns a queryable store. */
public class CompositeStoreApiCoverageTest {
    private static final StateStoreProvider EMPTY_PROVIDER = new StateStoreProvider() {
        @Override
        public <T> List<T> stores(String name, org.apache.kafka.streams.state.QueryableStoreType<T> type) {
            return List.of();
        }
    };

    @Test
    void shouldReturnEmptyKeyValueQueriesWithoutStores() {
        CompositeReadOnlyKeyValueStore<String, String> keyValue = new CompositeReadOnlyKeyValueStore<>(
                EMPTY_PROVIDER, QueryableStoreTypes.keyValueStore(), "store");
        assertThat(keyValue.get("key")).isNull();
        assertThat(keyValue.approximateNumEntries()).isZero();
        assertEmpty(keyValue.all());
        assertEmpty(keyValue.range("a", "z"));
        assertEmpty(keyValue.reverseRange("a", "z"));
        assertEmpty(keyValue.prefixScan("a", Serdes.String().serializer()));
        assertEmpty(keyValue.reverseAll());
    }

    @Test
    void shouldMergeMultipleKeyValueStoresThroughPublicCompositeQueries() {
        KeyValueStore<String, String> first = Stores.keyValueStoreBuilder(
                Stores.inMemoryKeyValueStore("composite-first"), Serdes.String(), Serdes.String()).build();
        KeyValueStore<String, String> second = Stores.keyValueStoreBuilder(
                Stores.inMemoryKeyValueStore("composite-second"), Serdes.String(), Serdes.String()).build();
        org.apache.kafka.test.InternalMockProcessorContext<?, ?> context = new org.apache.kafka.test.InternalMockProcessorContext<>(
                new java.io.File("build/composite-key-value"), Serdes.String(), Serdes.String(),
                NativeCoverageFixtures.recordCollector(), null);
        context.initialize();
        context.setTime(1L);
        first.init((org.apache.kafka.streams.processor.StateStoreContext) context, first);
        second.init((org.apache.kafka.streams.processor.StateStoreContext) context, second);
        first.put("a", "one");
        second.put("b", "two");
        second.put("c", "three");
        StateStoreProvider provider = new StateStoreProvider() {
            @Override
            public <T> List<T> stores(String name, org.apache.kafka.streams.state.QueryableStoreType<T> type) {
                return (List<T>) List.of(first, second);
            }
        };
        CompositeReadOnlyKeyValueStore<String, String> composite = new CompositeReadOnlyKeyValueStore<>(
                provider, QueryableStoreTypes.keyValueStore(), "composite-key-value");
        KeyValueIterator<String, String> all = composite.all();
        assertThat(all.hasNext()).isTrue();
        assertThat(all.next()).isEqualTo(KeyValue.pair("a", "one"));
        assertThat(all.hasNext()).isTrue();
        all.next();
        all.close();
        KeyValueIterator<String, String> range = composite.range("a", "z");
        while (range.hasNext()) {
            range.next();
        }
        range.close();
        KeyValueIterator<String, String> reverse = composite.reverseAll();
        assertThat(reverse.hasNext()).isTrue();
        reverse.next();
        reverse.close();
        first.close();
        second.close();
    }

    @Test
    void shouldReturnEmptyWindowQueriesWithoutStores() {
        CompositeReadOnlyWindowStore<String, String> window = new CompositeReadOnlyWindowStore<>(
                EMPTY_PROVIDER, QueryableStoreTypes.windowStore(), "window");
        Instant start = Instant.ofEpochMilli(0);
        Instant end = Instant.ofEpochMilli(10);
        assertThat(window.fetch("key", 1L)).isNull();
        assertEmpty(window.fetch("key", start, end));
        assertEmpty(window.backwardFetch("key", start, end));
        assertEmpty(window.fetch("a", "z", start, end));
        assertEmpty(window.backwardFetch("a", "z", start, end));
        assertEmpty(window.all());
        assertEmpty(window.backwardAll());
        assertEmpty(window.fetchAll(start, end));
        assertEmpty(window.backwardFetchAll(start, end));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void shouldMergeSessionStoresThroughPublicCompositeIterators() {
        org.apache.kafka.streams.state.SessionStore<String, String> session1 =
                new org.apache.kafka.streams.state.internals.SessionStoreBuilder<>(
                        Stores.inMemorySessionStore("composite-session-one", Duration.ofMinutes(1)),
                        Serdes.String(), Serdes.String(), org.apache.kafka.common.utils.Time.SYSTEM).build();
        org.apache.kafka.streams.state.SessionStore<String, String> session2 =
                new org.apache.kafka.streams.state.internals.SessionStoreBuilder<>(
                        Stores.inMemorySessionStore("composite-session-two", Duration.ofMinutes(1)),
                        Serdes.String(), Serdes.String(), org.apache.kafka.common.utils.Time.SYSTEM).build();
        org.apache.kafka.test.InternalMockProcessorContext<?, ?> context = new org.apache.kafka.test.InternalMockProcessorContext<>(
                new java.io.File("build/composite-session"), Serdes.String(), Serdes.String(),
                NativeCoverageFixtures.recordCollector(), null);
        context.initialize();
        context.setTime(1L);
        session1.init((org.apache.kafka.streams.processor.StateStoreContext) context, session1);
        session2.init((org.apache.kafka.streams.processor.StateStoreContext) context, session2);
        session1.put(new Windowed<>("a", new SessionWindow(1L, 2L)), "one");
        session2.put(new Windowed<>("a", new SessionWindow(3L, 4L)), "two");
        StateStoreProvider sessions = new StateStoreProvider() {
            @Override
            public <T> List<T> stores(String name, org.apache.kafka.streams.state.QueryableStoreType<T> type) {
                return (List<T>) List.of(session1, session2);
            }
        };
        CompositeReadOnlySessionStore<String, String> compositeSession = new CompositeReadOnlySessionStore<>(
                sessions, QueryableStoreTypes.sessionStore(), "composite-session");
        KeyValueIterator<Windowed<String>, String> found = compositeSession.findSessions("a", 0L, 10L);
        assertThat(found.hasNext()).isTrue();
        found.next();
        found.close();
        KeyValueIterator<Windowed<String>, String> backward = compositeSession.backwardFindSessions("a", 0L, 10L);
        assertThat(backward.hasNext()).isTrue();
        backward.next();
        backward.close();
        session1.close();
        session2.close();
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void shouldMergeWindowStoresThroughPublicCompositeQueries() {
        WindowStore<String, String> first = new org.apache.kafka.streams.state.internals.WindowStoreBuilder<>(
                Stores.inMemoryWindowStore("composite-window-one", Duration.ofMinutes(1), Duration.ofSeconds(10), false),
                Serdes.String(), Serdes.String(), org.apache.kafka.common.utils.Time.SYSTEM).build();
        WindowStore<String, String> second = new org.apache.kafka.streams.state.internals.WindowStoreBuilder<>(
                Stores.inMemoryWindowStore("composite-window-two", Duration.ofMinutes(1), Duration.ofSeconds(10), false),
                Serdes.String(), Serdes.String(), org.apache.kafka.common.utils.Time.SYSTEM).build();
        org.apache.kafka.test.InternalMockProcessorContext<?, ?> context = new org.apache.kafka.test.InternalMockProcessorContext<>(
                new java.io.File("build/composite-window"), Serdes.String(), Serdes.String(),
                NativeCoverageFixtures.recordCollector(), null);
        context.initialize();
        context.setTime(1L);
        first.init((org.apache.kafka.streams.processor.StateStoreContext) context, first);
        second.init((org.apache.kafka.streams.processor.StateStoreContext) context, second);
        first.put("a", "one", 10L);
        second.put("a", "two", 20L);
        StateStoreProvider windows = new StateStoreProvider() {
            @Override
            public <T> List<T> stores(String name, org.apache.kafka.streams.state.QueryableStoreType<T> type) {
                return (List<T>) List.of(first, second);
            }
        };
        CompositeReadOnlyWindowStore<String, String> composite = new CompositeReadOnlyWindowStore<>(
                windows, QueryableStoreTypes.windowStore(), "composite-window");
        KeyValueIterator<Windowed<String>, String> all = composite.fetchAll(Instant.ofEpochMilli(0L), Instant.ofEpochMilli(30L));
        assertThat(all.hasNext()).isTrue();
        all.next();
        assertThat(all.hasNext()).isTrue();
        all.next();
        all.close();
        KeyValueIterator<Windowed<String>, String> reverse = composite.backwardFetchAll(Instant.ofEpochMilli(0L), Instant.ofEpochMilli(30L));
        assertThat(reverse.hasNext()).isTrue();
        reverse.next();
        reverse.close();
        first.close();
        second.close();
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void shouldQueryPersistentTimestampedStoreThroughCompositeWindowApi() {
        TimestampedWindowStore<String, String> timestamped = (TimestampedWindowStore<String, String>) Stores
                .timestampedWindowStoreBuilder(
                        Stores.persistentWindowStore("composite-timestamped-window", java.time.Duration.ofMinutes(1),
                                java.time.Duration.ofSeconds(10), false), Serdes.String(), Serdes.String())
                .build();
        org.apache.kafka.test.InternalMockProcessorContext<?, ?> context = new org.apache.kafka.test.InternalMockProcessorContext<>(
                new java.io.File("build/composite-timestamped-window"), Serdes.String(), Serdes.String(),
                NativeCoverageFixtures.recordCollector(), null);
        context.initialize();
        timestamped.init((org.apache.kafka.streams.processor.StateStoreContext) context, timestamped);
        timestamped.put("key", ValueAndTimestamp.make("value", 100L), 100L);

        StateStoreProvider provider = new StateStoreProvider() {
            @Override
            public <T> List<T> stores(String name, org.apache.kafka.streams.state.QueryableStoreType<T> type) {
                return (List<T>) List.of((ReadOnlyWindowStore) timestamped);
            }
        };
        CompositeReadOnlyWindowStore<String, Object> composite = new CompositeReadOnlyWindowStore<>(
                provider, QueryableStoreTypes.windowStore(), "composite-timestamped-window");
        Instant start = Instant.ofEpochMilli(50L);
        Instant end = Instant.ofEpochMilli(150L);
        assertThat(composite.fetch("key", start, end).hasNext()).isTrue();
        assertThat(composite.backwardFetch("key", start, end).hasNext()).isTrue();
        assertThat(composite.fetchAll(start, end).hasNext()).isTrue();
        assertThat(composite.backwardFetchAll(start, end).hasNext()).isTrue();

        timestamped.close();
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void shouldDriveLegacyPersistentWindowAdapterFromCompositeQueries() {
        TimestampedWindowStore<String, String> timestamped = (TimestampedWindowStore<String, String>) Stores
                .timestampedWindowStoreBuilder(
                        Stores.persistentWindowStore("composite-legacy-window", java.time.Duration.ofMinutes(1),
                                java.time.Duration.ofSeconds(10), false), Serdes.String(), Serdes.String())
                .build();
        org.apache.kafka.test.InternalMockProcessorContext<?, ?> context = new org.apache.kafka.test.InternalMockProcessorContext<>(
                new java.io.File("build/composite-legacy-window"), Serdes.String(), Serdes.String(),
                NativeCoverageFixtures.recordCollector(), null);
        context.initialize();
        timestamped.init((org.apache.kafka.streams.processor.StateStoreContext) context, timestamped);
        timestamped.put("key", ValueAndTimestamp.make("value", 100L), 100L);

        StateStoreProvider provider = new StateStoreProvider() {
            @Override
            public <T> List<T> stores(String name, org.apache.kafka.streams.state.QueryableStoreType<T> type) {
                return (List<T>) List.of((ReadOnlyWindowStore) timestamped);
            }
        };
        CompositeReadOnlyWindowStore<String, Object> composite = new CompositeReadOnlyWindowStore<>(
                provider, QueryableStoreTypes.windowStore(), "composite-legacy-window");
        Instant start = Instant.ofEpochMilli(50L);
        Instant end = Instant.ofEpochMilli(150L);
        WindowStoreIterator<Object> forward = composite.fetch("key", start, end);
        assertThat(forward.hasNext()).isTrue();
        forward.close();
        WindowStoreIterator<Object> backward = composite.backwardFetch("key", start, end);
        assertThat(backward.hasNext()).isTrue();
        backward.close();
        KeyValueIterator<Windowed<String>, Object> ranged = composite.fetch("a", "z", start, end);
        assertThat(ranged.hasNext()).isTrue();
        ranged.close();
        KeyValueIterator<Windowed<String>, Object> backwardRanged = composite.backwardFetch("a", "z", start, end);
        assertThat(backwardRanged.hasNext()).isTrue();
        backwardRanged.close();
        KeyValueIterator<Windowed<String>, Object> all = composite.fetchAll(start, end);
        assertThat(all.hasNext()).isTrue();
        all.close();
        KeyValueIterator<Windowed<String>, Object> backwardAll = composite.backwardFetchAll(start, end);
        assertThat(backwardAll.hasNext()).isTrue();
        backwardAll.close();
        timestamped.close();
    }

    @Test
    void shouldReturnEmptySessionQueriesWithoutStores() {
        CompositeReadOnlySessionStore<String, String> session = new CompositeReadOnlySessionStore<>(
                EMPTY_PROVIDER, QueryableStoreTypes.sessionStore(), "session");
        assertEmpty(session.fetch("key"));
        assertEmpty(session.backwardFetch("key"));
        assertEmpty(session.fetch("a", "z"));
        assertEmpty(session.backwardFetch("a", "z"));
        assertThat(session.fetchSession("key", 0L, 10L)).isNull();
        assertEmpty(session.findSessions("key", 0L, 10L));
        assertEmpty(session.backwardFindSessions("key", 0L, 10L));
        assertEmpty(session.findSessions("a", "z", 0L, 10L));
        assertEmpty(session.backwardFindSessions("a", "z", 0L, 10L));
    }

    private static void assertEmpty(KeyValueIterator<?, ?> iterator) {
        assertThat(iterator.hasNext()).isFalse();
        iterator.close();
    }

    private static void assertEmpty(WindowStoreIterator<?> iterator) {
        assertThat(iterator.hasNext()).isFalse();
        iterator.close();
    }
}
