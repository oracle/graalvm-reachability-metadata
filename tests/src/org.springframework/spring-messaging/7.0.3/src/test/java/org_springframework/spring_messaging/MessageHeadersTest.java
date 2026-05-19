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
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.messaging.MessageHeaders;

public class MessageHeadersTest {

    @Test
    void serializeFiltersOutNonSerializableHeaders() throws Exception {
        Map<String, Object> rawHeaders = new HashMap<>();
        rawHeaders.put("serializableHeader", "kept");
        rawHeaders.put("numericHeader", 42);
        rawHeaders.put("nonSerializableHeader", new Object());
        MessageHeaders headers = new MessageHeaders(rawHeaders);

        MessageHeaders deserialized = deserialize(serialize(headers));

        assertThat(deserialized.get("serializableHeader")).isEqualTo("kept");
        assertThat(deserialized.get("numericHeader")).isEqualTo(42);
        assertThat(deserialized.containsKey("nonSerializableHeader")).isFalse();
        assertThat(deserialized.getId()).isEqualTo(headers.getId());
        assertThat(deserialized.getTimestamp()).isEqualTo(headers.getTimestamp());
    }

    private static byte[] serialize(Object object) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(object);
        }
        return bytes.toByteArray();
    }

    private static MessageHeaders deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return (MessageHeaders) input.readObject();
        }
    }
}
