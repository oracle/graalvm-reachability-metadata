/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_paho.org_eclipse_paho_client_mqttv3;

import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Drives the asynchronous client's token and action-listener API: `MqttAsyncClient`
/// connects, subscribes with per-filter message listeners, and publishes at QoS 1 and 2.
///
/// The scenario is a fleet dispatcher: trucks publish telemetry and alerts, the
/// dispatcher subscribes to wildcard filters with one listener per filter, every
/// operation reports through an `IMqttActionListener` carrying a user context, and the
/// publisher observes each QoS handshake completing through its delivery tokens.
public class AsyncClientTokenFlowTest {

    private static final String IMAGE = "eclipse-mosquitto:2.0.22";

    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(90);

    private static final long TIMEOUT_MS = 10_000;

    private static String containerName;

    private static String serverUri;

    @BeforeAll
    static void startBroker() throws Exception {
        containerName = "paho-async-" + System.nanoTime();
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
    void dispatcherReceivesFleetTrafficThroughTokensAndListeners() throws Exception {
        RecordingCallback dispatcherEvents = new RecordingCallback();
        MqttAsyncClient dispatcher = new MqttAsyncClient(serverUri, "dispatcher", new MemoryPersistence());
        dispatcher.setCallback(dispatcherEvents);
        RecordingCallback truckEvents = new RecordingCallback();
        MqttAsyncClient truck = new MqttAsyncClient(serverUri, "truck-7", new MemoryPersistence());
        truck.setCallback(truckEvents);
        try {
            ListenerOutcome connected = new ListenerOutcome();
            IMqttToken connectToken = dispatcher.connect(options(), "dispatcher-connect", connected);
            connected.awaitSuccess();
            assertThat(connected.context).isEqualTo("dispatcher-connect");
            assertThat(connectToken.isComplete()).isTrue();
            assertThat(connectToken.getSessionPresent()).isFalse();
            assertThat(dispatcher.isConnected()).isTrue();
            assertThat(dispatcherEvents.connectComplete.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
            assertThat(dispatcherEvents.reconnect).isFalse();
            assertThat(dispatcherEvents.serverUri).isEqualTo(serverUri);

            BlockingQueue<Received> telemetry = new LinkedBlockingQueue<>();
            BlockingQueue<Received> alerts = new LinkedBlockingQueue<>();
            IMqttMessageListener onTelemetry = (topic, message) -> telemetry.add(new Received(topic, message));
            IMqttMessageListener onAlert = (topic, message) -> alerts.add(new Received(topic, message));
            ListenerOutcome subscribed = new ListenerOutcome();
            IMqttToken subscribeToken = dispatcher.subscribe(
                    new String[] {"fleet/+/telemetry", "fleet/+/alerts/#"}, new int[] {1, 2},
                    "fleet-subscription", subscribed, new IMqttMessageListener[] {onTelemetry, onAlert});
            subscribed.awaitSuccess();
            assertThat(subscribed.context).isEqualTo("fleet-subscription");
            assertThat(subscribeToken.getGrantedQos()).containsExactly(1, 2);
            assertThat(subscribeToken.getTopics()).containsExactly("fleet/+/telemetry", "fleet/+/alerts/#");

            truck.connect(options()).waitForCompletion(TIMEOUT_MS);
            IMqttDeliveryToken position = truck.publish("fleet/truck-7/telemetry",
                    bytes("lat=44.81;lon=20.46"), 1, false);
            MqttMessage brakeAlert = new MqttMessage(bytes("brake-temperature-high"));
            brakeAlert.setQos(2);
            ListenerOutcome alertDelivered = new ListenerOutcome();
            IMqttDeliveryToken alert = truck.publish("fleet/truck-7/alerts/brakes", brakeAlert,
                    "brake-alert", alertDelivered);

            position.waitForCompletion(TIMEOUT_MS);
            alertDelivered.awaitSuccess();
            assertThat(alertDelivered.context).isEqualTo("brake-alert");
            assertThat(alert.isComplete()).isTrue();
            assertThat(truckEvents.awaitDeliveries(2)).containsExactlyInAnyOrder(position.getMessageId(),
                    alert.getMessageId());
            assertThat(truck.getPendingDeliveryTokens()).isEmpty();

            Received telemetryEvent = telemetry.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS);
            assertThat(telemetryEvent).isNotNull();
            assertThat(telemetryEvent.topic).isEqualTo("fleet/truck-7/telemetry");
            assertThat(telemetryEvent.payload).isEqualTo("lat=44.81;lon=20.46");
            assertThat(telemetryEvent.qos).isEqualTo(1);
            assertThat(telemetryEvent.retained).isFalse();
            assertThat(telemetryEvent.duplicate).isFalse();
            Received alertEvent = alerts.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS);
            assertThat(alertEvent).isNotNull();
            assertThat(alertEvent.topic).isEqualTo("fleet/truck-7/alerts/brakes");
            assertThat(alertEvent.payload).isEqualTo("brake-temperature-high");
            assertThat(alertEvent.qos).isEqualTo(2);
            assertThat(dispatcherEvents.unrouted).isEmpty();

            ListenerOutcome unsubscribed = new ListenerOutcome();
            dispatcher.unsubscribe("fleet/+/telemetry", "drop-telemetry", unsubscribed);
            unsubscribed.awaitSuccess();
            assertThat(unsubscribed.context).isEqualTo("drop-telemetry");
            truck.publish("fleet/truck-7/telemetry", bytes("lat=44.82;lon=20.47"), 1, false)
                    .waitForCompletion(TIMEOUT_MS);
            truck.publish("fleet/truck-7/alerts/tyres", bytes("pressure-low"), 1, false)
                    .waitForCompletion(TIMEOUT_MS);
            Received marker = alerts.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS);
            assertThat(marker).isNotNull();
            assertThat(marker.topic).isEqualTo("fleet/truck-7/alerts/tyres");
            assertThat(marker.qos).isEqualTo(1);
            assertThat(telemetry).isEmpty();
            assertThat(dispatcherEvents.unrouted).isEmpty();

            ListenerOutcome disconnected = new ListenerOutcome();
            dispatcher.disconnect(1_000, "dispatcher-shutdown", disconnected);
            disconnected.awaitSuccess();
            assertThat(disconnected.context).isEqualTo("dispatcher-shutdown");
            assertThat(dispatcher.isConnected()).isFalse();
            assertThat(dispatcherEvents.lost).isEmpty();
        } finally {
            closeQuietly(truck);
            closeQuietly(dispatcher);
        }
    }

    @Test
    void connectFailuresSurfaceAsReasonCodes() throws Exception {
        MqttAsyncClient client = new MqttAsyncClient(serverUri, "dispatcher-audit", new MemoryPersistence());
        try {
            client.connect(options()).waitForCompletion(TIMEOUT_MS);
            assertThatThrownBy(() -> client.connect(options(), "second-connect", new ListenerOutcome()))
                    .isInstanceOfSatisfying(MqttException.class, e -> assertThat(e.getReasonCode())
                            .isEqualTo(MqttException.REASON_CODE_CLIENT_CONNECTED));
            assertThat(client.isConnected()).isTrue();
            client.disconnect().waitForCompletion(TIMEOUT_MS);
        } finally {
            closeQuietly(client);
        }

        MqttAsyncClient refused = new MqttAsyncClient("tcp://127.0.0.1:1", "dispatcher-offline",
                new MemoryPersistence());
        try {
            MqttConnectOptions options = options();
            options.setConnectionTimeout(5);
            ListenerOutcome offline = new ListenerOutcome();
            refused.connect(options, "offline-connect", offline);
            offline.awaitFailure();
            assertThat(offline.context).isEqualTo("offline-connect");
            assertThat(((MqttException) offline.failure).getReasonCode())
                    .isEqualTo(MqttException.REASON_CODE_SERVER_CONNECT_ERROR);
            assertThat(refused.isConnected()).isFalse();
        } finally {
            closeQuietly(refused);
        }
    }

    /// Records the outcome an `IMqttActionListener` reports for one asynchronous operation.
    private static final class ListenerOutcome implements IMqttActionListener {

        private final CountDownLatch done = new CountDownLatch(1);

        private volatile boolean succeeded;

        private volatile Object context;

        private volatile Throwable failure;

        @Override
        public void onSuccess(IMqttToken token) {
            succeeded = true;
            context = token.getUserContext();
            done.countDown();
        }

        @Override
        public void onFailure(IMqttToken token, Throwable exception) {
            context = token.getUserContext();
            failure = exception;
            done.countDown();
        }

        void awaitSuccess() throws InterruptedException {
            assertThat(done.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
            assertThat(failure).isNull();
            assertThat(succeeded).isTrue();
        }

        void awaitFailure() throws InterruptedException {
            assertThat(done.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
            assertThat(succeeded).isFalse();
            assertThat(failure).isNotNull();
        }
    }

    /// Collects the client-wide callbacks: connection events, completed deliveries, and
    /// messages that no per-filter listener claimed.
    private static final class RecordingCallback implements MqttCallbackExtended {

        private final CountDownLatch connectComplete = new CountDownLatch(1);

        private final BlockingQueue<Integer> delivered = new LinkedBlockingQueue<>();

        private final List<String> unrouted = new ArrayList<>();

        private final List<Throwable> lost = new ArrayList<>();

        private volatile boolean reconnect;

        private volatile String serverUri;

        @Override
        public void connectComplete(boolean reconnect, String serverUri) {
            this.reconnect = reconnect;
            this.serverUri = serverUri;
            connectComplete.countDown();
        }

        @Override
        public void connectionLost(Throwable cause) {
            synchronized (lost) {
                lost.add(cause);
            }
        }

        @Override
        public void messageArrived(String topic, MqttMessage message) {
            synchronized (unrouted) {
                unrouted.add(topic);
            }
        }

        @Override
        public void deliveryComplete(IMqttDeliveryToken token) {
            delivered.add(token.getMessageId());
        }

        List<Integer> awaitDeliveries(int count) throws InterruptedException {
            List<Integer> ids = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                Integer id = delivered.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS);
                assertThat(id).as("delivery %d of %d", i + 1, count).isNotNull();
                ids.add(id);
            }
            return ids;
        }
    }

    /// One message as a per-filter listener observed it.
    private static final class Received {

        private final String topic;

        private final String payload;

        private final int qos;

        private final boolean retained;

        private final boolean duplicate;

        Received(String topic, MqttMessage message) {
            this.topic = topic;
            this.payload = new String(message.getPayload(), StandardCharsets.UTF_8);
            this.qos = message.getQos();
            this.retained = message.isRetained();
            this.duplicate = message.isDuplicate();
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

    private static void closeQuietly(MqttAsyncClient client) throws MqttException {
        if (client.isConnected()) {
            client.disconnectForcibly(1_000, 1_000);
        }
        client.close();
    }

    private static void awaitBroker() throws Exception {
        long deadline = System.nanoTime() + STARTUP_TIMEOUT.toNanos();
        MqttException last = null;
        while (System.nanoTime() < deadline) {
            MqttAsyncClient probe = new MqttAsyncClient(serverUri, "startup-probe", new MemoryPersistence());
            try {
                probe.connect(options()).waitForCompletion(TIMEOUT_MS);
                probe.disconnect().waitForCompletion(TIMEOUT_MS);
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
