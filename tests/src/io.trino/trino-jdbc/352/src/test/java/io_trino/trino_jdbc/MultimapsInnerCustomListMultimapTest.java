/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino.trino_jdbc;

import io.trino.jdbc.$internal.guava.base.Supplier;
import io.trino.jdbc.$internal.guava.collect.ListMultimap;
import io.trino.jdbc.$internal.guava.collect.Multimaps;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

public class MultimapsInnerCustomListMultimapTest {
    @Test
    void preservesSortedKeysListsAndSerializableFactory() throws Exception {
        ListMultimap<String, String> original = Multimaps.newListMultimap(new TreeMap<>(), Lists.INSTANCE);
        original.put("z", "last");
        original.putAll("a", List.of("first", "first", "second"));

        ListMultimap<String, String> restored = SerializationRoundTrip.copy(original);

        assertNotSame(original, restored);
        assertEquals(original, restored);
        assertEquals(List.of("a", "z"), List.copyOf(restored.keySet()));
        assertEquals(List.of("first", "first", "second"), restored.get("a"));
        restored.get("new").add("created");
        restored.get("a").add(1, "inserted");
        assertEquals(List.of("created"), restored.get("new"));
        assertEquals(List.of("first", "inserted", "first", "second"), restored.get("a"));
        assertEquals(4, original.size());
    }

    public enum Lists implements Supplier<List<String>> {
        INSTANCE;

        @Override
        public List<String> get() {
            return new LinkedList<>();
        }
    }
}
