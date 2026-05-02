/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_collections.google_collections;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.EnumBiMap;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;

public class EnumBiMapTest {
    @Test
    void serializedEnumBiMapPreservesEnumTypesAndBidirectionalMappings() throws Exception {
        EnumBiMap<Direction, Signal> original = EnumBiMap.create(Direction.class, Signal.class);
        original.put(Direction.NORTH, Signal.GO);
        original.put(Direction.SOUTH, Signal.STOP);

        EnumBiMap<Direction, Signal> restored = deserialize(serialize(original));

        assertThat(restored.keyType()).isSameAs(Direction.class);
        assertThat(restored.valueType()).isSameAs(Signal.class);
        assertThat(restored).containsEntry(Direction.NORTH, Signal.GO);
        assertThat(restored).containsEntry(Direction.SOUTH, Signal.STOP);
        assertThat(restored.inverse()).containsEntry(Signal.GO, Direction.NORTH);
        assertThat(restored.inverse()).containsEntry(Signal.STOP, Direction.SOUTH);
    }

    private static byte[] serialize(EnumBiMap<Direction, Signal> value) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
        }
        return bytes.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static EnumBiMap<Direction, Signal> deserialize(byte[] bytes)
            throws IOException, ClassNotFoundException {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return (EnumBiMap<Direction, Signal>) input.readObject();
        }
    }

    private enum Direction {
        NORTH,
        SOUTH
    }

    private enum Signal {
        GO,
        STOP
    }
}
