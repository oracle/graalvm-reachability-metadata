/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_lang3;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.event.EventListenerSupport;
import org.junit.jupiter.api.Test;

public class EventListenerSupportTest {

    @Test
    public void serializationRoundTripKeepsSerializableListenersAndDropsNonSerializableListeners()
            throws IOException, ClassNotFoundException {
        EventListenerSupport<AuditListener> listenerSupport = EventListenerSupport.create(AuditListener.class);
        SerializableAuditListener serializableListener = new SerializableAuditListener("persisted");
        NonSerializableAuditListener nonSerializableListener = new NonSerializableAuditListener("transient");

        listenerSupport.addListener(serializableListener);
        listenerSupport.addListener(nonSerializableListener);
        listenerSupport.fire().onAudit("before-serialization");

        EventListenerSupport<AuditListener> deserializedListenerSupport = roundTrip(listenerSupport);
        AuditListener[] restoredListeners = deserializedListenerSupport.getListeners();

        assertThat(restoredListeners).hasSize(1);
        assertThat(restoredListeners[0]).isInstanceOf(SerializableAuditListener.class);

        SerializableAuditListener restoredListener = (SerializableAuditListener) restoredListeners[0];
        assertThat(restoredListener.events()).containsExactly("persisted:before-serialization");

        deserializedListenerSupport.fire().onAudit("after-deserialization");

        assertThat(restoredListener.events())
                .containsExactly("persisted:before-serialization", "persisted:after-deserialization");
        assertThat(nonSerializableListener.events()).containsExactly("transient:before-serialization");
    }

    @SuppressWarnings("unchecked")
    private static EventListenerSupport<AuditListener> roundTrip(final EventListenerSupport<AuditListener> listenerSupport)
            throws IOException, ClassNotFoundException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream)) {
            objectOutputStream.writeObject(listenerSupport);
        }

        try (ObjectInputStream objectInputStream = new ObjectInputStream(
                new ByteArrayInputStream(byteArrayOutputStream.toByteArray()))) {
            return (EventListenerSupport<AuditListener>) objectInputStream.readObject();
        }
    }

    public interface AuditListener {

        void onAudit(String message);
    }

    public static final class SerializableAuditListener implements AuditListener, Serializable {
        private static final long serialVersionUID = 1L;

        private final String name;
        private final List<String> events = new ArrayList<>();

        public SerializableAuditListener(final String name) {
            this.name = name;
        }

        @Override
        public void onAudit(final String message) {
            events.add(name + ":" + message);
        }

        public List<String> events() {
            return events;
        }
    }

    public static final class NonSerializableAuditListener implements AuditListener {

        private final String name;
        private final List<String> events = new ArrayList<>();

        public NonSerializableAuditListener(final String name) {
            this.name = name;
        }

        @Override
        public void onAudit(final String message) {
            events.add(name + ":" + message);
        }

        public List<String> events() {
            return events;
        }
    }
}
