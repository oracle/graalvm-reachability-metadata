/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_github_resilience4j.resilience4j_consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.List;

import io.github.resilience4j.consumer.CircularEventConsumer;
import io.github.resilience4j.consumer.DefaultEventConsumerRegistry;
import io.github.resilience4j.consumer.EventConsumerRegistry;
import io.github.resilience4j.core.EventProcessor;
import org.junit.jupiter.api.Test;

public class Resilience4j_consumerTest {
    @Test
    void circularEventConsumerRetainsNewestEventsUpToCapacity() {
        CircularEventConsumer<String> consumer = new CircularEventConsumer<>(3);

        consumer.consumeEvent("first");
        consumer.consumeEvent("second");
        consumer.consumeEvent("third");
        consumer.consumeEvent("fourth");

        assertThat(consumer.getBufferedEvents()).containsExactly("second", "third", "fourth");
        assertThat(consumer.getBufferedEventsStream()).containsExactly("second", "third", "fourth");
    }

    @Test
    void circularEventConsumerReturnsSnapshotOfBufferedEvents() {
        CircularEventConsumer<Integer> consumer = new CircularEventConsumer<>(2);

        consumer.consumeEvent(10);
        consumer.consumeEvent(20);
        List<Integer> bufferedEvents = consumer.getBufferedEvents();
        consumer.consumeEvent(30);

        assertThat(bufferedEvents).containsExactly(10, 20);
        assertThat(consumer.getBufferedEvents()).containsExactly(20, 30);
    }

    @Test
    void circularEventConsumerBuffersEventsPublishedThroughEventProcessor() {
        EventProcessor<CharSequence> eventProcessor = new EventProcessor<>();
        CircularEventConsumer<CharSequence> consumer = new CircularEventConsumer<>(2);
        eventProcessor.onEvent(consumer);

        boolean firstEventConsumed = eventProcessor.processEvent("published-first");
        boolean secondEventConsumed = eventProcessor.processEvent("published-second");
        boolean thirdEventConsumed = eventProcessor.processEvent("published-third");

        assertThat(firstEventConsumed).isTrue();
        assertThat(secondEventConsumed).isTrue();
        assertThat(thirdEventConsumed).isTrue();
        assertThat(consumer.getBufferedEvents()).containsExactly("published-second", "published-third");
    }

    @Test
    void eventConsumerRegistryCreatesRetrievesListsAndRemovesConsumers() {
        EventConsumerRegistry<String> registry = new DefaultEventConsumerRegistry<>();

        CircularEventConsumer<String> alphaConsumer = registry.createEventConsumer("alpha", 2);
        CircularEventConsumer<String> betaConsumer = registry.createEventConsumer("beta", 1);
        registry.getEventConsumer("alpha").consumeEvent("alpha-one");
        registry.getEventConsumer("alpha").consumeEvent("alpha-two");
        registry.getEventConsumer("beta").consumeEvent("beta-one");
        registry.getEventConsumer("beta").consumeEvent("beta-two");

        assertThat(registry.getEventConsumer("alpha")).isSameAs(alphaConsumer);
        assertThat(registry.getEventConsumer("beta")).isSameAs(betaConsumer);
        assertThat(registry.getAllEventConsumer()).containsExactlyInAnyOrder(alphaConsumer, betaConsumer);
        assertThat(alphaConsumer.getBufferedEvents()).containsExactly("alpha-one", "alpha-two");
        assertThat(betaConsumer.getBufferedEvents()).containsExactly("beta-two");

        assertThat(registry.removeEventConsumer("alpha")).isSameAs(alphaConsumer);
        assertThat(registry.getEventConsumer("alpha")).isNull();
        assertThat(registry.removeEventConsumer("unknown")).isNull();
        assertThat(registry.getAllEventConsumer()).containsExactly(betaConsumer);
    }

    @Test
    void eventConsumerRegistryReplacesConsumerRegisteredWithTheSameName() {
        EventConsumerRegistry<String> registry = new DefaultEventConsumerRegistry<>();

        CircularEventConsumer<String> previousConsumer = registry.createEventConsumer("shared", 3);
        previousConsumer.consumeEvent("old");
        CircularEventConsumer<String> replacementConsumer = registry.createEventConsumer("shared", 1);
        replacementConsumer.consumeEvent("new-one");
        replacementConsumer.consumeEvent("new-two");

        assertThat(registry.getEventConsumer("shared")).isSameAs(replacementConsumer);
        assertThat(registry.getAllEventConsumer()).containsExactly(replacementConsumer);
        assertThat(previousConsumer.getBufferedEvents()).containsExactly("old");
        assertThat(replacementConsumer.getBufferedEvents()).containsExactly("new-two");
    }

    @Test
    void eventConsumerRegistryReturnsImmutableListOfConsumers() {
        EventConsumerRegistry<String> registry = new DefaultEventConsumerRegistry<>();
        CircularEventConsumer<String> firstConsumer = registry.createEventConsumer("first", 1);
        CircularEventConsumer<String> secondConsumer = registry.createEventConsumer("second", 1);

        List<CircularEventConsumer<String>> consumers = registry.getAllEventConsumer();

        assertThat(consumers).containsExactlyInAnyOrder(firstConsumer, secondConsumer);
        assertThatExceptionOfType(UnsupportedOperationException.class)
            .isThrownBy(() -> consumers.add(new CircularEventConsumer<>(1)));
    }
}
