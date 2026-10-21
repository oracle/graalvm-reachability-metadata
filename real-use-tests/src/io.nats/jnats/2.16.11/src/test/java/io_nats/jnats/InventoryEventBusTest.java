/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_nats.jnats;

import io.nats.client.Connection;
import io.nats.client.ConnectionListener;
import io.nats.client.Dispatcher;
import io.nats.client.Message;
import io.nats.client.Nats;
import io.nats.client.Options;
import io.nats.client.Subscription;
import io.nats.client.impl.Headers;
import io.nats.client.impl.NatsMessage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs a warehouse inventory event bus on core NATS publish-subscribe through the jnats
 * dispatcher, queue-group, wildcard-subject, header, request-reply, and drain API.
 *
 * Stores publish low-stock events with headers; two restock workers share them through a
 * queue group, an auditor sees every event through a full-wildcard subscription, a stock
 * service answers lookups through request-reply, and the application drains its connection
 * on shutdown while a connection listener observes the lifecycle.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InventoryEventBusTest {

    private static final String NATS_IMAGE = "nats:2.15.0";

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

    private static final List<String> STORES = List.of("berlin", "lisbon", "oslo", "rome", "vienna", "madrid");

    private static final Map<String, Integer> STOCK = Map.of(
            "sku-1", 42,
            "sku-2", 0);

    private final String containerName = "real-use-jnats-" + UUID.randomUUID();

    private String serverUrl;

    @BeforeAll
    void startServer() throws Exception {
        int port = freePort();
        serverUrl = "nats://127.0.0.1:" + port;
        Process run = new ProcessBuilder("docker", "run", "-d", "--rm", "--name", containerName,
                "-p", port + ":4222", NATS_IMAGE)
                .inheritIO()
                .start();
        assertThat(run.waitFor(120, TimeUnit.SECONDS)).isTrue();
        assertThat(run.exitValue()).isZero();
        awaitServer();
    }

    @AfterAll
    void stopServer() throws Exception {
        Process remove = new ProcessBuilder("docker", "rm", "-f", containerName).inheritIO().start();
        remove.waitFor(60, TimeUnit.SECONDS);
    }

    @Test
    void sharesLowStockEventsAcrossWorkersAndAuditsEveryEvent() throws Exception {
        LifecycleRecorder lifecycle = new LifecycleRecorder();
        try (Connection workerConnection = connect(null); Connection storeConnection = connect(null)) {
            Connection auditConnection = connect(lifecycle);
            assertThat(lifecycle.connected.await(10, TimeUnit.SECONDS)).isTrue();

            CountDownLatch restocked = new CountDownLatch(STORES.size());
            Map<String, List<String>> handledByWorker = new ConcurrentHashMap<>();
            for (String worker : List.of("worker-a", "worker-b")) {
                Dispatcher dispatcher = workerConnection.createDispatcher(message -> {
                    handledByWorker.computeIfAbsent(worker, key -> new CopyOnWriteArrayList<>())
                            .add(message.getSubject());
                    restocked.countDown();
                });
                dispatcher.subscribe("inventory.*.low", "restock");
            }

            Subscription audit = auditConnection.subscribe("inventory.>");
            Subscription restockAudit = auditConnection.subscribe("inventory.*.restocked");
            restockAudit.unsubscribe(1);
            workerConnection.flush(REQUEST_TIMEOUT);
            auditConnection.flush(REQUEST_TIMEOUT);

            for (String store : STORES) {
                Headers headers = new Headers()
                        .add("Store", store)
                        .add("Sku", "sku-2");
                storeConnection.publish(NatsMessage.builder()
                        .subject("inventory." + store + ".low")
                        .headers(headers)
                        .data("remaining=1", StandardCharsets.UTF_8)
                        .build());
            }
            storeConnection.publish("inventory.berlin.restocked", "sku-2".getBytes(StandardCharsets.UTF_8));
            storeConnection.publish("inventory.oslo.restocked", "sku-1".getBytes(StandardCharsets.UTF_8));
            storeConnection.flush(REQUEST_TIMEOUT);

            assertThat(restocked.await(10, TimeUnit.SECONDS)).isTrue();
            List<String> handled = new ArrayList<>();
            handledByWorker.values().forEach(handled::addAll);
            assertThat(handled).containsExactlyInAnyOrderElementsOf(
                    STORES.stream().map(store -> "inventory." + store + ".low").toList());

            List<Message> audited = new ArrayList<>();
            for (int i = 0; i < STORES.size() + 2; i++) {
                Message message = audit.nextMessage(REQUEST_TIMEOUT);
                assertThat(message).isNotNull();
                audited.add(message);
            }
            assertThat(audited).filteredOn(message -> message.getSubject().endsWith(".low"))
                    .hasSize(STORES.size())
                    .allSatisfy(message -> {
                        assertThat(message.hasHeaders()).isTrue();
                        assertThat(message.getHeaders().getFirst("Sku")).isEqualTo("sku-2");
                        assertThat(message.getSubject())
                                .isEqualTo("inventory." + message.getHeaders().getFirst("Store") + ".low");
                        assertThat(new String(message.getData(), StandardCharsets.UTF_8)).isEqualTo("remaining=1");
                    });

            Message firstRestock = restockAudit.nextMessage(REQUEST_TIMEOUT);
            assertThat(firstRestock.getSubject()).isEqualTo("inventory.berlin.restocked");
            assertThat(firstRestock.hasHeaders()).isFalse();
            assertThat(restockAudit.isActive()).isFalse();

            CompletableFuture<Boolean> drained = auditConnection.drain(REQUEST_TIMEOUT);
            assertThat(drained.get(10, TimeUnit.SECONDS)).isTrue();
            assertThat(auditConnection.getStatus()).isEqualTo(Connection.Status.CLOSED);
            assertThat(lifecycle.closed.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(lifecycle.events).startsWith(ConnectionListener.Events.CONNECTED)
                    .endsWith(ConnectionListener.Events.CLOSED);
        }
    }

    @Test
    void answersStockLookupsThroughRequestReply() throws Exception {
        try (Connection serviceConnection = connect(null); Connection clientConnection = connect(null)) {
            Dispatcher stockService = serviceConnection.createDispatcher(message -> {
                String sku = new String(message.getData(), StandardCharsets.UTF_8);
                Integer quantity = STOCK.get(sku);
                Headers headers = new Headers().add("Status", quantity == null ? "unknown" : "ok");
                serviceConnection.publish(NatsMessage.builder()
                        .subject(message.getReplyTo())
                        .headers(headers)
                        .data(quantity == null ? "" : quantity.toString(), StandardCharsets.UTF_8)
                        .build());
            });
            stockService.subscribe("inventory.lookup");
            serviceConnection.flush(REQUEST_TIMEOUT);

            Message known = clientConnection.request("inventory.lookup",
                    "sku-1".getBytes(StandardCharsets.UTF_8), REQUEST_TIMEOUT);
            assertThat(known.getHeaders().getFirst("Status")).isEqualTo("ok");
            assertThat(new String(known.getData(), StandardCharsets.UTF_8)).isEqualTo("42");

            Message soldOut = clientConnection.request("inventory.lookup", "sku-2".getBytes(StandardCharsets.UTF_8))
                    .get(5, TimeUnit.SECONDS);
            assertThat(new String(soldOut.getData(), StandardCharsets.UTF_8)).isEqualTo("0");

            Message unknown = clientConnection.request("inventory.lookup",
                    "sku-9".getBytes(StandardCharsets.UTF_8), REQUEST_TIMEOUT);
            assertThat(unknown.getHeaders().getFirst("Status")).isEqualTo("unknown");
            assertThat(unknown.getData()).isEmpty();

            serviceConnection.closeDispatcher(stockService);
            serviceConnection.flush(REQUEST_TIMEOUT);
            Message unanswered = clientConnection.request("inventory.lookup",
                    "sku-1".getBytes(StandardCharsets.UTF_8), Duration.ofMillis(500));
            assertThat(unanswered).isNull();
        }
    }

    private Connection connect(ConnectionListener listener) throws Exception {
        Options.Builder options = new Options.Builder()
                .server(serverUrl)
                .connectionTimeout(Duration.ofSeconds(2));
        if (listener != null) {
            options.connectionListener(listener);
        }
        return Nats.connect(options.build());
    }

    private void awaitServer() throws Exception {
        long deadline = System.currentTimeMillis() + 120_000L;
        IOException lastFailure = null;
        while (System.currentTimeMillis() < deadline) {
            try (Connection probe = connect(null)) {
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

    /// Records the connection lifecycle events an application would log.
    static final class LifecycleRecorder implements ConnectionListener {

        final List<Events> events = new CopyOnWriteArrayList<>();

        final CountDownLatch connected = new CountDownLatch(1);

        final CountDownLatch closed = new CountDownLatch(1);

        @Override
        public void connectionEvent(Connection connection, Events type) {
            events.add(type);
            if (type == Events.CONNECTED) {
                connected.countDown();
            } else if (type == Events.CLOSED) {
                closed.countDown();
            }
        }
    }
}
