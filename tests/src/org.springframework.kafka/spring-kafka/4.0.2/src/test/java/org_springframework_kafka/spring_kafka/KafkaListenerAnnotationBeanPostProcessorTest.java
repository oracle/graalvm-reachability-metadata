/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_kafka.spring_kafka;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.KafkaListenerAnnotationBeanPostProcessor;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.AcknowledgingConsumerAwareMessageListener;
import org.springframework.kafka.listener.MessageListenerContainer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public class KafkaListenerAnnotationBeanPostProcessorTest {
    @Test
    @SuppressWarnings("unchecked")
    void registersAndInvokesListenerThroughJdkInterfaceProxy() {
        KafkaListenerAnnotationBeanPostProcessor<String, String> processor = new KafkaListenerAnnotationBeanPostProcessor<>();
        OrderListener target = new OrderListener();
        ProxyFactory proxyFactory = new ProxyFactory();
        proxyFactory.setTarget(target);
        proxyFactory.setInterfaces(OrderHandler.class);
        OrderHandler listener = (OrderHandler) proxyFactory.getProxy();

        ConcurrentKafkaListenerContainerFactory<String, String> containerFactory = new ConcurrentKafkaListenerContainerFactory<>();
        containerFactory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(
                Map.of(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092"),
                new StringDeserializer(), new StringDeserializer()));
        containerFactory.setAutoStartup(false);
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("kafkaListenerContainerFactory", containerFactory);
        KafkaListenerEndpointRegistry registry = new KafkaListenerEndpointRegistry();
        try {
            processor.setBeanFactory(beanFactory);
            processor.setEndpointRegistry(registry);

            assertSame(listener, processor.postProcessAfterInitialization(listener, "orderListener"));
            processor.afterSingletonsInstantiated();

            MessageListenerContainer container = registry.getListenerContainer("order-listener");
            assertNotNull(container);
            assertFalse(container.isRunning());
            assertFalse(container.isAutoStartup());
            assertArrayEquals(new String[]{"orders"}, container.getContainerProperties().getTopics());
            AcknowledgingConsumerAwareMessageListener<String, String> messageListener =
                    (AcknowledgingConsumerAwareMessageListener<String, String>) container.getContainerProperties().getMessageListener();
            messageListener.onMessage(new ConsumerRecord<>("orders", 0, 0L, "key", "order-42"), null, null);

            assertEquals(List.of("order-42"), target.orders);
        } finally {
            registry.destroy();
            beanFactory.destroySingletons();
        }
    }

    public interface OrderHandler {
        void receive(String order);
    }

    public static class OrderListener implements OrderHandler {
        private final List<String> orders = new ArrayList<>();

        @Override
        @KafkaListener(id = "order-listener", topics = "orders", autoStartup = "false")
        public void receive(String order) {
            orders.add(order);
        }
    }
}
