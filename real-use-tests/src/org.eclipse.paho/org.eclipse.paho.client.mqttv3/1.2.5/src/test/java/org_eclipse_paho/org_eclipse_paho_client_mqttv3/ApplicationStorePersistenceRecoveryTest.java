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
import org.eclipse.paho.client.mqttv3.MqttClientPersistence;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttPersistable;
import org.eclipse.paho.client.mqttv3.MqttPersistenceException;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Exercises the pluggable client persistence SPI: an application-implemented
/// `MqttClientPersistence` that stores the client's in-flight QoS 1 and QoS 2 flows as
/// `MqttPersistable` records and from which a restarted client restores and resends them.
///
/// The scenario is a parcel courier app that keeps Paho's state in its own key-value store,
/// which outlives client instances the way an embedded database outlives a process. The
/// courier reports deliveries while its uplink silently swallows every packet, the process
/// is killed, and a fresh client built over the same store resends the unacknowledged
/// reports so the depot finally receives them. A clean-session start wipes stale state
/// through the store instead, and a full store makes guaranteed publishes fail fast while
/// fire-and-forget pings still go out.
public class ApplicationStorePersistenceRecoveryTest {

    private static final String IMAGE = "eclipse-mosquitto:2.0.22";

    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(90);

    private static final long TIMEOUT_MS = 10_000;

    private static String containerName;

    private static int brokerPort;

    private static String brokerUri;

    @BeforeAll
    static void startBroker() throws Exception {
        containerName = "paho-app-store-" + System.nanoTime();
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
    void restartedCourierResendsUnacknowledgedReportsFromTheApplicationStore() throws Exception {
        String courierId = "courier-" + System.nanoTime();
        String topic = "depot/" + courierId + "/deliveries";
        CourierStore store = new CourierStore(16);
        DepotInbox depot = new DepotInbox();
        MqttClient depotClient = new MqttClient(brokerUri, "depot-" + System.nanoTime(), new MemoryPersistence());
        depotClient.setTimeToWait(TIMEOUT_MS);
        depotClient.setCallback(depot);
        try {
            depotClient.connect(cleanOptions());
            depotClient.subscribe(topic, 2);

            String relayUri = abandonInFlightReports(store, courierId, topic);
            assertThat(store.opens).isNotEmpty().containsOnly(courierId + " @ " + relayUri);
            assertThat(store.open).isFalse();
            assertThat(store.keysWithPrefix("s-")).hasSize(2);
            assertThat(store.storedPayloads())
                    .containsExactlyInAnyOrder("parcel-1001 delivered", "parcel-1002 signed");

            MqttAsyncClient restarted = new MqttAsyncClient(brokerUri, courierId, store);
            CourierEvents events = new CourierEvents();
            restarted.setCallback(events);
            try {
                assertThat(store.opens).last().isEqualTo(courierId + " @ " + brokerUri);
                assertThat(store.open).as("closed again once the state is restored").isFalse();
                IMqttDeliveryToken[] recovered = restarted.getPendingDeliveryTokens();
                assertThat(recovered).hasSize(2);
                for (IMqttDeliveryToken token : recovered) {
                    assertThat(token.isComplete()).isFalse();
                }

                MqttConnectOptions resume = cleanOptions();
                resume.setCleanSession(false);
                restarted.connect(resume).waitForCompletion(TIMEOUT_MS);
                assertThat(store.open).as("reopened for the connection").isTrue();
                for (IMqttDeliveryToken token : recovered) {
                    token.waitForCompletion(TIMEOUT_MS);
                    assertThat(token.getException()).isNull();
                }

                Set<String> received = new HashSet<>(List.of(depot.next(), depot.next()));
                assertThat(received).containsExactlyInAnyOrder("parcel-1001 delivered", "parcel-1002 signed");
                assertThat(events.awaitCompleted(2)).isTrue();
                assertThat(restarted.getPendingDeliveryTokens()).isEmpty();
                assertThat(store.keys().hasMoreElements()).isFalse();

                restarted.publish(topic, report("parcel-1003 delivered", 1)).waitForCompletion(TIMEOUT_MS);
                assertThat(depot.next()).isEqualTo("parcel-1003 delivered");
                assertThat(store.keys().hasMoreElements()).isFalse();
            } finally {
                shutdown(restarted);
            }
            assertThat(store.open).isFalse();
        } finally {
            depotClient.disconnect();
            depotClient.close();
        }
    }

    @Test
    void cleanSessionStartClearsStaleFlowsThroughTheApplicationStore() throws Exception {
        String courierId = "courier-" + System.nanoTime();
        CourierStore store = new CourierStore(16);
        abandonInFlightReports(store, courierId, "depot/" + courierId + "/deliveries");
        assertThat(store.keysWithPrefix("s-")).hasSize(2);
        assertThat(store.clears).isZero();

        MqttAsyncClient fresh = new MqttAsyncClient(brokerUri, courierId, store);
        try {
            assertThat(fresh.getPendingDeliveryTokens()).hasSize(2);
            fresh.connect(cleanOptions()).waitForCompletion(TIMEOUT_MS);
            assertThat(store.clears).isPositive();
            assertThat(store.keys().hasMoreElements()).isFalse();
            assertThat(fresh.getPendingDeliveryTokens()).isEmpty();
        } finally {
            shutdown(fresh);
        }
    }

    @Test
    void fullStoreRejectsGuaranteedReportsButLetsQosZeroPingsThrough() throws Exception {
        String courierId = "courier-" + System.nanoTime();
        String topic = "depot/" + courierId + "/pings";
        CourierStore store = new CourierStore(0);
        DepotInbox depot = new DepotInbox();
        MqttClient depotClient = new MqttClient(brokerUri, "depot-" + System.nanoTime(), new MemoryPersistence());
        depotClient.setTimeToWait(TIMEOUT_MS);
        depotClient.setCallback(depot);
        MqttAsyncClient courier = new MqttAsyncClient(brokerUri, courierId, store);
        try {
            depotClient.connect(cleanOptions());
            depotClient.subscribe(topic, 1);
            courier.connect(cleanOptions()).waitForCompletion(TIMEOUT_MS);

            assertThatThrownBy(() -> courier.publish(topic, report("parcel-2001 delivered", 1)))
                    .isInstanceOf(MqttPersistenceException.class)
                    .hasRootCauseMessage("courier store is full");
            assertThat(store.keys().hasMoreElements()).isFalse();
            assertThat(courier.isConnected()).isTrue();

            courier.publish(topic, report("courier alive", 0)).waitForCompletion(TIMEOUT_MS);
            assertThat(depot.next()).isEqualTo("courier alive");
            assertThat(store.keys().hasMoreElements()).isFalse();
        } finally {
            shutdown(courier);
            depotClient.disconnect();
            depotClient.close();
        }
    }

    /// Connects a durable courier through an uplink that swallows its reports, so both stay
    /// in flight in `store`, then kills the client as a crashed process would.
    ///
    /// Returns the uplink URI the store was opened with.
    private static String abandonInFlightReports(CourierStore store, String courierId, String topic)
            throws Exception {
        try (Uplink uplink = new Uplink(brokerPort)) {
            MqttAsyncClient courier = new MqttAsyncClient(uplink.uri(), courierId, store);
            MqttConnectOptions options = cleanOptions();
            options.setCleanSession(false);
            courier.connect(options).waitForCompletion(TIMEOUT_MS);

            uplink.swallowUpstream();
            IMqttDeliveryToken delivered = courier.publish(topic, report("parcel-1001 delivered", 1));
            IMqttDeliveryToken signed = courier.publish(topic, report("parcel-1002 signed", 2));
            assertThat(delivered.isComplete()).isFalse();
            assertThat(signed.isComplete()).isFalse();

            courier.disconnectForcibly(0, 1_000);
            courier.close(true);
            return uplink.uri();
        }
    }

    /// The courier app's own key-value store, kept outside any client instance so a restarted
    /// client finds what the previous one left behind.
    private static final class CourierStore implements MqttClientPersistence {

        private final Map<String, Record> records = new TreeMap<>();

        private final int capacity;

        private final List<String> opens = Collections.synchronizedList(new ArrayList<>());

        private volatile boolean open;

        private volatile int clears;

        CourierStore(int capacity) {
            this.capacity = capacity;
        }

        @Override
        public void open(String clientId, String serverURI) {
            opens.add(clientId + " @ " + serverURI);
            open = true;
        }

        @Override
        public void close() {
            open = false;
        }

        @Override
        public synchronized void put(String key, MqttPersistable persistable) throws MqttPersistenceException {
            if (!records.containsKey(key) && records.size() >= capacity) {
                throw new MqttPersistenceException(new IOException("courier store is full"));
            }
            records.put(key, new Record(
                    copy(persistable.getHeaderBytes(), persistable.getHeaderOffset(), persistable.getHeaderLength()),
                    copy(persistable.getPayloadBytes(), persistable.getPayloadOffset(),
                            persistable.getPayloadLength())));
        }

        @Override
        public synchronized MqttPersistable get(String key) {
            return records.get(key);
        }

        @Override
        public synchronized void remove(String key) {
            records.remove(key);
        }

        @Override
        public synchronized Enumeration<String> keys() {
            return Collections.enumeration(new ArrayList<>(records.keySet()));
        }

        @Override
        public synchronized void clear() {
            clears++;
            records.clear();
        }

        @Override
        public synchronized boolean containsKey(String key) {
            return records.containsKey(key);
        }

        synchronized List<String> keysWithPrefix(String prefix) {
            List<String> matching = new ArrayList<>();
            for (String key : records.keySet()) {
                if (key.startsWith(prefix)) {
                    matching.add(key);
                }
            }
            return matching;
        }

        synchronized List<String> storedPayloads() {
            List<String> payloads = new ArrayList<>();
            for (Record record : records.values()) {
                payloads.add(new String(record.payload, StandardCharsets.UTF_8));
            }
            return payloads;
        }

        private static byte[] copy(byte[] bytes, int offset, int length) {
            return bytes == null ? new byte[0] : Arrays.copyOfRange(bytes, offset, offset + length);
        }
    }

    /// One stored flow: the packet header and payload exactly as the client handed them over.
    private static final class Record implements MqttPersistable {

        private final byte[] header;

        private final byte[] payload;

        Record(byte[] header, byte[] payload) {
            this.header = header;
            this.payload = payload;
        }

        @Override
        public byte[] getHeaderBytes() {
            return header;
        }

        @Override
        public int getHeaderLength() {
            return header.length;
        }

        @Override
        public int getHeaderOffset() {
            return 0;
        }

        @Override
        public byte[] getPayloadBytes() {
            return payload;
        }

        @Override
        public int getPayloadLength() {
            return payload.length;
        }

        @Override
        public int getPayloadOffset() {
            return 0;
        }
    }

    /// Counts the delivery confirmations the restarted courier receives.
    private static final class CourierEvents implements MqttCallback {

        private final BlockingQueue<IMqttDeliveryToken> completed = new LinkedBlockingQueue<>();

        @Override
        public void connectionLost(Throwable cause) {
        }

        @Override
        public void messageArrived(String topic, MqttMessage message) {
        }

        @Override
        public void deliveryComplete(IMqttDeliveryToken token) {
            completed.add(token);
        }

        boolean awaitCompleted(int count) throws InterruptedException {
            for (int i = 0; i < count; i++) {
                if (completed.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS) == null) {
                    return false;
                }
            }
            return true;
        }
    }

    /// Queues the reports the depot receives.
    private static final class DepotInbox implements MqttCallback {

        private final BlockingQueue<String> arrived = new LinkedBlockingQueue<>();

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
            String report = arrived.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS);
            assertThat(report).as("report within %d ms", TIMEOUT_MS).isNotNull();
            return report;
        }
    }

    /// The courier's uplink: a TCP relay to the broker that can start silently discarding
    /// everything the courier sends while keeping the connection open.
    private static final class Uplink implements AutoCloseable {

        private final int targetPort;

        private final ServerSocket listener;

        private final Thread acceptor;

        private final List<Socket> active = Collections.synchronizedList(new ArrayList<>());

        private volatile boolean forwardUpstream = true;

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

        void swallowUpstream() {
            forwardUpstream = false;
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

        private void pump(Socket from, Socket to, boolean upstream) {
            Thread thread = new Thread(() -> {
                byte[] chunk = new byte[8192];
                try (InputStream in = from.getInputStream(); OutputStream out = to.getOutputStream()) {
                    int read;
                    while ((read = in.read(chunk)) >= 0) {
                        if (!upstream || forwardUpstream) {
                            out.write(chunk, 0, read);
                            out.flush();
                        }
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
            synchronized (active) {
                for (Socket socket : active) {
                    closeSocket(socket);
                }
            }
            acceptor.join(TIMEOUT_MS);
        }
    }

    private static MqttMessage report(String text, int qos) {
        MqttMessage message = new MqttMessage(text.getBytes(StandardCharsets.UTF_8));
        message.setQos(qos);
        return message;
    }

    private static MqttConnectOptions cleanOptions() {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setMqttVersion(MqttConnectOptions.MQTT_VERSION_3_1_1);
        options.setCleanSession(true);
        options.setAutomaticReconnect(false);
        options.setKeepAliveInterval(60);
        return options;
    }

    private static void shutdown(MqttAsyncClient client) throws MqttException {
        if (client.isConnected()) {
            client.disconnect().waitForCompletion(TIMEOUT_MS);
        }
        client.close();
    }

    private static void awaitBroker() throws Exception {
        long deadline = System.nanoTime() + STARTUP_TIMEOUT.toNanos();
        MqttException last = null;
        while (System.nanoTime() < deadline) {
            MqttClient probe = new MqttClient(brokerUri, "startup-probe", new MemoryPersistence());
            try {
                probe.connect(cleanOptions());
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
