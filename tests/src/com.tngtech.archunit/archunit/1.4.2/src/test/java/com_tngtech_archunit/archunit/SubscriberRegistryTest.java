/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_tngtech_archunit.archunit;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.thirdparty.com.google.common.eventbus.EventBus;
import com.tngtech.archunit.thirdparty.com.google.common.eventbus.Subscribe;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

public class SubscriberRegistryTest {
    @Test
    void registrationDiscoversSubscriberMethodsForTheirDistinctEventTypes() {
        EventBus eventBus = new EventBus("registry-test");
        RecordingSubscriber subscriber = new RecordingSubscriber();

        eventBus.register(subscriber);
        eventBus.post("message");
        eventBus.post(42);

        assertThat(subscriber.events).containsExactly("text:message", "number:42");
    }

    public static final class RecordingSubscriber {
        private final List<String> events = new ArrayList<>();

        @Subscribe
        public void onText(String value) {
            events.add("text:" + value);
        }

        @Subscribe
        public void onNumber(Integer value) {
            events.add("number:" + value);
        }
    }
}
