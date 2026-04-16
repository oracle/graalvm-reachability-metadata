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
    void firesEventsToAllRegisteredListenersThroughTheProxy() {
        final EventListenerSupport<SampleListener> support = EventListenerSupport.create(SampleListener.class);
        final RecordingListener first = new RecordingListener();
        final RecordingListener second = new RecordingListener();

        support.addListener(first);
        support.addListener(second);

        support.fire().onEvent("alpha");

        assertThat(first.events()).containsExactly("alpha");
        assertThat(second.events()).containsExactly("alpha");
        assertThat(support.getListeners()).containsExactly(first, second);
    }

    @Test
    void serializesOnlySerializableListenersAndRestoresTransientFieldsOnRead()
            throws IOException, ClassNotFoundException {
        final EventListenerSupport<SampleListener> support = EventListenerSupport.create(SampleListener.class);
        final RecordingListener serializableListener = new RecordingListener();
        final NonSerializableListener nonSerializableListener = new NonSerializableListener();

        support.addListener(serializableListener);
        support.addListener(nonSerializableListener);

        final byte[] serializedSupport = serialize(support);
        final EventListenerSupport<SampleListener> deserializedSupport = deserialize(serializedSupport);
        final SampleListener[] restoredListeners = deserializedSupport.getListeners();

        assertThat(restoredListeners).hasSize(1);
        assertThat(restoredListeners[0]).isInstanceOf(RecordingListener.class);

        final RecordingListener restoredListener = (RecordingListener) restoredListeners[0];
        deserializedSupport.fire().onEvent("beta");

        assertThat(restoredListener.events()).containsExactly("beta");
    }

    private static byte[] serialize(final EventListenerSupport<SampleListener> support) throws IOException {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutput = new ObjectOutputStream(output)) {
            objectOutput.writeObject(support);
        }
        return output.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static EventListenerSupport<SampleListener> deserialize(final byte[] serializedSupport)
            throws IOException, ClassNotFoundException {
        final ByteArrayInputStream input = new ByteArrayInputStream(serializedSupport);
        try (ObjectInputStream objectInput = new ObjectInputStream(input)) {
            return (EventListenerSupport<SampleListener>) objectInput.readObject();
        }
    }

    public interface SampleListener {
        void onEvent(String event);
    }

    private static final class RecordingListener implements SampleListener, Serializable {
        private static final long serialVersionUID = 1L;

        private final List<String> events = new ArrayList<>();

        @Override
        public void onEvent(final String event) {
            events.add(event);
        }

        private List<String> events() {
            return events;
        }
    }

    private static final class NonSerializableListener implements SampleListener {
        @Override
        public void onEvent(final String event) {
        }
    }
}
