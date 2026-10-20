/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_common;

import io.netty.util.collection.CharObjectHashMap;
import io.netty.util.collection.CharObjectMap;
import io.netty.util.collection.IntCollections;
import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.collection.IntObjectMap;
import io.netty.util.collection.LongObjectHashMap;
import io.netty.util.collection.LongObjectMap;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the primitive-keyed collections subsystem of netty-common: `IntObjectHashMap`,
 * `LongObjectHashMap` and `CharObjectHashMap` with unboxed lookups, `PrimitiveEntry` iteration
 * with in-place updates and removal, boxed `Map` views, null values, growth past the initial
 * capacity, `keyToString` customization and the `IntCollections` empty and unmodifiable views.
 *
 * A multiplexing protocol server tracks open streams by their odd client stream ids, correlates
 * in-flight RPC calls by 64-bit call ids, dispatches control frames by a one-character opcode,
 * and hands monitoring code a read-only snapshot of its stream table.
 */
class MultiplexedStreamRegistryCollectionsTest {

    @Test
    void streamTableTracksOpenStreamsAndClosesIdleOnes() {
        StreamRegistry registry = new StreamRegistry();
        for (int streamId = 1; streamId <= 9; streamId += 2) {
            registry.open(streamId, "/api/item/" + streamId);
        }
        registry.recordBytes(3, 512);
        registry.recordBytes(7, 2048);

        List<Integer> closed = registry.closeIdleStreams();

        assertThat(closed).containsExactlyInAnyOrder(1, 5, 9);
        assertThat(registry.streams.size()).isEqualTo(2);
        assertThat(registry.streams.containsKey(3)).isTrue();
        assertThat(registry.streams.containsKey(5)).isFalse();
        assertThat(registry.streams.get(7).bytesReceived).isEqualTo(2048);
        assertThat(registry.streams.get(11)).isNull();

        IntObjectMap.PrimitiveEntry<Stream> only = null;
        registry.streams.remove(3);
        for (IntObjectMap.PrimitiveEntry<Stream> entry : registry.streams.entries()) {
            only = entry;
        }
        assertThat(only).isNotNull();
        assertThat(only.key()).isEqualTo(7);
        only.setValue(new Stream("/api/item/7?retry", 0));
        assertThat(registry.streams.get(7).path).isEqualTo("/api/item/7?retry");
        assertThat(registry.streams.toString()).isEqualTo("{stream-7=/api/item/7?retry}");
    }

    @Test
    void streamTableGrowsPastInitialCapacityAndShrinksBack() {
        IntObjectHashMap<String> streams = new IntObjectHashMap<>(4);
        for (int streamId = 1; streamId < 2000; streamId += 2) {
            assertThat(streams.put(streamId, "s" + streamId)).isNull();
        }
        assertThat(streams.size()).isEqualTo(1000);
        assertThat(streams.put(1001, "replaced")).isEqualTo("s1001");
        assertThat(streams.size()).isEqualTo(1000);

        for (int streamId = 1; streamId < 2000; streamId += 4) {
            assertThat(streams.remove(streamId)).isNotNull();
        }
        assertThat(streams.size()).isEqualTo(500);
        assertThat(streams.get(1001)).isNull();
        assertThat(streams.get(1003)).isEqualTo("s1003");
        assertThat(streams.containsValue("s1999")).isTrue();
        assertThat(streams.containsValue("s1997")).isFalse();

        Set<Integer> sortedIds = new TreeSet<>(streams.keySet());
        assertThat(sortedIds.iterator().next()).isEqualTo(3);
        assertThat(sortedIds).allMatch(id -> id % 4 == 3);

        streams.keySet().retainAll(Set.of(3, 7, 11, 13));
        assertThat(streams.keySet()).containsExactlyInAnyOrder(3, 7, 11);
        streams.clear();
        assertThat(streams.isEmpty()).isTrue();
    }

    @Test
    void boxedMapViewInteroperatesWithJdkCollections() {
        IntObjectHashMap<String> priorities = new IntObjectHashMap<>();
        priorities.putAll(Map.of(1, "high", 3, "low"));
        Map<Integer, String> boxed = priorities;

        assertThat(boxed.get(Integer.valueOf(1))).isEqualTo("high");
        assertThat(boxed.containsKey(Integer.valueOf(3))).isTrue();
        assertThat(boxed.put(5, "medium")).isNull();
        assertThat(priorities.get(5)).isEqualTo("medium");

        for (Map.Entry<Integer, String> entry : boxed.entrySet()) {
            entry.setValue(entry.getValue().toUpperCase());
        }
        assertThat(priorities.values()).containsExactlyInAnyOrder("HIGH", "LOW", "MEDIUM");

        priorities.put(7, null);
        assertThat(priorities.containsKey(7)).isTrue();
        assertThat(priorities.get(7)).isNull();
        assertThat(priorities.containsValue(null)).isTrue();
        assertThat(priorities.size()).isEqualTo(4);

        IntObjectHashMap<String> copy = new IntObjectHashMap<>();
        copy.putAll(priorities);
        assertThat(copy).isEqualTo(priorities);
        assertThat(copy.hashCode()).isEqualTo(priorities.hashCode());
        copy.remove(7);
        assertThat(copy).isNotEqualTo(priorities);
    }

    @Test
    void inFlightCallsCorrelateBySixtyFourBitIds() {
        CallCorrelator correlator = new CallCorrelator();
        long first = correlator.send("getUser");
        long second = correlator.send("listOrders");
        long third = correlator.send("ping");
        correlator.pending.put(Long.MIN_VALUE, "legacy");
        correlator.pending.put(-1L, "broadcast");

        assertThat(first).isGreaterThan(Integer.MAX_VALUE);
        assertThat(correlator.complete(second)).isEqualTo("listOrders");
        assertThat(correlator.complete(second)).isNull();
        assertThat(correlator.pending.get(Long.MIN_VALUE)).isEqualTo("legacy");
        assertThat(correlator.pending.get(-1L)).isEqualTo("broadcast");

        List<Long> ids = new ArrayList<>();
        Iterator<LongObjectMap.PrimitiveEntry<String>> it = correlator.pending.entries().iterator();
        while (it.hasNext()) {
            LongObjectMap.PrimitiveEntry<String> entry = it.next();
            ids.add(entry.key());
            if (entry.key() < 0) {
                it.remove();
            }
        }
        assertThat(ids).containsExactlyInAnyOrder(first, third, Long.MIN_VALUE, -1L);
        assertThat(correlator.pending.keySet()).containsExactlyInAnyOrder(first, third);
    }

    @Test
    void controlFramesDispatchByOpcodeCharacter() {
        CharObjectMap<String> handlers = new CharObjectHashMap<>();
        handlers.put('P', "ping");
        handlers.put('G', "goaway");
        handlers.put('W', "window-update");
        handlers.put('\u00e9', "extension");

        List<String> dispatched = new ArrayList<>();
        for (char opcode : "PWXG\u00e9".toCharArray()) {
            String handler = handlers.get(opcode);
            dispatched.add(handler == null ? "unknown:" + opcode : handler);
        }

        assertThat(dispatched).containsExactly("ping", "window-update", "unknown:X", "goaway", "extension");
        assertThat(handlers.remove('G')).isEqualTo("goaway");
        assertThat(handlers.containsKey('G')).isFalse();
        assertThat(handlers.keySet()).containsExactlyInAnyOrder('P', 'W', '\u00e9');
    }

    @Test
    void monitoringReceivesReadOnlyStreamSnapshot() {
        IntObjectHashMap<String> streams = new IntObjectHashMap<>();
        streams.put(1, "open");
        streams.put(3, "half-closed");
        IntObjectMap<String> snapshot = IntCollections.unmodifiableMap(streams);

        assertThat(snapshot.get(3)).isEqualTo("half-closed");
        assertThat(snapshot.size()).isEqualTo(2);
        assertThatThrownBy(() -> snapshot.put(5, "open")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> snapshot.remove(1)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> snapshot.entries().iterator().next().setValue("closed"))
                .isInstanceOf(UnsupportedOperationException.class);

        streams.put(5, "open");
        assertThat(snapshot.containsKey(5)).isTrue();

        IntObjectMap<String> none = IntCollections.emptyMap();
        assertThat(none.isEmpty()).isTrue();
        assertThat(none.get(1)).isNull();
        assertThat(none.entries()).isEmpty();
        assertThatThrownBy(() -> none.put(1, "open")).isInstanceOf(UnsupportedOperationException.class);
    }

    /** Per-stream state the server keeps while a stream is open. */
    static final class Stream {
        final String path;
        long bytesReceived;

        Stream(String path, long bytesReceived) {
            this.path = path;
            this.bytesReceived = bytesReceived;
        }

        @Override
        public String toString() {
            return path;
        }
    }

    /** Stream table whose diagnostics label keys as stream ids. */
    static final class StreamTable extends IntObjectHashMap<Stream> {
        @Override
        protected String keyToString(int key) {
            return "stream-" + key;
        }
    }

    /** Opens streams, records traffic and reaps streams that never received data. */
    static final class StreamRegistry {
        final StreamTable streams = new StreamTable();

        void open(int streamId, String path) {
            streams.put(streamId, new Stream(path, 0));
        }

        void recordBytes(int streamId, long bytes) {
            streams.get(streamId).bytesReceived += bytes;
        }

        List<Integer> closeIdleStreams() {
            List<Integer> closed = new ArrayList<>();
            Iterator<IntObjectMap.PrimitiveEntry<Stream>> it = streams.entries().iterator();
            while (it.hasNext()) {
                IntObjectMap.PrimitiveEntry<Stream> entry = it.next();
                if (entry.value().bytesReceived == 0) {
                    closed.add(entry.key());
                    it.remove();
                }
            }
            return closed;
        }
    }

    /** Assigns call ids above the 32-bit range and matches responses to their requests. */
    static final class CallCorrelator {
        final LongObjectHashMap<String> pending = new LongObjectHashMap<>();
        private long nextId = 1L << 40;

        long send(String method) {
            long id = nextId++;
            pending.put(id, method);
            return id;
        }

        String complete(long id) {
            return pending.remove(id);
        }
    }
}
