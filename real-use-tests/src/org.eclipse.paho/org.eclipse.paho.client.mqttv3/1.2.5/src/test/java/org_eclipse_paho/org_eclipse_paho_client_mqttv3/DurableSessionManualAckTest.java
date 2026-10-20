/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_paho.org_eclipse_paho_client_mqttv3;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.eclipse.paho.client.mqttv3.persist.MqttDefaultFilePersistence;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/// Exercises durable sessions on the blocking `MqttClient`: a `cleanSession=false`
/// subscriber backed by `MqttDefaultFilePersistence` that acknowledges messages manually.
///
/// The scenario is a ledger service: it subscribes once, goes offline while invoices are
/// published, and on reconnect receives the backlog the broker kept for its session. It
/// acknowledges only what it has booked, so an invoice it never acknowledged comes back
/// marked as a duplicate on the next connection, and a clean connection drops the session.
public class DurableSessionManualAckTest {

    private static final String IMAGE = "eclipse-mosquitto:2.0.22";

    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(90);

    private static final long TIMEOUT_MS = 10_000;

    private static String containerName;

    private static String serverUri;

    @TempDir
    Path persistenceDir;

    @BeforeAll
    static void startBroker() throws Exception {
        containerName = "paho-durable-" + System.nanoTime();
        docker("run", "-d", "--name", containerName, "-p", "127.0.0.1::1883", IMAGE,
                "mosquitto", "-c", "/mosquitto-no-auth.conf");
        String mapping = docker("port", containerName, "1883/tcp").lines().findFirst().orElseThrow();
        serverUri = "tcp://127.0.0.1:" + mapping.substring(mapping.lastIndexOf(':') + 1);
        awaitBroker();
    }

    @AfterAll
    static void stopBroker() throws Exception {
        if (containerName != null) {
            docker("rm", "-f", "-v", containerName);
        }
    }

    @Test
    void ledgerReceivesBacklogAndRedeliveryAcrossReconnects() throws Exception {
        String ledgerId = "ledger-" + System.nanoTime();
        Inbox inbox = new Inbox();
        MqttClient ledger = new MqttClient(serverUri, ledgerId,
                new MqttDefaultFilePersistence(persistenceDir.toString()));
        ledger.setTimeToWait(TIMEOUT_MS);
        ledger.setManualAcks(true);
        ledger.setCallback(inbox);
        MqttClient billing = new MqttClient(serverUri, "billing-" + System.nanoTime(), new MemoryPersistence());
        billing.setTimeToWait(TIMEOUT_MS);
        try {
            IMqttToken firstConnect = ledger.connectWithResult(options(false));
            assertThat(firstConnect.getSessionPresent()).isFalse();
            assertThat(ledger.getServerURI()).isEqualTo(serverUri);
            assertThat(ledger.getCurrentServerURI()).isEqualTo(serverUri);
            IMqttToken subscription = ledger.subscribeWithResponse("invoices/#", 1);
            assertThat(subscription.getGrantedQos()).containsExactly(1);
            ledger.disconnect();
            assertThat(ledger.isConnected()).isFalse();

            billing.connect(options(true));
            billing.publish("invoices/1001", bytes("amount=120.00"), 1, false);
            billing.publish("invoices/1002", bytes("amount=75.50"), 1, false);
            billing.publish("invoices/1003", bytes("amount=9.99"), 0, false);

            IMqttToken resumed = ledger.connectWithResult(options(false));
            assertThat(resumed.getSessionPresent()).isTrue();
            Received first = inbox.next();
            Received second = inbox.next();
            assertThat(first.topic).isEqualTo("invoices/1001");
            assertThat(first.payload).isEqualTo("amount=120.00");
            assertThat(first.qos).isEqualTo(1);
            assertThat(first.duplicate).isFalse();
            assertThat(second.topic).isEqualTo("invoices/1002");
            assertThat(second.payload).isEqualTo("amount=75.50");
            assertThat(second.duplicate).isFalse();
            ledger.messageArrivedComplete(first.id, first.qos);
            ledger.disconnect();

            IMqttToken retried = ledger.connectWithResult(options(false));
            assertThat(retried.getSessionPresent()).isTrue();
            Received redelivered = inbox.next();
            assertThat(redelivered.topic).isEqualTo("invoices/1002");
            assertThat(redelivered.payload).isEqualTo("amount=75.50");
            assertThat(redelivered.duplicate).isTrue();
            ledger.messageArrivedComplete(redelivered.id, redelivered.qos);

            billing.publish("invoices/1004", bytes("amount=310.25"), 1, false);
            Received live = inbox.next();
            assertThat(live.topic).isEqualTo("invoices/1004");
            assertThat(live.duplicate).isFalse();
            ledger.messageArrivedComplete(live.id, live.qos);
            assertThat(inbox.isEmpty()).isTrue();
            assertThat(inbox.lost).isEmpty();
            ledger.disconnect();

            IMqttToken clean = ledger.connectWithResult(options(true));
            assertThat(clean.getSessionPresent()).isFalse();
            ledger.disconnect();
            IMqttToken fresh = ledger.connectWithResult(options(false));
            assertThat(fresh.getSessionPresent()).isFalse();
            billing.publish("invoices/1005", bytes("amount=1.00"), 1, false);
            ledger.subscribe("invoices/1006", 1);
            billing.publish("invoices/1006", bytes("amount=2.00"), 1, false);
            Received afterReset = inbox.next();
            assertThat(afterReset.topic).isEqualTo("invoices/1006");
            ledger.messageArrivedComplete(afterReset.id, afterReset.qos);
            assertThat(inbox.isEmpty()).isTrue();
        } finally {
            closeQuietly(billing);
            closeQuietly(ledger);
        }
    }

    /// Queues every message the ledger receives until it books and acknowledges it.
    private static final class Inbox implements MqttCallback {

        private final BlockingQueue<Received> arrived = new LinkedBlockingQueue<>();

        private final List<Throwable> lost = new ArrayList<>();

        @Override
        public void connectionLost(Throwable cause) {
            synchronized (lost) {
                lost.add(cause);
            }
        }

        @Override
        public void messageArrived(String topic, MqttMessage message) {
            arrived.add(new Received(topic, message));
        }

        @Override
        public void deliveryComplete(IMqttDeliveryToken token) {
        }

        Received next() throws InterruptedException {
            Received message = arrived.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS);
            assertThat(message).as("message within %d ms", TIMEOUT_MS).isNotNull();
            return message;
        }

        boolean isEmpty() {
            return arrived.isEmpty();
        }
    }

    /// One message as the ledger's callback observed it.
    private static final class Received {

        private final String topic;

        private final String payload;

        private final int id;

        private final int qos;

        private final boolean duplicate;

        Received(String topic, MqttMessage message) {
            this.topic = topic;
            this.payload = new String(message.getPayload(), StandardCharsets.UTF_8);
            this.id = message.getId();
            this.qos = message.getQos();
            this.duplicate = message.isDuplicate();
        }
    }

    private static MqttConnectOptions options(boolean cleanSession) {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setMqttVersion(MqttConnectOptions.MQTT_VERSION_3_1_1);
        options.setCleanSession(cleanSession);
        options.setAutomaticReconnect(false);
        options.setKeepAliveInterval(30);
        return options;
    }

    private static void closeQuietly(MqttClient client) throws MqttException {
        if (client.isConnected()) {
            client.disconnectForcibly(1_000, 1_000);
        }
        client.close();
    }

    private static void awaitBroker() throws Exception {
        long deadline = System.nanoTime() + STARTUP_TIMEOUT.toNanos();
        MqttException last = null;
        while (System.nanoTime() < deadline) {
            MqttClient probe = new MqttClient(serverUri, "startup-probe", new MemoryPersistence());
            try {
                probe.connect(options(true));
                probe.disconnect();
                return;
            } catch (MqttException e) {
                last = e;
            } finally {
                closeQuietly(probe);
            }
            Thread.sleep(500);
        }
        throw new IllegalStateException("Mosquitto did not accept connections in time", last);
    }

    private static String docker(String... arguments) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("docker");
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output;
        try (InputStream stream = process.getInputStream()) {
            output = new String(stream.readAllBytes(), StandardCharsets.UTF_8).trim();
        }
        assertThat(process.waitFor(60, TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).as(output).isZero();
        return output;
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
