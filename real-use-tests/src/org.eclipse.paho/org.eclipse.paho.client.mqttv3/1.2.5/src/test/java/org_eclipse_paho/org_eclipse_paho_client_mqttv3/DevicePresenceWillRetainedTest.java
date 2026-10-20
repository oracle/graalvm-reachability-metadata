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
import org.eclipse.paho.client.mqttv3.MqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttTopic;
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

/// Tracks device presence through Last Will and Testament and retained messages:
/// `MqttConnectOptions.setWill`, retained publishes through the `MqttTopic` handle a
/// connected `MqttClient` returns, and topic-filter matching with `MqttTopic.isMatched`.
///
/// The scenario is a plant presence board: each device registers a retained `offline`
/// will and announces itself with a retained `online` status. A crashed pump makes the
/// broker publish its will, a gracefully stopped valve leaves its `online` status in
/// place, a dashboard that joins later reads the last known state from retained
/// messages, and decommissioning a device clears its retained status.
public class DevicePresenceWillRetainedTest {

    private static final String IMAGE = "eclipse-mosquitto:2.0.22";

    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(90);

    private static final long TIMEOUT_MS = 10_000;

    private static final String STATUS_FILTER = "plant/+/status";

    private static String containerName;

    private static String serverUri;

    @BeforeAll
    static void startBroker() throws Exception {
        containerName = "paho-presence-" + System.nanoTime();
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
    void presenceBoardFollowsWillsAndRetainedStatus() throws Exception {
        PresenceBoard monitorBoard = new PresenceBoard();
        MqttClient monitor = client("monitor");
        MqttClient pump = client("pump-1");
        MqttClient valve = client("valve-2");
        MqttClient registry = client("registry");
        try {
            monitor.setCallback(monitorBoard);
            monitor.connect(options());
            monitor.subscribe(STATUS_FILTER, 1);

            pump.connect(deviceOptions("pump-1"));
            MqttTopic pumpStatus = pump.getTopic(statusTopic("pump-1"));
            assertThat(pumpStatus.getName()).isEqualTo("plant/pump-1/status");
            MqttDeliveryToken pumpOnline = pumpStatus.publish(retained("online"));
            pumpOnline.waitForCompletion(TIMEOUT_MS);
            assertThat(pumpOnline.isComplete()).isTrue();
            assertThat(pumpOnline.getMessage()).isNull();

            valve.connect(deviceOptions("valve-2"));
            valve.getTopic(statusTopic("valve-2")).publish(retained("online")).waitForCompletion(TIMEOUT_MS);

            assertThat(monitorBoard.next()).isEqualTo(new Status("pump-1", "online", false));
            assertThat(monitorBoard.next()).isEqualTo(new Status("valve-2", "online", false));

            pump.disconnectForcibly(0, 1_000, false);
            assertThat(pump.isConnected()).isFalse();
            assertThat(monitorBoard.next()).isEqualTo(new Status("pump-1", "offline", false));

            valve.disconnect(1_000);
            assertThat(valve.isConnected()).isFalse();
            registry.connect(options());
            registry.publish(statusTopic("audit"), bytes("sweep"), 1, false);
            assertThat(monitorBoard.next()).isEqualTo(new Status("audit", "sweep", false));

            assertThat(dashboardSnapshot("dashboard-1", 2)).containsExactlyInAnyOrder(
                    new Status("pump-1", "offline", true),
                    new Status("valve-2", "online", true));
            assertThat(monitorBoard.next()).isEqualTo(new Status("audit", "snapshot-end", false));

            MqttTopic decommission = registry.getTopic(statusTopic("pump-1"));
            decommission.publish(retained("")).waitForCompletion(TIMEOUT_MS);
            Status cleared = monitorBoard.next();
            assertThat(cleared.device).isEqualTo("pump-1");
            assertThat(cleared.state).isEmpty();

            assertThat(dashboardSnapshot("dashboard-2", 1))
                    .containsExactly(new Status("valve-2", "online", true));
            assertThat(monitorBoard.next()).isEqualTo(new Status("audit", "snapshot-end", false));
            assertThat(monitorBoard.foreign).isEmpty();
            assertThat(monitorBoard.lost).isEmpty();
        } finally {
            closeQuietly(registry);
            closeQuietly(valve);
            closeQuietly(pump);
            closeQuietly(monitor);
        }
    }

    @Test
    void topicNamesAndFiltersAreValidatedBeforeTheyReachTheBroker() {
        assertThat(MqttTopic.isMatched(STATUS_FILTER, "plant/pump-1/status")).isTrue();
        assertThat(MqttTopic.isMatched(STATUS_FILTER, "plant/line-a/pump-1/status")).isFalse();
        assertThat(MqttTopic.isMatched("plant/#", "plant/line-a/pump-1/status")).isTrue();
        assertThat(MqttTopic.isMatched("plant/+/+/status", "plant/line-a/pump-1/status")).isTrue();

        MqttTopic.validate(STATUS_FILTER, true);
        MqttTopic.validate("plant/#", true);
        assertThatThrownBy(() -> MqttTopic.validate("plant/#/status", true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MqttTopic.validate("plant/pump+/status", true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MqttTopic.validate(STATUS_FILTER, false))
                .isInstanceOf(IllegalArgumentException.class);

        MqttConnectOptions options = options();
        assertThatThrownBy(() -> options.setWill(STATUS_FILTER, bytes("offline"), 1, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(options.getWillDestination()).isNull();
    }

    /// Reads the retained status of every device a newly connected dashboard is handed.
    private static List<Status> dashboardSnapshot(String clientId, int expected) throws Exception {
        PresenceBoard board = new PresenceBoard();
        MqttClient dashboard = client(clientId);
        try {
            dashboard.setCallback(board);
            dashboard.connect(options());
            dashboard.subscribe(STATUS_FILTER, 1);
            List<Status> snapshot = new ArrayList<>();
            for (int i = 0; i < expected; i++) {
                snapshot.add(board.next());
            }
            dashboard.publish(statusTopic("audit"), bytes("snapshot-end"), 1, false);
            assertThat(board.next()).isEqualTo(new Status("audit", "snapshot-end", false));
            assertThat(board.foreign).isEmpty();
            dashboard.disconnect(1_000);
            return snapshot;
        } finally {
            closeQuietly(dashboard);
        }
    }

    /// Turns status messages into per-device presence updates, routing by topic filter.
    private static final class PresenceBoard implements MqttCallback {

        private final BlockingQueue<Status> updates = new LinkedBlockingQueue<>();

        private final List<String> foreign = new ArrayList<>();

        private final List<Throwable> lost = new ArrayList<>();

        @Override
        public void messageArrived(String topic, MqttMessage message) {
            if (!MqttTopic.isMatched(STATUS_FILTER, topic)) {
                synchronized (foreign) {
                    foreign.add(topic);
                }
                return;
            }
            String device = topic.split(MqttTopic.TOPIC_LEVEL_SEPARATOR)[1];
            String state = new String(message.getPayload(), StandardCharsets.UTF_8);
            updates.add(new Status(device, state, message.isRetained()));
        }

        @Override
        public void connectionLost(Throwable cause) {
            synchronized (lost) {
                lost.add(cause);
            }
        }

        @Override
        public void deliveryComplete(IMqttDeliveryToken token) {
        }

        Status next() throws InterruptedException {
            Status status = updates.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS);
            assertThat(status).as("presence update").isNotNull();
            return status;
        }
    }

    /// One presence update as the board observed it.
    private static final class Status {

        private final String device;

        private final String state;

        private final boolean retained;

        Status(String device, String state, boolean retained) {
            this.device = device;
            this.state = state;
            this.retained = retained;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Status)) {
                return false;
            }
            Status that = (Status) other;
            return device.equals(that.device) && state.equals(that.state) && retained == that.retained;
        }

        @Override
        public int hashCode() {
            return (device.hashCode() * 31 + state.hashCode()) * 31 + Boolean.hashCode(retained);
        }

        @Override
        public String toString() {
            return device + "=" + state + (retained ? " (retained)" : "");
        }
    }

    private static MqttConnectOptions deviceOptions(String device) {
        MqttConnectOptions options = options();
        options.setWill(statusTopic(device), bytes("offline"), 1, true);
        assertThat(options.getWillDestination()).isEqualTo(statusTopic(device));
        assertThat(options.getWillMessage().isRetained()).isTrue();
        assertThat(options.getWillMessage().getQos()).isEqualTo(1);
        return options;
    }

    private static MqttConnectOptions options() {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setMqttVersion(MqttConnectOptions.MQTT_VERSION_3_1_1);
        options.setCleanSession(true);
        options.setAutomaticReconnect(false);
        options.setKeepAliveInterval(30);
        return options;
    }

    private static MqttMessage retained(String state) {
        MqttMessage message = new MqttMessage(bytes(state));
        message.setQos(1);
        message.setRetained(true);
        return message;
    }

    private static String statusTopic(String device) {
        return "plant/" + device + "/status";
    }

    private static MqttClient client(String role) throws MqttException {
        return new MqttClient(serverUri, role + "-" + System.nanoTime(), new MemoryPersistence());
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
