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
import org.apache.pulsar.shade.com.google.common.eventbus.EventBus;
import org.apache.pulsar.shade.com.google.common.eventbus.Subscribe;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(55)
public class SubscriberTest {
    @Test
    void discoversAndInvokesAnnotatedSubscriber() {
        EventBus eventBus = new EventBus("pulsar-events");
        MessageListener listener = new MessageListener();
        eventBus.register(listener);

        eventBus.post("connected");
        eventBus.post("ready");

        assertThat(listener.messages).containsExactly("connected", "ready");
        eventBus.unregister(listener);
    }

    public static final class MessageListener {
        private final List<String> messages = new ArrayList<>();

        @Subscribe
        public void onMessage(String message) {
            messages.add(message);
        }
    }
}
