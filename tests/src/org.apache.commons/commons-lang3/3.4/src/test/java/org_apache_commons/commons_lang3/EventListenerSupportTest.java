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
    public void serializationRoundTripRetainsSerializableListenersAndRebuildsProxy() throws Exception {
        EventListenerSupport<SampleListener> listenerSupport = EventListenerSupport.create(SampleListener.class);
        SerializableSampleListener serializableListener = new SerializableSampleListener();
        NonSerializableSampleListener nonSerializableListener = new NonSerializableSampleListener();
        listenerSupport.addListener(serializableListener);
        listenerSupport.addListener(nonSerializableListener);

        listenerSupport.fire().messageReceived("before");

        EventListenerSupport<SampleListener> restored = deserialize(serialize(listenerSupport));
        SampleListener[] restoredListeners = restored.getListeners();

        assertThat(restoredListeners).hasSize(1);
        assertThat(restoredListeners[0]).isInstanceOf(SerializableSampleListener.class);
        SerializableSampleListener restoredListener = (SerializableSampleListener) restoredListeners[0];
        assertThat(restoredListener.messages()).containsExactly("before");

        restored.fire().messageReceived("after");

        assertThat(restoredListener.messages()).containsExactly("before", "after");
        assertThat(nonSerializableListener.messages()).containsExactly("before");
    }

    private static byte[] serialize(final Object value) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream)) {
            objectOutputStream.writeObject(value);
        }
        return byteArrayOutputStream.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static EventListenerSupport<SampleListener> deserialize(final byte[] bytes)
            throws IOException, ClassNotFoundException {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return (EventListenerSupport<SampleListener>) objectInputStream.readObject();
        }
    }

    public interface SampleListener {
        void messageReceived(String message);
    }

    public static final class SerializableSampleListener implements SampleListener, Serializable {
        private static final long serialVersionUID = 1L;

        private final List<String> messages = new ArrayList<String>();

        @Override
        public void messageReceived(final String message) {
            messages.add(message);
        }

        public List<String> messages() {
            return messages;
        }
    }

    public static final class NonSerializableSampleListener implements SampleListener {
        private final List<String> messages = new ArrayList<String>();

        @Override
        public void messageReceived(final String message) {
            messages.add(message);
        }

        public List<String> messages() {
            return messages;
        }
    }
}
