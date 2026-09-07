/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.apache.commons.lang3.SerializationUtils;
import org.testcontainers.shaded.org.apache.commons.lang3.event.EventListenerSupport;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

public class EventListenerSupportTest {
    @Test
    void broadcastsEventsAndRetainsSerializableListeners() {
        EventListenerSupport<MessageListener> listeners = EventListenerSupport.create(MessageListener.class);
        RecordingListener listener = new RecordingListener();
        listeners.addListener(listener);

        listeners.fire().message("first");
        assertThat(listener.lastMessage.get()).isEqualTo("first");
        assertThat(listeners.getListeners()).containsExactly(listener);

        EventListenerSupport<MessageListener> restored = SerializationUtils.roundtrip(listeners);
        restored.fire().message("restored");
        assertThat(((RecordingListener) restored.getListeners()[0]).lastMessage.get()).isEqualTo("restored");
    }

    public interface MessageListener extends Serializable {
        void message(String value);
    }

    public static class RecordingListener implements MessageListener {
        private static final long serialVersionUID = 1L;
        private final AtomicReference<String> lastMessage = new AtomicReference<>();

        @Override
        public void message(String value) {
            lastMessage.set(value);
        }
    }
}
