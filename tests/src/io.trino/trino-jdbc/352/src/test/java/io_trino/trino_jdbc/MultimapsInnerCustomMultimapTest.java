/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino.trino_jdbc;

import io.trino.jdbc.$internal.guava.base.Supplier;
import io.trino.jdbc.$internal.guava.collect.Multimap;
import io.trino.jdbc.$internal.guava.collect.Multimaps;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.List;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MultimapsInnerCustomMultimapTest {
    @Test
    void restoresQueueBackedValuesAndCreatesNewQueues() throws Exception {
        Multimap<String, String> original = Multimaps.newMultimap(new TreeMap<>(), Queues.INSTANCE);
        original.putAll("pending", List.of("first", "second", "first"));
        original.put("done", "archived");

        Multimap<String, String> restored = SerializationRoundTrip.copy(original);

        assertNotSame(original, restored);
        assertEquals(4, restored.size());
        assertEquals(List.of("done", "pending"), List.copyOf(restored.keySet()));
        assertEquals(List.of("first", "second", "first"), List.copyOf(restored.get("pending")));
        assertTrue(restored.remove("pending", "first"));
        restored.put("new", "created");
        assertEquals(List.of("second", "first"), List.copyOf(restored.get("pending")));
        assertEquals(List.of("created"), List.copyOf(restored.get("new")));
        assertEquals(3, original.get("pending").size());
    }

    public enum Queues implements Supplier<Collection<String>> {
        INSTANCE;

        @Override
        public Collection<String> get() {
            return new ArrayDeque<>();
        }
    }
}
