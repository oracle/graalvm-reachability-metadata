/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import org.apache.pulsar.client.admin.PulsarAdmin;
import org.apache.pulsar.client.api.CompressionType;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.MessageId;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.client.api.SubscriptionInitialPosition;
import org.apache.pulsar.common.schema.SchemaInfo;
import org.apache.pulsar.common.schema.SchemaType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(55)
public class PulsarClientAllTest {
    private static final String ADMIN_URL = "http://127.0.0.1:8080";
    private static final String BROKER_URL = "pulsar://127.0.0.1:6650";
    private static final String CONTAINER_NAME = "graalvm-pulsar-client-all-test";
    private static final String IMAGE = "apachepulsar/pulsar:4.2.5";
    private static final Duration PROCESS_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration READINESS_TIMEOUT = Duration.ofSeconds(50);
    private static final int OPERATION_TIMEOUT_SECONDS = 15;

    private PulsarAdmin admin;
    private PulsarClient client;

    @BeforeAll
    void startPulsar() throws Exception {
        removeExistingContainer();
        try {
            runDocker(
                    true,
                    "run",
                    "--detach",
                    "--rm",
                    "--name",
                    CONTAINER_NAME,
                    "-p",
                    "6650:6650",
                    "-p",
                    "8080:8080",
                    IMAGE,
                    "bin/pulsar",
                    "standalone",
                    "--no-functions-worker",
                    "--no-stream-storage");
            waitUntil(PulsarClientAllTest::brokerAdminIsReady, READINESS_TIMEOUT);

            admin = PulsarAdmin.builder()
                    .serviceHttpUrl(ADMIN_URL)
                    .connectionTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .requestTimeout(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .build();
            client = PulsarClient.builder()
                    .serviceUrl(BROKER_URL)
                    .connectionTimeout(10, TimeUnit.SECONDS)
                    .operationTimeout(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .lookupTimeout(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .build();
        } catch (Exception e) {
            runDocker(false, "logs", CONTAINER_NAME);
            removeExistingContainer();
            throw e;
        }
    }

    @AfterAll
    void stopPulsar() throws Exception {
        try {
            if (admin != null) {
                admin.close();
            }
        } finally {
            try {
                if (client != null) {
                    client.close();
                }
            } finally {
                removeExistingContainer();
            }
        }
    }

    @Test
    void administersStandaloneBrokerAndTopics() throws Exception {
        List<String> clusters = admin.clusters()
                .getClustersAsync()
                .get(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        List<String> tenants = admin.tenants()
                .getTenantsAsync()
                .get(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        String topic = topic("admin");

        admin.topics()
                .createNonPartitionedTopicAsync(topic)
                .get(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        List<String> topics = admin.topics()
                .getListAsync("public/default")
                .get(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        assertThat(clusters).contains("standalone");
        assertThat(tenants).contains("public");
        assertThat(topics).contains(topic);

        admin.topics()
                .deleteAsync(topic)
                .get(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    void publishesAndAcknowledgesStringMessage() throws Exception {
        String topic = topic("strings");
        String subscription = "string-subscription";
        try (Consumer<String> consumer = client.newConsumer(Schema.STRING)
                        .topic(topic)
                        .subscriptionName(subscription)
                        .subscriptionInitialPosition(SubscriptionInitialPosition.Earliest)
                        .subscribe();
                Producer<String> producer = client.newProducer(Schema.STRING)
                        .topic(topic)
                        .producerName("string-producer")
                        .sendTimeout(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        .compressionType(CompressionType.LZ4)
                        .create()) {
            MessageId sentId = producer.newMessage()
                    .key("order-7")
                    .property("source", "integration-test")
                    .eventTime(123456789L)
                    .value("pulsar-message")
                    .sendAsync()
                    .get(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            Message<String> received =
                    consumer.receive(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            assertThat(sentId).isNotNull();
            assertThat(received).isNotNull();
            assertThat(received.getValue()).isEqualTo("pulsar-message");
            assertThat(received.getKey()).isEqualTo("order-7");
            assertThat(received.getProperty("source")).isEqualTo("integration-test");
            assertThat(received.getEventTime()).isEqualTo(123456789L);
            assertThat(received.getTopicName()).isEqualTo(topic);

            consumer.acknowledge(received);
            assertThat(consumer.isConnected()).isTrue();
            assertThat(producer.isConnected()).isTrue();
        }
    }

    @Test
    void exchangesJsonMessageWithCompressionAndRegisteredSchema() throws Exception {
        String topic = topic("json");
        Schema<PurchaseOrder> schema = Schema.JSON(PurchaseOrder.class);
        try (Consumer<PurchaseOrder> consumer = client.newConsumer(schema)
                        .topic(topic)
                        .subscriptionName("json-subscription")
                        .subscriptionInitialPosition(SubscriptionInitialPosition.Earliest)
                        .subscribe();
                Producer<PurchaseOrder> producer = client.newProducer(schema)
                        .topic(topic)
                        .producerName("json-producer")
                        .sendTimeout(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        .compressionType(CompressionType.ZSTD)
                        .enableBatching(false)
                        .create()) {
            PurchaseOrder order = new PurchaseOrder("purchase-42", 3);
            producer.send(order);

            Message<PurchaseOrder> received =
                    consumer.receive(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            SchemaInfo registeredSchema = admin.schemas()
                    .getSchemaInfoAsync(topic)
                    .get(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            assertThat(received).isNotNull();
            assertThat(received.getValue().getOrderId()).isEqualTo("purchase-42");
            assertThat(received.getValue().getUnits()).isEqualTo(3);
            assertThat(registeredSchema.getType()).isEqualTo(SchemaType.JSON);
            assertThat(registeredSchema.getSchemaDefinition()).contains("orderId", "units");

            consumer.acknowledge(received);
        }
    }

    private static String topic(String suffix) {
        return "persistent://public/default/graalvm-client-all-" + suffix;
    }

    private static boolean brokerAdminIsReady() throws IOException {
        HttpURLConnection connection =
                (HttpURLConnection) URI.create(ADMIN_URL + "/admin/v2/clusters")
                        .toURL()
                        .openConnection();
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(10_000);
        try {
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return false;
            }
            try (InputStream input = connection.getInputStream()) {
                String response = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                return !response.isBlank();
            }
        } finally {
            connection.disconnect();
        }
    }

    private static void waitUntil(Callable<Boolean> condition, Duration timeout)
            throws Exception {
        Exception lastException = null;
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            try {
                if (condition.call()) {
                    return;
                }
            } catch (Exception e) {
                lastException = e;
            }
            try {
                Thread.sleep(1_000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for Pulsar", e);
            }
        }
        String message = "Pulsar did not become ready within " + timeout;
        throw lastException == null
                ? new IllegalStateException(message)
                : new IllegalStateException(message, lastException);
    }

    private static void removeExistingContainer() throws Exception {
        runDocker(false, "rm", "--force", CONTAINER_NAME);
    }

    private static void runDocker(boolean requireSuccess, String... arguments)
            throws Exception {
        String[] command = new String[arguments.length + 1];
        command[0] = "docker";
        System.arraycopy(arguments, 0, command, 1, arguments.length);
        Process process = new ProcessBuilder(command).inheritIO().start();
        if (!process.waitFor(PROCESS_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
            process.destroyForcibly();
            process.waitFor(10, TimeUnit.SECONDS);
            throw new IllegalStateException(
                    "Docker command timed out: " + String.join(" ", command));
        }
        if (requireSuccess && process.exitValue() != 0) {
            throw new IllegalStateException(
                    "Docker command failed with exit code "
                            + process.exitValue()
                            + ": "
                            + String.join(" ", command));
        }
    }

    public static final class PurchaseOrder {
        private String orderId;
        private int units;

        public PurchaseOrder() {}

        public PurchaseOrder(String orderId, int units) {
            this.orderId = orderId;
            this.units = units;
        }

        public String getOrderId() {
            return orderId;
        }

        public void setOrderId(String orderId) {
            this.orderId = orderId;
        }

        public int getUnits() {
            return units;
        }

        public void setUnits(int units) {
            this.units = units;
        }
    }
}
