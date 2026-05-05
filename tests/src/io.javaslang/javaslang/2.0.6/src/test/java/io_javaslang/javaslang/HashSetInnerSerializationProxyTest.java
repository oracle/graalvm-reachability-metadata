/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_javaslang.javaslang;

import javaslang.collection.HashSet;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

public class HashSetInnerSerializationProxyTest {

    @Test
    void serializesAndDeserializesNonEmptyHashSetThroughSerializationProxy() throws Exception {
        final HashSet<String> set = HashSet.of("alpha", "beta", "gamma", "alpha");

        final HashSet<String> roundTripped = deserialize(serialize(set));

        assertThat(roundTripped).isEqualTo(set);
        assertThat(roundTripped).isNotSameAs(set);
        assertThat(roundTripped).containsExactlyInAnyOrder("alpha", "beta", "gamma");
    }

    private static byte[] serialize(HashSet<String> set) throws IOException {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(set);
        }
        return bytes.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static HashSet<String> deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return (HashSet<String>) input.readObject();
        }
    }
}
