/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_collections.google_collections;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.EnumHashBiMap;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;

public class EnumHashBiMapTest {
    @Test
    void serializedEnumHashBiMapPreservesKeyTypeAndMappings() throws Exception {
        EnumHashBiMap<Color, String> original = EnumHashBiMap.create(Color.class);
        original.put(Color.RED, "warm");
        original.put(Color.BLUE, null);

        EnumHashBiMap<Color, String> restored = deserialize(serialize(original));

        assertThat(restored.keyType()).isSameAs(Color.class);
        assertThat(restored).containsEntry(Color.RED, "warm");
        assertThat(restored).containsEntry(Color.BLUE, null);
        assertThat(restored.inverse()).containsEntry("warm", Color.RED);
        assertThat(restored.inverse()).containsEntry(null, Color.BLUE);

        restored.put(Color.GREEN, "cool");
        assertThat(restored.inverse()).containsEntry("cool", Color.GREEN);
    }

    private static byte[] serialize(EnumHashBiMap<Color, String> value) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
        }
        return bytes.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static EnumHashBiMap<Color, String> deserialize(byte[] bytes)
            throws IOException, ClassNotFoundException {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return (EnumHashBiMap<Color, String>) input.readObject();
        }
    }

    private enum Color {
        RED,
        BLUE,
        GREEN
    }
}
