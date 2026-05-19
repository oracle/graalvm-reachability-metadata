/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_amqp.spring_amqp;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.Set;

import org.junit.jupiter.api.Test;

import org.springframework.amqp.utils.SerializationUtils;

public class SerializationUtilsTest {

    @Test
    void serializesAndDeserializesUsingObjectInputStream() throws IOException {
        byte[] serialized = SerializationUtils.serialize("spring-amqp");

        assertThat(serialized).isNotNull();
        try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            Object deserialized = SerializationUtils.deserialize(objectInputStream);

            assertThat(deserialized).isEqualTo("spring-amqp");
        }
    }

    @Test
    void deserializesAllowedClassUsingInputStreamOverload() throws IOException {
        byte[] serialized = SerializationUtils.serialize("trusted payload");

        Object deserialized = SerializationUtils.deserialize(
                new ByteArrayInputStream(serialized),
                Set.of(),
                Thread.currentThread().getContextClassLoader());

        assertThat(deserialized).isEqualTo("trusted payload");
    }
}
