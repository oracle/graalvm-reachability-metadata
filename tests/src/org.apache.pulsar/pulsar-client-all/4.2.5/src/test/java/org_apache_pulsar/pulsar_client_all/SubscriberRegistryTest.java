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

public class SubscriberRegistryTest {
    @Test
    void registrationDiscoversAnnotatedMethods() {
        EventBus eventBus = new EventBus();
        RecordingSubscriber subscriber = new RecordingSubscriber();
        eventBus.register(subscriber);

        eventBus.post("first");
        eventBus.post("second");

        assertThat(subscriber.messages).containsExactly("first", "second");
    }

    public static class RecordingSubscriber {
        private final List<String> messages = new ArrayList<>();

        @Subscribe
        public void record(String message) {
            messages.add(message);
        }
    }
}
