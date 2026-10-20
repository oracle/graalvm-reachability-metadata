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

import javax.net.SocketFactory;
import javax.net.ssl.SSLSocketFactory;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/// Exercises the plain `tcp://` transport through an application `SocketFactory` set with
/// `MqttConnectOptions.setSocketFactory`: a factory that tunnels the connection through a
/// SOCKS5 egress proxy, a factory that tunes every socket it hands out, a proxy refusal
/// surfacing from `connect`, and the rejection of a TLS factory on a plain TCP URI.
///
/// The scenario is a production-line gateway on a plant network whose only route to the
/// cloud broker is a SOCKS5 egress proxy. The gateway's MQTT client is given a socket
/// factory that opens proxied sockets, so its telemetry and the operators' commands both
/// travel through the tunnel while the operators connect to the broker directly. The proxy
/// enforces an allowlist of destinations, and the gateway must report a refused tunnel as a
/// failed connect. A second deployment tunes its sockets for low latency, and the factory has
/// to be consulted again on every new connection.
public class SocketFactoryEgressProxyTest {

    private static final String IMAGE = "eclipse-mosquitto:2.0.22";

    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(90);

    private static final long TIMEOUT_MS = 15_000;

    private static String containerName;

    private static int brokerPort;

    private static String brokerUri;

    @BeforeAll
    static void startBroker() throws Exception {
        containerName = "paho-socket-factory-" + System.nanoTime();
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
    void gatewayExchangesTelemetryAndCommandsThroughTheSocksTunnel() throws Exception {
        String line = "line-" + System.nanoTime();
        String telemetryTopic = "plant/" + line + "/telemetry";
        String commandTopic = "plant/" + line + "/commands";
        try (SocksEgressProxy proxy = new SocksEgressProxy(brokerPort)) {
            Inbox operatorInbox = new Inbox();
            MqttClient operator = new MqttClient(brokerUri, "operator-" + System.nanoTime(), new MemoryPersistence());
            operator.setTimeToWait(TIMEOUT_MS);
            operator.setCallback(operatorInbox);
            Inbox gatewayInbox = new Inbox();
            MqttClient gateway = new MqttClient(brokerUri, "gateway-" + System.nanoTime(), new MemoryPersistence());
            gateway.setTimeToWait(TIMEOUT_MS);
            gateway.setCallback(gatewayInbox);
            try {
                operator.connect(options());
                operator.subscribe(telemetryTopic, 1);

                MqttConnectOptions gatewayOptions = options();
                gatewayOptions.setSocketFactory(new SocksSocketFactory(proxy.address()));
                gateway.connect(gatewayOptions);
                assertThat(gateway.isConnected()).isTrue();
                assertThat(gateway.getCurrentServerURI()).isEqualTo(brokerUri);
                assertThat(proxy.tunnels()).containsExactly("127.0.0.1:" + brokerPort);

                gateway.subscribe(commandTopic, 1);
                gateway.publish(telemetryTopic, text("{\"spindle-rpm\":11840}", 1));
                assertThat(operatorInbox.next()).isEqualTo(telemetryTopic + " {\"spindle-rpm\":11840}");

                operator.publish(commandTopic, text("{\"action\":\"pause\"}", 1));
                assertThat(gatewayInbox.next()).isEqualTo(commandTopic + " {\"action\":\"pause\"}");

                assertThat(proxy.bytesToBroker()).as("CONNECT, SUBSCRIBE and PUBLISH crossed the tunnel")
                        .isGreaterThan(0);
                assertThat(proxy.bytesFromBroker()).as("CONNACK, SUBACK and the command crossed the tunnel")
                        .isGreaterThan(0);

                gateway.disconnect();
                assertThat(gateway.isConnected()).isFalse();
            } finally {
                gateway.close();
                operator.disconnect();
                operator.close();
            }
            assertThat(proxy.tunnels()).as("one tunnel for the whole session").hasSize(1);
        }
    }

    @Test
    void tunnelRefusedByTheProxyAllowlistFailsTheConnect() throws Exception {
        try (SocksEgressProxy proxy = new SocksEgressProxy(brokerPort);
                ServerSocket unlisted = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            String unlistedUri = "tcp://127.0.0.1:" + unlisted.getLocalPort();
            MqttClient gateway = new MqttClient(unlistedUri, "gateway-" + System.nanoTime(), new MemoryPersistence());
            gateway.setTimeToWait(TIMEOUT_MS);
            try {
                MqttConnectOptions gatewayOptions = options();
                gatewayOptions.setSocketFactory(new SocksSocketFactory(proxy.address()));

                MqttException failure = catchThrowableOfType(() -> gateway.connect(gatewayOptions),
                        MqttException.class);

                assertThat(failure).isNotNull();
                assertThat(failure.getCause()).isInstanceOf(SocketException.class)
                        .hasMessageContaining("not allowed by ruleset");
                assertThat(gateway.isConnected()).isFalse();
                assertThat(proxy.refused()).containsExactly("127.0.0.1:" + unlisted.getLocalPort());
                assertThat(proxy.tunnels()).isEmpty();
            } finally {
                gateway.close();
            }
        }
    }

    @Test
    void tunedFactoryIsConsultedForEveryNewConnection() throws Exception {
        TunedSocketFactory factory = new TunedSocketFactory();
        String topic = "plant/press-" + System.nanoTime() + "/cycle";
        Inbox inbox = new Inbox();
        MqttClient press = new MqttClient(brokerUri, "press-" + System.nanoTime(), new MemoryPersistence());
        press.setTimeToWait(TIMEOUT_MS);
        press.setCallback(inbox);
        try {
            MqttConnectOptions pressOptions = options();
            pressOptions.setSocketFactory(factory);

            press.connect(pressOptions);
            press.subscribe(topic, 0);
            press.publish(topic, text("cycle-1", 0));
            assertThat(inbox.next()).isEqualTo(topic + " cycle-1");
            press.disconnect();

            press.connect(pressOptions);
            press.subscribe(topic, 0);
            press.publish(topic, text("cycle-2", 0));
            assertThat(inbox.next()).isEqualTo(topic + " cycle-2");

            List<Socket> sockets = factory.sockets();
            assertThat(sockets).as("a fresh socket per connect").hasSize(2);
            assertThat(sockets.get(0).isClosed()).as("the first session's socket").isTrue();
            Socket current = sockets.get(1);
            assertThat(current.isConnected()).isTrue();
            assertThat(current.getPort()).isEqualTo(brokerPort);
            assertThat(current.getTcpNoDelay()).isTrue();
            assertThat(current.getKeepAlive()).isTrue();
            press.disconnect();
        } finally {
            press.close();
        }
    }

    @Test
    void tlsFactoryOnAPlainTcpUriIsRejectedBeforeAnySocketOpens() throws Exception {
        MqttClient gateway = new MqttClient(brokerUri, "gateway-" + System.nanoTime(), new MemoryPersistence());
        try {
            MqttConnectOptions gatewayOptions = options();
            gatewayOptions.setSocketFactory(SSLSocketFactory.getDefault());

            MqttException failure = catchThrowableOfType(() -> gateway.connect(gatewayOptions),
                    MqttException.class);

            assertThat(failure).isNotNull();
            assertThat(failure.getReasonCode()).isEqualTo(MqttException.REASON_CODE_SOCKET_FACTORY_MISMATCH);
            assertThat(gateway.isConnected()).isFalse();
        } finally {
            gateway.close();
        }
    }

    /// The gateway's socket factory: every socket it creates connects through the egress proxy.
    private static final class SocksSocketFactory extends SocketFactory {

        private final Proxy proxy;

        SocksSocketFactory(InetSocketAddress proxyAddress) {
            this.proxy = new Proxy(Proxy.Type.SOCKS, proxyAddress);
        }

        @Override
        public Socket createSocket() {
            return new Socket(proxy);
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException {
            Socket socket = createSocket();
            socket.connect(new InetSocketAddress(host, port));
            return socket;
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
            Socket socket = createSocket();
            socket.bind(new InetSocketAddress(localHost, localPort));
            socket.connect(new InetSocketAddress(host, port));
            return socket;
        }

        @Override
        public Socket createSocket(InetAddress host, int port) throws IOException {
            Socket socket = createSocket();
            socket.connect(new InetSocketAddress(host, port));
            return socket;
        }

        @Override
        public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort)
                throws IOException {
            Socket socket = createSocket();
            socket.bind(new InetSocketAddress(localAddress, localPort));
            socket.connect(new InetSocketAddress(address, port));
            return socket;
        }
    }

    /// A low-latency socket factory that disables Nagle's algorithm, enables TCP keep-alive,
    /// and keeps every socket it hands out so the deployment can audit them.
    private static final class TunedSocketFactory extends SocketFactory {

        private final List<Socket> created = Collections.synchronizedList(new ArrayList<>());

        List<Socket> sockets() {
            synchronized (created) {
                return new ArrayList<>(created);
            }
        }

        @Override
        public Socket createSocket() throws IOException {
            Socket socket = new Socket();
            socket.setTcpNoDelay(true);
            socket.setKeepAlive(true);
            created.add(socket);
            return socket;
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException {
            Socket socket = createSocket();
            socket.connect(new InetSocketAddress(host, port));
            return socket;
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
            Socket socket = createSocket();
            socket.bind(new InetSocketAddress(localHost, localPort));
            socket.connect(new InetSocketAddress(host, port));
            return socket;
        }

        @Override
        public Socket createSocket(InetAddress host, int port) throws IOException {
            Socket socket = createSocket();
            socket.connect(new InetSocketAddress(host, port));
            return socket;
        }

        @Override
        public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort)
                throws IOException {
            Socket socket = createSocket();
            socket.bind(new InetSocketAddress(localAddress, localPort));
            socket.connect(new InetSocketAddress(address, port));
            return socket;
        }
    }

    /// The plant's SOCKS5 egress proxy: it accepts the no-authentication method, opens a tunnel
    /// only to the allowlisted broker port, refuses every other destination with the
    /// "not allowed by ruleset" reply, and counts the bytes it relays in each direction.
    private static final class SocksEgressProxy implements AutoCloseable {

        private static final int VERSION = 5;

        private static final int NO_AUTHENTICATION = 0;

        private static final int COMMAND_CONNECT = 1;

        private static final int ADDRESS_IPV4 = 1;

        private static final int ADDRESS_DOMAIN = 3;

        private static final int ADDRESS_IPV6 = 4;

        private static final int REPLY_SUCCEEDED = 0;

        private static final int REPLY_NOT_ALLOWED = 2;

        private final int allowedPort;

        private final ServerSocket listener;

        private final Thread acceptor;

        private final List<Socket> active = Collections.synchronizedList(new ArrayList<>());

        private final List<String> tunnels = Collections.synchronizedList(new ArrayList<>());

        private final List<String> refused = Collections.synchronizedList(new ArrayList<>());

        private final AtomicLong bytesToBroker = new AtomicLong();

        private final AtomicLong bytesFromBroker = new AtomicLong();

        SocksEgressProxy(int allowedPort) throws IOException {
            this.allowedPort = allowedPort;
            this.listener = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
            this.acceptor = new Thread(this::acceptLoop, "socks-acceptor");
            this.acceptor.setDaemon(true);
            this.acceptor.start();
        }

        InetSocketAddress address() {
            return new InetSocketAddress(InetAddress.getLoopbackAddress(), listener.getLocalPort());
        }

        List<String> tunnels() {
            synchronized (tunnels) {
                return new ArrayList<>(tunnels);
            }
        }

        List<String> refused() {
            synchronized (refused) {
                return new ArrayList<>(refused);
            }
        }

        long bytesToBroker() {
            return bytesToBroker.get();
        }

        long bytesFromBroker() {
            return bytesFromBroker.get();
        }

        private void acceptLoop() {
            while (!listener.isClosed()) {
                try {
                    Socket client = listener.accept();
                    active.add(client);
                    Thread handler = new Thread(() -> handle(client), "socks-handler");
                    handler.setDaemon(true);
                    handler.start();
                } catch (IOException e) {
                    return;
                }
            }
        }

        private void handle(Socket client) {
            try {
                DataInputStream in = new DataInputStream(client.getInputStream());
                OutputStream out = client.getOutputStream();
                if (in.readUnsignedByte() != VERSION) {
                    closeSocket(client);
                    return;
                }
                byte[] methods = new byte[in.readUnsignedByte()];
                in.readFully(methods);
                out.write(new byte[] {VERSION, NO_AUTHENTICATION});
                out.flush();

                if (in.readUnsignedByte() != VERSION || in.readUnsignedByte() != COMMAND_CONNECT) {
                    closeSocket(client);
                    return;
                }
                in.readUnsignedByte();
                String host = readHost(in);
                int port = in.readUnsignedShort();
                String destination = host + ":" + port;
                if (port != allowedPort) {
                    refused.add(destination);
                    out.write(reply(REPLY_NOT_ALLOWED));
                    out.flush();
                    closeSocket(client);
                    return;
                }
                Socket broker = new Socket(host, port);
                active.add(broker);
                tunnels.add(destination);
                out.write(reply(REPLY_SUCCEEDED));
                out.flush();
                pump(client, broker, bytesToBroker);
                pump(broker, client, bytesFromBroker);
            } catch (IOException e) {
                closeSocket(client);
            }
        }

        private static String readHost(DataInputStream in) throws IOException {
            int type = in.readUnsignedByte();
            byte[] raw;
            if (type == ADDRESS_IPV4) {
                raw = new byte[4];
            } else if (type == ADDRESS_IPV6) {
                raw = new byte[16];
            } else if (type == ADDRESS_DOMAIN) {
                byte[] name = new byte[in.readUnsignedByte()];
                in.readFully(name);
                return new String(name, StandardCharsets.US_ASCII);
            } else {
                throw new IOException("Unsupported SOCKS address type " + type);
            }
            in.readFully(raw);
            return InetAddress.getByAddress(raw).getHostAddress();
        }

        /// A reply carrying the status and an all-zero IPv4 bound address and port.
        private static byte[] reply(int status) {
            return new byte[] {VERSION, (byte) status, 0, ADDRESS_IPV4, 0, 0, 0, 0, 0, 0};
        }

        private void pump(Socket from, Socket to, AtomicLong counter) {
            Thread thread = new Thread(() -> {
                byte[] buffer = new byte[8192];
                try (InputStream in = from.getInputStream(); OutputStream out = to.getOutputStream()) {
                    int read;
                    while ((read = in.read(buffer)) >= 0) {
                        counter.addAndGet(read);
                        out.write(buffer, 0, read);
                        out.flush();
                    }
                } catch (IOException ignored) {
                    // The peer or the test closed the tunnel.
                } finally {
                    closeSocket(from);
                    closeSocket(to);
                }
            }, "socks-pump");
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

    /// Collects the messages a client receives as `topic payload` lines.
    private static final class Inbox implements MqttCallback {

        private final BlockingQueue<String> arrived = new LinkedBlockingQueue<>();

        @Override
        public void connectionLost(Throwable cause) {
        }

        @Override
        public void messageArrived(String topic, MqttMessage message) {
            arrived.add(topic + " " + new String(message.getPayload(), StandardCharsets.UTF_8));
        }

        @Override
        public void deliveryComplete(IMqttDeliveryToken token) {
        }

        String next() throws InterruptedException {
            String message = arrived.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS);
            assertThat(message).as("message within %d ms", TIMEOUT_MS).isNotNull();
            return message;
        }
    }

    private static MqttMessage text(String text, int qos) {
        MqttMessage message = new MqttMessage(text.getBytes(StandardCharsets.UTF_8));
        message.setQos(qos);
        return message;
    }

    private static MqttConnectOptions options() {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setMqttVersion(MqttConnectOptions.MQTT_VERSION_3_1_1);
        options.setCleanSession(true);
        options.setAutomaticReconnect(false);
        options.setConnectionTimeout(10);
        return options;
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
