/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;

import org.junit.jupiter.api.Test;
import org.springframework.core.serializer.DefaultDeserializer;

public class DefaultDeserializerTest {

    @Test
    void deserializesObjectUsingProvidedClassLoader() throws Exception {
        String payload = "spring-core deserializer payload";
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

        Object deserialized = new DefaultDeserializer(classLoader).deserialize(new ByteArrayInputStream(serialize(payload)));

        assertThat(deserialized).isEqualTo(payload);
    }

    private static byte[] serialize(Object payload) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
            objectOutputStream.writeObject(payload);
        }
        return outputStream.toByteArray();
    }
}
