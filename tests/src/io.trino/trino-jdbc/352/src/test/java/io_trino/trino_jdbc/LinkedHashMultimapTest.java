/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino.trino_jdbc;

import io.trino.jdbc.$internal.guava.collect.LinkedHashMultimap;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LinkedHashMultimapTest {
    @Test
    void preservesKeyValueAndGlobalEntryOrderAcrossSerialization() throws Exception {
        LinkedHashMultimap<String, String> original = LinkedHashMultimap.create();
        original.put("fruit", "pear");
        original.put("vegetable", "carrot");
        original.put("fruit", "apple");
        original.put("grain", "rice");
        original.put("vegetable", "pea");
        assertFalse(original.put("fruit", "pear"));

        LinkedHashMultimap<String, String> restored = roundTrip(original);

        assertNotSame(original, restored);
        assertEquals(original, restored);
        assertEquals(5, restored.size());
        assertEquals(List.of("fruit", "vegetable", "grain"), List.copyOf(restored.keySet()));
        assertEquals(List.of("pear", "apple"), List.copyOf(restored.get("fruit")));
        assertEquals(List.of(
                Map.entry("fruit", "pear"),
                Map.entry("vegetable", "carrot"),
                Map.entry("fruit", "apple"),
                Map.entry("grain", "rice"),
                Map.entry("vegetable", "pea")), List.copyOf(restored.entries()));
        assertFalse(restored.put("fruit", "pear"));

        assertEquals(List.of("pear", "apple"), List.copyOf(restored.removeAll("fruit")));
        assertTrue(restored.put("fruit", "plum"));
        assertEquals(List.of("vegetable", "grain", "fruit"), List.copyOf(restored.keySet()));
        assertEquals(List.of(
                Map.entry("vegetable", "carrot"),
                Map.entry("grain", "rice"),
                Map.entry("vegetable", "pea"),
                Map.entry("fruit", "plum")), List.copyOf(restored.entries()));
        assertEquals(List.of("pear", "apple"), List.copyOf(original.get("fruit")));
        assertEquals(5, original.size());
    }

    @Test
    void restoresEmptyMultimapReadyForInsertion() throws Exception {
        LinkedHashMultimap<String, String> original = LinkedHashMultimap.create();

        LinkedHashMultimap<String, String> restored = roundTrip(original);

        assertNotSame(original, restored);
        assertTrue(restored.isEmpty());
        assertTrue(restored.put("color", "blue"));
        assertTrue(restored.put("color", "green"));
        assertFalse(restored.put("color", "blue"));
        assertEquals(List.of("blue", "green"), List.copyOf(restored.values()));
        assertTrue(original.isEmpty());
    }

    @SuppressWarnings("unchecked")
    private static LinkedHashMultimap<String, String> roundTrip(LinkedHashMultimap<String, String> value)
            throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
        }
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (LinkedHashMultimap<String, String>) input.readObject();
        }
    }
}
