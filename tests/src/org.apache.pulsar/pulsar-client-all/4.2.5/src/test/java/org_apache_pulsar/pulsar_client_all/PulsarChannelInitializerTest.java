/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Duration;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.pulsar.client.api.BatchReceivePolicy;
import org.apache.pulsar.client.api.CompressionType;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.Messages;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.client.api.SubscriptionInitialPosition;
import org.apache.pulsar.shade.javax.ws.rs.client.Client;
import org.apache.pulsar.shade.javax.ws.rs.client.ClientBuilder;
import org.apache.pulsar.shade.javax.ws.rs.core.MediaType;
import org.apache.pulsar.shade.javax.ws.rs.core.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class PulsarChannelInitializerTest {

    private static final String SERVICE_URL = "pulsar://localhost:6650";
    private static final Duration STARTUP_TIMEOUT = Duration.ofMinutes(5);

    private Process broker;
    private PulsarClient client;

    @BeforeAll
    void startBroker() throws Exception {
        broker = new ProcessBuilder(
                        "docker",
                        "run",
                        "--rm",
                        "-p",
                        "6650:6650",
                        "-p",
                        "8080:8080",
                        "apachepulsar/pulsar:4.2.5",
                        "bin/pulsar",
                        "standalone")
                .inheritIO()
                .start();
        client = waitForBroker();
    }

    @AfterAll
    void stopBroker() throws Exception {
        if (client != null) {
            client.close();
        }
        if (broker != null && broker.isAlive()) {
            broker.destroy();
            if (!broker.waitFor(20, TimeUnit.SECONDS)) {
                broker.destroyForcibly();
                broker.waitFor(20, TimeUnit.SECONDS);
            }
        }
    }

    @Test
    @Timeout(60)
    void publishesAndConsumesMessagesWithEveryCompressionCodec() throws Exception {
        final String topic = "persistent://public/default/metadata-" + UUID.randomUUID();
        final EnumSet<CompressionType> compressionTypes = EnumSet.allOf(CompressionType.class);
        final BatchReceivePolicy batchPolicy = BatchReceivePolicy.builder()
                .maxNumMessages(compressionTypes.size())
                .timeout(10, TimeUnit.SECONDS)
                .build();

        try (Consumer<String> consumer = client.newConsumer(Schema.STRING)
                .topic(topic)
                .subscriptionName("metadata-subscription")
                .subscriptionInitialPosition(SubscriptionInitialPosition.Earliest)
                .batchReceivePolicy(batchPolicy)
                .subscribe()) {
            for (CompressionType compressionType : compressionTypes) {
                try (Producer<String> producer = client.newProducer(Schema.STRING)
                        .topic(topic)
                        .producerName("producer-" + compressionType.name().toLowerCase())
                        .compressionType(compressionType)
                        .compressionMinMsgBodySize(0)
                        .sendTimeout(30, TimeUnit.SECONDS)
                        .create()) {
                    producer.newMessage()
                            .property("compression", compressionType.name())
                            .value("payload-" + compressionType.name())
                            .send();
                }
            }

            final Messages<String> received = consumer.batchReceive();
            final Set<String> values = new HashSet<>();
            for (Message<String> message : received) {
                values.add(message.getValue());
                assertThat(message.getProperty("compression")).isNotBlank();
                consumer.acknowledge(message);
            }

            assertThat(values).containsExactlyInAnyOrderElementsOf(compressionTypes.stream()
                    .map(type -> "payload-" + type.name())
                    .toList());
        }
    }

    @Test
    @Timeout(60)
    void queriesBrokerAdministrationEndpointWithJerseyClient() {
        final Client httpClient = ClientBuilder.newBuilder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();
        try {
            try (Response response = httpClient.target("http://localhost:8080/admin/v2/clusters")
                    .request(MediaType.APPLICATION_JSON_TYPE)
                    .get()) {
                assertThat(response.getStatus()).isEqualTo(200);
                assertThat(response.readEntity(String.class)).contains("standalone");
            }
        } finally {
            httpClient.close();
        }
    }

    private PulsarClient waitForBroker() throws Exception {
        final long deadline = System.nanoTime() + STARTUP_TIMEOUT.toNanos();
        Exception lastFailure = null;
        while (System.nanoTime() < deadline) {
            if (!broker.isAlive()) {
                throw new IllegalStateException("Pulsar broker process exited with code " + broker.exitValue());
            }

            PulsarClient candidate = null;
            try {
                candidate = newClient();
                candidate.getPartitionsForTopic("persistent://public/default/readiness")
                        .get(15, TimeUnit.SECONDS);
                return candidate;
            } catch (Exception e) {
                lastFailure = e;
                closeCandidate(candidate);
                Thread.sleep(2000);
            }
        }
        throw new IllegalStateException("Pulsar broker did not become ready within " + STARTUP_TIMEOUT, lastFailure);
    }

    private static PulsarClient newClient() throws PulsarClientException {
        return PulsarClient.builder()
                .serviceUrl(SERVICE_URL)
                .connectionTimeout(15, TimeUnit.SECONDS)
                .lookupTimeout(15, TimeUnit.SECONDS)
                .operationTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    private static void closeCandidate(PulsarClient candidate) throws IOException {
        if (candidate != null) {
            candidate.close();
        }
    }
}
