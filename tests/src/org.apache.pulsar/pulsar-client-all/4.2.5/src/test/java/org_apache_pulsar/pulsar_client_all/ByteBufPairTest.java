/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import org.apache.pulsar.client.admin.PulsarAdmin;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.Schema;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ByteBufPairTest {
    private static final String CONTAINER_NAME = "graalvm-pulsar-client-all-test";
    private static final String HTTP_URL = "http://localhost:8080";
    private static final String SERVICE_URL = "pulsar://localhost:6650";
    private static final String TOPIC = "persistent://public/default/reachability-metadata";

    private Process pulsar;

    @BeforeAll
    void startPulsar() throws Exception {
        removeExistingContainer();
        pulsar = new ProcessBuilder(
                        "docker",
                        "run",
                        "--rm",
                        "--name",
                        CONTAINER_NAME,
                        "-p",
                        "6650:6650",
                        "-p",
                        "8080:8080",
                        "apachepulsar/pulsar:4.2.5",
                        "bin/pulsar",
                        "standalone",
                        "--no-functions-worker",
                        "-nss")
                .inheritIO()
                .start();

        waitUntil(this::standaloneNamespaceIsReady, Duration.ofSeconds(120), Duration.ofSeconds(1));
    }

    @AfterAll
    void stopPulsar() throws Exception {
        if (pulsar != null && pulsar.isAlive()) {
            pulsar.destroy();
            if (!pulsar.waitFor(10, TimeUnit.SECONDS)) {
                pulsar.destroyForcibly();
                pulsar.waitFor(10, TimeUnit.SECONDS);
            }
        }
        removeExistingContainer();
    }

    @Test
    void publishesAndConsumesThroughTheBinaryProtocol() throws Exception {
        try (PulsarAdmin admin = PulsarAdmin.builder()
                        .serviceHttpUrl(HTTP_URL)
                        .connectionTimeout(10, TimeUnit.SECONDS)
                        .readTimeout(30, TimeUnit.SECONDS)
                        .build();
                PulsarClient client = PulsarClient.builder()
                        .serviceUrl(SERVICE_URL)
                        .connectionTimeout(10, TimeUnit.SECONDS)
                        .operationTimeout(30, TimeUnit.SECONDS)
                        .build();
                Consumer<String> consumer = client.newConsumer(Schema.STRING)
                        .topic(TOPIC)
                        .subscriptionName("reachability-metadata-subscription")
                        .subscribe();
                Producer<String> producer = client.newProducer(Schema.STRING).topic(TOPIC).create()) {
            assertThat(admin.clusters().getClusters()).contains("standalone");

            producer.newMessage()
                    .key("integration-key")
                    .property("source", "native-image-test")
                    .value("pulsar-message")
                    .send();
            Message<String> message = consumer.receive(30, TimeUnit.SECONDS);

            assertThat(message).isNotNull();
            assertThat(message.getValue()).isEqualTo("pulsar-message");
            assertThat(message.getKey()).isEqualTo("integration-key");
            assertThat(message.getProperty("source")).isEqualTo("native-image-test");
            consumer.acknowledge(message);
            assertThat(admin.topics().getStats(TOPIC).getSubscriptions())
                    .containsKey("reachability-metadata-subscription");
        }
    }

    private boolean standaloneNamespaceIsReady() throws Exception {
        if (!pulsar.isAlive()) {
            throw new IllegalStateException("Pulsar container exited before becoming ready");
        }
        java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(HTTP_URL + "/admin/v2/namespaces/public"))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return response.statusCode() == 200 && response.body().contains("public/default");
    }

    private static void removeExistingContainer() throws Exception {
        Process process = new ProcessBuilder("docker", "rm", "-f", CONTAINER_NAME)
                .redirectErrorStream(true)
                .start();
        if (!process.waitFor(10, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            process.waitFor(10, TimeUnit.SECONDS);
        }
    }

    private static void waitUntil(
            Callable<Boolean> condition, Duration timeout, Duration sleepTime) throws Exception {
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
            Thread.sleep(sleepTime.toMillis());
        }
        throw new IllegalStateException("Pulsar was not ready within " + timeout, lastException);
    }
}
