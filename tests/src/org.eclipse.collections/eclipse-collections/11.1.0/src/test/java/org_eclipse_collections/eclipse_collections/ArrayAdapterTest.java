/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_collections.eclipse_collections;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.eclipse.collections.impl.list.fixed.ArrayAdapter;
import org.junit.jupiter.api.Test;

public class ArrayAdapterTest {
    @Test
    void serializationRoundTripPreservesArrayBackedContents() throws IOException, ClassNotFoundException {
        final ArrayAdapter<String> adapter = ArrayAdapter.newArrayWith("alpha", "bravo", "charlie");

        final byte[] serialized = serialize(adapter);
        final ArrayAdapter<String> deserialized = deserialize(serialized);

        assertThat(deserialized).containsExactly("alpha", "bravo", "charlie");
    }

    private static byte[] serialize(ArrayAdapter<String> adapter) throws IOException {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(bytes)) {
            objectOutputStream.writeObject(adapter);
        }
        return bytes.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static ArrayAdapter<String> deserialize(byte[] serialized) throws IOException, ClassNotFoundException {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            return (ArrayAdapter<String>) objectInputStream.readObject();
        }
    }
}
