/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_paho.org_eclipse_paho_client_mqttv3;

import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttSecurityException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Exercises broker password authentication and high-availability server URI failover:
/// `MqttConnectOptions.setUserName`/`setPassword`, `setServerURIs` lists the client walks in
/// order until one broker accepts it, `MqttClient.getCurrentServerURI`, and the
/// `MqttSecurityException` reason codes a refusing CONNACK turns into.
///
/// The scenario is a delivery fleet: the dispatcher and each truck are configured with a
/// list of brokers whose first entries are down, so they fail over to the one that is up and
/// log in with their own credentials before the dispatcher sends a truck its route. A truck
/// with a stale password and an anonymous client are refused, and a list in which every
/// broker is down surfaces the last connect error.
public class PasswordAuthFailoverTest {

    private static final String IMAGE = "eclipse-mosquitto:2.0.22";

    private static final String BROKER_CONFIG = """
            listener 1883
            allow_anonymous false
            password_file /tmp/auth/passwd
            """;

    private static final String BROKER_SCRIPT = """
            mkdir -p /tmp/auth \
            && printf '%s' "$1" > /tmp/auth/mosquitto.conf \
            && touch /tmp/auth/passwd \
            && mosquitto_passwd -b /tmp/auth/passwd dispatcher "$2" \
            && mosquitto_passwd -b /tmp/auth/passwd truck-7 "$3" \
            && chmod 0700 /tmp/auth && chmod 0600 /tmp/auth/* && chown -R mosquitto /tmp/auth \
            && exec mosquitto -c /tmp/auth/mosquitto.conf""";

    private static final String DISPATCHER_PASSWORD = "dispatch-secret";

    private static final String TRUCK_PASSWORD = "truck-7-secret";

    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(90);

    private static final long TIMEOUT_MS = 10_000;

    private static String containerName;

    private static String serverUri;

    @BeforeAll
    static void startBroker() throws Exception {
        containerName = "paho-auth-" + System.nanoTime();
        docker("run", "-d", "--name", containerName, "-p", "127.0.0.1::1883", IMAGE,
                "sh", "-c", BROKER_SCRIPT, "sh", BROKER_CONFIG, DISPATCHER_PASSWORD, TRUCK_PASSWORD);
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
    void fleetFailsOverToTheLiveBrokerAndLogsIn() throws Exception {
        String[] brokers = {deadBrokerUri(), deadBrokerUri(), serverUri};
        BlockingQueue<String> routes = new LinkedBlockingQueue<>();
        MqttClient dispatcher = client(brokers[0], "dispatcher");
        MqttClient truck = client(brokers[0], "truck-7");
        try {
            MqttConnectOptions truckOptions = credentials("truck-7", TRUCK_PASSWORD);
            truckOptions.setServerURIs(brokers);
            assertThat(truckOptions.getServerURIs()).containsExactly(brokers);
            IMqttToken truckConnect = truck.connectWithResult(truckOptions);
            assertThat(truckConnect.isComplete()).isTrue();
            assertThat(truckConnect.getSessionPresent()).isFalse();
            assertThat(truck.getServerURI()).isEqualTo(brokers[0]);
            assertThat(truck.getCurrentServerURI()).isEqualTo(serverUri);
            truck.subscribe("fleet/truck-7/route", 1,
                    (topic, message) -> routes.add(topic + " " + text(message)));

            MqttConnectOptions dispatcherOptions = credentials("dispatcher", DISPATCHER_PASSWORD);
            dispatcherOptions.setServerURIs(brokers);
            dispatcher.connect(dispatcherOptions);
            assertThat(dispatcher.isConnected()).isTrue();
            assertThat(dispatcher.getCurrentServerURI()).isEqualTo(serverUri);

            dispatcher.publish("fleet/truck-7/route", bytes("depot>harbor>depot"), 1, false);
            assertThat(routes.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS))
                    .isEqualTo("fleet/truck-7/route depot>harbor>depot");
            truck.disconnect(1_000);
            dispatcher.disconnect(1_000);
            assertThat(truck.isConnected()).isFalse();
        } finally {
            closeQuietly(truck);
            closeQuietly(dispatcher);
        }
    }

    @Test
    void brokerRefusesStaleAndMissingCredentials() throws Exception {
        MqttClient stale = client(serverUri, "truck-7");
        try {
            assertThatThrownBy(() -> stale.connect(credentials("truck-7", "last-years-secret")))
                    .isInstanceOfSatisfying(MqttSecurityException.class, e -> assertThat(e.getReasonCode())
                            .isEqualTo(MqttException.REASON_CODE_NOT_AUTHORIZED));
            assertThat(stale.isConnected()).isFalse();
        } finally {
            closeQuietly(stale);
        }

        MqttClient anonymous = client(serverUri, "anonymous");
        try {
            assertThatThrownBy(() -> anonymous.connect(options()))
                    .isInstanceOfSatisfying(MqttSecurityException.class, e -> assertThat(e.getReasonCode())
                            .isEqualTo(MqttException.REASON_CODE_NOT_AUTHORIZED));
            assertThat(anonymous.isConnected()).isFalse();
        } finally {
            closeQuietly(anonymous);
        }
    }

    @Test
    void listOfUnreachableBrokersSurfacesTheConnectError() throws Exception {
        String[] brokers = {deadBrokerUri(), deadBrokerUri()};
        MqttClient truck = client(brokers[0], "truck-7");
        try {
            MqttConnectOptions options = credentials("truck-7", TRUCK_PASSWORD);
            options.setServerURIs(brokers);
            assertThatThrownBy(() -> truck.connect(options))
                    .isInstanceOfSatisfying(MqttException.class, e -> {
                        assertThat(e.getReasonCode()).isEqualTo(MqttException.REASON_CODE_SERVER_CONNECT_ERROR);
                        assertThat(e.getCause()).isInstanceOf(ConnectException.class);
                    });
            assertThat(truck.isConnected()).isFalse();
        } finally {
            closeQuietly(truck);
        }
    }

    @Test
    void connectOptionsRejectMalformedBrokerLists() {
        MqttConnectOptions options = credentials("truck-7", TRUCK_PASSWORD);
        assertThat(options.getUserName()).isEqualTo("truck-7");
        assertThat(options.getPassword()).containsExactly(TRUCK_PASSWORD.toCharArray());

        assertThatThrownBy(() -> options.setServerURIs(new String[] {serverUri, "http://127.0.0.1:1883"}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(options.getServerURIs()).isNull();
    }

    private static MqttConnectOptions credentials(String user, String password) {
        MqttConnectOptions options = options();
        options.setUserName(user);
        options.setPassword(password.toCharArray());
        return options;
    }

    private static MqttConnectOptions options() {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setMqttVersion(MqttConnectOptions.MQTT_VERSION_3_1_1);
        options.setCleanSession(true);
        options.setAutomaticReconnect(false);
        options.setConnectionTimeout(5);
        options.setKeepAliveInterval(30);
        return options;
    }

    /// Returns the URI of a local port nothing listens on, so connecting to it is refused.
    private static String deadBrokerUri() throws Exception {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return "tcp://127.0.0.1:" + socket.getLocalPort();
        }
    }

    private static MqttClient client(String uri, String role) throws MqttException {
        return new MqttClient(uri, role + "-" + System.nanoTime(), new MemoryPersistence());
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
            MqttClient probe = client(serverUri, "startup-probe");
            try {
                probe.connect(credentials("dispatcher", DISPATCHER_PASSWORD));
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

    private static String text(MqttMessage message) {
        return new String(message.getPayload(), StandardCharsets.UTF_8);
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
