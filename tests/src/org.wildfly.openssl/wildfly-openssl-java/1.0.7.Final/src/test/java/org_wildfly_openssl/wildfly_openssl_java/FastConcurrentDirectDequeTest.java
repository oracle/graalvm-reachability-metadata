/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_wildfly_openssl.wildfly_openssl_java;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.junit.jupiter.api.Test;
import org.wildfly.openssl.util.FastConcurrentDirectDeque;

import static org.assertj.core.api.Assertions.assertThat;

public class FastConcurrentDirectDequeTest {
    @Test
    void serializationRoundTripPreservesElementsInDequeOrder() throws IOException, ClassNotFoundException {
        FastConcurrentDirectDeque<String> original = new FastConcurrentDirectDeque<>();
        original.addLast("first");
        original.addLast("second");
        original.addFirst("zero");

        byte[] serialized = serialize(original);
        FastConcurrentDirectDeque<String> restored = deserialize(serialized);

        assertThat(restored).containsExactly("zero", "first", "second");
        assertThat(restored.pollFirst()).isEqualTo("zero");
        assertThat(restored.pollLast()).isEqualTo("second");
        assertThat(restored).containsExactly("first");
    }

    private static byte[] serialize(FastConcurrentDirectDeque<String> deque) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(deque);
        }
        return bytes.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static FastConcurrentDirectDeque<String> deserialize(byte[] serialized)
        throws IOException, ClassNotFoundException {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            return (FastConcurrentDirectDeque<String>) input.readObject();
        }
    }
}
