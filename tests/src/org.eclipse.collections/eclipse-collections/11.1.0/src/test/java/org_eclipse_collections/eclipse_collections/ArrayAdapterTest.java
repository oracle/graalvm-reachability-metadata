/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_collections.eclipse_collections;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.eclipse.collections.impl.list.fixed.ArrayAdapter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ArrayAdapterTest {

    @Test
    void serializesAndDeserializesArrayAdapterElements() throws IOException, ClassNotFoundException {
        ArrayAdapter<String> adapter = ArrayAdapter.newArrayWith("alpha", "beta", "gamma");

        @SuppressWarnings("unchecked")
        ArrayAdapter<String> restored = (ArrayAdapter<String>) deserialize(serialize(adapter));

        assertThat(restored)
                .isInstanceOf(ArrayAdapter.class)
                .containsExactly("alpha", "beta", "gamma");
    }

    private static byte[] serialize(Object value) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
        }
        return bytes.toByteArray();
    }

    private static Object deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return input.readObject();
        }
    }
}
