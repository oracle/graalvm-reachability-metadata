/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.Serializable;
import java.util.EventListener;

import org.apache.pulsar.shade.org.apache.commons.lang3.SerializationUtils;
import org.apache.pulsar.shade.org.apache.commons.lang3.event.EventListenerSupport;
import org.junit.jupiter.api.Test;

public class EventListenerSupportTest {
    @Test
    void proxiesEventsToListenersAndSurvivesSerialization() {
        final EventListenerSupport<MessageListener> support = EventListenerSupport.create(MessageListener.class);
        final RecordingListener listener = new RecordingListener();
        support.addListener(listener);
        support.fire().messageReceived("hello");

        assertThat(listener.message).isEqualTo("hello");
        final EventListenerSupport<MessageListener> restored = SerializationUtils.roundtrip(support);
        restored.fire().messageReceived("restored");
        assertThat(((RecordingListener) restored.getListeners()[0]).message).isEqualTo("restored");
    }

    public interface MessageListener extends EventListener, Serializable {
        void messageReceived(String message);
    }

    public static final class RecordingListener implements MessageListener {
        private static final long serialVersionUID = 1L;

        private String message;

        @Override
        public void messageReceived(String received) {
            message = received;
        }
    }
}
