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
import java.util.Arrays;

import javaslang.collection.Stream;
import org.junit.jupiter.api.Test;

public class StreamModuleInnerSerializationProxyTest {

    @Test
    public void serializesAndDeserializesElementsThroughSerializationProxy() throws Exception {
        Stream<String> original = Stream.of("alpha", "beta", "gamma");

        byte[] serialized = serialize(original);
        Object deserialized = deserialize(serialized);

        assertThat(serialized).isNotEmpty();
        assertThat(deserialized).isInstanceOf(Stream.class);
        assertThat(deserialized).isNotSameAs(original);
        assertThat(deserialized).isEqualTo(original);

        @SuppressWarnings("unchecked")
        Stream<String> deserializedStream = (Stream<String>) deserialized;
        assertThat(deserializedStream.toJavaList()).isEqualTo(Arrays.asList("alpha", "beta", "gamma"));
    }

    private static byte[] serialize(Object value) throws IOException {
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
