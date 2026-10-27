/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.apache.pulsar.shade.org.apache.commons.lang3.event.EventUtils;
import org.junit.jupiter.api.Test;

public class EventUtilsTest {
    @Test
    void bindsListenerEventsToTargetMethod() {
        MessageSource source = new MessageSource();
        MessageTarget target = new MessageTarget();
        EventUtils.bindEventsToMethod(target, "record", source, MessageListener.class, "received");

        source.publish("pulsar");
        source.publish("bookkeeper");

        assertThat(target.messages).containsExactly("pulsar", "bookkeeper");
    }

    public interface MessageListener {
        void received(String message);
    }

    public static class MessageSource {
        private final List<MessageListener> listeners = new ArrayList<>();

        public void addMessageListener(MessageListener listener) {
            listeners.add(listener);
        }

        public void publish(String message) {
            listeners.forEach(listener -> listener.received(message));
        }
    }

    public static class MessageTarget {
        private final List<String> messages = new ArrayList<>();

        public void record(String message) {
            messages.add(message);
        }
    }
}
