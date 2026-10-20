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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Covers client identity and MQTT protocol-version negotiation: client IDs from
/// `MqttClient.generateClientId`, a broker session taken over by a second connection with
/// the same client ID, MQTT 3.1 (`MQTT_VERSION_3_1`) next to 3.1.1 and the
/// `MQTT_VERSION_DEFAULT` fallback, and empty client IDs the broker accepts or refuses.
///
/// The scenario is a kiosk fleet. A kiosk stores a generated client ID and keeps a durable
/// session. When its replacement unit comes online with that ID, the broker drops the old
/// connection and hands the session to the new unit. Legacy kiosks still speak MQTT 3.1,
/// and throwaway diagnostic probes connect with an empty client ID.
public class ClientIdentityProtocolVersionTest {

    private static final String IMAGE = "eclipse-mosquitto:2.0.22";

    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(90);

    private static final long TIMEOUT_MS = 10_000;

    private static String containerName;

    private static String serverUri;

    @BeforeAll
    static void startBroker() throws Exception {
        containerName = "paho-identity-" + System.nanoTime();
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
    void replacementKioskTakesOverTheSessionOfItsClientId() throws Exception {
        String kioskId = MqttClient.generateClientId();
        Inbox original = new Inbox();
        Inbox replacement = new Inbox();
        MqttClient originalUnit = new MqttClient(serverUri, kioskId, new MemoryPersistence());
        MqttClient replacementUnit = new MqttClient(serverUri, kioskId, new MemoryPersistence());
        MqttClient operator = new MqttClient(serverUri, MqttClient.generateClientId(), new MemoryPersistence());
        try {
            originalUnit.setCallback(original);
            IMqttToken firstConnect = originalUnit.connectWithResult(durable(MqttConnectOptions.MQTT_VERSION_3_1_1));
            assertThat(firstConnect.getSessionPresent()).isFalse();
            originalUnit.subscribe(commandTopic(kioskId), 1);

            replacementUnit.setCallback(replacement);
            IMqttToken takeover = replacementUnit.connectWithResult(durable(MqttConnectOptions.MQTT_VERSION_3_1_1));
            assertThat(takeover.getSessionPresent()).isTrue();
            assertThat(replacementUnit.getClientId()).isEqualTo(originalUnit.getClientId()).isEqualTo(kioskId);

            Throwable cause = original.awaitLoss();
            assertThat(cause).isInstanceOf(MqttException.class);
            assertThat(((MqttException) cause).getReasonCode()).isEqualTo(MqttException.REASON_CODE_CONNECTION_LOST);
            assertThat(originalUnit.isConnected()).isFalse();
            assertThat(replacementUnit.isConnected()).isTrue();

            operator.connect(clean(MqttConnectOptions.MQTT_VERSION_3_1_1));
            operator.publish(commandTopic(kioskId), bytes("show-maintenance-banner"), 1, false);
            assertThat(replacement.next()).isEqualTo(commandTopic(kioskId) + "=show-maintenance-banner");
            assertThat(original.received).isEmpty();
            assertThat(replacement.lost).isEmpty();
        } finally {
            closeQuietly(operator);
            closeQuietly(replacementUnit);
            closeQuietly(originalUnit);
        }
    }

    @Test
    void legacyMqtt31KioskResumesItsDurableSession() throws Exception {
        String kioskId = MqttClient.generateClientId();
        assertThat(kioskId).startsWith("paho").hasSizeLessThanOrEqualTo(23);
        assertThat(MqttClient.generateClientId()).isNotEqualTo(kioskId);

        Inbox inbox = new Inbox();
        MqttClient kiosk = new MqttClient(serverUri, kioskId, new MemoryPersistence());
        MqttClient operator = new MqttClient(serverUri, MqttClient.generateClientId(), new MemoryPersistence());
        try {
            kiosk.setCallback(inbox);
            MqttConnectOptions legacy = durable(MqttConnectOptions.MQTT_VERSION_3_1);
            assertThat(kiosk.connectWithResult(legacy).getSessionPresent()).isFalse();
            assertThat(legacy.getMqttVersion()).isEqualTo(MqttConnectOptions.MQTT_VERSION_3_1);
            kiosk.subscribe(commandTopic(kioskId), 1);
            kiosk.disconnect(1_000);

            operator.connect(clean(MqttConnectOptions.MQTT_VERSION_3_1_1));
            operator.publish(commandTopic(kioskId), bytes("refresh-catalogue"), 1, false);

            // MQTT 3.1 has no session-present flag, so a resumed session still reports false.
            IMqttToken legacyResume = kiosk.connectWithResult(legacy);
            assertThat(legacyResume.getSessionPresent()).isFalse();
            assertThat(inbox.next()).isEqualTo(commandTopic(kioskId) + "=refresh-catalogue");
            kiosk.disconnect(1_000);

            operator.publish(commandTopic(kioskId), bytes("rotate-logs"), 1, false);
            IMqttToken upgradedResume = kiosk.connectWithResult(durable(MqttConnectOptions.MQTT_VERSION_3_1_1));
            assertThat(upgradedResume.getSessionPresent()).isTrue();
            assertThat(inbox.next()).isEqualTo(commandTopic(kioskId) + "=rotate-logs");
            assertThat(inbox.lost).isEmpty();
        } finally {
            closeQuietly(operator);
            closeQuietly(kiosk);
        }
    }

    @Test
    void emptyClientIdsAreAcceptedOnlyForCleanMqtt311Sessions() throws Exception {
        assertThatThrownBy(() -> new MqttClient(serverUri, null, new MemoryPersistence()))
                .isInstanceOf(IllegalArgumentException.class);

        Inbox inbox = new Inbox();
        MqttClient probe = new MqttClient(serverUri, "", new MemoryPersistence());
        try {
            probe.setCallback(inbox);
            IMqttToken connect = probe.connectWithResult(clean(MqttConnectOptions.MQTT_VERSION_3_1_1));
            assertThat(connect.getSessionPresent()).isFalse();
            assertThat(probe.getClientId()).isEmpty();
            String echoTopic = "fleet/diagnostics/" + System.nanoTime();
            probe.subscribe(echoTopic, 1);
            probe.publish(echoTopic, bytes("latency-probe"), 1, false);
            assertThat(inbox.next()).isEqualTo(echoTopic + "=latency-probe");
            probe.disconnect(1_000);
        } finally {
            closeQuietly(probe);
        }

        assertRefusedEmptyClientId(durable(MqttConnectOptions.MQTT_VERSION_3_1_1));
        assertRefusedEmptyClientId(clean(MqttConnectOptions.MQTT_VERSION_3_1));

        // The default version retries as MQTT 3.1 and restores the option once both are refused.
        MqttConnectOptions negotiated = durable(MqttConnectOptions.MQTT_VERSION_DEFAULT);
        assertRefusedEmptyClientId(negotiated);
        assertThat(negotiated.getMqttVersion()).isEqualTo(MqttConnectOptions.MQTT_VERSION_DEFAULT);
    }

    private static void assertRefusedEmptyClientId(MqttConnectOptions options) throws MqttException {
        MqttClient probe = new MqttClient(serverUri, "", new MemoryPersistence());
        try {
            assertThatThrownBy(() -> probe.connect(options))
                    .isInstanceOfSatisfying(MqttException.class, e -> assertThat(e.getReasonCode())
                            .isEqualTo(MqttException.REASON_CODE_INVALID_CLIENT_ID));
            assertThat(probe.isConnected()).isFalse();
        } finally {
            closeQuietly(probe);
        }
    }

    /// Collects the commands a kiosk receives and the cause of any lost connection.
    private static final class Inbox implements MqttCallback {

        private final BlockingQueue<String> messages = new LinkedBlockingQueue<>();

        private final List<String> received = new ArrayList<>();

        private final BlockingQueue<Throwable> lost = new LinkedBlockingQueue<>();

        @Override
        public void messageArrived(String topic, MqttMessage message) {
            String entry = topic + "=" + new String(message.getPayload(), StandardCharsets.UTF_8);
            synchronized (received) {
                received.add(entry);
            }
            messages.add(entry);
        }

        @Override
        public void connectionLost(Throwable cause) {
            lost.add(cause);
        }

        @Override
        public void deliveryComplete(IMqttDeliveryToken token) {
        }

        String next() throws InterruptedException {
            String message = messages.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS);
            assertThat(message).as("kiosk message").isNotNull();
            return message;
        }

        Throwable awaitLoss() throws InterruptedException {
            Throwable cause = lost.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS);
            assertThat(cause).as("lost connection").isNotNull();
            return cause;
        }
    }

    private static MqttConnectOptions durable(int mqttVersion) {
        MqttConnectOptions options = clean(mqttVersion);
        options.setCleanSession(false);
        return options;
    }

    private static MqttConnectOptions clean(int mqttVersion) {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setMqttVersion(mqttVersion);
        options.setCleanSession(true);
        options.setAutomaticReconnect(false);
        options.setKeepAliveInterval(30);
        return options;
    }

    private static String commandTopic(String kioskId) {
        return "fleet/" + kioskId + "/cmd";
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
            MqttClient probe = new MqttClient(serverUri, "startup-probe-" + System.nanoTime(),
                    new MemoryPersistence());
            try {
                probe.connect(clean(MqttConnectOptions.MQTT_VERSION_3_1_1));
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
