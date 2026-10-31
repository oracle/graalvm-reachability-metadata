/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino.trino_jdbc;

import io.trino.jdbc.$internal.guava.collect.LinkedListMultimap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

public class LinkedListMultimapTest {
    @Test
    void restoresInterleavedEntriesAndMutableListViews() throws Exception {
        LinkedListMultimap<String, String> original = LinkedListMultimap.create();
        original.put("fruit", "pear");
        original.put("grain", "rice");
        original.put("fruit", "pear");
        original.put("fruit", "apple");

        LinkedListMultimap<String, String> restored = SerializationRoundTrip.copy(original);

        assertNotSame(original, restored);
        assertEquals(List.of(Map.entry("fruit", "pear"), Map.entry("grain", "rice"),
                Map.entry("fruit", "pear"), Map.entry("fruit", "apple")), restored.entries());
        assertEquals(List.of("pear", "pear", "apple"), restored.get("fruit"));
        restored.get("fruit").set(1, "plum");
        restored.get("grain").add("oats");
        assertEquals(List.of("pear", "rice", "plum", "apple", "oats"), restored.values());
        assertEquals(List.of("pear", "rice", "pear", "apple"), original.values());
    }
}
