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
    void roundTripsNonEmptyLinkedHashSetThroughSerializationProxy() throws Exception {
        final LinkedHashSet<String> source = LinkedHashSet.of("alpha", "beta", "gamma", "beta");

        final Object restored = deserialize(serialize(source));

        assertThat(restored).isInstanceOf(LinkedHashSet.class).isEqualTo(source).isNotSameAs(source);
        @SuppressWarnings("unchecked")
        final LinkedHashSet<String> restoredSet = (LinkedHashSet<String>) restored;
        assertThat(restoredSet.length()).isEqualTo(3);
        assertThat(restoredSet.toJavaList()).containsExactlyInAnyOrder("alpha", "beta", "gamma");
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
