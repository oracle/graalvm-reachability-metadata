/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_cloud.spring_cloud_stream_binder_kafka;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.stream.binder.kafka.KafkaBindingRebalanceListener;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.test.EmbeddedKafkaKraftBroker;

import static org.assertj.core.api.Assertions.assertThat;

public class Spring_cloud_stream_binder_kafkaTest {

    @Test
    void sendsMessagesBetweenFunctionBindingsThroughKafka() throws InterruptedException {
        String identifier = UUID.randomUUID().toString();
        String topic = "binder-integration-" + identifier;
        String group = "binder-group-" + identifier;
        String message = "message sent through the Kafka binder";
        EmbeddedKafkaKraftBroker broker = new EmbeddedKafkaKraftBroker(1, 1, topic);
        broker.afterPropertiesSet();

        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(BindingConfiguration.class)
                .web(WebApplicationType.NONE)
                .properties(
                        "spring.cloud.function.definition=consumer",
                        "spring.cloud.stream.kafka.binder.brokers=" + broker.getBrokersAsString(),
                        "spring.cloud.stream.bindings.consumer-in-0.destination=" + topic,
                        "spring.cloud.stream.bindings.consumer-in-0.group=" + group,
                        "spring.cloud.stream.output-bindings=publisher",
                        "spring.cloud.stream.bindings.publisher-out-0.destination=" + topic)
                .run()) {
            MessageCollector collector = context.getBean(MessageCollector.class);
            StreamBridge streamBridge = context.getBean(StreamBridge.class);

            assertThat(streamBridge.send("publisher-out-0", message)).isTrue();
            assertThat(collector.awaitMessage()).isTrue();
            assertThat(collector.message()).isEqualTo(message);
        } finally {
            broker.destroy();
        }
    }

    @Test
    void invokesRebalanceListenerWhenConsumerIsAssignedKafkaPartitions() throws InterruptedException {
        String identifier = UUID.randomUUID().toString();
        String topic = "rebalance-listener-" + identifier;
        String group = "rebalance-group-" + identifier;
        EmbeddedKafkaKraftBroker broker = new EmbeddedKafkaKraftBroker(1, 1, topic);
        broker.afterPropertiesSet();

        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(RebalanceListenerConfiguration.class)
                .web(WebApplicationType.NONE)
                .properties(
                        "spring.cloud.function.definition=consumer",
                        "spring.cloud.stream.kafka.binder.brokers=" + broker.getBrokersAsString(),
                        "spring.cloud.stream.bindings.consumer-in-0.destination=" + topic,
                        "spring.cloud.stream.bindings.consumer-in-0.group=" + group)
                .run()) {
            AssignmentCollector collector = context.getBean(AssignmentCollector.class);

            assertThat(collector.awaitAssignment()).isTrue();
            assertThat(collector.bindingName()).isEqualTo("consumer-in-0");
            assertThat(collector.partitions()).containsExactly(new TopicPartition(topic, 0));
            assertThat(collector.initial()).isTrue();
        } finally {
            broker.destroy();
        }
    }

    @Test
    void sendsMessagesToDynamicallyCreatedKafkaDestination() throws InterruptedException {
        String identifier = UUID.randomUUID().toString();
        String topic = "dynamic-destination-" + identifier;
        String group = "dynamic-group-" + identifier;
        String message = "message sent to a dynamic Kafka destination";
        EmbeddedKafkaKraftBroker broker = new EmbeddedKafkaKraftBroker(1, 1, topic);
        broker.afterPropertiesSet();

        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(BindingConfiguration.class)
                .web(WebApplicationType.NONE)
                .properties(
                        "spring.cloud.function.definition=consumer",
                        "spring.cloud.stream.kafka.binder.brokers=" + broker.getBrokersAsString(),
                        "spring.cloud.stream.bindings.consumer-in-0.destination=" + topic,
                        "spring.cloud.stream.bindings.consumer-in-0.group=" + group)
                .run()) {
            MessageCollector collector = context.getBean(MessageCollector.class);
            StreamBridge streamBridge = context.getBean(StreamBridge.class);

            assertThat(streamBridge.send(topic, message)).isTrue();
            assertThat(collector.awaitMessage()).isTrue();
            assertThat(collector.message()).isEqualTo(message);
        } finally {
            broker.destroy();
        }
    }

    @Configuration
    @EnableAutoConfiguration
    public static class BindingConfiguration {

        @Bean
        MessageCollector messageCollector() {
            return new MessageCollector();
        }

        @Bean
        Consumer<String> consumer(MessageCollector collector) {
            return collector::record;
        }
    }

    @Configuration
    @EnableAutoConfiguration
    public static class RebalanceListenerConfiguration {

        @Bean
        AssignmentCollector assignmentCollector() {
            return new AssignmentCollector();
        }

        @Bean
        Consumer<String> consumer() {
            return value -> { };
        }
    }

    static final class AssignmentCollector implements KafkaBindingRebalanceListener {
        private final CountDownLatch partitionsAssigned = new CountDownLatch(1);
        private volatile String bindingName;
        private volatile Set<TopicPartition> partitions;
        private volatile boolean initial;

        @Override
        public void onPartitionsAssigned(String assignedBindingName,
                org.apache.kafka.clients.consumer.Consumer<?, ?> consumer,
                Collection<TopicPartition> assignedPartitions, boolean isInitial) {
            bindingName = assignedBindingName;
            partitions = Set.copyOf(assignedPartitions);
            initial = isInitial;
            partitionsAssigned.countDown();
        }

        boolean awaitAssignment() throws InterruptedException {
            return partitionsAssigned.await(30, TimeUnit.SECONDS);
        }

        String bindingName() {
            return bindingName;
        }

        Set<TopicPartition> partitions() {
            return partitions;
        }

        boolean initial() {
            return initial;
        }
    }

    static final class MessageCollector {
        private final CountDownLatch messageReceived = new CountDownLatch(1);
        private volatile String message;

        void record(String value) {
            message = value;
            messageReceived.countDown();
        }

        boolean awaitMessage() throws InterruptedException {
            return messageReceived.await(30, TimeUnit.SECONDS);
        }

        String message() {
            return message;
        }
    }
}
