/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_paho.org_eclipse_paho_client_mqttv3;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.ScheduledExecutorPingSender;
import org.eclipse.paho.client.mqttv3.TimerPingSender;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/// Exercises the keep-alive ping machinery: the `MqttPingSender` implementations
/// `TimerPingSender` and `ScheduledExecutorPingSender` that send PINGREQ on an idle
/// connection, and the client-side timeout that drops the connection when PINGRESP stops.
///
/// The scenario is a vehicle telematics unit that holds an MQTT session open over a cellular
/// link and reports only rarely. Between reports the client has to keep the link alive on its
/// own, either on a Paho timer thread or on the unit's shared scheduler. When the link turns
/// half-open and the broker's ping responses no longer arrive, the unit must notice through
/// `connectionLost` instead of believing it is still online. A relay between the client and the
/// broker parses MQTT frames so the test can count the pings and drop the responses.
public class KeepAlivePingSenderTest {

    private static final String IMAGE = "eclipse-mosquitto:2.0.22";

    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(90);

    private static final long TIMEOUT_MS = 15_000;

    private static final int KEEP_ALIVE_SECONDS = 1;

    private static final int CONNECT = 1;

    private static final int PINGREQ = 12;

    private static final int PINGRESP = 13;

    private static String containerName;

    private static int brokerPort;

    private static String brokerUri;

    @BeforeAll
    static void startBroker() throws Exception {
        containerName = "paho-keep-alive-" + System.nanoTime();
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
    void idleUnitKeepsItsSessionAliveOnTheSharedScheduler() throws Exception {
        String unitId = "unit-" + System.nanoTime();
        String topic = "fleet/" + unitId + "/position";
        ScheduledExecutorService scheduler = unitScheduler();
        Inbox inbox = new Inbox();
        MqttClient dispatcher = new MqttClient(brokerUri, "dispatch-" + System.nanoTime(), new MemoryPersistence());
        dispatcher.setTimeToWait(TIMEOUT_MS);
        dispatcher.setCallback(inbox);
        try (CellularLink link = new CellularLink(brokerPort)) {
            dispatcher.connect(options(60));
            dispatcher.subscribe(topic, 1);

            MqttClient unit = new MqttClient(link.uri(), unitId, new MemoryPersistence(), scheduler);
            unit.setTimeToWait(TIMEOUT_MS);
            UnitEvents events = new UnitEvents();
            unit.setCallback(events);
            try {
                unit.connect(options(KEEP_ALIVE_SECONDS));
                assertThat(link.connectKeepAlive()).isEqualTo(KEEP_ALIVE_SECONDS);

                assertThat(await(() -> link.upstream(PINGREQ) >= 3 && link.downstream(PINGRESP) >= 3))
                        .as("three answered pings while idle").isTrue();
                assertThat(unit.isConnected()).isTrue();
                assertThat(events.lost.isDone()).isFalse();

                unit.publish(topic, position("45.2671,19.8335", 1));
                assertThat(inbox.next()).isEqualTo("45.2671,19.8335");

                unit.disconnect();
            } finally {
                unit.close();
            }
            assertThat(scheduler.isShutdown()).as("the unit's scheduler belongs to the application").isFalse();
        } finally {
            dispatcher.disconnect();
            dispatcher.close();
            scheduler.shutdownNow();
        }
    }

    @Test
    void asyncUnitPingsThroughAnExplicitSchedulerPingSender() throws Exception {
        ScheduledExecutorService scheduler = unitScheduler();
        try (CellularLink link = new CellularLink(brokerPort)) {
            MqttAsyncClient unit = new MqttAsyncClient(link.uri(), "unit-" + System.nanoTime(),
                    new MemoryPersistence(), new ScheduledExecutorPingSender(scheduler), scheduler);
            try {
                unit.connect(options(KEEP_ALIVE_SECONDS)).waitForCompletion(TIMEOUT_MS);
                assertThat(await(() -> link.upstream(PINGREQ) >= 2 && link.downstream(PINGRESP) >= 2))
                        .as("two answered pings while idle").isTrue();
                assertThat(unit.isConnected()).isTrue();
                unit.disconnect().waitForCompletion(TIMEOUT_MS);
            } finally {
                unit.close();
            }
        } finally {
            scheduler.shutdownNow();
        }
    }

    @Test
    void halfOpenLinkIsDetectedWhenPingResponsesStopArriving() throws Exception {
        try (CellularLink link = new CellularLink(brokerPort)) {
            MqttAsyncClient unit = new MqttAsyncClient(link.uri(), "unit-" + System.nanoTime(),
                    new MemoryPersistence(), new TimerPingSender());
            UnitEvents events = new UnitEvents();
            unit.setCallback(events);
            try {
                unit.connect(options(KEEP_ALIVE_SECONDS)).waitForCompletion(TIMEOUT_MS);
                assertThat(await(() -> link.downstream(PINGRESP) >= 1)).as("first ping answered").isTrue();

                link.dropPingResponses();
                Throwable cause = events.lost.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);

                assertThat(cause).isInstanceOf(MqttException.class);
                assertThat(((MqttException) cause).getReasonCode()).isEqualTo(MqttException.REASON_CODE_CLIENT_TIMEOUT);
                assertThat(link.droppedPingResponses()).as("pings the broker answered in vain").isPositive();
                assertThat(unit.isConnected()).isFalse();
            } finally {
                unit.close();
            }
        }
    }

    /// Records how the unit's connection ended.
    private static final class UnitEvents implements MqttCallback {

        private final CompletableFuture<Throwable> lost = new CompletableFuture<>();

        @Override
        public void connectionLost(Throwable cause) {
            lost.complete(cause);
        }

        @Override
        public void messageArrived(String topic, MqttMessage message) {
        }

        @Override
        public void deliveryComplete(IMqttDeliveryToken token) {
        }
    }

    /// Hands the dispatcher the positions the unit reports.
    private static final class Inbox implements MqttCallback {

        private final List<String> arrived = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void connectionLost(Throwable cause) {
        }

        @Override
        public void messageArrived(String topic, MqttMessage message) {
            arrived.add(new String(message.getPayload(), StandardCharsets.UTF_8));
        }

        @Override
        public void deliveryComplete(IMqttDeliveryToken token) {
        }

        String next() throws InterruptedException {
            assertThat(await(() -> !arrived.isEmpty())).as("position within %d ms", TIMEOUT_MS).isTrue();
            return arrived.remove(0);
        }
    }

    /// The unit's cellular link: a TCP relay to the broker that reads whole MQTT frames, counts
    /// them by packet type, and can stop delivering the broker's ping responses.
    private static final class CellularLink implements AutoCloseable {

        private final int targetPort;

        private final ServerSocket listener;

        private final Thread acceptor;

        private final List<Socket> active = Collections.synchronizedList(new ArrayList<>());

        private final AtomicInteger[] upstream = counters();

        private final AtomicInteger[] downstream = counters();

        private volatile int connectKeepAlive = -1;

        private final AtomicInteger droppedPingResponses = new AtomicInteger();

        private volatile boolean dropPingResponses;

        CellularLink(int targetPort) throws IOException {
            this.targetPort = targetPort;
            this.listener = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
            this.acceptor = new Thread(this::acceptLoop, "cellular-acceptor");
            this.acceptor.setDaemon(true);
            this.acceptor.start();
        }

        String uri() {
            return "tcp://127.0.0.1:" + listener.getLocalPort();
        }

        int upstream(int type) {
            return upstream[type].get();
        }

        int downstream(int type) {
            return downstream[type].get();
        }

        int connectKeepAlive() {
            return connectKeepAlive;
        }

        int droppedPingResponses() {
            return droppedPingResponses.get();
        }

        void dropPingResponses() {
            dropPingResponses = true;
        }

        private void acceptLoop() {
            while (!listener.isClosed()) {
                try {
                    Socket client = listener.accept();
                    Socket broker = new Socket(InetAddress.getLoopbackAddress(), targetPort);
                    active.add(client);
                    active.add(broker);
                    pump(client, broker, true);
                    pump(broker, client, false);
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
                            if (type == CONNECT) {
                                connectKeepAlive = connectKeepAlive(frame);
                            }
                            upstream[type].incrementAndGet();
                        } else if (type == PINGRESP && dropPingResponses) {
                            droppedPingResponses.incrementAndGet();
                            continue;
                        } else {
                            downstream[type].incrementAndGet();
                        }
                        out.write(frame);
                        out.flush();
                    }
                } catch (IOException ignored) {
                    // The peer or the test closed the link.
                } finally {
                    closeSocket(from);
                    closeSocket(to);
                }
            }, "cellular-pump");
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

        /// Reads the keep-alive seconds from an MQTT 3.1.1 CONNECT frame, which follow the
        /// protocol name `MQTT`, the protocol level, and the connect flags.
        private static int connectKeepAlive(byte[] frame) {
            int body = 2;
            while ((frame[body - 1] & 0x80) != 0) {
                body++;
            }
            int offset = body + 2 + 4 + 1 + 1;
            return ((frame[offset] & 0xFF) << 8) | (frame[offset + 1] & 0xFF);
        }

        private static AtomicInteger[] counters() {
            AtomicInteger[] counters = new AtomicInteger[16];
            for (int i = 0; i < counters.length; i++) {
                counters[i] = new AtomicInteger();
            }
            return counters;
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

    /// The unit's shared scheduler. A client given an executor runs its sender, receiver, and
    /// callback loops on it for the life of the connection, so the pool needs a thread for each
    /// of them and one more for the ping task.
    private static ScheduledExecutorService unitScheduler() {
        return Executors.newScheduledThreadPool(4);
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

    private static MqttMessage position(String text, int qos) {
        MqttMessage message = new MqttMessage(text.getBytes(StandardCharsets.UTF_8));
        message.setQos(qos);
        return message;
    }

    private static MqttConnectOptions options(int keepAliveSeconds) {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setMqttVersion(MqttConnectOptions.MQTT_VERSION_3_1_1);
        options.setCleanSession(true);
        options.setAutomaticReconnect(false);
        options.setKeepAliveInterval(keepAliveSeconds);
        return options;
    }

    private static void awaitBroker() throws Exception {
        long deadline = System.nanoTime() + STARTUP_TIMEOUT.toNanos();
        MqttException last = null;
        while (System.nanoTime() < deadline) {
            MqttClient probe = new MqttClient(brokerUri, "startup-probe", new MemoryPersistence());
            try {
                probe.connect(options(60));
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
