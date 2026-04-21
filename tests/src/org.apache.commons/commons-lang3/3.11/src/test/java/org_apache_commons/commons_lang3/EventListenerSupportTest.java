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
    public void serializationRoundTripDropsNonSerializableListenersAndRecreatesTransientFields()
            throws IOException, ClassNotFoundException {
        EventListenerSupport<SampleListener> listenerSupport = EventListenerSupport.create(SampleListener.class);
        SerializableSampleListener serializableListener = new SerializableSampleListener();
        SampleListener originalProxy = listenerSupport.fire();

        listenerSupport.addListener(serializableListener);
        listenerSupport.addListener(new NonSerializableSampleListener());

        SampleListener[] originalListeners = listenerSupport.getListeners();
        assertThat(originalListeners).hasSize(2);
        assertThat(originalListeners.getClass().getComponentType()).isEqualTo(SampleListener.class);
        assertThat(originalProxy).isNotNull();

        EventListenerSupport<SampleListener> restored = roundTrip(listenerSupport);

        SampleListener[] restoredListeners = restored.getListeners();
        assertThat(restoredListeners).hasSize(1);
        assertThat(restoredListeners.getClass().getComponentType()).isEqualTo(SampleListener.class);
        assertThat(restoredListeners[0]).isInstanceOf(SerializableSampleListener.class);
        assertThat(((SerializableSampleListener) restoredListeners[0]).messages()).isEmpty();
        assertThat(restored.fire()).isNotNull().isNotSameAs(originalProxy);
    }

    @SuppressWarnings("unchecked")
    private EventListenerSupport<SampleListener> roundTrip(final EventListenerSupport<SampleListener> listenerSupport)
            throws IOException, ClassNotFoundException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
            objectOutputStream.writeObject(listenerSupport);
        }

        try (ObjectInputStream objectInputStream =
                new ObjectInputStream(new ByteArrayInputStream(outputStream.toByteArray()))) {
            return (EventListenerSupport<SampleListener>) objectInputStream.readObject();
        }
    }

    public interface SampleListener {

        void onEvent(String message);
    }

    public static final class SerializableSampleListener implements SampleListener, Serializable {

        private static final long serialVersionUID = 1L;

        private final List<String> messages = new ArrayList<>();

        @Override
        public void onEvent(final String message) {
            messages.add(message);
        }

        public List<String> messages() {
            return messages;
        }
    }

    public static final class NonSerializableSampleListener implements SampleListener {

        @Override
        public void onEvent(final String message) {
        }
    }
}
