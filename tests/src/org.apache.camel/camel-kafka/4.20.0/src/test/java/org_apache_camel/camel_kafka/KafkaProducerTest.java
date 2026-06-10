/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_camel.camel_kafka;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Producer;
import org.apache.camel.component.kafka.KafkaEndpoint;
import org.apache.camel.component.kafka.KafkaProducer;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.errors.TopicExistsException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class KafkaProducerTest {
    private static final String KAFKA_IMAGE = "apache/kafka:4.2.0";
    private static final Duration KAFKA_START_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration READY_CHECK_TIMEOUT = Duration.ofSeconds(6);

    @Test
    void kafkaProducerSendsRecordAndChecksNetworkClientReadiness() throws Exception {
        int brokerPort = findAvailablePort();
        String brokers = "localhost:" + brokerPort;
        String topic = "camel-producer-" + UUID.randomUUID();
        String message = "message-" + UUID.randomUUID();

        try (KafkaBroker broker = KafkaBroker.start(brokerPort)) {
            waitForKafka(brokers);
            createTopic(brokers, topic);

            CamelContext context = new DefaultCamelContext();
            KafkaProducer producer = null;
            try {
                context.start();
                Endpoint endpoint = context.getEndpoint(kafkaEndpoint(topic, brokers));
                assertThat(endpoint).isInstanceOf(KafkaEndpoint.class);
                KafkaEndpoint kafkaEndpoint = (KafkaEndpoint) endpoint;
                Producer camelProducer = kafkaEndpoint.createProducer();
                assertThat(camelProducer).isInstanceOf(KafkaProducer.class);

                producer = (KafkaProducer) camelProducer;
                producer.start();

                Exchange exchange = kafkaEndpoint.createExchange();
                exchange.getMessage().setBody(message);
                producer.process(exchange);

                assertThat(exchange.getException()).isNull();
                assertThat(waitForReadinessCheck(producer))
                        .describedAs("Kafka producer should report a ready broker connection after sending")
                        .isTrue();
            } finally {
                if (producer != null) {
                    producer.stop();
                }
                context.stop();
            }
        }
    }

    private static String kafkaEndpoint(String topic, String brokers) {
        return "kafka:" + topic
                + "?brokers=" + brokers
                + "&requestRequiredAcks=all"
                + "&deliveryTimeoutMs=10000"
                + "&requestTimeoutMs=3000"
                + "&maxBlockMs=5000"
                + "&shutdownTimeout=2000";
    }

    private static boolean waitForReadinessCheck(KafkaProducer producer) throws InterruptedException {
        long deadline = System.nanoTime() + READY_CHECK_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            if (producer.isReady()) {
                return true;
            }
            Thread.sleep(200);
        }
        return false;
    }

    private static void waitForKafka(String brokers) throws Exception {
        Properties properties = new Properties();
        properties.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, brokers);
        properties.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, "1000");
        properties.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, "1000");

        long deadline = System.nanoTime() + KAFKA_START_TIMEOUT.toNanos();
        Exception lastFailure = null;
        while (System.nanoTime() < deadline) {
            try (Admin admin = Admin.create(properties)) {
                admin.describeCluster().nodes().get(1, TimeUnit.SECONDS);
                return;
            } catch (ExecutionException | TimeoutException e) {
                lastFailure = e;
                Thread.sleep(500);
            }
        }
        throw new IllegalStateException("Kafka broker did not become ready at " + brokers, lastFailure);
    }

    private static void createTopic(String brokers, String topic) throws Exception {
        Properties properties = new Properties();
        properties.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, brokers);
        properties.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, "3000");
        properties.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, "5000");

        try (Admin admin = Admin.create(properties)) {
            try {
                admin.createTopics(List.of(new NewTopic(topic, 1, (short) 1))).all().get(10, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                if (!(e.getCause() instanceof TopicExistsException)) {
                    throw e;
                }
            }
        }
    }

    private static int findAvailablePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static final class KafkaBroker implements AutoCloseable {
        private final String containerName;

        private KafkaBroker(String containerName) {
            this.containerName = containerName;
        }

        static KafkaBroker start(int brokerPort) throws Exception {
            String containerName = "camel-kafka-producer-" + UUID.randomUUID();
            List<String> command = new ArrayList<>();
            command.add("docker");
            command.add("run");
            command.add("--detach");
            command.add("--name");
            command.add(containerName);
            command.add("-p");
            command.add("127.0.0.1:" + brokerPort + ":9092");
            command.add("-e");
            command.add("KAFKA_NODE_ID=1");
            command.add("-e");
            command.add("KAFKA_PROCESS_ROLES=broker,controller");
            command.add("-e");
            command.add("KAFKA_LISTENERS=PLAINTEXT://:9092,CONTROLLER://:9093");
            command.add("-e");
            command.add("KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://localhost:" + brokerPort);
            command.add("-e");
            command.add("KAFKA_CONTROLLER_LISTENER_NAMES=CONTROLLER");
            command.add("-e");
            command.add("KAFKA_LISTENER_SECURITY_PROTOCOL_MAP=CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT");
            command.add("-e");
            command.add("KAFKA_INTER_BROKER_LISTENER_NAME=PLAINTEXT");
            command.add("-e");
            command.add("KAFKA_CONTROLLER_QUORUM_VOTERS=1@localhost:9093");
            command.add("-e");
            command.add("KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1");
            command.add("-e");
            command.add("KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR=1");
            command.add("-e");
            command.add("KAFKA_TRANSACTION_STATE_LOG_MIN_ISR=1");
            command.add("-e");
            command.add("KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS=0");
            command.add(KAFKA_IMAGE);

            runCommand(command, Duration.ofSeconds(15));
            return new KafkaBroker(containerName);
        }

        @Override
        public void close() throws Exception {
            runCommand(List.of("docker", "rm", "--force", containerName), Duration.ofSeconds(10));
        }
    }

    private static String runCommand(List<String> command, Duration timeout) throws Exception {
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
        if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("Command timed out: " + String.join(" ", command));
        }

        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        if (process.exitValue() != 0) {
            throw new IllegalStateException("Command failed: " + String.join(" ", command) + "\n" + output);
        }
        return output;
    }
}
