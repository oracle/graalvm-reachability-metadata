/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_zipkin_reporter2.zipkin_reporter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import zipkin2.Endpoint;
import zipkin2.Span;
import zipkin2.codec.SpanBytesDecoder;
import zipkin2.reporter.AsyncReporter;
import zipkin2.reporter.BytesMessageSender;
import zipkin2.reporter.ClosedSenderException;
import zipkin2.reporter.Encoding;
import zipkin2.reporter.InMemoryReporterMetrics;
import zipkin2.reporter.SpanBytesEncoder;

/**
 * An application-defined non-HTTP span transport built on `BytesMessageSender.Base`: messages
 * framed by `Encoding` and written over a persistent TCP connection, `messageSizeInBytes`
 * accounting inherited from the base class bounding the batches `AsyncReporter` drains, the
 * legacy JSON v1 format next to Thrift and Proto3, an unreachable agent counted as dropped
 * messages by cause, and the sender refusing work once closed.
 *
 * A ledger service does not talk to a Zipkin collector directly: it streams its spans to a
 * node-local tracing agent that forwards them on. The service writes a small `TcpAgentSender`
 * that keeps one socket open to the agent and prefixes every message with its length. A
 * `ServerSocket` plays the agent and records every frame, so each test observes the bytes the
 * agent would forward to Zipkin.
 */
public class TcpAgentSenderTransportTest {

    private static final String TRACE_ID = "4f3e2d1c0b0a09080706050403020100";

    private static final Endpoint LEDGER = Endpoint.newBuilder()
            .serviceName("ledger")
            .ip("10.1.4.7")
            .port(9411)
            .build();

    private Agent agent;

    @BeforeEach
    void startAgent() throws IOException {
        agent = Agent.start();
    }

    @AfterEach
    void stopAgent() throws IOException {
        agent.stop();
    }

    @Test
    void spansStreamToTheAgentInEveryFormatTheSenderIsBuiltFor() throws Exception {
        List<Span> posting = List.of(
                span("0000000000000001", "post-entry"),
                span("0000000000000002", "close-batch"));

        // An older agent still speaks the v1 JSON model, which travels in a JSON list.
        try (TcpAgentSender sender = new TcpAgentSender(Encoding.JSON, agent.address(), 64 * 1024)) {
            AsyncReporter<Span> reporter = AsyncReporter.builder(sender)
                    .messageTimeout(0, TimeUnit.MILLISECONDS)
                    .build(SpanBytesEncoder.JSON_V1);
            posting.forEach(reporter::report);
            reporter.flush();
            reporter.close();
        }
        byte[] v1 = agent.nextFrame();
        assertEquals((byte) '[', v1[0]);
        assertEquals(posting, SpanBytesDecoder.JSON_V1.decodeList(v1));

        try (TcpAgentSender sender = new TcpAgentSender(Encoding.PROTO3, agent.address(), 64 * 1024)) {
            AsyncReporter<Span> reporter = AsyncReporter.builder(sender)
                    .messageTimeout(0, TimeUnit.MILLISECONDS)
                    .build(SpanBytesEncoder.PROTO3);
            posting.forEach(reporter::report);
            reporter.flush();
            reporter.close();
        }
        assertEquals(posting, SpanBytesDecoder.PROTO3.decodeList(agent.nextFrame()));

        try (TcpAgentSender sender = new TcpAgentSender(Encoding.THRIFT, agent.address(), 64 * 1024)) {
            AsyncReporter<Span> reporter = AsyncReporter.builder(sender)
                    .messageTimeout(0, TimeUnit.MILLISECONDS)
                    .build(SpanBytesEncoder.THRIFT);
            posting.forEach(reporter::report);
            reporter.flush();
            reporter.close();
        }
        List<Span> thrift = SpanBytesDecoder.THRIFT.decodeList(agent.nextFrame());
        assertEquals(posting.size(), thrift.size());
        for (int i = 0; i < posting.size(); i++) {
            assertEquals(posting.get(i).id(), thrift.get(i).id());
            assertEquals(posting.get(i).name(), thrift.get(i).name());
            assertEquals(posting.get(i).duration(), thrift.get(i).duration());
        }

        // The application picks the span encoder matching whatever its sender was configured with.
        assertSame(SpanBytesEncoder.JSON_V2, SpanBytesEncoder.forEncoding(Encoding.JSON));
        assertSame(SpanBytesEncoder.PROTO3, SpanBytesEncoder.forEncoding(Encoding.PROTO3));
        assertNull(agent.pollFrame());
    }

    @Test
    void oneConnectionCarriesEveryMessageAndFramesMatchTheSizeAccounting() throws Exception {
        List<byte[]> pair = List.of(
                SpanBytesEncoder.JSON_V2.encode(span("00000000000000a1", "debit-account")),
                SpanBytesEncoder.JSON_V2.encode(span("00000000000000a2", "credit-account")));

        try (TcpAgentSender sender = new TcpAgentSender(Encoding.JSON, agent.address(), 64 * 1024)) {
            assertEquals(Encoding.JSON, sender.encoding());
            assertEquals(Encoding.JSON.listSizeInBytes(pair), sender.messageSizeInBytes(pair));
            assertEquals(
                    Encoding.JSON.listSizeInBytes(pair.get(0).length),
                    sender.messageSizeInBytes(pair.get(0).length));

            sender.send(pair);
            sender.send(List.of(pair.get(1)));

            byte[] first = agent.nextFrame();
            assertEquals(sender.messageSizeInBytes(pair), first.length);
            assertEquals(List.of(
                    span("00000000000000a1", "debit-account"),
                    span("00000000000000a2", "credit-account")),
                    SpanBytesDecoder.JSON_V2.decodeList(first));
            byte[] second = agent.nextFrame();
            assertEquals(sender.messageSizeInBytes(List.of(pair.get(1))), second.length);
        }
        assertEquals(1, agent.connectionsAccepted());
    }

    @Test
    void reporterDrainsBatchesNoLargerThanTheSenderAccepts() throws Exception {
        List<Span> entries = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            entries.add(span("000000000000000" + i, "post-entry"));
        }
        List<byte[]> twoEntries = List.of(
                SpanBytesEncoder.JSON_V2.encode(entries.get(0)),
                SpanBytesEncoder.JSON_V2.encode(entries.get(1)));
        InMemoryReporterMetrics metrics = new InMemoryReporterMetrics();

        // The agent's read buffer holds exactly two ledger entries, so that is the message budget.
        int budget = Encoding.JSON.listSizeInBytes(twoEntries);
        try (TcpAgentSender sender = new TcpAgentSender(Encoding.JSON, agent.address(), budget)) {
            AsyncReporter<Span> reporter = AsyncReporter.builder(sender)
                    .messageTimeout(0, TimeUnit.MILLISECONDS)
                    .metrics(metrics)
                    .build(SpanBytesEncoder.JSON_V2);
            entries.forEach(reporter::report);

            List<Span> received = new ArrayList<>();
            int[] expectedBatchSizes = {2, 2, 1};
            for (int expected : expectedBatchSizes) {
                reporter.flush();
                byte[] frame = agent.nextFrame();
                assertTrue(frame.length <= budget);
                List<Span> batch = SpanBytesDecoder.JSON_V2.decodeList(frame);
                assertEquals(expected, batch.size());
                received.addAll(batch);
            }
            reporter.close();

            assertEquals(entries, received);
            assertEquals(3, metrics.messages());
            assertEquals(5, metrics.spans());
            assertEquals(0, metrics.messagesDropped());
        }
        assertEquals(1, agent.connectionsAccepted());
    }

    @Test
    void anUnreachableAgentDropsTheMessageAndRecordsWhy() throws Exception {
        InetSocketAddress downAgent = agent.address();
        agent.stop();
        InMemoryReporterMetrics metrics = new InMemoryReporterMetrics();

        try (TcpAgentSender sender = new TcpAgentSender(Encoding.PROTO3, downAgent, 64 * 1024)) {
            AsyncReporter<Span> reporter = AsyncReporter.builder(sender)
                    .messageTimeout(0, TimeUnit.MILLISECONDS)
                    .metrics(metrics)
                    .build(SpanBytesEncoder.PROTO3);
            reporter.report(span("00000000000000b1", "post-entry"));
            reporter.report(span("00000000000000b2", "post-entry"));
            reporter.flush();
            reporter.close();

            // The message is counted as attempted, then as dropped with all of its spans.
            assertEquals(1, metrics.messages());
            assertEquals(1, metrics.messagesDropped());
            assertEquals(2, metrics.spansDropped());
            Map<Class<? extends Throwable>, Long> causes = metrics.messagesDroppedByCause();
            assertEquals(1L, causes.get(ConnectException.class));

            // An operator reading the job log sees the same failure from a direct send.
            List<byte[]> direct = List.of(SpanBytesEncoder.PROTO3.encode(span("00000000000000b3", "close-batch")));
            assertThrows(ConnectException.class, () -> sender.send(direct));
        }
    }

    @Test
    void aClosedSenderRefusesFurtherMessages() throws Exception {
        TcpAgentSender sender = new TcpAgentSender(Encoding.JSON, agent.address(), 64 * 1024);
        List<byte[]> entry = List.of(SpanBytesEncoder.JSON_V2.encode(span("00000000000000c1", "post-entry")));
        sender.send(entry);
        assertNotNull(agent.nextFrame());

        sender.close();
        sender.close();
        assertThrows(ClosedSenderException.class, () -> sender.send(entry));

        // Spans reported through a reporter over the closed sender never reach the agent.
        InMemoryReporterMetrics metrics = new InMemoryReporterMetrics();
        AsyncReporter<Span> reporter = AsyncReporter.builder(sender)
                .messageTimeout(0, TimeUnit.MILLISECONDS)
                .metrics(metrics)
                .build(SpanBytesEncoder.JSON_V2);
        reporter.report(span("00000000000000c2", "post-entry"));
        // A closed sender is not a transient failure: the reporter drops the message and rethrows.
        assertThrows(ClosedSenderException.class, reporter::flush);
        reporter.close();
        assertEquals(1, metrics.messagesDropped());
        assertEquals(1, metrics.spansDropped());
        assertEquals(1L, metrics.messagesDroppedByCause().get(ClosedSenderException.class));
        assertNull(agent.pollFrame());
    }

    private static Span span(String id, String name) {
        return Span.newBuilder()
                .traceId(TRACE_ID)
                .id(id)
                .name(name)
                .kind(Span.Kind.SERVER)
                .localEndpoint(LEDGER)
                .timestamp(1_700_000_300_000_000L)
                .duration(1_250L)
                .putTag("ledger.book", "eur-ops")
                .build();
    }

    /// The application's transport to the node-local agent: one lazily opened socket carrying
    /// messages framed by the configured `Encoding`, each preceded by its length.
    static final class TcpAgentSender extends BytesMessageSender.Base {

        private final InetSocketAddress agentAddress;
        private final int messageMaxBytes;
        private Socket socket;
        private DataOutputStream out;
        private boolean closed;

        TcpAgentSender(Encoding encoding, InetSocketAddress agentAddress, int messageMaxBytes) {
            super(encoding);
            this.agentAddress = agentAddress;
            this.messageMaxBytes = messageMaxBytes;
        }

        @Override
        public int messageMaxBytes() {
            return messageMaxBytes;
        }

        @Override
        public synchronized void send(List<byte[]> encodedSpans) throws IOException {
            if (closed) {
                throw new ClosedSenderException();
            }
            byte[] message = encoding.encode(encodedSpans);
            if (out == null) {
                Socket connection = new Socket();
                try {
                    connection.connect(agentAddress, 5_000);
                } catch (IOException e) {
                    connection.close();
                    throw e;
                }
                socket = connection;
                out = new DataOutputStream(new BufferedOutputStream(connection.getOutputStream()));
            }
            out.writeInt(message.length);
            out.write(message);
            out.flush();
        }

        @Override
        public synchronized void close() throws IOException {
            if (closed) {
                return;
            }
            closed = true;
            if (socket != null) {
                socket.close();
            }
        }
    }

    /// The node-local tracing agent: accepts connections and records every length-prefixed frame.
    static final class Agent {

        private final ServerSocket server;
        private final BlockingQueue<byte[]> frames = new LinkedBlockingQueue<>();
        private final List<Socket> connections = new ArrayList<>();
        private int accepted;

        private Agent(ServerSocket server) {
            this.server = server;
        }

        static Agent start() throws IOException {
            ServerSocket server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
            Agent agent = new Agent(server);
            Thread acceptor = new Thread(agent::acceptLoop, "tracing-agent");
            acceptor.setDaemon(true);
            acceptor.start();
            return agent;
        }

        InetSocketAddress address() {
            return new InetSocketAddress(server.getInetAddress(), server.getLocalPort());
        }

        byte[] nextFrame() throws InterruptedException {
            byte[] frame = frames.poll(10, TimeUnit.SECONDS);
            assertNotNull(frame, "the agent received no frame");
            return frame;
        }

        byte[] pollFrame() throws InterruptedException {
            return frames.poll(200, TimeUnit.MILLISECONDS);
        }

        synchronized int connectionsAccepted() {
            return accepted;
        }

        void stop() throws IOException {
            server.close();
            synchronized (this) {
                for (Socket connection : connections) {
                    connection.close();
                }
            }
        }

        private void acceptLoop() {
            while (!server.isClosed()) {
                Socket connection;
                try {
                    connection = server.accept();
                } catch (IOException e) {
                    return;
                }
                synchronized (this) {
                    accepted++;
                    connections.add(connection);
                }
                Thread reader = new Thread(() -> readFrames(connection), "tracing-agent-reader");
                reader.setDaemon(true);
                reader.start();
            }
        }

        private void readFrames(Socket connection) {
            try (DataInputStream in = new DataInputStream(connection.getInputStream())) {
                while (true) {
                    int length = in.readInt();
                    byte[] frame = new byte[length];
                    in.readFully(frame);
                    frames.add(frame);
                }
            } catch (EOFException e) {
                // The ledger service closed its connection.
            } catch (IOException e) {
                // The agent is shutting down.
            }
        }
    }
}
