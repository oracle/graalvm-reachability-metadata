/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_cloud.spring_cloud_stream_binder_kafka;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
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
