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
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.core.serializer.DefaultDeserializer;

public class DefaultDeserializerTest {

    @Test
    void deserializesObjectFromInputStream() throws Exception {
        Map<String, Integer> payload = new LinkedHashMap<>();
        payload.put("spring", 5);
        payload.put("core", 3);

        Object deserialized = new DefaultDeserializer().deserialize(new ByteArrayInputStream(serialize(payload)));

        assertThat(deserialized).isEqualTo(payload);
    }

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
