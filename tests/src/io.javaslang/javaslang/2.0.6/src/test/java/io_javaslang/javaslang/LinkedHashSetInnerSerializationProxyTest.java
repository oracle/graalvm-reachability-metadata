/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_javaslang.javaslang;

import javaslang.collection.LinkedHashSet;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

public class LinkedHashSetInnerSerializationProxyTest {

    @Test
    void serializesAndDeserializesNonEmptyLinkedHashSetThroughSerializationProxy() throws Exception {
        final LinkedHashSet<String> set = LinkedHashSet.of("alpha", "beta", "gamma", "beta");

        final LinkedHashSet<String> roundTripped = deserialize(serialize(set));

        assertThat(roundTripped).isNotSameAs(set);
        assertThat(roundTripped.size()).isEqualTo(set.size());
        assertThat(roundTripped).containsExactlyInAnyOrder("alpha", "beta", "gamma");
    }

    private static byte[] serialize(LinkedHashSet<String> set) throws IOException {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(set);
        }
        return bytes.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static LinkedHashSet<String> deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return (LinkedHashSet<String>) input.readObject();
        }
    }
}
