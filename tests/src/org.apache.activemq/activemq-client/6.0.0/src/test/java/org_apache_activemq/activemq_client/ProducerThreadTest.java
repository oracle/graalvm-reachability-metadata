/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_client;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.util.ProducerThread;
import org.junit.jupiter.api.Test;

import jakarta.jms.Connection;
import jakarta.jms.MessageConsumer;
import jakarta.jms.Queue;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class ProducerThreadTest {

    @Test
    void publishesTextLoadedFromItsBundledPayloadResource() throws Exception {
        BrokerService broker = new BrokerService();
        broker.setBrokerName("producer-thread-test");
        broker.setPersistent(false);
        broker.setUseJmx(false);
        broker.start();
        broker.waitUntilStarted();

        try (Connection connection = new ActiveMQConnectionFactory("vm://producer-thread-test?create=false")
                .createConnection()) {
            connection.start();
            try (Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
                Queue queue = session.createQueue("producer.payload");
                CountDownLatch finished = new CountDownLatch(1);
                ProducerThread producer = new ProducerThread(session, queue);
                producer.setMessageCount(1);
                producer.setTextMessageSize(64);
                producer.setFinished(finished);
                producer.start();

                assertThat(finished.await(30, TimeUnit.SECONDS)).isTrue();
                try (MessageConsumer consumer = session.createConsumer(queue)) {
                    TextMessage received = (TextMessage) consumer.receive(10000);
                    assertThat(received.getText())
                            .hasSize(64)
                            .startsWith("Lorem ipsum dolor sit amet");
                }
            }
        } finally {
            broker.stop();
            broker.waitUntilStopped();
        }
    }
}
