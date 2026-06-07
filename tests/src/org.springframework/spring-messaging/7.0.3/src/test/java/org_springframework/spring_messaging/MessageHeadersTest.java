/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.MessageHeaders;

public class MessageHeadersTest {
    @Test
    void serializationOmitsNonSerializableHeaderValues() throws Exception {
        Map<String, Object> headerValues = new HashMap<>();
        headerValues.put("serializable", "kept");
        headerValues.put("nonSerializable", new Object());
        MessageHeaders headers = new MessageHeaders(headerValues);

        MessageHeaders deserializedHeaders = serializeAndDeserialize(headers);

        assertThat(deserializedHeaders.get("serializable")).isEqualTo("kept");
        assertThat(deserializedHeaders.get("nonSerializable")).isNull();
        assertThat(deserializedHeaders.getId()).isEqualTo(headers.getId());
        assertThat(deserializedHeaders.getTimestamp()).isEqualTo(headers.getTimestamp());
    }

    private static MessageHeaders serializeAndDeserialize(MessageHeaders headers) throws Exception {
        byte[] serializedHeaders;
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(headers);
            output.flush();
            serializedHeaders = bytes.toByteArray();
        }

        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serializedHeaders))) {
            Object deserialized = input.readObject();
            assertThat(deserialized).isInstanceOf(MessageHeaders.class);
            return (MessageHeaders) deserialized;
        }
    }
}
