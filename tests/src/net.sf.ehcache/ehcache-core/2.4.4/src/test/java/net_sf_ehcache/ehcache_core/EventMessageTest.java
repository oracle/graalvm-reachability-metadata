/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_ehcache.ehcache_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import net.sf.ehcache.Element;
import net.sf.ehcache.distribution.EventMessage;

import org.junit.jupiter.api.Test;

public class EventMessageTest {
    @Test
    void serializesAndRestoresElementEventMessage() throws IOException, ClassNotFoundException {
        Element element = new Element("cache-key", "cache-value", 42L);
        EventMessage message = new EventMessage(EventMessage.PUT, "message-key", element);

        byte[] serialized = serialize(message);
        EventMessage restored = deserialize(serialized);

        assertThat(restored).isNotSameAs(message);
        assertThat(restored.getEvent()).isEqualTo(EventMessage.PUT);
        assertThat(restored.getSerializableKey()).isEqualTo("message-key");
        assertThat(restored.isValid()).isTrue();
        assertThat(restored.getElement()).isNotSameAs(element);
        assertThat(restored.getElement().getObjectKey()).isEqualTo("cache-key");
        assertThat(restored.getElement().getObjectValue()).isEqualTo("cache-value");
        assertThat(restored.getElement().getVersion()).isEqualTo(42L);
    }

    @Test
    void serializesAndRestoresNullElementEventMessage() throws IOException, ClassNotFoundException {
        EventMessage message = new EventMessage(EventMessage.REMOVE, "cache-key", null);

        byte[] serialized = serialize(message);
        EventMessage restored = deserialize(serialized);

        assertThat(restored).isNotSameAs(message);
        assertThat(restored.getEvent()).isEqualTo(EventMessage.REMOVE);
        assertThat(restored.getSerializableKey()).isEqualTo("cache-key");
        assertThat(restored.isValid()).isTrue();
        assertThat(restored.getElement()).isNull();
    }

    private byte[] serialize(EventMessage message) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(bytes)) {
            objectOutputStream.writeObject(message);
        }
        return bytes.toByteArray();
    }

    private EventMessage deserialize(byte[] serialized) throws IOException, ClassNotFoundException {
        ByteArrayInputStream bytes = new ByteArrayInputStream(serialized);
        try (ObjectInputStream objectInputStream = new ObjectInputStream(bytes)) {
            return (EventMessage) objectInputStream.readObject();
        }
    }
}
