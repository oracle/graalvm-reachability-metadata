/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_javaslang.javaslang;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import javaslang.collection.LinkedHashSet;
import org.junit.jupiter.api.Test;

public class LinkedHashSetInnerSerializationProxyTest {

    @Test
    public void serializesAndDeserializesElementsThroughSerializationProxy() throws Exception {
        LinkedHashSet<String> original = LinkedHashSet.of("alpha", "beta", "gamma", "alpha");

        byte[] serialized = serialize(original);
        Object deserialized = deserialize(serialized);

        assertThat(serialized).isNotEmpty();
        assertThat(deserialized).isInstanceOf(LinkedHashSet.class);
        assertThat(deserialized).isNotSameAs(original);
        assertThat(deserialized).isEqualTo(original);

        @SuppressWarnings("unchecked")
        LinkedHashSet<String> deserializedSet = (LinkedHashSet<String>) deserialized;
        assertThat(deserializedSet).hasSize(3);
        assertThat(deserializedSet).containsExactlyInAnyOrder("alpha", "beta", "gamma");
    }

    private static byte[] serialize(Serializable value) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream stream = new ObjectOutputStream(bytes)) {
            stream.writeObject(value);
        }
        return bytes.toByteArray();
    }

    private static Object deserialize(byte[] serialized) throws IOException, ClassNotFoundException {
        try (ObjectInputStream stream = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            return stream.readObject();
        }
    }
}
