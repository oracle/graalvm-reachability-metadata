/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino.trino_jdbc;

import io.trino.jdbc.$internal.guava.collect.TreeMultimap;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;

public class TreeMultimapTest {
    @Test
    void restoresBothComparatorsAndNavigableViews() throws Exception {
        TreeMultimap<String, String> original = TreeMultimap.create(
                Comparator.reverseOrder(), String.CASE_INSENSITIVE_ORDER);
        original.putAll("fruit", List.of("pear", "Apple", "PEAR"));
        original.put("vegetable", "carrot");

        TreeMultimap<String, String> restored = SerializationRoundTrip.copy(original);

        assertNotSame(original, restored);
        assertEquals(original, restored);
        assertEquals(List.of("vegetable", "fruit"), List.copyOf(restored.keySet()));
        assertEquals(List.of("Apple", "pear"), List.copyOf(restored.get("fruit")));
        assertEquals("pear", restored.get("fruit").higher("Apple"));
        assertFalse(restored.put("fruit", "APPLE"));
        restored.put("fruit", "banana");
        assertEquals("banana", restored.get("fruit").higher("Apple"));
        restored.put("new", "value");
        assertFalse(original.containsKey("new"));
        assertEquals(3, original.size());
    }
}
