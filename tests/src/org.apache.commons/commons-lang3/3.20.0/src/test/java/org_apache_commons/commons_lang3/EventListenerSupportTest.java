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
    public void serializationRoundTripKeepsSerializableListenersAndDropsOthers() throws IOException,
            ClassNotFoundException {
        EventListenerSupport<SampleListener> listenerSupport = EventListenerSupport.create(SampleListener.class);
        SerializableRecordingListener serializableListener = new SerializableRecordingListener("serializable");
        NonSerializableRecordingListener nonSerializableListener = new NonSerializableRecordingListener("transient");

        listenerSupport.addListener(serializableListener);
        listenerSupport.addListener(nonSerializableListener);

        EventListenerSupport<SampleListener> deserialized = roundTrip(listenerSupport);

        SampleListener[] retainedListeners = deserialized.getListeners();
        assertThat(retainedListeners).hasSize(1);
        assertThat(retainedListeners[0]).isInstanceOf(SerializableRecordingListener.class);

        deserialized.fire().onEvent("payload");

        SerializableRecordingListener retainedListener = (SerializableRecordingListener) retainedListeners[0];
        assertThat(retainedListener.deliveries()).containsExactly("serializable:payload");
        assertThat(nonSerializableListener.deliveries()).isEmpty();
    }

    private static EventListenerSupport<SampleListener> roundTrip(
            final EventListenerSupport<SampleListener> listenerSupport) throws IOException, ClassNotFoundException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(output)) {
            objectOutputStream.writeObject(listenerSupport);
        }
        ByteArrayInputStream input = new ByteArrayInputStream(output.toByteArray());
        try (ObjectInputStream objectInputStream = new ObjectInputStream(input)) {
            @SuppressWarnings("unchecked")
            EventListenerSupport<SampleListener> deserialized =
                    (EventListenerSupport<SampleListener>) objectInputStream.readObject();
            return deserialized;
        }
    }

    public interface SampleListener {

        void onEvent(String message);
    }

    public static final class SerializableRecordingListener implements SampleListener, Serializable {
        private static final long serialVersionUID = 1L;

        private final String name;
        private final List<String> deliveries = new ArrayList<>();

        public SerializableRecordingListener(final String name) {
            this.name = name;
        }

        @Override
        public void onEvent(final String message) {
            deliveries.add(name + ":" + message);
        }

        public List<String> deliveries() {
            return deliveries;
        }
    }

    public static final class NonSerializableRecordingListener implements SampleListener {

        private final String name;
        private final List<String> deliveries = new ArrayList<>();

        public NonSerializableRecordingListener(final String name) {
            this.name = name;
        }

        @Override
        public void onEvent(final String message) {
            deliveries.add(name + ":" + message);
        }

        public List<String> deliveries() {
            return deliveries;
        }
    }
}
