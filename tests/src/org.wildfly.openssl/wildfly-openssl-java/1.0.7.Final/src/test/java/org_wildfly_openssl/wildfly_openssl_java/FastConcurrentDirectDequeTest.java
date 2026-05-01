/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_wildfly_openssl.wildfly_openssl_java;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.wildfly.openssl.util.FastConcurrentDirectDeque;

public class FastConcurrentDirectDequeTest {
    @Test
    void serializesAndDeserializesElementsInDequeOrder() throws Exception {
        FastConcurrentDirectDeque<String> original = new FastConcurrentDirectDeque<>(
                Arrays.asList("first", "second", "third"));
        original.addFirst("zero");
        original.addLast("fourth");

        FastConcurrentDirectDeque<String> restored = deserialize(serialize(original));

        assertThat(restored).isNotSameAs(original);
        assertThat(restored).containsExactly("zero", "first", "second", "third", "fourth");
        assertThat(restored.pollFirst()).isEqualTo("zero");
        assertThat(restored.pollLast()).isEqualTo("fourth");
        assertThat(restored).containsExactly("first", "second", "third");
    }

    private static byte[] serialize(Serializable value) throws Exception {
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectStream = new ObjectOutputStream(byteStream)) {
            objectStream.writeObject(value);
        }
        return byteStream.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static FastConcurrentDirectDeque<String> deserialize(byte[] value) throws Exception {
        try (ObjectInputStream objectStream = new ObjectInputStream(new ByteArrayInputStream(value))) {
            return (FastConcurrentDirectDeque<String>) objectStream.readObject();
        }
    }
}
