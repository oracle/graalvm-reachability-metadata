/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_client_jakarta;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import jakarta.jms.Connection;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.Queue;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.util.ProducerThread;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ProducerThreadTest {

    private static final int OPERATION_TIMEOUT_MILLIS = 10_000;

    @Test
    void sendsTextMessageLoadedFromBundledDemoResource() throws Exception {
        String brokerName = "producer-thread-resource-" + UUID.randomUUID();
        String brokerUrl = "vm://" + brokerName + "?broker.persistent=false&broker.useJmx=false";
        ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(brokerUrl);
        connectionFactory.setSendTimeout(OPERATION_TIMEOUT_MILLIS);

        try (Connection connection = connectionFactory.createConnection();
                Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
            Queue queue = session.createQueue("queue." + brokerName);
            CountDownLatch finished = new CountDownLatch(1);
            ProducerThread producerThread = new ProducerThread(session, queue);
            producerThread.setName("producer-thread-resource-test");
            producerThread.setPersistent(false);
            producerThread.setMessageCount(1);
            producerThread.setTextMessageSize(64);
            producerThread.setFinished(finished);

            connection.start();
            producerThread.start();

            try {
                assertThat(finished.await(OPERATION_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)).isTrue();
                producerThread.join(OPERATION_TIMEOUT_MILLIS);
                assertThat(producerThread.isAlive()).isFalse();
                assertThat(producerThread.getSentCount()).isEqualTo(1);

                try (MessageConsumer consumer = session.createConsumer(queue)) {
                    Message received = consumer.receive(OPERATION_TIMEOUT_MILLIS);

                    assertThat(received).isInstanceOf(TextMessage.class);
                    assertThat(((TextMessage) received).getText())
                            .hasSize(64)
                            .startsWith("Lorem ipsum dolor sit amet");
                }
            } finally {
                producerThread.setRunning(false);
                producerThread.resumeProducer();
                producerThread.join(OPERATION_TIMEOUT_MILLIS);
            }
        }
    }
}
