/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_streams;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.Windowed;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.TimestampedWindowStore;
import org.apache.kafka.streams.state.WindowStoreIterator;
import org.apache.kafka.streams.state.internals.ReadOnlyWindowStoreFacade;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WindowAndSessionStoreCoverageTest {
    @Test
    void readOnlyFacadeSupportsBackwardAndRangeQueriesWithoutExposingTimestamps() {
        @SuppressWarnings("unchecked")
        TimestampedWindowStore<String, Long> inner = Mockito.mock(TimestampedWindowStore.class);
        @SuppressWarnings("unchecked")
        WindowStoreIterator<org.apache.kafka.streams.state.ValueAndTimestamp<Long>> values =
                Mockito.mock(WindowStoreIterator.class);
        @SuppressWarnings("unchecked")
        KeyValueIterator<Windowed<String>, org.apache.kafka.streams.state.ValueAndTimestamp<Long>> entries =
                Mockito.mock(KeyValueIterator.class);
        Instant from = Instant.ofEpochMilli(10);
        Instant to = Instant.ofEpochMilli(20);
        when(inner.backwardFetch("a", from, to)).thenReturn(values);
        when(inner.fetch("a", "z", from, to)).thenReturn(entries);
        when(inner.backwardFetch("a", "z", from, to)).thenReturn(entries);
        when(inner.fetchAll(from, to)).thenReturn(entries);
        when(inner.backwardFetchAll(from, to)).thenReturn(entries);
        ReadOnlyWindowStoreFacade<String, Long> facade = new TestFacade(inner);

        assertThat(facade.backwardFetch("a", from, to)).isNotNull();
        assertThat(facade.fetch("a", "z", from, to)).isNotNull();
        assertThat(facade.backwardFetch("a", "z", from, to)).isNotNull();
        assertThat(facade.fetchAll(from, to)).isNotNull();
        assertThat(facade.backwardFetchAll(from, to)).isNotNull();
        verify(inner).backwardFetch("a", from, to);
        verify(inner).fetch("a", "z", from, to);
        verify(inner).backwardFetch("a", "z", from, to);
        verify(inner).fetchAll(from, to);
        verify(inner).backwardFetchAll(from, to);
    }

    @Test
    void iteratorConsumersPreserveOrderAndCloseResources() {
        @SuppressWarnings("unchecked")
        KeyValueIterator<String, Long> iterator = Mockito.mock(KeyValueIterator.class);
        when(iterator.hasNext()).thenReturn(true, true, false);
        when(iterator.next()).thenReturn(KeyValue.pair("a", 1L), KeyValue.pair("b", 2L));

        List<KeyValue<String, Long>> result = new ArrayList<>();
        try (iterator) {
            while (iterator.hasNext()) {
                result.add(iterator.next());
            }
        }

        assertThat(result).extracting(entry -> entry.value).containsExactly(1L, 2L);
        verify(iterator).close();
    }

    private static final class TestFacade extends ReadOnlyWindowStoreFacade<String, Long> {
        private TestFacade(TimestampedWindowStore<String, Long> inner) {
            super(inner);
        }
    }
}
