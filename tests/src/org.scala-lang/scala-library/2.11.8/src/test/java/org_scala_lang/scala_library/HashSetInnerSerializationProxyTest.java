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

import scala.collection.immutable.HashSet;

import static org.assertj.core.api.Assertions.assertThat;

public class HashSetInnerSerializationProxyTest {

    @Test
    void restoresImmutableSetElements() throws Exception {
        HashSet<String> original = new HashSet<>();
        original = original.$plus("alpha");
        original = original.$plus("beta");

        HashSet<String> restored = roundTrip(original);

        assertThat(restored).isNotSameAs(original);
        assertThat(restored.size()).isEqualTo(2);
        assertThat(restored.contains("alpha")).isTrue();
        assertThat(restored.contains("beta")).isTrue();
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
