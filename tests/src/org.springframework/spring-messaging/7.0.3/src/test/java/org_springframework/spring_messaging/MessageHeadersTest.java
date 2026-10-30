/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_messaging;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.MessageHeaders;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public class MessageHeadersTest {

    @Test
    void preservesSerializableHeadersAcrossSerialization() throws IOException, ClassNotFoundException {
        MessageHeaders headers = new MessageHeaders(Map.of("destination", "orders", "attempt", 3));

        MessageHeaders restored = roundTrip(headers);

        assertEquals(headers, restored);
        assertEquals("orders", restored.get("destination", String.class));
        assertEquals(Integer.valueOf(3), restored.get("attempt", Integer.class));
        assertNotNull(restored.getId());
        assertEquals(headers.getId(), restored.getId());
        assertEquals(headers.getTimestamp(), restored.getTimestamp());
    }

    @Test
    void omitsNonSerializableHeadersWithoutChangingOriginal() throws IOException, ClassNotFoundException {
        Object localContext = new Object();
        MessageHeaders headers = new MessageHeaders(Map.of(
                "destination", "orders", "attempt", 3, "localContext", localContext));
        Map<String, Object> expected = new HashMap<>(headers);
        expected.remove("localContext");

        MessageHeaders restored = roundTrip(headers);

        assertEquals(expected, restored);
        assertFalse(restored.containsKey("localContext"));
        assertEquals("orders", restored.get("destination", String.class));
        assertEquals(Integer.valueOf(3), restored.get("attempt", Integer.class));
        assertEquals(headers.getId(), restored.getId());
        assertEquals(headers.getTimestamp(), restored.getTimestamp());
        assertSame(localContext, headers.get("localContext"));
    }

    private static MessageHeaders roundTrip(MessageHeaders headers) throws IOException, ClassNotFoundException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(headers);
        }
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (MessageHeaders) input.readObject();
        }
    }
}
