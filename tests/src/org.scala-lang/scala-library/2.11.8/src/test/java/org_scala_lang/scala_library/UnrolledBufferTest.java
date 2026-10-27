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

import scala.collection.mutable.UnrolledBuffer;
import scala.reflect.ClassTag$;

import static org.assertj.core.api.Assertions.assertThat;

public class UnrolledBufferTest {

    @Test
    void restoresBufferedElementsInSequence() throws Exception {
        UnrolledBuffer<String> original = new UnrolledBuffer<>(ClassTag$.MODULE$.apply(String.class));
        original.$plus$eq("alpha");
        original.$plus$eq("beta");

        UnrolledBuffer<String> restored = roundTrip(original);

        assertThat(restored.length()).isEqualTo(2);
        assertThat(restored.apply(0)).isEqualTo("alpha");
        assertThat(restored.apply(1)).isEqualTo("beta");
    }

    @SuppressWarnings("unchecked")
    private static <T> T roundTrip(T value) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
        }

        try (ObjectInputStream input = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))) {
            return (T) input.readObject();
        }
    }
}
