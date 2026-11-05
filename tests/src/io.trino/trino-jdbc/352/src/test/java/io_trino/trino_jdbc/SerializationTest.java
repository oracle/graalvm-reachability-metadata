/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino.trino_jdbc;

import io.trino.jdbc.$internal.guava.collect.ArrayListMultimap;
import io.trino.jdbc.$internal.guava.collect.HashBiMap;
import io.trino.jdbc.$internal.guava.collect.TreeMultiset;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;

public class SerializationTest {
    @Test
    void preservesBidirectionalMapMappingsAndIndependentMutation() throws Exception {
        HashBiMap<String, String> original = HashBiMap.create();
        original.put("north", "N");
        original.put("south", "S");

        HashBiMap<String, String> restored = roundTrip(original);

        assertNotSame(original, restored);
        assertEquals(original, restored);
        assertEquals("north", restored.inverse().get("N"));
        restored.put("east", "E");
        assertEquals("east", restored.inverse().get("E"));
        assertFalse(original.containsKey("east"));
    }

    @Test
    void preservesMultimapKeysDuplicateValuesAndListOrder() throws Exception {
        ArrayListMultimap<String, String> original = ArrayListMultimap.create();
        original.putAll("fruit", List.of("pear", "apple", "pear"));
        original.put("vegetable", "carrot");

        ArrayListMultimap<String, String> restored = roundTrip(original);

        assertNotSame(original, restored);
        assertEquals(original, restored);
        assertEquals(4, restored.size());
        assertEquals(List.of("pear", "apple", "pear"), restored.get("fruit"));
        assertEquals(List.of("carrot"), restored.get("vegetable"));
        restored.put("fruit", "plum");
        assertEquals(List.of("pear", "apple", "pear", "plum"), restored.get("fruit"));
        assertEquals(3, original.get("fruit").size());
    }

    @Test
    void restoresSortedMultisetCountsComparatorAndMutableTree() throws Exception {
        TreeMultiset<String> original = TreeMultiset.create();
        original.add("pear", 3);
        original.add("apple", 2);

        TreeMultiset<String> restored = roundTrip(original);

        assertNotSame(original, restored);
        assertEquals(original, restored);
        assertEquals(5, restored.size());
        assertEquals(3, restored.count("pear"));
        assertEquals(2, restored.count("apple"));
        assertEquals(List.of("apple", "pear"), List.copyOf(restored.elementSet()));
        restored.add("banana", 4);
        restored.remove("pear", 2);
        assertEquals(List.of("apple", "banana", "pear"), List.copyOf(restored.elementSet()));
        assertEquals(4, restored.count("banana"));
        assertEquals(1, restored.count("pear"));
        assertEquals(0, original.count("banana"));
        assertEquals(3, original.count("pear"));
    }

    @SuppressWarnings("unchecked")
    private static <T extends Serializable> T roundTrip(T value) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
        }
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (T) input.readObject();
        }
    }
}
