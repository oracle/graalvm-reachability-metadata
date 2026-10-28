/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_paho.org_eclipse_paho_client_mqttv3;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
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
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Exercises the client's PUBLISH packet framing: the variable-length remaining-length
/// encoding at each of its one- to four-byte boundaries, binary payloads carried byte for
/// byte at QoS 0, 1 and 2, zero-length payloads, UTF-8 topic names measured in bytes, and a
/// broker `max_packet_size` that drops a client whose frame exceeds it.
///
/// The scenario is a firmware distribution service: it pushes firmware images and delta
/// patches to a device fleet, sizing each image so the whole PUBLISH frame lands exactly on
/// an encoding boundary, and the device verifies every image against its SHA-256 digest. A
/// topic whose multi-byte name exceeds the protocol's 65535-byte limit is refused before it
/// is sent, and an image above the broker's packet limit costs the publisher its connection.
public class PublishPacketFramingTest {

    private static final String IMAGE = "eclipse-mosquitto:2.0.22";

    /// Packet limit the broker enforces on every frame a client sends it.
    private static final int MAX_PACKET_SIZE = 3 * 1024 * 1024;

    private static final String BROKER_CONFIG = """
            listener 1883
            allow_anonymous true
            max_packet_size %d
            """.formatted(MAX_PACKET_SIZE);

    private static final String BROKER_SCRIPT = """
            mkdir -p /tmp/framing \
            && printf '%s' "$1" > /tmp/framing/mosquitto.conf \
            && exec mosquitto -c /tmp/framing/mosquitto.conf""";

    /// Region and model segments in German and Chinese, so the topic is longer in UTF-8 bytes
    /// than in characters.
    private static final String FLEET_TOPIC = "firmware/Z\u00fcrich/\u56fa\u4ef6";

    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(90);

    private static final long TIMEOUT_MS = 30_000;

    private static String containerName;

    private static String serverUri;

    @BeforeAll
    static void startBroker() throws Exception {
        containerName = "paho-framing-" + System.nanoTime();
        docker("run", "-d", "--name", containerName, "-p", "127.0.0.1::1883", IMAGE,
                "sh", "-c", BROKER_SCRIPT, "sh", BROKER_CONFIG);
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
    void firmwareImagesSurviveEveryRemainingLengthBoundary() throws Exception {
        Device device = new Device();
        MqttClient deviceClient = client("device-", device);
        MqttClient distributor = client("distributor-", null);
        try {
            deviceClient.connect(options());
            assertThat(deviceClient.subscribeWithResponse("firmware/#", 2).getGrantedQos())
                    .containsExactly(2);
            distributor.connect(options());

            int[][] framings = {
                {127, 0}, {128, 1}, {16_383, 2}, {16_384, 0}, {2_097_151, 1}, {2_097_152, 2},
            };
            Random random = new Random(9443);
            for (int[] framing : framings) {
                String topic = FLEET_TOPIC + "/image-" + framing[0];
                int qos = framing[1];
                byte[] image = new byte[payloadLength(topic, qos, framing[0])];
                random.nextBytes(image);
                MqttMessage message = new MqttMessage(image);
                message.setQos(qos);
                distributor.publish(topic, message);

                Received received = device.next();
                assertThat(received.topic).isEqualTo(topic);
                assertThat(received.qos).isEqualTo(qos);
                assertThat(received.length).isEqualTo(image.length);
                assertThat(received.digest).as("SHA-256 of %s", topic).isEqualTo(sha256(image));
            }

            MqttMessage cancelPatch = new MqttMessage(new byte[0]);
            cancelPatch.setQos(1);
            distributor.publish(FLEET_TOPIC + "/patch-cancelled", cancelPatch);
            Received cancelled = device.next();
            assertThat(cancelled.topic).isEqualTo(FLEET_TOPIC + "/patch-cancelled");
            assertThat(cancelled.length).isZero();
            assertThat(cancelled.retained).isFalse();

            String overlong = "firmware/" + "\u56fa".repeat(21_843);
            assertThat(overlong.length()).isLessThan(65_535);
            assertThat(overlong.getBytes(StandardCharsets.UTF_8).length).isGreaterThan(65_535);
            assertThatThrownBy(() -> distributor.publish(overlong, new MqttMessage(bytes("v2"))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid topic length");
            String longest = "firmware/" + "\u56fa".repeat(21_842);
            MqttMessage manifest = new MqttMessage(bytes("manifest-v2"));
            manifest.setQos(1);
            distributor.publish(longest, manifest);
            Received fromLongest = device.next();
            assertThat(fromLongest.topic).isEqualTo(longest);
            assertThat(fromLongest.text()).isEqualTo("manifest-v2");

            assertThat(device.isEmpty()).isTrue();
            assertThat(device.lost).isEmpty();
        } finally {
            closeQuietly(distributor);
            closeQuietly(deviceClient);
        }
    }

    @Test
    void imageAboveBrokerPacketLimitDropsThePublisher() throws Exception {
        Device device = new Device();
        Uplink uplink = new Uplink();
        MqttClient deviceClient = client("device-", device);
        MqttClient distributor = client("distributor-", uplink);
        try {
            deviceClient.connect(options());
            deviceClient.subscribe("firmware/#", 1);
            distributor.connect(options());

            String topic = FLEET_TOPIC + "/full-image";
            byte[] oversized = new byte[MAX_PACKET_SIZE + 1];
            new Random(11).nextBytes(oversized);
            MqttMessage tooLarge = new MqttMessage(oversized);
            tooLarge.setQos(1);
            assertThatThrownBy(() -> distributor.publish(topic, tooLarge))
                    .isInstanceOf(MqttException.class)
                    .extracting(e -> ((MqttException) e).getReasonCode())
                    .isEqualTo((int) MqttException.REASON_CODE_CONNECTION_LOST);
            assertThat(uplink.dropped.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
            assertThat(distributor.isConnected()).isFalse();

            distributor.connect(options());
            byte[] fitting = new byte[payloadLength(topic, 1, MAX_PACKET_SIZE - 1_024)];
            new Random(12).nextBytes(fitting);
            MqttMessage largest = new MqttMessage(fitting);
            largest.setQos(1);
            distributor.publish(topic, largest);
            Received received = device.next();
            assertThat(received.topic).isEqualTo(topic);
            assertThat(received.length).isEqualTo(fitting.length);
            assertThat(received.digest).isEqualTo(sha256(fitting));
            assertThat(device.isEmpty()).isTrue();
        } finally {
            closeQuietly(distributor);
            closeQuietly(deviceClient);
        }
    }

    /// Receives firmware on the device and keeps only the digest of each image.
    private static final class Device implements MqttCallback {

        private final BlockingQueue<Received> arrived = new LinkedBlockingQueue<>();

        private final List<Throwable> lost = new ArrayList<>();

        @Override
        public void connectionLost(Throwable cause) {
            synchronized (lost) {
                lost.add(cause);
            }
        }

        @Override
        public void messageArrived(String topic, MqttMessage message) throws Exception {
            arrived.add(new Received(topic, message));
        }

        @Override
        public void deliveryComplete(IMqttDeliveryToken token) {
        }

        Received next() throws InterruptedException {
            Received message = arrived.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS);
            assertThat(message).as("firmware within %d ms", TIMEOUT_MS).isNotNull();
            return message;
        }

        boolean isEmpty() {
            return arrived.isEmpty();
        }
    }

    /// Signals when the broker drops the distributor's connection.
    private static final class Uplink implements MqttCallback {

        private final CountDownLatch dropped = new CountDownLatch(1);

        @Override
        public void connectionLost(Throwable cause) {
            dropped.countDown();
        }

        @Override
        public void messageArrived(String topic, MqttMessage message) {
        }

        @Override
        public void deliveryComplete(IMqttDeliveryToken token) {
        }
    }

    /// One firmware message as the device observed it.
    private static final class Received {

        private final String topic;

        private final int qos;

        private final boolean retained;

        private final int length;

        private final String digest;

        private final byte[] payload;

        Received(String topic, MqttMessage message) throws Exception {
            this.topic = topic;
            this.qos = message.getQos();
            this.retained = message.isRetained();
            this.payload = message.getPayload();
            this.length = payload.length;
            this.digest = sha256(payload);
        }

        String text() {
            return new String(payload, StandardCharsets.UTF_8);
        }
    }

    /// Payload length that makes a PUBLISH frame's remaining length exactly `remainingLength`:
    /// the two-byte topic length prefix, the UTF-8 topic, and a packet id above QoS 0.
    private static int payloadLength(String topic, int qos, int remainingLength) {
        int header = 2 + topic.getBytes(StandardCharsets.UTF_8).length + (qos > 0 ? 2 : 0);
        return remainingLength - header;
    }

    private static String sha256(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }

    private static MqttClient client(String prefix, MqttCallback callback) throws MqttException {
        MqttClient client = new MqttClient(serverUri, prefix + System.nanoTime(), new MemoryPersistence());
        client.setTimeToWait(TIMEOUT_MS);
        if (callback != null) {
            client.setCallback(callback);
        }
        return client;
    }

    private static MqttConnectOptions options() {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setMqttVersion(MqttConnectOptions.MQTT_VERSION_3_1_1);
        options.setCleanSession(true);
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
                probe.connect(options());
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
