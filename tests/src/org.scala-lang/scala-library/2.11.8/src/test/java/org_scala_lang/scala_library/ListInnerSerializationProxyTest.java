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

import scala.collection.immutable.List;
import scala.collection.mutable.ListBuffer;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises the immutable list serialization proxy. \u00A7FS-repository-functional-spec.5.2 */
public class ListInnerSerializationProxyTest {

    @Test
    void serializesAndDeserializesElementsInOrder() throws Exception {
        ListBuffer<String> elements = new ListBuffer<>();
        elements.$plus$eq("first");
        elements.$plus$eq("second");
        List<String> original = elements.toList();

        List<String> restored = roundTrip(original);

        assertThat(restored).isEqualTo(original);
        assertThat(restored.length()).isEqualTo(2);
        assertThat(restored.apply(0)).isEqualTo("first");
        assertThat(restored.apply(1)).isEqualTo("second");
    }

    @SuppressWarnings("unchecked")
    private static List<String> roundTrip(List<String> list) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(list);
        }

        try (ObjectInputStream input = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))) {
            return (List<String>) input.readObject();
        }
    }
}
