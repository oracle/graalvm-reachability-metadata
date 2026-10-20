/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.com.google.common.eventbus.EventBus;
import org.apache.pulsar.shade.com.google.common.eventbus.Subscribe;
import org.junit.jupiter.api.Test;

public class SubscriberTest {
    @Test
    void eventBusInvokesSubscriberMethod() {
        EventBus eventBus = new EventBus();
        MessageSubscriber subscriber = new MessageSubscriber();
        eventBus.register(subscriber);

        eventBus.post("delivered");

        assertThat(subscriber.message).isEqualTo("delivered");
    }

    public static class MessageSubscriber {
        private String message;

        @Subscribe
        public void receive(String received) {
            message = received;
        }
    }
}
