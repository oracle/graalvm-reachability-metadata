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

import javaslang.collection.Stream;
import org.junit.jupiter.api.Test;

public class StreamModuleInnerSerializationProxyTest {

    @Test
    void roundTripsNonEmptyStreamThroughSerializationProxy() throws Exception {
        final Stream<String> source = Stream.of("alpha", "beta", "gamma");

        final Object restored = deserialize(serialize((Serializable) source));

        assertThat(restored).isInstanceOf(Stream.class).isEqualTo(source).isNotSameAs(source);
        @SuppressWarnings("unchecked")
        final Stream<String> restoredStream = (Stream<String>) restored;
        assertThat(restoredStream.length()).isEqualTo(3);
        assertThat(restoredStream.toJavaList()).containsExactly("alpha", "beta", "gamma");
    }

    private static byte[] serialize(final Serializable value) throws IOException {
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
            objectOutputStream.writeObject(value);
        }
        return outputStream.toByteArray();
    }

    private static Object deserialize(final byte[] bytes)
            throws IOException, ClassNotFoundException {
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);
        try (ObjectInputStream objectInputStream = new ObjectInputStream(inputStream)) {
            return objectInputStream.readObject();
        }
    }
}
