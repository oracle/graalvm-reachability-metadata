/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_streams;

import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.Windowed;
import org.apache.kafka.streams.kstream.internals.SessionWindow;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.SessionStore;
import org.apache.kafka.streams.state.StateSerdes;
import org.apache.kafka.streams.state.Stores;
import org.apache.kafka.streams.state.TimestampedKeyValueStore;
import org.apache.kafka.streams.state.StoreBuilder;
import org.apache.kafka.streams.state.internals.KeyValueStoreBuilder;
import org.apache.kafka.streams.state.internals.SessionStoreBuilder;
import org.apache.kafka.streams.state.internals.TimestampedKeyValueStoreBuilder;
import org.apache.kafka.test.InternalMockProcessorContext;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises change-logging store wrappers through the public store-builder APIs. */
public class ChangeLoggingStoreApiCoverageTest {
    @Test
    void shouldReadSessionStateThroughBothKeyRangeDirections() {
        StoreBuilder<SessionStore<String, byte[]>> builder = new SessionStoreBuilder<String, byte[]>(
                Stores.inMemorySessionStore("logging-session", Duration.ofMinutes(1)),
                org.apache.kafka.common.serialization.Serdes.String(),
                org.apache.kafka.common.serialization.Serdes.ByteArray(), org.apache.kafka.common.utils.Time.SYSTEM)
                .withLoggingEnabled(Map.of());
        SessionStore<String, byte[]> store = builder.build();
        InternalMockProcessorContext<?, ?> context = context();
        store.init((ProcessorContext) context, store);

        Windowed<String> window = new Windowed<>("key", new SessionWindow(10L, 20L));
        store.put(window, new byte[] {9});
        assertThat(store.fetch("key").hasNext()).isTrue();
        assertThat(store.backwardFetch("key").hasNext()).isTrue();
        assertThat(store.fetch("key", "key").hasNext()).isTrue();
        assertThat(store.backwardFetch("key", "key").hasNext()).isTrue();
        assertThat(store.findSessions("key", 0L, 30L).hasNext()).isTrue();
        assertThat(store.backwardFindSessions("key", 0L, 30L).hasNext()).isTrue();
        assertThat(store.fetchSession("key", 10L, 20L)).containsExactly(9);
    }

    @Test
    void shouldDeleteAndBatchWriteThroughKeyValueWrappers() {
        StoreBuilder<KeyValueStore<Bytes, byte[]>> builder = new KeyValueStoreBuilder<Bytes, byte[]>(
                Stores.inMemoryKeyValueStore("logging-kv"),
                org.apache.kafka.common.serialization.Serdes.Bytes(),
                org.apache.kafka.common.serialization.Serdes.ByteArray(), org.apache.kafka.common.utils.Time.SYSTEM)
                .withLoggingEnabled(Map.of());
        KeyValueStore<Bytes, byte[]> store = builder.build();
        InternalMockProcessorContext<?, ?> context = context();
        store.init((ProcessorContext) context, store);
        Bytes key = Bytes.wrap(new byte[] {2});
        store.put(key, new byte[] {3});
        assertThat(store.delete(key)).containsExactly(3);
        assertThat(store.get(key)).isNull();

        StoreBuilder<TimestampedKeyValueStore<String, String>> timestampedBuilder = new TimestampedKeyValueStoreBuilder<>(
                Stores.inMemoryKeyValueStore("logging-timestamped"),
                org.apache.kafka.common.serialization.Serdes.String(),
                org.apache.kafka.common.serialization.Serdes.String(), org.apache.kafka.common.utils.Time.SYSTEM)
                .withLoggingEnabled(Map.of());
        TimestampedKeyValueStore<String, String> timestamped = timestampedBuilder.build();
        timestamped.init((ProcessorContext) context, timestamped);
        timestamped.put("key", org.apache.kafka.streams.state.ValueAndTimestamp.make("value", 40L));
        assertThat(timestamped.get("key").value()).isEqualTo("value");
        assertThat(ByteBuffer.allocate(12).putLong(40L).put(new byte[] {4}).array()).hasSize(12);
        timestamped.putAll(List.of(KeyValue.pair("other", org.apache.kafka.streams.state.ValueAndTimestamp.make("other", 41L))));
        assertThat(timestamped.get("other").value()).isEqualTo("other");
    }

    private static InternalMockProcessorContext<?, ?> context() {
        InternalMockProcessorContext<?, ?> context = new InternalMockProcessorContext<>(
                StateSerdes.withBuiltinTypes("logging-context", String.class, byte[].class),
                NativeCoverageFixtures.recordCollector());
        context.initialize();
        context.setTime(1L);
        return context;
    }
}
