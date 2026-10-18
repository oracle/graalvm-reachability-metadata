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

public class SubscriberTest {

    @Test
    void invokesAnAnnotatedSubscriber() {
        final EventBus bus = new EventBus("pulsar-events");
        final MessageListener listener = new MessageListener();
        bus.register(listener);

        bus.post("created");

        assertThat(listener.events).containsExactly("created");
    }

    public static final class MessageListener {
        private final List<String> events = new ArrayList<>();

        @Subscribe
        public void onMessage(String event) {
            events.add(event);
        }
    }
}
