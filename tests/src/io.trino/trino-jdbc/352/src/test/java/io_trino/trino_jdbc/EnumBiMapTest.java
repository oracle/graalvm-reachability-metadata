/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino.trino_jdbc;

import io.trino.jdbc.$internal.guava.collect.EnumBiMap;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EnumBiMapTest {
    public enum Stage {
        QUEUED,
        RUNNING,
        COMPLETE
    }

    public enum Color {
        RED,
        AMBER,
        GREEN
    }

    @Test
    void preservesEnumTypesMappingsAndInverseAcrossSerialization() throws Exception {
        EnumBiMap<Stage, Color> original = EnumBiMap.create(Stage.class, Color.class);
        original.put(Stage.COMPLETE, Color.GREEN);
        original.put(Stage.QUEUED, Color.RED);

        EnumBiMap<Stage, Color> restored = roundTrip(original);

        assertNotSame(original, restored);
        assertEquals(original, restored);
        assertSame(Stage.class, restored.keyType());
        assertSame(Color.class, restored.valueType());
        assertEquals(List.of(Stage.QUEUED, Stage.COMPLETE), List.copyOf(restored.keySet()));
        assertSame(Stage.QUEUED, restored.inverse().get(Color.RED));
        assertSame(Stage.COMPLETE, restored.inverse().get(Color.GREEN));
        assertSame(restored, restored.inverse().inverse());
        assertThrows(IllegalArgumentException.class, () -> restored.put(Stage.RUNNING, Color.RED));

        assertNull(restored.inverse().put(Color.AMBER, Stage.RUNNING));
        assertSame(Color.AMBER, restored.get(Stage.RUNNING));
        assertSame(Color.RED, restored.forcePut(Stage.QUEUED, Color.GREEN));
        assertFalse(restored.containsKey(Stage.COMPLETE));
        assertFalse(restored.inverse().containsKey(Color.RED));
        assertSame(Stage.QUEUED, restored.inverse().get(Color.GREEN));
        assertEquals(2, restored.size());
        assertSame(Color.GREEN, original.get(Stage.COMPLETE));
        assertSame(Color.RED, original.get(Stage.QUEUED));
        assertFalse(original.containsKey(Stage.RUNNING));
    }

    @Test
    void restoresEmptyMapWithEnumTypesAndMutableInverse() throws Exception {
        EnumBiMap<Stage, Color> original = EnumBiMap.create(Stage.class, Color.class);

        EnumBiMap<Stage, Color> restored = roundTrip(original);

        assertNotSame(original, restored);
        assertTrue(restored.isEmpty());
        assertSame(Stage.class, restored.keyType());
        assertSame(Color.class, restored.valueType());
        restored.put(Stage.RUNNING, Color.AMBER);
        assertSame(Stage.RUNNING, restored.inverse().get(Color.AMBER));
        assertSame(Stage.RUNNING, restored.inverse().remove(Color.AMBER));
        assertTrue(restored.isEmpty());
        restored.inverse().put(Color.GREEN, Stage.COMPLETE);
        assertSame(Color.GREEN, restored.get(Stage.COMPLETE));
        assertEquals(1, restored.size());
        assertTrue(original.isEmpty());
    }

    @SuppressWarnings("unchecked")
    private static EnumBiMap<Stage, Color> roundTrip(EnumBiMap<Stage, Color> map) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(map);
        }
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (EnumBiMap<Stage, Color>) input.readObject();
        }
    }
}
