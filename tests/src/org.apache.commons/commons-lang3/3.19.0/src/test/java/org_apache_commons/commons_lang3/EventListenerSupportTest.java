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
    public void serializationRoundTripFiltersNonSerializableListeners() throws IOException, ClassNotFoundException {
        EventListenerSupport<SampleListener> original = EventListenerSupport.create(SampleListener.class);
        SerializableRecordingListener serializableListener = new SerializableRecordingListener("kept");
        NonSerializableRecordingListener nonSerializableListener = new NonSerializableRecordingListener("dropped");
        original.addListener(serializableListener);
        original.addListener(nonSerializableListener);

        byte[] serialized = serialize(original);
        EventListenerSupport<SampleListener> deserialized = deserialize(serialized);

        SampleListener[] listeners = deserialized.getListeners();
        assertThat(listeners).hasSize(1);
        assertThat(listeners[0]).isInstanceOf(SerializableRecordingListener.class);

        deserialized.fire().onEvent("payload");

        SerializableRecordingListener retainedListener = (SerializableRecordingListener) listeners[0];
        assertThat(retainedListener.deliveries()).containsExactly("kept:payload");
        assertThat(nonSerializableListener.deliveries()).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private static EventListenerSupport<SampleListener> deserialize(final byte[] serialized)
            throws IOException, ClassNotFoundException {
        try (ObjectInputStream inputStream = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            Object listenerSupport = inputStream.readObject();
            return (EventListenerSupport<SampleListener>) listenerSupport;
        }
    }

    private static byte[] serialize(final EventListenerSupport<SampleListener> listenerSupport) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ObjectOutputStream outputStream = new ObjectOutputStream(output)) {
            outputStream.writeObject(listenerSupport);
        }
        return output.toByteArray();
    }

    public interface SampleListener {

        void onEvent(String message);
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
}
