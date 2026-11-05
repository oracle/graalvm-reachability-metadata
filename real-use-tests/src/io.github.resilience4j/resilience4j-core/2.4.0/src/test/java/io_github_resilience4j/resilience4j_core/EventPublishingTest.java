/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_github_resilience4j.resilience4j_core;

import io.github.resilience4j.core.EventConsumer;
import io.github.resilience4j.core.EventProcessor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Publishes delivery updates through Resilience4j's event processor subsystem.
 *
 * A delivery service sends every status update to its audit stream and routes completed deliveries
 * to a customer notification stream, so each stream observes the events intended for it.
 */
class EventPublishingTest {

    @Test
    void routesAllDeliveryUpdatesAndSpecificCompletionNotifications() {
        EventProcessor<DeliveryEvent> deliveryEvents = new EventProcessor<>();
        List<String> auditLog = new ArrayList<>();
        List<String> notifications = new ArrayList<>();

        deliveryEvents.onEvent(event -> auditLog.add(event.description()));
        EventConsumer<DeliveryCompleted> completionConsumer = event ->
                notifications.add("notify:" + event.orderId());
        deliveryEvents.registerConsumer(DeliveryCompleted.class.getName(), completionConsumer);

        assertThat(deliveryEvents.hasConsumers()).isTrue();
        assertThat(deliveryEvents.processEvent(new DeliveryStarted("order-17"))).isTrue();
        assertThat(deliveryEvents.processEvent(new DeliveryCompleted("order-17"))).isTrue();

        assertThat(auditLog).containsExactly("started:order-17", "completed:order-17");
        assertThat(notifications).containsExactly("notify:order-17");
    }

    private sealed interface DeliveryEvent permits DeliveryStarted, DeliveryCompleted {
        String description();
    }

    private record DeliveryStarted(String orderId) implements DeliveryEvent {
        @Override
        public String description() {
            return "started:" + orderId;
        }
    }

    private record DeliveryCompleted(String orderId) implements DeliveryEvent {
        @Override
        public String description() {
            return "completed:" + orderId;
        }
    }
}
