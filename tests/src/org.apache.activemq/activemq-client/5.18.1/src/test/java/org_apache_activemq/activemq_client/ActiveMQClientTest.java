/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_client;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.io.File;
import java.io.IOException;
import java.util.Random;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ActiveMQClientTest {

    private static final String CONTAINER_NAME = "activemq-metadata-test-" + new Random().nextLong();

    private static final String QUEUE_NAME = "queue-test-" + new Random().nextLong();

    private static final Logger logger = LoggerFactory.getLogger("ActiveMQClientTest");

    private Process process;

    @BeforeAll
    void beforeAll() throws IOException {
        logger.info("Starting ActiveMQ ...");
        process = new ProcessBuilder(
                "docker", "run", "--rm", "-p", "61616:61616",
                "--name", CONTAINER_NAME,
                "symptoma/activemq:latest")
                .redirectOutput(new File("activemq-start-stdout.txt"))
                .redirectError(new File("activemq-start-stderr.txt"))
                .start();

        logger.info("Waiting for ActiveMQ to become available");
        waitUntil(() -> {
            createConnection().close();
            return true;
        }, 60);

        logger.info("ActiveMQ started");
    }

    @AfterAll
    void tearDown() throws IOException {
        if (process != null && process.isAlive()) {
            logger.info("Shutting down ActiveMQ ...");
            Process shutdownProcess = new ProcessBuilder("docker", "stop", CONTAINER_NAME)
                    .redirectOutput(new File("activemq-stop-stdout.txt"))
                    .redirectError(new File("activemq-stop-stderr.txt"))
                    .start();

            logger.info("Waiting for ActiveMQ to shut down");
            waitUntil(() -> !shutdownProcess.isAlive(), 30);

            logger.info("ActiveMQ shut down");
        }
    }

    // not using Awaitility library because of `com.oracle.svm.core.jdk.UnsupportedFeatureError: ThreadMXBean methods` issue
    // which happens if a condition is not fulfilled when a test is running in a native image
    private void waitUntil(Callable<Boolean> conditionEvaluator, int timeoutSeconds) {
        Exception lastException = null;

        long end  = System.currentTimeMillis() + timeoutSeconds * 1000;
        while (System.currentTimeMillis() < end) {
            try {
                Thread.sleep(2000L);
            } catch (InterruptedException e) {
                // continue
            }
            try {
                if (conditionEvaluator.call()) {
                    return;
                }
            } catch (Exception e) {
                lastException = e;
            }
        }
        String errorMessage = "Condition was not fulfilled within " + timeoutSeconds + " seconds";
        throw lastException == null ? new IllegalStateException(errorMessage) : new IllegalStateException(errorMessage, lastException);
    }



    @Test
    void test() throws Exception {
        String text = "hello";
        sendMessage(text);
        assertThat(receiveMessage()).isEqualTo(text);
    }

    private Connection createConnection() throws JMSException {
        ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory("tcp://localhost:61616");
        return connectionFactory.createConnection();
    }

    private void sendMessage(String text) throws Exception {
        try (Connection connection = createConnection()) {
            connection.start();
            try (Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
                Destination destination = session.createQueue(QUEUE_NAME);
                try (MessageProducer producer = session.createProducer(destination)) {
                    producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
                    producer.send(session.createTextMessage(text));
                    logger.info("Sent text message: {}", text);
                }
            }
        }
    }

    private String receiveMessage() throws Exception {
        try (Connection connection = createConnection()) {
            connection.start();
            connection.setExceptionListener(e -> {
                logger.error("JMS Exception", e);
            });
            try (Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
                Destination destination = session.createQueue(QUEUE_NAME);
                try (MessageConsumer consumer = session.createConsumer(destination)) {
                    Message message = consumer.receive(1000);
                    String text = ((TextMessage) message).getText();
                    logger.info("Received text message: {}", text);
                    return text;
                }
            }
        }
    }
}
