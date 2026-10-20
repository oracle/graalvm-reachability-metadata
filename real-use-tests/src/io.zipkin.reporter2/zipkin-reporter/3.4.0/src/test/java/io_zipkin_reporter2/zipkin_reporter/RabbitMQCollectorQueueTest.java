/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_zipkin_reporter2.zipkin_reporter;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rabbitmq.client.Address;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.GetResponse;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import zipkin2.Endpoint;
import zipkin2.Span;
import zipkin2.codec.SpanBytesDecoder;
import zipkin2.reporter.AsyncReporter;
import zipkin2.reporter.ClosedSenderException;
import zipkin2.reporter.Encoding;
import zipkin2.reporter.InMemoryReporterMetrics;
import zipkin2.reporter.SpanBytesEncoder;
import zipkin2.reporter.amqp.RabbitMQSender;

/**
 * Shipping spans to a Zipkin collector queue on a RabbitMQ broker through `RabbitMQSender`,
 * configured as an operations team deploys it: dedicated credentials and virtual host, an
 * application-supplied `ConnectionFactory`, per-service queues derived with `toBuilder()`,
 * `AsyncReporter` flushes in Proto3 and JSON v2, an unreachable broker counted as dropped
 * messages, and the sender refusing work after shutdown.
 *
 * A payments service reports its spans into the `observability` virtual host, where the
 * collector reads them from a durable queue. The test plays that collector with the plain AMQP
 * client: it declares the queues the services publish to and pulls each message off them to
 * decode the spans it carries.
 */
public class RabbitMQCollectorQueueTest {

    private static final String IMAGE = "rabbitmq:4.3";
    private static final String USER = "tracer";
    private static final String PASSWORD = "tr4ce-only";
    private static final String VHOST = "observability";
    private static final String TRACE_ID = "4b1d0c9e7a2f36581e0d9c4b7a6f3e12";
    private static final Endpoint PAYMENTS = Endpoint.newBuilder().serviceName("payments").ip("10.0.4.17").port(8443).build();

    private static String containerName;
    private static int amqpPort;
    private static Connection collectorConnection;
    private static Channel collector;

    @BeforeAll
    static void startBroker() throws Exception {
        containerName = "zipkin-rabbit-" + UUID.randomUUID();
        try (ServerSocket socket = new ServerSocket(0)) {
            amqpPort = socket.getLocalPort();
        }
        docker("run", "-d", "--name", containerName,
                "-e", "RABBITMQ_DEFAULT_USER=" + USER,
                "-e", "RABBITMQ_DEFAULT_PASS=" + PASSWORD,
                "-e", "RABBITMQ_DEFAULT_VHOST=" + VHOST,
                "-p", "127.0.0.1:" + amqpPort + ":5672",
                IMAGE);
        collectorConnection = connectWhenReady();
        collector = collectorConnection.createChannel();
    }

    @AfterAll
    static void stopBroker() throws Exception {
        try {
            if (collectorConnection != null) {
                collectorConnection.close();
            }
        } finally {
            docker("rm", "-f", "-v", containerName);
        }
    }

    @Test
    void asyncReporterDeliversProto3BatchesIntoTheCollectorQueueOfTheVirtualHost() throws Exception {
        String queue = declareCollectorQueue("zipkin-payments");
        RabbitMQSender sender = RabbitMQSender.newBuilder()
                .addresses("127.0.0.1:" + amqpPort)
                .username(USER)
                .password(PASSWORD)
                .virtualHost(VHOST)
                .queue(queue)
                .encoding(Encoding.PROTO3)
                .build();
        InMemoryReporterMetrics metrics = new InMemoryReporterMetrics();
        AsyncReporter<Span> reporter = AsyncReporter.builder(sender)
                .metrics(metrics)
                .messageTimeout(0, TimeUnit.MILLISECONDS)
                .build(SpanBytesEncoder.PROTO3);
        try {
            Span authorize = span("0000000000000a01", null, "authorize-card", Span.Kind.SERVER);
            Span ledger = span("0000000000000a02", "0000000000000a01", "post-ledger-entry", Span.Kind.CLIENT);
            Span fraud = span("0000000000000a03", "0000000000000a01", "score-fraud", Span.Kind.CLIENT);
            reporter.report(authorize);
            reporter.report(ledger);
            reporter.report(fraud);
            reporter.flush();

            byte[] body = nextMessage(queue);
            assertEquals(List.of(authorize, ledger, fraud), SpanBytesDecoder.PROTO3.decodeList(body));
            assertEquals(1, metrics.messages());
            assertEquals(3, metrics.spans());
            assertEquals(body.length, metrics.messageBytes());
            assertEquals(0, metrics.messagesDropped());

            List<byte[]> encoded = List.of(
                    SpanBytesEncoder.PROTO3.encode(authorize),
                    SpanBytesEncoder.PROTO3.encode(ledger),
                    SpanBytesEncoder.PROTO3.encode(fraud));
            assertEquals(body.length, sender.messageSizeInBytes(encoded));
            assertEquals("RabbitMQSender{addresses=[127.0.0.1:" + amqpPort + "], queue=" + queue + "}", sender.toString());
        } finally {
            reporter.close();
            sender.close();
        }
    }

    @Test
    void servicesShareAnApplicationConnectionFactoryAndRouteToTheirOwnQueues() throws Exception {
        String refundsQueue = declareCollectorQueue("zipkin-refunds");
        String settlementQueue = declareCollectorQueue("zipkin-settlement");
        ConnectionFactory factory = new ConnectionFactory();
        factory.setUsername(USER);
        factory.setPassword(PASSWORD);
        factory.setVirtualHost(VHOST);
        RabbitMQSender refunds = RabbitMQSender.newBuilder()
                .connectionFactory(factory)
                .addresses(List.of(new Address("127.0.0.1", amqpPort)))
                .queue(refundsQueue)
                .build();
        // The settlement batch job copies the refunds configuration and only changes the queue.
        RabbitMQSender settlement = refunds.toBuilder().queue(settlementQueue).build();
        try {
            assertEquals(Encoding.JSON, refunds.encoding());
            assertEquals(Encoding.JSON, settlement.encoding());
            assertEquals(refunds.messageMaxBytes(), settlement.messageMaxBytes());

            Span refund = span("0000000000000b01", null, "issue-refund", Span.Kind.SERVER);
            Span settle = span("0000000000000b02", null, "settle-batch", Span.Kind.PRODUCER);
            List<byte[]> refundSpans = List.of(SpanBytesEncoder.JSON_V2.encode(refund));
            List<byte[]> settleSpans = List.of(SpanBytesEncoder.JSON_V2.encode(settle));
            refunds.send(refundSpans);
            settlement.send(settleSpans);

            byte[] refundBody = nextMessage(refundsQueue);
            byte[] settleBody = nextMessage(settlementQueue);
            // The broker carries the framed JSON list unchanged.
            assertArrayEquals(Encoding.JSON.encode(refundSpans), refundBody);
            assertArrayEquals(Encoding.JSON.encode(settleSpans), settleBody);
            assertEquals(List.of(refund), SpanBytesDecoder.JSON_V2.decodeList(refundBody));
            assertEquals(List.of(settle), SpanBytesDecoder.JSON_V2.decodeList(settleBody));
            assertEquals(0, collector.messageCount(refundsQueue));
            assertEquals(0, collector.messageCount(settlementQueue));
        } finally {
            refunds.close();
            settlement.close();
        }
    }

    @Test
    void rejectedCredentialsDropMessagesAndSurfaceToDirectCallers() throws Exception {
        String queue = declareCollectorQueue("zipkin-chargebacks");
        RabbitMQSender misconfigured = RabbitMQSender.newBuilder()
                .addresses("127.0.0.1:" + amqpPort)
                .username(USER)
                .password("rotated-yesterday")
                .virtualHost(VHOST)
                .queue(queue)
                .connectionTimeout(5000)
                .build();
        InMemoryReporterMetrics metrics = new InMemoryReporterMetrics();
        AsyncReporter<Span> reporter = AsyncReporter.builder(misconfigured)
                .metrics(metrics)
                .messageTimeout(0, TimeUnit.MILLISECONDS)
                .build();
        try {
            reporter.report(span("0000000000000c01", null, "open-chargeback", Span.Kind.SERVER));
            reporter.report(span("0000000000000c02", "0000000000000c01", "notify-merchant", Span.Kind.PRODUCER));
            reporter.flush();

            assertEquals(1, metrics.messagesDropped());
            assertEquals(2, metrics.spansDropped());
            assertEquals(Map.of(RuntimeException.class, 1L), metrics.messagesDroppedByCause());

            List<byte[]> spans = List.of(SpanBytesEncoder.JSON_V2.encode(
                    span("0000000000000c03", null, "close-chargeback", Span.Kind.SERVER)));
            RuntimeException failure = assertThrows(RuntimeException.class, () -> misconfigured.send(spans));
            assertEquals("Unable to establish connection to RabbitMQ server", failure.getMessage());
            assertEquals(0, collector.messageCount(queue));
        } finally {
            reporter.close();
            misconfigured.close();
        }
    }

    @Test
    void closedSenderRefusesFurtherBatchesAfterDeliveringEarlierOnes() throws Exception {
        String queue = declareCollectorQueue("zipkin-payouts");
        RabbitMQSender sender = RabbitMQSender.newBuilder()
                .addresses("127.0.0.1:" + amqpPort)
                .username(USER)
                .password(PASSWORD)
                .virtualHost(VHOST)
                .queue(queue)
                .build();
        Span payout = span("0000000000000d01", null, "schedule-payout", Span.Kind.SERVER);
        List<byte[]> spans = List.of(SpanBytesEncoder.JSON_V2.encode(payout));
        try {
            sender.send(spans);
            assertEquals(List.of(payout), SpanBytesDecoder.JSON_V2.decodeList(nextMessage(queue)));
        } finally {
            sender.close();
        }
        // Closing twice during an orderly shutdown is harmless.
        sender.close();

        assertThrows(ClosedSenderException.class, () -> sender.send(spans));
        assertEquals(0, collector.messageCount(queue));
    }

    private static String declareCollectorQueue(String name) throws IOException {
        collector.queueDeclare(name, true, false, false, null);
        collector.queuePurge(name);
        return name;
    }

    private static byte[] nextMessage(String queue) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (System.nanoTime() < deadline) {
            GetResponse response = collector.basicGet(queue, true);
            if (response != null) {
                return response.getBody();
            }
            Thread.sleep(50);
        }
        throw new AssertionError("No message arrived on " + queue);
    }

    private static Span span(String id, String parentId, String name, Span.Kind kind) {
        Span.Builder builder = Span.newBuilder()
                .traceId(TRACE_ID)
                .id(id)
                .name(name)
                .kind(kind)
                .localEndpoint(PAYMENTS)
                .timestamp(1_760_000_000_000_000L + Long.parseLong(id, 16))
                .duration(1_250L)
                .putTag("payment.region", "eu-west");
        if (parentId != null) {
            builder.parentId(parentId);
        }
        return builder.build();
    }

    private static Connection connectWhenReady() throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("127.0.0.1");
        factory.setPort(amqpPort);
        factory.setUsername(USER);
        factory.setPassword(PASSWORD);
        factory.setVirtualHost(VHOST);
        long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(2);
        List<Exception> failures = new ArrayList<>();
        while (System.nanoTime() < deadline) {
            try {
                return factory.newConnection("zipkin-collector");
            } catch (Exception e) {
                failures.add(e);
                Thread.sleep(500);
            }
        }
        AssertionError error = new AssertionError("RabbitMQ did not accept connections in time");
        failures.stream().reduce((first, last) -> last).ifPresent(error::initCause);
        throw error;
    }

    private static void docker(String... args) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("docker");
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).inheritIO().start();
        assertTrue(process.waitFor(5, TimeUnit.MINUTES), "docker " + args[0] + " timed out");
        assertEquals(0, process.exitValue(), "docker " + args[0] + " failed");
    }
}
