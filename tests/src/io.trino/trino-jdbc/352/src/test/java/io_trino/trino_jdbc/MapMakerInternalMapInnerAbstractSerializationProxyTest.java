/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino.trino_jdbc;

import io.trino.jdbc.$internal.guava.collect.MapMaker;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.ref.Reference;
import java.util.concurrent.ConcurrentMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MapMakerInternalMapInnerAbstractSerializationProxyTest {
    public enum Channel {
        NEWS,
        ALERTS
    }

    @Test
    void restoresWeakKeyMapEntriesAndConcurrentUpdates() throws Exception {
        ConcurrentMap<Channel, String> original = new MapMaker()
                .initialCapacity(8)
                .concurrencyLevel(2)
                .weakKeys()
                .makeMap();
        original.put(Channel.NEWS, "daily");
        original.put(Channel.ALERTS, "urgent");

        ConcurrentMap<Channel, String> restored = roundTrip(original);

        assertNotSame(original, restored);
        assertEquals(2, restored.size());
        assertEquals("daily", restored.get(Channel.NEWS));
        assertEquals("urgent", restored.get(Channel.ALERTS));
        assertEquals("daily", restored.putIfAbsent(Channel.NEWS, "weekly"));
        assertTrue(restored.replace(Channel.NEWS, "daily", "weekly"));
        assertEquals("weekly", restored.get(Channel.NEWS));
        assertTrue(restored.remove(Channel.ALERTS, "urgent"));
        assertFalse(restored.containsKey(Channel.ALERTS));
        assertEquals("daily", original.get(Channel.NEWS));
        assertEquals("urgent", original.get(Channel.ALERTS));
    }

    @Test
    void restoresEmptyMapWithIdentityBasedWeakKeys() throws Exception {
        ConcurrentMap<String, String> original = new MapMaker().weakKeys().makeMap();

        ConcurrentMap<String, String> restored = roundTrip(original);

        assertNotSame(original, restored);
        assertTrue(restored.isEmpty());
        String firstKey = new String("subscriber");
        String equalKey = new String("subscriber");
        restored.put(firstKey, "first");
        assertEquals("first", restored.get(firstKey));
        assertNull(restored.get(equalKey));
        assertNull(restored.putIfAbsent(equalKey, "second"));
        assertEquals(2, restored.size());
        assertEquals("second", restored.get(equalKey));
        assertEquals("first", restored.remove(firstKey));
        assertEquals("second", restored.get(equalKey));
        assertTrue(original.isEmpty());
        Reference.reachabilityFence(firstKey);
        Reference.reachabilityFence(equalKey);
    }

    @SuppressWarnings("unchecked")
    private static <K, V> ConcurrentMap<K, V> roundTrip(ConcurrentMap<K, V> map) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(map);
        }
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (ConcurrentMap<K, V>) input.readObject();
        }
    }
}
