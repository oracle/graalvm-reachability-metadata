/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_paho.org_eclipse_paho_client_mqttv3;

import org.eclipse.paho.client.mqttv3.DisconnectedBufferOptions;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/// Exercises automatic reconnect with the disconnected-message buffer on `MqttAsyncClient`:
/// `setAutomaticReconnect`, `DisconnectedBufferOptions`, and `MqttCallbackExtended`.
///
/// The scenario is a field gateway that reaches the broker through a flaky uplink, modelled
/// by a TCP relay the test can sever. While the uplink is down the gateway keeps publishing
/// readings into a bounded offline buffer that drops the oldest entry when full, and it
/// discards one stale reading by hand. Once the uplink returns, the client reconnects on its
/// own, resumes its durable session, flushes the buffer in order, and receives a command the
/// control room sent while it was away.
public class AutomaticReconnectOfflineBufferTest {

    private static final String IMAGE = "eclipse-mosquitto:2.0.22";

    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(90);

    private static final long TIMEOUT_MS = 10_000;

    private static final long RECONNECT_TIMEOUT_MS = 30_000;

    private static String containerName;

    private static int brokerPort;

    private static String brokerUri;

    @BeforeAll
    static void startBroker() throws Exception {
        containerName = "paho-reconnect-" + System.nanoTime();
        docker("run", "-d", "--name", containerName, "-p", "127.0.0.1::1883", IMAGE,
                "mosquitto", "-c", "/mosquitto-no-auth.conf");
        String mapping = docker("port", containerName, "1883/tcp").lines().findFirst().orElseThrow();
        brokerPort = Integer.parseInt(mapping.substring(mapping.lastIndexOf(':') + 1));
        brokerUri = "tcp://127.0.0.1:" + brokerPort;
        awaitBroker();
    }

    @AfterAll
    static void stopBroker() throws Exception {
        if (containerName != null) {
            docker("rm", "-f", "-v", containerName);
        }
    }

    @Test
    void gatewayBuffersReadingsWhileOfflineAndFlushesThemAfterReconnecting() throws Exception {
        String gatewayId = "gateway-" + System.nanoTime();
        try (Uplink uplink = new Uplink(brokerPort)) {
            GatewayEvents events = new GatewayEvents();
            MqttAsyncClient gateway = new MqttAsyncClient(uplink.uri(), gatewayId, new MemoryPersistence());
            gateway.setCallback(events);
            DisconnectedBufferOptions bufferOptions = new DisconnectedBufferOptions();
            bufferOptions.setBufferEnabled(true);
            bufferOptions.setBufferSize(3);
            bufferOptions.setPersistBuffer(false);
            bufferOptions.setDeleteOldestMessages(true);
            gateway.setBufferOpts(bufferOptions);

            Inbox telemetry = new Inbox();
            MqttClient monitor = new MqttClient(brokerUri, "monitor-" + System.nanoTime(), new MemoryPersistence());
            monitor.setTimeToWait(TIMEOUT_MS);
            monitor.setCallback(telemetry);
            MqttClient control = new MqttClient(brokerUri, "control-" + System.nanoTime(), new MemoryPersistence());
            control.setTimeToWait(TIMEOUT_MS);
            try {
                monitor.connect(directOptions());
                monitor.subscribe("telemetry/#", 1);
                control.connect(directOptions());

                MqttConnectOptions gatewayOptions = new MqttConnectOptions();
                gatewayOptions.setMqttVersion(MqttConnectOptions.MQTT_VERSION_3_1_1);
                gatewayOptions.setCleanSession(false);
                gatewayOptions.setAutomaticReconnect(true);
                gatewayOptions.setMaxReconnectDelay(2_000);
                gatewayOptions.setKeepAliveInterval(30);
                assertThat(gatewayOptions.isAutomaticReconnect()).isTrue();
                gateway.connect(gatewayOptions).waitForCompletion(TIMEOUT_MS);
                Connected initial = events.nextConnect(TIMEOUT_MS);
                assertThat(initial.reconnect).isFalse();
                assertThat(initial.serverUri).isEqualTo(uplink.uri());
                gateway.subscribe("gateways/" + gatewayId + "/commands", 1).waitForCompletion(TIMEOUT_MS);

                gateway.publish("telemetry/temperature", reading("20.5")).waitForCompletion(TIMEOUT_MS);
                assertThat(telemetry.next().payload).isEqualTo("20.5");

                uplink.sever();
                assertThat(events.lost.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isNotNull();
                assertThat(gateway.isConnected()).isFalse();

                IMqttDeliveryToken dropped = gateway.publish("telemetry/temperature", reading("21.0"));
                gateway.publish("telemetry/temperature", reading("21.5"));
                IMqttDeliveryToken buffered = gateway.publish("telemetry/temperature", reading("22.0"));
                gateway.publish("telemetry/humidity", reading("48"));
                assertThat(dropped.isComplete()).isFalse();
                assertThat(buffered.isComplete()).isFalse();
                assertThat(gateway.getBufferedMessageCount()).isEqualTo(3);
                assertThat(text(gateway.getBufferedMessage(0))).isEqualTo("21.5");
                assertThat(text(gateway.getBufferedMessage(1))).isEqualTo("22.0");
                assertThat(text(gateway.getBufferedMessage(2))).isEqualTo("48");
                gateway.deleteBufferedMessage(0);
                assertThat(gateway.getBufferedMessageCount()).isEqualTo(2);

                control.publish("gateways/" + gatewayId + "/commands", bytes("calibrate"), 1, false);

                uplink.awaitAttemptWhileDown();
                assertThat(gateway.isConnected()).isFalse();
                uplink.restore();
                Connected resumed = events.nextConnect(RECONNECT_TIMEOUT_MS);
                assertThat(resumed.reconnect).isTrue();
                assertThat(resumed.serverUri).isEqualTo(uplink.uri());
                assertThat(gateway.isConnected()).isTrue();

                Received command = events.nextCommand();
                assertThat(command.topic).isEqualTo("gateways/" + gatewayId + "/commands");
                assertThat(command.payload).isEqualTo("calibrate");

                buffered.waitForCompletion(TIMEOUT_MS);
                assertThat(buffered.isComplete()).isTrue();
                assertThat(buffered.getException()).isNull();
                gateway.publish("telemetry/temperature", reading("22.5")).waitForCompletion(TIMEOUT_MS);

                Received first = telemetry.next();
                Received second = telemetry.next();
                Received live = telemetry.next();
                assertThat(first.topic).isEqualTo("telemetry/temperature");
                assertThat(first.payload).isEqualTo("22.0");
                assertThat(second.topic).isEqualTo("telemetry/humidity");
                assertThat(second.payload).isEqualTo("48");
                assertThat(live.payload).isEqualTo("22.5");
                assertThat(telemetry.isEmpty()).isTrue();
                assertThat(gateway.getBufferedMessageCount()).isZero();
            } finally {
                closeQuietly(gateway);
                closeQuietly(monitor);
                closeQuietly(control);
            }
        }
    }

    /// Records the gateway's connection lifecycle and the commands it receives.
    private static final class GatewayEvents implements MqttCallbackExtended {

        private final BlockingQueue<Connected> connects = new LinkedBlockingQueue<>();

        private final BlockingQueue<Throwable> lost = new LinkedBlockingQueue<>();

        private final BlockingQueue<Received> commands = new LinkedBlockingQueue<>();

        @Override
        public void connectComplete(boolean reconnect, String serverUri) {
            connects.add(new Connected(reconnect, serverUri));
        }

        @Override
        public void connectionLost(Throwable cause) {
            lost.add(cause);
        }

        @Override
        public void messageArrived(String topic, MqttMessage message) {
            commands.add(new Received(topic, message));
        }

        @Override
        public void deliveryComplete(IMqttDeliveryToken token) {
        }

        Connected nextConnect(long timeoutMs) throws InterruptedException {
            Connected connected = connects.poll(timeoutMs, TimeUnit.MILLISECONDS);
            assertThat(connected).as("connection within %d ms", timeoutMs).isNotNull();
            return connected;
        }

        Received nextCommand() throws InterruptedException {
            Received command = commands.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS);
            assertThat(command).as("command within %d ms", TIMEOUT_MS).isNotNull();
            return command;
        }
    }

    /// One `connectComplete` notification.
    private static final class Connected {

        private final boolean reconnect;

        private final String serverUri;

        Connected(boolean reconnect, String serverUri) {
            this.reconnect = reconnect;
            this.serverUri = serverUri;
        }
    }

    /// Queues every telemetry message the monitoring service receives.
    private static final class Inbox implements MqttCallbackExtended {

        private final BlockingQueue<Received> arrived = new LinkedBlockingQueue<>();

        @Override
        public void connectComplete(boolean reconnect, String serverUri) {
        }

        @Override
        public void connectionLost(Throwable cause) {
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

    /// One message as a callback observed it.
    private static final class Received {

        private final String topic;

        private final String payload;

        Received(String topic, MqttMessage message) {
            this.topic = topic;
            this.payload = text(message);
        }
    }

    /// The gateway's uplink: a TCP relay to the broker that can be cut and restored.
    ///
    /// While cut, it closes every connection it accepts, so reconnect attempts fail fast.
    private static final class Uplink implements AutoCloseable {

        private final int targetPort;

        private final ServerSocket listener;

        private final Thread acceptor;

        private final List<Socket> active = new ArrayList<>();

        private boolean up = true;

        private int acceptedWhileDown;

        Uplink(int targetPort) throws IOException {
            this.targetPort = targetPort;
            this.listener = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
            this.acceptor = new Thread(this::acceptLoop, "uplink-acceptor");
            this.acceptor.setDaemon(true);
            this.acceptor.start();
        }

        String uri() {
            return "tcp://127.0.0.1:" + listener.getLocalPort();
        }

        synchronized void sever() throws IOException {
            up = false;
            for (Socket socket : active) {
                socket.close();
            }
            active.clear();
        }

        synchronized void restore() {
            up = true;
        }

        /// Waits until the client's automatic reconnect has knocked on the cut link.
        synchronized void awaitAttemptWhileDown() throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(RECONNECT_TIMEOUT_MS);
            while (acceptedWhileDown == 0) {
                long remainingMs = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
                assertThat(remainingMs).as("reconnect attempt within %d ms", RECONNECT_TIMEOUT_MS).isPositive();
                wait(remainingMs);
            }
        }

        private void acceptLoop() {
            while (!listener.isClosed()) {
                try {
                    Socket client = listener.accept();
                    if (!admit(client)) {
                        client.close();
                    }
                } catch (IOException e) {
                    return;
                }
            }
        }

        private synchronized boolean admit(Socket client) throws IOException {
            if (!up) {
                acceptedWhileDown++;
                notifyAll();
                return false;
            }
            Socket broker = new Socket(InetAddress.getLoopbackAddress(), targetPort);
            active.add(client);
            active.add(broker);
            pump(client, broker);
            pump(broker, client);
            return true;
        }

        private static void pump(Socket from, Socket to) {
            Thread thread = new Thread(() -> {
                byte[] chunk = new byte[8192];
                try (InputStream in = from.getInputStream(); OutputStream out = to.getOutputStream()) {
                    int read;
                    while ((read = in.read(chunk)) >= 0) {
                        out.write(chunk, 0, read);
                        out.flush();
                    }
                } catch (IOException ignored) {
                    // The peer or the test closed the link.
                } finally {
                    closeSocket(from);
                    closeSocket(to);
                }
            }, "uplink-pump");
            thread.setDaemon(true);
            thread.start();
        }

        private static void closeSocket(Socket socket) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // Already closed.
            }
        }

        @Override
        public void close() throws Exception {
            listener.close();
            sever();
            acceptor.join(TIMEOUT_MS);
        }
    }

    private static MqttMessage reading(String value) {
        MqttMessage message = new MqttMessage(bytes(value));
        message.setQos(1);
        return message;
    }

    private static String text(MqttMessage message) {
        return new String(message.getPayload(), StandardCharsets.UTF_8);
    }

    private static MqttConnectOptions directOptions() {
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
        client.close(true);
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
            MqttClient probe = new MqttClient(brokerUri, "startup-probe", new MemoryPersistence());
            try {
                probe.connect(directOptions());
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
