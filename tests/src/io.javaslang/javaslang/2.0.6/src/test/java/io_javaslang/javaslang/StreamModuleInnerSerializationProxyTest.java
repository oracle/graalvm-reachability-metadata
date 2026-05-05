/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_javaslang.javaslang;

import javaslang.collection.Stream;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

public class StreamModuleInnerSerializationProxyTest {

    @Test
    void serializesAndDeserializesNonEmptyStreamThroughSerializationProxy() throws Exception {
        final Stream<String> stream = Stream.of("alpha", "beta").append("gamma");

        final Stream<String> roundTripped = deserialize(serialize(stream));

        assertThat(roundTripped).isEqualTo(stream);
        assertThat(roundTripped).isNotSameAs(stream);
        assertThat(roundTripped).containsExactly("alpha", "beta", "gamma");
    }

    private static byte[] serialize(Stream<String> stream) throws IOException {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(stream);
        }
        return bytes.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static Stream<String> deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return (Stream<String>) input.readObject();
        }
    }
}
