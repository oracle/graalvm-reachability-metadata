/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.Serializable;

import org.apache.pulsar.shade.org.apache.commons.lang3.event.EventListenerSupport;
import org.junit.jupiter.api.Test;

public class EventListenerSupportTest {
    @Test
    void roundTripPreservesSerializableListeners() throws Exception {
        EventListenerSupport<MessageListener> input = EventListenerSupport.create(MessageListener.class);
        input.addListener(new RecordingListener());

        EventListenerSupport<MessageListener> restored = SerializationSupport.roundTrip(input);
        restored.fire().messageReceived("restored");

        assertThat(restored.getListeners()).hasSize(1);
        assertThat(((RecordingListener) restored.getListeners()[0]).message).isEqualTo("restored");
    }

    public interface MessageListener {
        void messageReceived(String message);
    }

    public static class RecordingListener implements MessageListener, Serializable {
        private String message;

        @Override
        public void messageReceived(String received) {
            message = received;
        }
    }
}
