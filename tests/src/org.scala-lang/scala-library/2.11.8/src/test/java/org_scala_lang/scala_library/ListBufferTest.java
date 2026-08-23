/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scala_lang.scala_library;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.junit.jupiter.api.Test;

import scala.collection.mutable.ListBuffer;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises supported {@link ListBuffer} serialization. §FS-repository-functional-spec.5.2 */
public class ListBufferTest {

    @Test
    void serializesAndDeserializesElementsInOrder() throws Exception {
        ListBuffer<String> original = new ListBuffer<>();
        original.$plus$eq("first");
        original.$plus$eq("second");

        byte[] serialized = serialize(original);
        Object restoredObject;
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            restoredObject = input.readObject();
        }

        assertThat(restoredObject).isInstanceOf(ListBuffer.class);
        @SuppressWarnings("unchecked")
        ListBuffer<String> restored = (ListBuffer<String>) restoredObject;
        assertThat(restored.length()).isEqualTo(2);
        assertThat(restored.apply(0)).isEqualTo("first");
        assertThat(restored.apply(1)).isEqualTo("second");

        restored.$plus$eq("third");
        assertThat(restored.apply(2)).isEqualTo("third");
    }

    private static byte[] serialize(ListBuffer<String> buffer) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(buffer);
        }
        return bytes.toByteArray();
    }
}
