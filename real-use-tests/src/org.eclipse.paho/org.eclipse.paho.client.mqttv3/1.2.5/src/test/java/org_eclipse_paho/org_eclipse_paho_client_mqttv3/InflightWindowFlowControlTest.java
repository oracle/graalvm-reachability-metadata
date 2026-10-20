/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_paho.org_eclipse_paho_client_mqttv3;

import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Exercises the client's in-flight window flow control: `MqttConnectOptions.setMaxInflight`
/// bounding the unacknowledged QoS 1 and 2 publishes, the `REASON_CODE_MAX_INFLIGHT` refusal
/// of any further publish while the window is full, and the window reopening as the broker's
/// PUBACK and PUBREC frames arrive, observed through `getInFlightMessageCount`,
/// `getPendingDeliveryTokens`, and the delivery tokens' action listeners.
///
/// The scenario is a sensor gateway on a production line that uplinks readings to a plant
/// historian. The gateway has little memory, so it caps how many readings may wait for the
/// broker's acknowledgement and backs off when the cap is reached instead of queueing without
/// bound. A relay between the gateway and the broker can hold the broker's acknowledgements, so
/// the test can fill the window on purpose and reopen it one acknowledgement at a time.
public class InflightWindowFlowControlTest {

    private static final String IMAGE = "eclipse-mosquitto:2.0.22";

    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(90);

    private static final long TIMEOUT_MS = 15_000;

    private static final int PUBLISH = 3;

    private static final int PUBACK = 4;

    private static final int PUBREC = 5;

    private static String containerName;

    private static int brokerPort;

    private static String brokerUri;

    @BeforeAll
    static void startBroker() throws Exception {
        containerName = "paho-inflight-" + System.nanoTime();
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
    void fullWindowRefusesReadingsUntilTheBrokerAcknowledges() throws Exception {
        String line = "line-" + System.nanoTime();
        Historian historian = new Historian(line);
        try (AckHoldingLink link = new AckHoldingLink(brokerPort)) {
            MqttAsyncClient gateway = new MqttAsyncClient(link.uri(), "gateway-" + System.nanoTime(),
                    new MemoryPersistence());
            DeliveryLog deliveries = new DeliveryLog();
            try {
                MqttConnectOptions options = options();
                options.setMaxInflight(2);
                gateway.connect(options).waitForCompletion(TIMEOUT_MS);
                assertThat(gateway.getInFlightMessageCount()).isZero();

                IMqttDeliveryToken temperature = gateway.publish(historian.topic("temperature"),
                        reading("71.4", 1), "temperature", deliveries);
                assertThat(await(() -> link.held() == 1)).as("PUBACK for the temperature held").isTrue();
                IMqttDeliveryToken pressure = gateway.publish(historian.topic("pressure"),
                        reading("2.31", 2), "pressure", deliveries);
                assertThat(await(() -> link.held() == 2)).as("PUBREC for the pressure held").isTrue();

                assertThat(gateway.getInFlightMessageCount()).isEqualTo(2);
                assertThat(gateway.getPendingDeliveryTokens()).containsExactlyInAnyOrder(temperature, pressure);
                assertThat(temperature.isComplete()).isFalse();
                assertThat(pressure.isComplete()).isFalse();

                assertThat(refusal(() -> gateway.publish(historian.topic("vibration"), reading("0.08", 1))))
                        .isEqualTo(MqttException.REASON_CODE_MAX_INFLIGHT);
                assertThat(refusal(() -> gateway.publish(historian.topic("heartbeat"), reading("ok", 0))))
                        .as("a full window refuses fire-and-forget readings too")
                        .isEqualTo(MqttException.REASON_CODE_MAX_INFLIGHT);
                assertThat(gateway.getPendingDeliveryTokens()).as("refused readings are not tracked").hasSize(2);
                assertThat(link.upstream(PUBLISH)).as("refused readings never reach the wire").isEqualTo(2);

                link.release(1);
                temperature.waitForCompletion(TIMEOUT_MS);
                assertThat(await(() -> gateway.getInFlightMessageCount() == 1)).as("window reopened").isTrue();
                assertThat(pressure.isComplete()).isFalse();
                assertThat(gateway.getPendingDeliveryTokens()).containsExactly(pressure);

                IMqttDeliveryToken vibration = gateway.publish(historian.topic("vibration"),
                        reading("0.08", 1), "vibration", deliveries);
                assertThat(await(() -> link.held() == 2)).as("PUBACK for the vibration held").isTrue();
                assertThat(gateway.getInFlightMessageCount()).isEqualTo(2);

                link.release(2);
                pressure.waitForCompletion(TIMEOUT_MS);
                vibration.waitForCompletion(TIMEOUT_MS);
                assertThat(await(() -> gateway.getInFlightMessageCount() == 0)).as("window drained").isTrue();
                assertThat(gateway.getPendingDeliveryTokens()).isEmpty();
                assertThat(await(() -> deliveries.completed().size() == 3)).isTrue();
                assertThat(deliveries.completed()).first().as("acknowledged while the others were held")
                        .isEqualTo("temperature");
                assertThat(deliveries.completed()).containsExactlyInAnyOrder("temperature", "pressure", "vibration");
                assertThat(deliveries.failures()).isZero();

                assertThat(historian.awaitReadings(3)).containsExactlyInAnyOrder(
                        "temperature=71.4", "pressure=2.31", "vibration=0.08");

                gateway.disconnect().waitForCompletion(TIMEOUT_MS);
            } finally {
                closeGateway(gateway);
                historian.close();
            }
        }
    }

    @Test
    void gatewayBacksOffOnAFullWindowAndUplinksAnOrderedBatch() throws Exception {
        assertThat(MqttConnectOptions.MAX_INFLIGHT_DEFAULT).isEqualTo(10);
        assertThat(new MqttConnectOptions().getMaxInflight()).isEqualTo(MqttConnectOptions.MAX_INFLIGHT_DEFAULT);
        assertThatThrownBy(() -> new MqttConnectOptions().setMaxInflight(-1))
                .isInstanceOf(IllegalArgumentException.class);

        int window = 3;
        int batch = 25;
        String line = "line-" + System.nanoTime();
        Historian historian = new Historian(line);
        MqttAsyncClient gateway = new MqttAsyncClient(brokerUri, "gateway-" + System.nanoTime(),
                new MemoryPersistence());
        try {
            MqttConnectOptions options = options();
            options.setMaxInflight(window);
            gateway.connect(options).waitForCompletion(TIMEOUT_MS);

            BackpressuredUplink uplink = new BackpressuredUplink(gateway, window);
            List<String> sent = new ArrayList<>();
            for (int i = 0; i < batch; i++) {
                String value = String.valueOf(60.0 + i / 2.0);
                uplink.send(historian.topic("temperature"), reading(value, 1));
                sent.add("temperature=" + value);
                assertThat(gateway.getInFlightMessageCount()).isLessThanOrEqualTo(window);
            }
            uplink.drain();

            assertThat(await(() -> gateway.getInFlightMessageCount() == 0)).as("window drained").isTrue();
            assertThat(gateway.getPendingDeliveryTokens()).isEmpty();
            assertThat(uplink.accepted()).isEqualTo(batch);
            assertThat(historian.awaitReadings(batch)).as("one ordered QoS 1 stream").containsExactlyElementsOf(sent);

            gateway.disconnect().waitForCompletion(TIMEOUT_MS);
        } finally {
            closeGateway(gateway);
            historian.close();
        }
    }

    /// The gateway's uplink: publishes a reading and, when the client refuses it because the
    /// in-flight window is full, waits for the oldest outstanding reading to be acknowledged and
    /// tries again. The receiver completes the token before the callback thread frees the window
    /// slot, so the retry also waits, bounded, for the in-flight count to drop below the window.
    private static final class BackpressuredUplink {

        private final MqttAsyncClient client;

        private final int window;

        private final List<IMqttDeliveryToken> outstanding = new ArrayList<>();

        private int accepted;

        BackpressuredUplink(MqttAsyncClient client, int window) {
            this.client = client;
            this.window = window;
        }

        void send(String topic, MqttMessage message) throws MqttException, InterruptedException {
            while (true) {
                try {
                    outstanding.add(client.publish(topic, message));
                    accepted++;
                    return;
                } catch (MqttException e) {
                    if (e.getReasonCode() != MqttException.REASON_CODE_MAX_INFLIGHT) {
                        throw e;
                    }
                    assertThat(outstanding).as("a full window has a reading to wait for").isNotEmpty();
                    outstanding.remove(0).waitForCompletion(TIMEOUT_MS);
                    assertThat(await(() -> client.getInFlightMessageCount() < window)).as("slot freed").isTrue();
                }
            }
        }

        void drain() throws MqttException {
            for (IMqttDeliveryToken token : outstanding) {
                token.waitForCompletion(TIMEOUT_MS);
            }
            outstanding.clear();
        }

        int accepted() {
            return accepted;
        }
    }

    /// Records, in completion order, the user context of each reading the broker acknowledged.
    private static final class DeliveryLog implements IMqttActionListener {

        private final List<String> completed = Collections.synchronizedList(new ArrayList<>());

        private volatile int failures;

        @Override
        public void onSuccess(IMqttToken token) {
            completed.add((String) token.getUserContext());
        }

        @Override
        public void onFailure(IMqttToken token, Throwable exception) {
            failures++;
        }

        List<String> completed() {
            synchronized (completed) {
                return new ArrayList<>(completed);
            }
        }

        int failures() {
            return failures;
        }
    }

    /// The plant historian: subscribes to the line's readings straight on the broker.
    private static final class Historian implements MqttCallback, AutoCloseable {

        private final String line;

        private final MqttClient client;

        private final List<String> readings = Collections.synchronizedList(new ArrayList<>());

        Historian(String line) throws MqttException {
            this.line = line;
            this.client = new MqttClient(brokerUri, "historian-" + System.nanoTime(), new MemoryPersistence());
            client.setTimeToWait(TIMEOUT_MS);
            client.setCallback(this);
            client.connect(options());
            client.subscribe("plant/" + line + "/+", 2);
        }

        String topic(String sensor) {
            return "plant/" + line + "/" + sensor;
        }

        List<String> awaitReadings(int count) throws InterruptedException {
            assertThat(await(() -> readings.size() >= count)).as("%d readings within %d ms", count, TIMEOUT_MS)
                    .isTrue();
            synchronized (readings) {
                return new ArrayList<>(readings);
            }
        }

        @Override
        public void connectionLost(Throwable cause) {
        }

        @Override
        public void messageArrived(String topic, MqttMessage message) {
            String sensor = topic.substring(topic.lastIndexOf('/') + 1);
            readings.add(sensor + "=" + new String(message.getPayload(), StandardCharsets.UTF_8));
        }

        @Override
        public void deliveryComplete(IMqttDeliveryToken token) {
        }

        @Override
        public void close() throws MqttException {
            if (client.isConnected()) {
                client.disconnect();
            }
            client.close();
        }
    }

    /// The gateway's link to the broker: a TCP relay that reads whole MQTT frames, counts the
    /// gateway's publishes, and holds the broker's PUBACK and PUBREC frames until released.
    private static final class AckHoldingLink implements AutoCloseable {

        private final int targetPort;

        private final ServerSocket listener;

        private final Thread acceptor;

        private final List<Socket> active = Collections.synchronizedList(new ArrayList<>());

        private final List<byte[]> held = new ArrayList<>();

        private final Object downstreamLock = new Object();

        private final int[] upstream = new int[16];

        private OutputStream toGateway;

        AckHoldingLink(int targetPort) throws IOException {
            this.targetPort = targetPort;
            this.listener = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
            this.acceptor = new Thread(this::acceptLoop, "gateway-link-acceptor");
            this.acceptor.setDaemon(true);
            this.acceptor.start();
        }

        String uri() {
            return "tcp://127.0.0.1:" + listener.getLocalPort();
        }

        int held() {
            synchronized (downstreamLock) {
                return held.size();
            }
        }

        int upstream(int type) {
            synchronized (upstream) {
                return upstream[type];
            }
        }

        /// Delivers the oldest held acknowledgements to the gateway in the order the broker sent them.
        void release(int count) throws IOException {
            synchronized (downstreamLock) {
                assertThat(held).hasSizeGreaterThanOrEqualTo(count);
                for (int i = 0; i < count; i++) {
                    toGateway.write(held.remove(0));
                }
                toGateway.flush();
            }
        }

        private void acceptLoop() {
            while (!listener.isClosed()) {
                try {
                    Socket gateway = listener.accept();
                    Socket broker = new Socket(InetAddress.getLoopbackAddress(), targetPort);
                    active.add(gateway);
                    active.add(broker);
                    synchronized (downstreamLock) {
                        toGateway = gateway.getOutputStream();
                    }
                    pump(gateway, broker, true);
                    pump(broker, gateway, false);
                } catch (IOException e) {
                    return;
                }
            }
        }

        private void pump(Socket from, Socket to, boolean toBroker) {
            Thread thread = new Thread(() -> {
                try (DataInputStream in = new DataInputStream(from.getInputStream());
                        OutputStream out = to.getOutputStream()) {
                    while (true) {
                        byte[] frame = readFrame(in);
                        int type = (frame[0] & 0xF0) >>> 4;
                        if (toBroker) {
                            synchronized (upstream) {
                                upstream[type]++;
                            }
                            out.write(frame);
                            out.flush();
                            continue;
                        }
                        synchronized (downstreamLock) {
                            if (type == PUBACK || type == PUBREC) {
                                held.add(frame);
                            } else {
                                out.write(frame);
                                out.flush();
                            }
                        }
                    }
                } catch (IOException ignored) {
                    // The peer or the test closed the link.
                } finally {
                    closeSocket(from);
                    closeSocket(to);
                }
            }, "gateway-link-pump");
            thread.setDaemon(true);
            thread.start();
        }

        /// Reads one MQTT control packet: the fixed header byte, the variable-length remaining
        /// length, and the remaining bytes, returned together as they appeared on the wire.
        private static byte[] readFrame(DataInputStream in) throws IOException {
            int first = in.read();
            if (first < 0) {
                throw new EOFException();
            }
            byte[] lengthBytes = new byte[4];
            int lengthCount = 0;
            int remaining = 0;
            int multiplier = 1;
            int digit;
            do {
                digit = in.readUnsignedByte();
                lengthBytes[lengthCount++] = (byte) digit;
                remaining += (digit & 0x7F) * multiplier;
                multiplier *= 128;
            } while ((digit & 0x80) != 0);
            byte[] frame = new byte[1 + lengthCount + remaining];
            frame[0] = (byte) first;
            System.arraycopy(lengthBytes, 0, frame, 1, lengthCount);
            in.readFully(frame, 1 + lengthCount, remaining);
            return frame;
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
            synchronized (active) {
                for (Socket socket : active) {
                    closeSocket(socket);
                }
            }
            acceptor.join(TIMEOUT_MS);
        }
    }

    /// A publish attempt the window is expected to refuse.
    @FunctionalInterface
    private interface Publish {

        IMqttDeliveryToken run() throws MqttException;
    }

    private static int refusal(Publish publish) {
        try {
            publish.run();
        } catch (MqttException e) {
            return e.getReasonCode();
        }
        throw new AssertionError("the publish was accepted while the in-flight window was full");
    }

    private static void closeGateway(MqttAsyncClient gateway) throws MqttException {
        if (gateway.isConnected()) {
            gateway.disconnectForcibly(1_000, 1_000);
        }
        gateway.close();
    }

    private static MqttMessage reading(String value, int qos) {
        MqttMessage message = new MqttMessage(value.getBytes(StandardCharsets.UTF_8));
        message.setQos(qos);
        return message;
    }

    private static MqttConnectOptions options() {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setMqttVersion(MqttConnectOptions.MQTT_VERSION_3_1_1);
        options.setCleanSession(true);
        options.setAutomaticReconnect(false);
        options.setKeepAliveInterval(60);
        return options;
    }

    private static boolean await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(TIMEOUT_MS);
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(50);
        }
        return condition.getAsBoolean();
    }

    private static void awaitBroker() throws Exception {
        long deadline = System.nanoTime() + STARTUP_TIMEOUT.toNanos();
        MqttException last = null;
        while (System.nanoTime() < deadline) {
            MqttClient probe = new MqttClient(brokerUri, "startup-probe", new MemoryPersistence());
            try {
                probe.connect(options());
                probe.disconnect();
                return;
            } catch (MqttException e) {
                last = e;
            } finally {
                if (probe.isConnected()) {
                    probe.disconnectForcibly(1_000, 1_000);
                }
                probe.close();
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
}
