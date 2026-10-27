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
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/// Exercises the WebSocket transport: `ws://` server URIs, which the client resolves to a
/// WebSocket network module and tunnels MQTT through after an HTTP upgrade handshake.
///
/// The scenario is a browser-facing dashboard gateway: a Mosquitto broker listens for plain
/// TCP and for WebSockets, the gateway connects over WebSockets with custom upgrade headers,
/// a backend sensor publishes over TCP, and traffic flows both ways across the transports,
/// including a firmware image large enough to need WebSocket's 64-bit frame length.
public class WebSocketTransportTest {

    private static final String IMAGE = "eclipse-mosquitto:2.0.22";

    private static final String BROKER_CONFIG = """
            listener 1883
            listener 8080
            protocol websockets
            allow_anonymous true
            """;

    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(90);

    private static final long TIMEOUT_MS = 10_000;

    private static String containerName;

    private static String tcpUri;

    private static String webSocketEndpoint;

    private static String webSocketUri;

    @BeforeAll
    static void startBroker() throws Exception {
        containerName = "paho-websocket-" + System.nanoTime();
        docker("run", "-d", "--name", containerName,
                "-p", "127.0.0.1::1883", "-p", "127.0.0.1::8080", IMAGE,
                "sh", "-c", "printf '%s' \"$0\" > /tmp/ws.conf && exec mosquitto -c /tmp/ws.conf",
                BROKER_CONFIG);
        tcpUri = "tcp://127.0.0.1:" + hostPort("1883/tcp");
        webSocketEndpoint = "ws://127.0.0.1:" + hostPort("8080/tcp");
        webSocketUri = webSocketEndpoint + "/mqtt";
        awaitBroker(tcpUri);
        awaitBroker(webSocketUri);
    }

    @AfterAll
    static void stopBroker() throws Exception {
        if (containerName != null) {
            docker("rm", "-f", "-v", containerName);
        }
    }

    @Test
    void dashboardExchangesTrafficWithTcpBackendOverWebSockets() throws Exception {
        Inbox dashboardInbox = new Inbox();
        MqttClient dashboard = new MqttClient(webSocketUri, "dashboard-" + System.nanoTime(),
                new MemoryPersistence());
        dashboard.setTimeToWait(TIMEOUT_MS);
        dashboard.setCallback(dashboardInbox);
        Inbox sensorInbox = new Inbox();
        MqttClient sensor = new MqttClient(tcpUri, "sensor-" + System.nanoTime(), new MemoryPersistence());
        sensor.setTimeToWait(TIMEOUT_MS);
        sensor.setCallback(sensorInbox);
        try {
            MqttConnectOptions dashboardOptions = options();
            Properties upgradeHeaders = new Properties();
            upgradeHeaders.setProperty("X-Dashboard-Tenant", "plant-3");
            upgradeHeaders.setProperty("User-Agent", "dashboard-gateway/1.0");
            dashboardOptions.setCustomWebSocketHeaders(upgradeHeaders);
            dashboardOptions.setServerURIs(new String[] {webSocketUri});
            assertThat(dashboardOptions.getCustomWebSocketHeaders())
                    .containsEntry("X-Dashboard-Tenant", "plant-3");

            dashboard.connect(dashboardOptions);
            assertThat(dashboard.isConnected()).isTrue();
            assertThat(dashboard.getServerURI()).isEqualTo(webSocketUri);
            // The network module reports the endpoint it dialled, without the upgrade path.
            assertThat(dashboard.getCurrentServerURI()).isEqualTo(webSocketEndpoint);
            dashboard.subscribe("plant/+/temperature", 1);
            dashboard.subscribe("plant/firmware", 1);

            sensor.connect(options());
            sensor.subscribe("plant/commands/#", 2);

            sensor.publish("plant/line-1/temperature", bytes("21.5"), 1, false);
            sensor.publish("plant/line-2/temperature", bytes("19.0"), 0, false);
            Received first = dashboardInbox.next();
            Received second = dashboardInbox.next();
            assertThat(first.topic).isEqualTo("plant/line-1/temperature");
            assertThat(first.text()).isEqualTo("21.5");
            assertThat(first.qos).isEqualTo(1);
            assertThat(second.topic).isEqualTo("plant/line-2/temperature");
            assertThat(second.text()).isEqualTo("19.0");
            assertThat(second.qos).isZero();

            String command = "{\"line\":\"line-1\",\"setpoint\":20.0," + "\"note\":\"" + "x".repeat(300) + "\"}";
            MqttMessage commandMessage = new MqttMessage(bytes(command));
            commandMessage.setQos(2);
            dashboard.publish("plant/commands/line-1", commandMessage);
            Received received = sensorInbox.next();
            assertThat(received.topic).isEqualTo("plant/commands/line-1");
            assertThat(received.qos).isEqualTo(2);
            assertThat(received.text()).isEqualTo(command);
            assertThat(dashboardInbox.delivered.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isNotNull();

            byte[] firmware = new byte[96 * 1024];
            for (int i = 0; i < firmware.length; i++) {
                firmware[i] = (byte) (i * 31 + 7);
            }
            sensor.publish("plant/firmware", firmware, 1, false);
            Received image = dashboardInbox.next();
            assertThat(image.topic).isEqualTo("plant/firmware");
            assertThat(image.payload.length).isEqualTo(firmware.length);
            assertThat(Arrays.equals(image.payload, firmware)).isTrue();

            assertThat(dashboardInbox.isEmpty()).isTrue();
            assertThat(dashboardInbox.lost).isEmpty();
            assertThat(sensorInbox.lost).isEmpty();

            dashboard.disconnect();
            assertThat(dashboard.isConnected()).isFalse();
            dashboard.connect(dashboardOptions);
            dashboard.subscribe("plant/line-3/temperature", 1);
            sensor.publish("plant/line-3/temperature", bytes("23.25"), 1, false);
            Received afterReconnect = dashboardInbox.next();
            assertThat(afterReconnect.topic).isEqualTo("plant/line-3/temperature");
            assertThat(afterReconnect.text()).isEqualTo("23.25");
        } finally {
            closeQuietly(sensor);
            closeQuietly(dashboard);
        }
    }

    /// Collects what one client's callback observes: arrivals, completed deliveries, losses.
    private static final class Inbox implements MqttCallback {

        private final BlockingQueue<Received> arrived = new LinkedBlockingQueue<>();

        private final BlockingQueue<IMqttDeliveryToken> delivered = new LinkedBlockingQueue<>();

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
            delivered.add(token);
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

    /// One message as a client's callback observed it.
    private static final class Received {

        private final String topic;

        private final byte[] payload;

        private final int qos;

        Received(String topic, MqttMessage message) {
            this.topic = topic;
            this.payload = message.getPayload();
            this.qos = message.getQos();
        }

        String text() {
            return new String(payload, StandardCharsets.UTF_8);
        }
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

    private static String hostPort(String containerPort) throws Exception {
        String mapping = docker("port", containerName, containerPort).lines().findFirst().orElseThrow();
        return mapping.substring(mapping.lastIndexOf(':') + 1);
    }

    private static void awaitBroker(String uri) throws Exception {
        long deadline = System.nanoTime() + STARTUP_TIMEOUT.toNanos();
        MqttException last = null;
        while (System.nanoTime() < deadline) {
            MqttClient probe = new MqttClient(uri, "startup-probe-" + System.nanoTime(), new MemoryPersistence());
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
        throw new IllegalStateException("Mosquitto did not accept connections on " + uri + " in time", last);
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
