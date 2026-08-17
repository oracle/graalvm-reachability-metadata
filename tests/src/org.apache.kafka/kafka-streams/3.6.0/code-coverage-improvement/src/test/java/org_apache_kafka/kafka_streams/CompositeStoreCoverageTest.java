/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_streams;

import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.Windowed;
import org.apache.kafka.streams.kstream.internals.SessionWindow;
import org.apache.kafka.streams.kstream.internals.TimeWindow;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.QueryableStoreType;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;
import org.apache.kafka.streams.state.ReadOnlySessionStore;
import org.apache.kafka.streams.state.ReadOnlyWindowStore;
import org.apache.kafka.streams.state.SessionStore;
import org.apache.kafka.streams.state.WindowStore;
import org.apache.kafka.streams.state.WindowStoreIterator;
import org.apache.kafka.streams.state.internals.CompositeReadOnlyKeyValueStore;
import org.apache.kafka.streams.state.internals.CompositeReadOnlySessionStore;
import org.apache.kafka.streams.state.internals.CompositeReadOnlyWindowStore;
import org.apache.kafka.streams.state.internals.StateStoreProvider;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Iterator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CompositeStoreCoverageTest {
    @Test
    void keyValueCompositeSearchesStoresAndSumsTheirSizes() {
        ReadOnlyKeyValueStore<String, String> first = keyValueStore(null, 4L);
        ReadOnlyKeyValueStore<String, String> second = keyValueStore("value", 7L);

        CompositeReadOnlyKeyValueStore<String, String> composite = new CompositeReadOnlyKeyValueStore<>(
                provider(List.of(first, second)), QueryableStoreTypes.keyValueStore(), "accounts");

        assertThat(composite.get("key")).isEqualTo("value");
        assertThat(composite.approximateNumEntries()).isEqualTo(11L);
        assertThat(values(composite.all())).containsExactly("value");
        assertThat(values(composite.reverseAll())).containsExactly("value");
        assertThat(values(composite.range("a", "z"))).containsExactly("value");
        assertThat(values(composite.reverseRange("a", "z"))).containsExactly("value");
    }

    @Test
    void windowCompositeTraversesEveryStoreInForwardAndBackwardDirections() {
        ReadOnlyWindowStore<String, String> first = windowStore("first", 1L);
        ReadOnlyWindowStore<String, String> second = windowStore("second", 2L);
        CompositeReadOnlyWindowStore<String, String> composite = new CompositeReadOnlyWindowStore<>(
                provider(List.of(first, second)), QueryableStoreTypes.windowStore(), "windows");
        Instant from = Instant.ofEpochMilli(0);
        Instant to = Instant.ofEpochMilli(10);

        assertThat(composite.fetch("key", 1L)).isEqualTo("first");
        assertThat(values(composite.fetch("key", from, to))).containsExactly("first");
        assertThat(values(composite.backwardFetch("key", from, to))).containsExactly("first");
        assertThat(values(composite.fetch("a", "z", from, to))).containsExactly("first", "second");
        assertThat(values(composite.backwardFetch("a", "z", from, to))).containsExactly("first", "second");
        assertThat(values(composite.all())).containsExactly("first", "second");
        assertThat(values(composite.backwardAll())).containsExactly("first", "second");
        assertThat(values(composite.fetchAll(from, to))).containsExactly("first", "second");
        assertThat(values(composite.backwardFetchAll(from, to))).containsExactly("first", "second");
    }

    @Test
    void sessionCompositeDelegatesPointLookupsAndCombinesSessionIterators() {
        ReadOnlySessionStore<String, String> first = sessionStore("first");
        ReadOnlySessionStore<String, String> second = sessionStore("second");
        when(first.fetchSession("key", 1L, 2L)).thenReturn(null);
        when(second.fetchSession("key", 1L, 2L)).thenReturn("second");
        CompositeReadOnlySessionStore<String, String> composite = new CompositeReadOnlySessionStore<>(
                provider(List.of(first, second)), QueryableStoreTypes.sessionStore(), "sessions");

        assertThat(composite.fetchSession("key", 1L, 2L)).isNull();
        assertThat(values(composite.fetch("key"))).containsExactly("first");
        assertThat(values(composite.backwardFetch("key"))).containsExactly("first");
        assertThat(values(composite.fetch("a", "z"))).containsExactly("first", "second");
        assertThat(values(composite.backwardFetch("a", "z"))).containsExactly("first", "second");
        assertThat(values(composite.findSessions("key", 0L, 9L))).containsExactly("first");
        assertThat(values(composite.backwardFindSessions("key", 0L, 9L))).containsExactly("first");
        assertThat(values(composite.findSessions("a", "z", 0L, 9L))).containsExactly("first");
        assertThat(values(composite.backwardFindSessions("a", "z", 0L, 9L))).containsExactly("first");
    }

    private static ReadOnlyKeyValueStore<String, String> keyValueStore(String value, long size) {
        KeyValueStore<String, String> store = mock(KeyValueStore.class);
        when(store.get("key")).thenReturn(value);
        when(store.approximateNumEntries()).thenReturn(size);
        when(store.all()).thenAnswer(ignored -> value == null
                ? new ListKeyValueIterator<>(List.of()) : iterator("key", value));
        when(store.reverseAll()).thenAnswer(ignored -> value == null
                ? new ListKeyValueIterator<>(List.of()) : iterator("key", value));
        when(store.range(any(), any())).thenAnswer(ignored -> value == null
                ? new ListKeyValueIterator<>(List.of()) : iterator("key", value));
        when(store.reverseRange(any(), any())).thenAnswer(ignored -> value == null
                ? new ListKeyValueIterator<>(List.of()) : iterator("key", value));
        return QueryableStoreTypes.<String, String>keyValueStore().create(
                provider(List.of(store)), "decorated-key-value");
    }

    private static ReadOnlyWindowStore<String, String> windowStore(String value, long timestamp) {
        WindowStore<String, String> store = mock(WindowStore.class);
        when(store.fetch("key", 1L)).thenReturn(value);
        when(store.fetch(any(), any(Instant.class), any(Instant.class))).thenAnswer(ignored -> windowIterator(timestamp, value));
        when(store.backwardFetch(any(), any(Instant.class), any(Instant.class))).thenAnswer(ignored -> windowIterator(timestamp, value));
        when(store.fetch(any(), any(), any(Instant.class), any(Instant.class))).thenAnswer(ignored -> iterator(new Windowed<>("key", new TimeWindow(0, 1)), value));
        when(store.backwardFetch(any(), any(), any(Instant.class), any(Instant.class))).thenAnswer(ignored -> iterator(new Windowed<>("key", new TimeWindow(0, 1)), value));
        when(store.all()).thenAnswer(ignored -> iterator(new Windowed<>("key", new TimeWindow(0, 1)), value));
        when(store.backwardAll()).thenAnswer(ignored -> iterator(new Windowed<>("key", new TimeWindow(0, 1)), value));
        when(store.fetchAll(any(Instant.class), any(Instant.class))).thenAnswer(ignored -> iterator(new Windowed<>("key", new TimeWindow(0, 1)), value));
        when(store.backwardFetchAll(any(Instant.class), any(Instant.class))).thenAnswer(ignored -> iterator(new Windowed<>("key", new TimeWindow(0, 1)), value));
        return QueryableStoreTypes.<String, String>windowStore().create(
                provider(List.of(store)), "decorated-window");
    }

    private static ReadOnlySessionStore<String, String> sessionStore(String value) {
        SessionStore<String, String> store = mock(SessionStore.class);
        Windowed<String> key = new Windowed<>("key", new SessionWindow(0, 1));
        when(store.fetch(any())).thenAnswer(ignored -> iterator(key, value));
        when(store.backwardFetch(any())).thenAnswer(ignored -> iterator(key, value));
        when(store.fetch(any(), any())).thenAnswer(ignored -> iterator(key, value));
        when(store.backwardFetch(any(), any())).thenAnswer(ignored -> iterator(key, value));
        when(store.findSessions(any(), any(Long.class), any(Long.class))).thenAnswer(ignored -> iterator(key, value));
        when(store.backwardFindSessions(any(), any(Long.class), any(Long.class))).thenAnswer(ignored -> iterator(key, value));
        when(store.findSessions(any(), any(), any(Long.class), any(Long.class))).thenAnswer(ignored -> iterator(key, value));
        when(store.backwardFindSessions(any(), any(), any(Long.class), any(Long.class))).thenAnswer(ignored -> iterator(key, value));
        return QueryableStoreTypes.<String, String>sessionStore().create(
                provider(List.of(store)), "decorated-session");
    }

    private static <T> StateStoreProvider provider(List<T> stores) {
        return new StateStoreProvider() {
            @Override
            @SuppressWarnings("unchecked")
            public <S> List<S> stores(String storeName, QueryableStoreType<S> type) {
                return (List<S>) stores;
            }
        };
    }

    private static <K, V> KeyValueIterator<K, V> iterator(K key, V value) {
        return new ListKeyValueIterator<>(List.of(KeyValue.pair(key, value)));
    }

    private static <V> WindowStoreIterator<V> windowIterator(long timestamp, V value) {
        return new ListWindowIterator<>(List.of(KeyValue.pair(timestamp, value)));
    }

    private static <K, V> List<V> values(KeyValueIterator<K, V> iterator) {
        try (iterator) {
            java.util.ArrayList<V> values = new java.util.ArrayList<>();
            iterator.forEachRemaining(entry -> values.add(entry.value));
            return values;
        }
    }

    private static final class ListKeyValueIterator<K, V> implements KeyValueIterator<K, V> {
        private final Iterator<KeyValue<K, V>> delegate;

        private ListKeyValueIterator(List<KeyValue<K, V>> entries) {
            delegate = entries.iterator();
        }

        @Override public void close() { }
        @Override public K peekNextKey() {
            throw new UnsupportedOperationException();
        }
        @Override public boolean hasNext() {
            return delegate.hasNext();
        }
        @Override public KeyValue<K, V> next() {
            return delegate.next();
        }
    }

    private static final class ListWindowIterator<V> implements WindowStoreIterator<V> {
        private final Iterator<KeyValue<Long, V>> delegate;

        private ListWindowIterator(List<KeyValue<Long, V>> entries) {
            delegate = entries.iterator();
        }

        @Override public void close() { }
        @Override public Long peekNextKey() {
            throw new UnsupportedOperationException();
        }
        @Override public boolean hasNext() {
            return delegate.hasNext();
        }
        @Override public KeyValue<Long, V> next() {
            return delegate.next();
        }
    }
}
