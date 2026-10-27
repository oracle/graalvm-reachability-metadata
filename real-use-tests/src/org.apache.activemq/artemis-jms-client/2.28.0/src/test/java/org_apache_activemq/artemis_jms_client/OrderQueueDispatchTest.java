/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.artemis_jms_client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import javax.jms.DeliveryMode;
import javax.jms.JMSConsumer;
import javax.jms.JMSContext;
import javax.jms.JMSProducer;
import javax.jms.Message;
import javax.jms.Queue;
import javax.jms.QueueBrowser;
import javax.jms.TextMessage;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dispatches warehouse orders over a point-to-point JMS queue through the Artemis JMS 2.0
 * `JMSContext` API: message properties, selectors, priorities, queue browsing, and
 * client acknowledgement with redelivery.
 *
 * An order service sends JSON orders to an embedded broker over TCP; each regional picker
 * consumes only its region through a selector, express orders overtake standard ones by
 * priority, and an order the picker fails to acknowledge is redelivered when it recovers.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrderQueueDispatchTest {

    static {
        // Like the Artemis distribution, keep the broker audit log off: it is not the subject here.
        System.setProperty("org.slf4j.simpleLogger.log.org.apache.activemq.audit", "warn");
    }

    private static final String ORDERS = "warehouse.orders";

    private final ObjectMapper mapper = new ObjectMapper();

    private ActiveMQServer broker;

    private ActiveMQConnectionFactory connectionFactory;

    @BeforeAll
    void startBroker() throws Exception {
        String url = "tcp://127.0.0.1:" + freePort();
        Configuration configuration = new ConfigurationImpl()
                .setPersistenceEnabled(false)
                .setSecurityEnabled(false)
                .addAcceptorConfiguration("netty", url);
        broker = ActiveMQServers.newActiveMQServer(configuration);
        broker.start();
        assertThat(broker.waitForActivation(30, TimeUnit.SECONDS)).isTrue();
        connectionFactory = new ActiveMQConnectionFactory(url);
    }

    @AfterAll
    void stopBroker() throws Exception {
        if (connectionFactory != null) {
            connectionFactory.close();
        }
        if (broker != null) {
            broker.stop();
        }
    }

    @Test
    void routesOrdersByRegionPriorityAndAcknowledgement() throws Exception {
        try (JMSContext orderService = connectionFactory.createContext()) {
            Queue orders = orderService.createQueue(ORDERS);
            JMSProducer producer = orderService.createProducer()
                    .setDeliveryMode(DeliveryMode.PERSISTENT);
            send(producer, orders, new Order("A-100", "north", "standard", 2), 4);
            send(producer, orders, new Order("A-101", "south", "standard", 1), 4);
            send(producer, orders, new Order("A-102", "north", "express", 5), 9);
            send(producer, orders, new Order("A-103", "north", "standard", 3), 4);
            send(producer, orders, new Order("A-104", "south", "express", 7), 9);

            List<String> pending = browse(orderService, orders, "region = 'south'");
            assertThat(pending).containsExactly("A-104", "A-101");
        }

        try (JMSContext northPicker = connectionFactory.createContext(JMSContext.CLIENT_ACKNOWLEDGE)) {
            Queue orders = northPicker.createQueue(ORDERS);
            JMSConsumer consumer = northPicker.createConsumer(orders, "region = 'north'");

            Message express = consumer.receive(10_000);
            assertThat(express).isNotNull();
            assertThat(readOrder(express)).isEqualTo(new Order("A-102", "north", "express", 5));
            assertThat(express.getJMSPriority()).isEqualTo(9);
            assertThat(express.getJMSRedelivered()).isFalse();
            assertThat(express.getStringProperty("service")).isEqualTo("express");

            northPicker.recover();
            Message redelivered = consumer.receive(10_000);
            assertThat(redelivered).isNotNull();
            assertThat(readOrder(redelivered).id()).isEqualTo("A-102");
            assertThat(redelivered.getJMSRedelivered()).isTrue();
            assertThat(redelivered.getIntProperty("JMSXDeliveryCount")).isEqualTo(2);
            redelivered.acknowledge();

            List<String> standard = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                Message message = consumer.receive(10_000);
                assertThat(message).isNotNull();
                standard.add(readOrder(message).id());
                message.acknowledge();
            }
            assertThat(standard).containsExactly("A-100", "A-103");
            assertThat(consumer.receiveNoWait()).isNull();
        }

        try (JMSContext southPicker = connectionFactory.createContext()) {
            Queue orders = southPicker.createQueue(ORDERS);
            assertThat(browse(southPicker, orders, null)).containsExactly("A-104", "A-101");
            try (JMSConsumer consumer = southPicker.createConsumer(orders, "quantity > 3")) {
                Order bulk = mapper.readValue(consumer.receiveBody(String.class, 10_000), Order.class);
                assertThat(bulk).isEqualTo(new Order("A-104", "south", "express", 7));
            }
            try (JMSConsumer consumer = southPicker.createConsumer(orders)) {
                assertThat(readOrder(consumer.receive(10_000)).id()).isEqualTo("A-101");
                assertThat(consumer.receiveNoWait()).isNull();
            }
        }

        assertThat(awaitDrained(ORDERS)).isZero();
    }

    private void send(JMSProducer producer, Queue queue, Order order, int priority) throws IOException {
        producer.setPriority(priority)
                .setProperty("region", order.region())
                .setProperty("service", order.service())
                .setProperty("quantity", order.quantity())
                .setJMSCorrelationID(order.id())
                .send(queue, mapper.writeValueAsString(order));
    }

    private List<String> browse(JMSContext context, Queue queue, String selector) throws Exception {
        List<String> ids = new ArrayList<>();
        try (QueueBrowser browser = context.createBrowser(queue, selector)) {
            Enumeration<?> messages = browser.getEnumeration();
            for (Object message : Collections.list(messages)) {
                ids.add(((Message) message).getJMSCorrelationID());
            }
        }
        return ids;
    }

    private Order readOrder(Message message) throws Exception {
        return mapper.readValue(((TextMessage) message).getText(), Order.class);
    }

    private long awaitDrained(String queue) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        long remaining = broker.locateQueue(queue).getMessageCount();
        while (remaining > 0 && System.nanoTime() < deadline) {
            Thread.sleep(50);
            remaining = broker.locateQueue(queue).getMessageCount();
        }
        return remaining;
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    /** A warehouse order as the order service serializes it to JSON. */
    record Order(String id, String region, String service, int quantity) {
    }
}
