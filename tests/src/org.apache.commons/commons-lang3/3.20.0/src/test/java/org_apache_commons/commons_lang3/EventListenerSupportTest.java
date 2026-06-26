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
    public void serializationRetainsSerializableListenersAndDropsOtherListeners()
            throws IOException, ClassNotFoundException {
        EventListenerSupport<SampleListener> listenerSupport = EventListenerSupport.create(SampleListener.class);
        RecordingSampleListener serializableListener = new RecordingSampleListener("serializable");
        NonSerializableSampleListener nonSerializableListener = new NonSerializableSampleListener("non-serializable");

        listenerSupport.addListener(serializableListener);
        listenerSupport.addListener(nonSerializableListener);

        EventListenerSupport<SampleListener> restoredListenerSupport = roundTrip(listenerSupport);

        assertThat(restoredListenerSupport.getListeners()).hasSize(1);

        SampleListener restoredListener = restoredListenerSupport.getListeners()[0];
        restoredListenerSupport.fire().onEvent("payload");

        assertThat(restoredListener).isInstanceOf(RecordingSampleListener.class);
        assertThat(((RecordingSampleListener) restoredListener).deliveries()).containsExactly("serializable:payload");
        assertThat(nonSerializableListener.deliveries()).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private static EventListenerSupport<SampleListener> roundTrip(
            final EventListenerSupport<SampleListener> listenerSupport) throws IOException, ClassNotFoundException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream)) {
            objectOutputStream.writeObject(listenerSupport);
        }

        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(byteArrayOutputStream.toByteArray());
        try (ObjectInputStream objectInputStream = new ObjectInputStream(byteArrayInputStream)) {
            return (EventListenerSupport<SampleListener>) objectInputStream.readObject();
        }
    }

    public interface SampleListener {

        void onEvent(String message);
    }

    public static final class RecordingSampleListener implements SampleListener, Serializable {

        private static final long serialVersionUID = 1L;

        private final String name;
        private final List<String> deliveries = new ArrayList<>();

        public RecordingSampleListener(final String name) {
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

    public static final class NonSerializableSampleListener implements SampleListener {

        private final String name;
        private final List<String> deliveries = new ArrayList<>();

        public NonSerializableSampleListener(final String name) {
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
