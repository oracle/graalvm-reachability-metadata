/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mozilla.rhino;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.junit.jupiter.api.Test;
import org.mozilla.javascript.ObjArray;

public class ObjArrayTest {

    @Test
    void preservesInlineAndExpandedElementsAcrossSerialization() throws Exception {
        final Object[] values = { "first", "second", null, "fourth", "fifth", "sixth", "seventh" };
        final ObjArray array = new ObjArray();
        for (final Object value : values) {
            array.add(value);
        }
        array.seal();

        final ObjArray restored = deserialize(serialize(array));

        assertThat(restored.isSealed()).isTrue();
        assertThat(restored.size()).isEqualTo(values.length);
        assertThat(restored.toArray()).containsExactly(values);
    }

    private static byte[] serialize(final ObjArray array) throws IOException {
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
            objectOutputStream.writeObject(array);
        }

        return outputStream.toByteArray();
    }

    private static ObjArray deserialize(final byte[] bytes) throws IOException, ClassNotFoundException {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return (ObjArray) objectInputStream.readObject();
        }
    }
}
