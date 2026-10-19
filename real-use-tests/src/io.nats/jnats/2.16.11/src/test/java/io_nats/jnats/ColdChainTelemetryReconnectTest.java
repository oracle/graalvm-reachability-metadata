/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_nats.jnats;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.Connection;
import io.nats.client.ConnectionListener;
import io.nats.client.Dispatcher;
import io.nats.client.Message;
import io.nats.client.Nats;
import io.nats.client.Options;
import io.nats.client.ReconnectDelayHandler;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Keeps a cold-chain telemetry link alive across a NATS server restart through the jnats
 * connection lifecycle: automatic reconnection, the reconnect buffer, subscription replay,
 * connection events, and connection statistics.
 *
 * A refrigerated-truck gateway publishes JSON temperature readings and listens to them on
 * the same connection; while the server is stopped it keeps publishing into the reconnect
 * buffer, and once the server is back the client reconnects with its own delay policy,
 * replays its subscription, and delivers the buffered readings in order.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ColdChainTelemetryReconnectTest {

    private static final String NATS_IMAGE = "nats:2.15.1";

    private static final String SUBJECTS = "telemetry.truck-7.>";

    private final ObjectMapper mapper = new ObjectMapper();

    private final String containerName = "real-use-jnats-reconnect-" + UUID.randomUUID();

    private String serverUrl;

    @BeforeAll
    void startServer() throws Exception {
        int port = freePort();
        serverUrl = "nats://127.0.0.1:" + port;
        docker("run", "-d", "--name", containerName, "-p", port + ":4222", NATS_IMAGE);
        awaitServer();
    }

    @AfterAll
    void stopServer() throws Exception {
        Process remove = new ProcessBuilder("docker", "rm", "-f", containerName).inheritIO().start();
        remove.waitFor(60, TimeUnit.SECONDS);
    }

    @Test
    void deliversBufferedReadingsAfterServerRestart() throws Exception {
        BlockingQueue<ConnectionListener.Events> events = new LinkedBlockingQueue<>();
        List<ConnectionListener.Events> history = new CopyOnWriteArrayList<>();
        AtomicLong reconnectAttempts = new AtomicLong();
        ReconnectDelayHandler fixedDelay = totalTries -> {
            reconnectAttempts.set(totalTries);
            return Duration.ofMillis(250);
        };
        Options options = new Options.Builder()
                .server(serverUrl)
                .connectionName("truck-7-gateway")
                .connectionTimeout(Duration.ofSeconds(2))
                .pingInterval(Duration.ofSeconds(1))
                .maxReconnects(-1)
                .reconnectDelayHandler(fixedDelay)
                .reconnectBufferSize(64 * 1024)
                .connectionListener((connection, event) -> {
                    history.add(event);
                    events.add(event);
                })
                .build();

        Connection gateway = Nats.connect(options);
        BlockingQueue<Reading> received = new LinkedBlockingQueue<>();
        Dispatcher monitor = gateway.createDispatcher(message -> received.add(readReading(message)));
        monitor.subscribe(SUBJECTS);
        gateway.flush(Duration.ofSeconds(5));
        String firstServerId = gateway.getServerInfo().getServerId();
        assertThat(gateway.getConnectedUrl()).isEqualTo(serverUrl);

        Reading beforeOutage = new Reading("truck-7", "chiller", 3.5);
        publish(gateway, "telemetry.truck-7.chiller", beforeOutage);
        assertThat(received.poll(10, TimeUnit.SECONDS)).isEqualTo(beforeOutage);

        docker("stop", "-t", "1", containerName);
        awaitEvent(events, ConnectionListener.Events.DISCONNECTED);
        assertThat(gateway.getStatus()).isIn(Connection.Status.DISCONNECTED, Connection.Status.RECONNECTING);

        List<Reading> duringOutage = List.of(
                new Reading("truck-7", "chiller", 4.1),
                new Reading("truck-7", "freezer", -18.2),
                new Reading("truck-7", "chiller", 4.6));
        publish(gateway, "telemetry.truck-7.chiller", duringOutage.get(0));
        publish(gateway, "telemetry.truck-7.freezer", duringOutage.get(1));
        publish(gateway, "telemetry.truck-7.chiller", duringOutage.get(2));
        assertThat(received).isEmpty();

        docker("start", containerName);
        awaitEvent(events, ConnectionListener.Events.RECONNECTED);
        for (Reading expected : duringOutage) {
            assertThat(received.poll(10, TimeUnit.SECONDS)).isEqualTo(expected);
        }

        assertThat(gateway.getStatus()).isEqualTo(Connection.Status.CONNECTED);
        assertThat(gateway.getServerInfo().getServerId()).isNotEqualTo(firstServerId);
        assertThat(gateway.getStatistics().getReconnects()).isEqualTo(1);
        assertThat(reconnectAttempts.get()).isPositive();
        assertThat(history).containsSubsequence(
                ConnectionListener.Events.DISCONNECTED, ConnectionListener.Events.RECONNECTED);

        Reading afterOutage = new Reading("truck-7", "freezer", -19.0);
        publish(gateway, "telemetry.truck-7.freezer", afterOutage);
        assertThat(received.poll(10, TimeUnit.SECONDS)).isEqualTo(afterOutage);
        assertThat(gateway.getStatistics().getInMsgs()).isGreaterThanOrEqualTo(5);

        gateway.close();
        awaitEvent(events, ConnectionListener.Events.CLOSED);
        assertThat(gateway.getStatus()).isEqualTo(Connection.Status.CLOSED);
    }

    private void publish(Connection connection, String subject, Reading reading) throws IOException {
        connection.publish(subject, mapper.writeValueAsBytes(reading));
    }

    private Reading readReading(Message message) {
        try {
            return mapper.readValue(message.getData(), Reading.class);
        } catch (IOException e) {
            throw new IllegalStateException("Unreadable reading on " + message.getSubject(), e);
        }
    }

    private static void awaitEvent(BlockingQueue<ConnectionListener.Events> events,
            ConnectionListener.Events wanted) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 60_000L;
        while (System.currentTimeMillis() < deadline) {
            ConnectionListener.Events event = events.poll(500, TimeUnit.MILLISECONDS);
            if (event == wanted) {
                return;
            }
        }
        throw new IllegalStateException("No " + wanted + " connection event within 60 seconds");
    }

    private static void docker(String... arguments) throws Exception {
        List<String> command = new ArrayList<>(List.of("docker"));
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).inheritIO().start();
        assertThat(process.waitFor(120, TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).isZero();
    }

    private void awaitServer() throws Exception {
        long deadline = System.currentTimeMillis() + 120_000L;
        IOException lastFailure = null;
        Options probeOptions = new Options.Builder()
                .server(serverUrl)
                .connectionTimeout(Duration.ofSeconds(2))
                .build();
        while (System.currentTimeMillis() < deadline) {
            try (Connection probe = Nats.connect(probeOptions)) {
                return;
            } catch (IOException e) {
                lastFailure = e;
                Thread.sleep(500L);
            }
        }
        throw new IllegalStateException("NATS did not accept connections at " + serverUrl, lastFailure);
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    /// A temperature reading from one compartment of a truck, published as a JSON body.
    record Reading(String truck, String compartment, double celsius) {
    }
}
