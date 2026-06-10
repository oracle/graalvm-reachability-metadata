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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.KafkaConsumer;
import org.apache.camel.component.kafka.TaskHealthState;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class KafkaFetchRecordsTest {
    private static final String KAFKA_IMAGE = "apache/kafka:4.2.0";
    private static final Duration KAFKA_START_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration HEALTH_TIMEOUT = Duration.ofSeconds(6);

    @Test
    void kafkaConsumerRouteReportsReadyFetchRecordHealthState() throws Exception {
        int brokerPort = findAvailablePort();
        String brokers = "localhost:" + brokerPort;
        String topic = "camel-fetch-records-" + UUID.randomUUID();
        String groupId = "camel-fetch-records-group-" + UUID.randomUUID();
        String routeId = "kafka-fetch-records-route-" + UUID.randomUUID();
        String message = "message-" + UUID.randomUUID();

        try (KafkaBroker broker = KafkaBroker.start(brokerPort)) {
            waitForKafka(brokers);
            createTopic(brokers, topic);

            CountDownLatch messageConsumed = new CountDownLatch(1);
            AtomicReference<String> receivedBody = new AtomicReference<>();

            CamelContext context = new DefaultCamelContext();
            try {
                context.addRoutes(new RouteBuilder() {
                    @Override
                    public void configure() {
                        from(kafkaEndpoint(topic, brokers, groupId))
                                .routeId(routeId)
                                .process(exchange -> {
                                    receivedBody.set(exchange.getMessage().getBody(String.class));
                                    messageConsumed.countDown();
                                });
                    }
                });
                context.start();

                sendRecord(brokers, topic, message);

                assertThat(messageConsumed.await(12, TimeUnit.SECONDS)).isTrue();
                assertThat(receivedBody.get()).isEqualTo(message);

                KafkaConsumer kafkaConsumer = kafkaConsumer(context, routeId);
                TaskHealthState state = waitForReadyHealthState(kafkaConsumer);

                assertThat(state.getClientId()).isNotNull();
                assertThat(state.getBootstrapServers()).isEqualTo(brokers);
                assertThat(state.getGroupId()).isEqualTo(groupId);
            } finally {
                context.stop();
            }
        }
    }

    private static String kafkaEndpoint(String topic, String brokers, String groupId) {
        return "kafka:" + topic
                + "?brokers=" + brokers
                + "&groupId=" + groupId
                + "&autoOffsetReset=earliest"
                + "&pollTimeoutMs=100"
                + "&shutdownTimeout=2000";
    }

    private static KafkaConsumer kafkaConsumer(CamelContext context, String routeId) {
        Route route = context.getRoutes().stream()
                .filter(candidate -> routeId.equals(candidate.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Route was not found: " + routeId));

        Consumer consumer = route.getConsumer();
        assertThat(consumer).isInstanceOf(KafkaConsumer.class);
        return (KafkaConsumer) consumer;
    }

    private static TaskHealthState waitForReadyHealthState(KafkaConsumer kafkaConsumer) throws InterruptedException {
        long deadline = System.nanoTime() + HEALTH_TIMEOUT.toNanos();
        TaskHealthState lastState = null;
        while (System.nanoTime() < deadline) {
            List<TaskHealthState> states = kafkaConsumer.healthStates();
            if (!states.isEmpty()) {
                lastState = states.get(0);
                if (lastState.isReady()) {
                    return lastState;
                }
            }
            Thread.sleep(200);
        }

        assertThat(lastState)
                .describedAs("Kafka fetch records health state should become ready")
                .isNotNull();
        assertThat(lastState.isReady())
                .describedAs(lastState.buildStateMessage())
                .isTrue();
        return lastState;
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

    private static void sendRecord(String brokers, String topic, String message) throws Exception {
        Properties properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, brokers);
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.put(ProducerConfig.ACKS_CONFIG, "all");
        properties.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, "10000");
        properties.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, "3000");
        properties.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, "5000");

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(properties)) {
            producer.send(new ProducerRecord<>(topic, "key", message)).get(10, TimeUnit.SECONDS);
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
            String containerName = "camel-kafka-" + UUID.randomUUID();
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
