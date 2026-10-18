/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_netty;

import com.google.protobuf.StringValue;
import io.grpc.Attributes;
import io.grpc.CallOptions;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerServiceDefinition;
import io.grpc.ServerTransportFilter;
import io.grpc.Status;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.ServerCalls;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises HTTP/2 keepalive on grpc-netty transports, where `NettyServerBuilder` and
 * `NettyChannelBuilder` `keepAliveTime` and `keepAliveTimeout` send PING frames on a quiet
 * connection and give up on it when the peer stops acknowledging them.
 *
 * A market-data feed holds long-lived quote subscriptions that can sit silent between trades. Each
 * connection runs through an HTTP/2 frame-aware TCP relay that counts PING and GOAWAY frames and,
 * like a broken NAT path, can swallow one direction of one connection while keeping its sockets
 * open. An answered server ping keeps a silent subscription usable; an unanswered one makes the
 * server cancel the subscription and send GOAWAY, after which the client resubscribes over a fresh
 * connection; a client whose own ping goes unanswered fails its subscription `UNAVAILABLE`.
 */
class MarketFeedKeepAliveTest {

    private static final String SERVICE = "market.Feed";

    private static final MethodDescriptor<StringValue, StringValue> SUBSCRIBE =
            MethodDescriptor.<StringValue, StringValue>newBuilder()
                    .setType(MethodDescriptor.MethodType.SERVER_STREAMING)
                    .setFullMethodName(MethodDescriptor.generateFullMethodName(SERVICE, "Subscribe"))
                    .setRequestMarshaller(ProtoUtils.marshaller(StringValue.getDefaultInstance()))
                    .setResponseMarshaller(ProtoUtils.marshaller(StringValue.getDefaultInstance()))
                    .build();

    /** A fixed window turns off BDP auto-tuning, so every PING on the wire is a keepalive. */
    private static final int FLOW_CONTROL_WINDOW = 1 << 20;

    /** The shortest keepalive interval grpc-netty accepts on either side; shorter values are raised to it. */
    private static final long KEEPALIVE_SECONDS = 10;

    private static final long WAIT_SECONDS = 30;

    @Test
    void answeredServerPingKeepsASilentSubscriptionUsable() throws Exception {
        FeedService feed = new FeedService();
        ConnectionWatch watch = new ConnectionWatch();
        Server server = pingingServer(feed, watch, 5);
        FrameRelay relay = new FrameRelay(server.getPort());
        ManagedChannel channel = channel(relay).build();
        try {
            QuoteListener listener = subscribe(channel, "ACME");
            assertThat(listener.quotes.poll(WAIT_SECONDS, TimeUnit.SECONDS)).isEqualTo("ACME open 101.25");
            Subscription subscription = feed.awaitSubscription();

            relay.awaitFrames(Frame.SERVER_PING, 1);
            relay.awaitFrames(Frame.CLIENT_PING_ACK, 1);
            assertThat(relay.count(Frame.CLIENT_PING)).isZero();
            assertThat(relay.count(Frame.SERVER_GOAWAY)).isZero();
            assertThat(subscription.cancelled.getCount()).isEqualTo(1);
            assertThat(watch.terminated.getCount()).isEqualTo(1);

            subscription.quotes.onNext(text("ACME trade 101.40"));
            subscription.quotes.onCompleted();
            assertThat(listener.quotes.poll(WAIT_SECONDS, TimeUnit.SECONDS)).isEqualTo("ACME trade 101.40");
            assertThat(listener.done.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
            assertThat(listener.failure.get()).isNull();
            assertThat(watch.ready.get()).isEqualTo(1);
        } finally {
            close(channel, server, relay);
        }
    }

    @Test
    void serverGivesUpOnAClientWhosePingAcknowledgementIsLost() throws Exception {
        FeedService feed = new FeedService();
        ConnectionWatch watch = new ConnectionWatch();
        Server server = pingingServer(feed, watch, 1);
        FrameRelay relay = new FrameRelay(server.getPort());
        ManagedChannel channel = channel(relay).build();
        try {
            QuoteListener stale = subscribe(channel, "GLOBEX");
            assertThat(stale.quotes.poll(WAIT_SECONDS, TimeUnit.SECONDS)).isEqualTo("GLOBEX open 101.25");
            Subscription lost = feed.awaitSubscription();
            relay.link(0).dropClientFrames = true;

            assertThat(lost.cancelled.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
            relay.awaitFrames(Frame.SERVER_GOAWAY, 1);
            assertThat(relay.count(Frame.SERVER_PING)).isEqualTo(1);
            assertThat(relay.count(Frame.CLIENT_PING_ACK)).isEqualTo(1);

            QuoteListener resubscribed = subscribe(channel, "GLOBEX");
            assertThat(resubscribed.quotes.poll(WAIT_SECONDS, TimeUnit.SECONDS)).isEqualTo("GLOBEX open 101.25");
            Subscription fresh = feed.awaitSubscription();
            assertThat(relay.linkCount()).isEqualTo(2);
            assertThat(watch.ready.get()).isEqualTo(2);

            fresh.quotes.onNext(text("GLOBEX trade 99.80"));
            fresh.quotes.onCompleted();
            assertThat(resubscribed.quotes.poll(WAIT_SECONDS, TimeUnit.SECONDS)).isEqualTo("GLOBEX trade 99.80");
            assertThat(resubscribed.done.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
            assertThat(resubscribed.failure.get()).isNull();
            assertThat(stale.quotes).isEmpty();
        } finally {
            close(channel, server, relay);
        }
    }

    @Test
    void clientFailsItsSubscriptionWhenServerStopsAnsweringPings() throws Exception {
        FeedService feed = new FeedService();
        ConnectionWatch watch = new ConnectionWatch();
        Server server = NettyServerBuilder.forAddress(new InetSocketAddress("127.0.0.1", 0))
                .flowControlWindow(FLOW_CONTROL_WINDOW)
                .addTransportFilter(watch)
                .addService(feed.bindService())
                .build()
                .start();
        FrameRelay relay = new FrameRelay(server.getPort());
        ManagedChannel channel = channel(relay)
                .keepAliveTime(KEEPALIVE_SECONDS, TimeUnit.SECONDS)
                .keepAliveTimeout(1, TimeUnit.SECONDS)
                .build();
        try {
            QuoteListener listener = subscribe(channel, "INITECH");
            assertThat(listener.quotes.poll(WAIT_SECONDS, TimeUnit.SECONDS)).isEqualTo("INITECH open 101.25");
            Subscription subscription = feed.awaitSubscription();
            relay.link(0).dropServerFrames = true;

            assertThat(listener.done.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
            Status status = Status.fromThrowable(listener.failure.get());
            assertThat(status.getCode()).isEqualTo(Status.Code.UNAVAILABLE);
            assertThat(status.getDescription()).contains("Keepalive failed");
            assertThat(relay.count(Frame.CLIENT_PING)).isEqualTo(1);
            assertThat(relay.count(Frame.SERVER_PING_ACK)).isEqualTo(1);
            assertThat(relay.count(Frame.SERVER_PING)).isZero();

            assertThat(subscription.cancelled.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
            assertThat(watch.terminated.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
        } finally {
            close(channel, server, relay);
        }
    }

    /** Starts a feed server that pings quiet clients and waits `timeoutSeconds` for each acknowledgement. */
    private static Server pingingServer(FeedService feed, ConnectionWatch watch, long timeoutSeconds)
            throws IOException {
        return NettyServerBuilder.forAddress(new InetSocketAddress("127.0.0.1", 0))
                .keepAliveTime(KEEPALIVE_SECONDS, TimeUnit.SECONDS)
                .keepAliveTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .flowControlWindow(FLOW_CONTROL_WINDOW)
                .addTransportFilter(watch)
                .addService(feed.bindService())
                .build()
                .start();
    }

    private static NettyChannelBuilder channel(FrameRelay relay) {
        return NettyChannelBuilder.forAddress("127.0.0.1", relay.port())
                .usePlaintext()
                .flowControlWindow(FLOW_CONTROL_WINDOW);
    }

    private static QuoteListener subscribe(ManagedChannel channel, String symbol) {
        QuoteListener listener = new QuoteListener();
        ClientCalls.asyncServerStreamingCall(
                channel.newCall(SUBSCRIBE, CallOptions.DEFAULT.withDeadlineAfter(2, TimeUnit.MINUTES)),
                text(symbol), listener);
        return listener;
    }

    private static void close(ManagedChannel channel, Server server, FrameRelay relay) throws Exception {
        channel.shutdownNow();
        server.shutdownNow();
        relay.close();
        assertThat(channel.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
        assertThat(server.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
    }

    private static StringValue text(String value) {
        return StringValue.newBuilder().setValue(value).build();
    }

    /** Streams an opening quote and hands the open subscription to the test, which publishes later trades. */
    private static final class FeedService {

        private final BlockingQueue<Subscription> subscriptions = new LinkedBlockingQueue<>();

        ServerServiceDefinition bindService() {
            return ServerServiceDefinition.builder(SERVICE)
                    .addMethod(SUBSCRIBE, ServerCalls.asyncServerStreamingCall(this::subscribe))
                    .build();
        }

        private void subscribe(StringValue symbol, StreamObserver<StringValue> quotes) {
            ServerCallStreamObserver<StringValue> serverQuotes = (ServerCallStreamObserver<StringValue>) quotes;
            Subscription subscription = new Subscription(serverQuotes);
            serverQuotes.setOnCancelHandler(subscription.cancelled::countDown);
            serverQuotes.onNext(text(symbol.getValue() + " open 101.25"));
            subscriptions.add(subscription);
        }

        Subscription awaitSubscription() throws InterruptedException {
            Subscription subscription = subscriptions.poll(WAIT_SECONDS, TimeUnit.SECONDS);
            assertThat(subscription).as("subscription reaching the feed").isNotNull();
            return subscription;
        }
    }

    /** One open quote stream on the server, with a latch released when the call is cancelled. */
    private static final class Subscription {

        private final ServerCallStreamObserver<StringValue> quotes;

        private final CountDownLatch cancelled = new CountDownLatch(1);

        Subscription(ServerCallStreamObserver<StringValue> quotes) {
            this.quotes = quotes;
        }
    }

    /** Collects the quotes a subscriber receives and how its subscription ended. */
    private static final class QuoteListener implements StreamObserver<StringValue> {

        private final BlockingQueue<String> quotes = new LinkedBlockingQueue<>();

        private final AtomicReference<Throwable> failure = new AtomicReference<>();

        private final CountDownLatch done = new CountDownLatch(1);

        @Override
        public void onNext(StringValue quote) {
            quotes.add(quote.getValue());
        }

        @Override
        public void onError(Throwable t) {
            failure.set(t);
            done.countDown();
        }

        @Override
        public void onCompleted() {
            done.countDown();
        }
    }

    /** Counts the connections the server accepts and signals when the first one closes. */
    private static final class ConnectionWatch extends ServerTransportFilter {

        private final AtomicInteger ready = new AtomicInteger();

        private final CountDownLatch terminated = new CountDownLatch(1);

        @Override
        public Attributes transportReady(Attributes transportAttrs) {
            ready.incrementAndGet();
            return transportAttrs;
        }

        @Override
        public void transportTerminated(Attributes transportAttrs) {
            terminated.countDown();
        }
    }

    /** Connection-level frames the relay saw, by sender; a PING ack answers the peer's PING. */
    private enum Frame {
        CLIENT_PING, CLIENT_PING_ACK, SERVER_PING, SERVER_PING_ACK, SERVER_GOAWAY
    }

    /**
     * Relays prior-knowledge HTTP/2 connections frame by frame to the feed server, counting frames
     * across all connections and dropping one direction of a connection on request.
     */
    private static final class FrameRelay implements AutoCloseable {

        private static final int CLIENT_PREFACE_LENGTH = 24;

        private static final int FRAME_HEADER_LENGTH = 9;

        private static final byte PING_TYPE = 0x6;

        private static final byte GOAWAY_TYPE = 0x7;

        private static final byte ACK_FLAG = 0x1;

        private final int backendPort;

        private final ServerSocket listener;

        private final List<Link> links = new CopyOnWriteArrayList<>();

        private final int[] frames = new int[Frame.values().length];

        FrameRelay(int backendPort) throws IOException {
            this.backendPort = backendPort;
            this.listener = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
            Thread acceptor = new Thread(this::acceptConnections, "market-relay-accept");
            acceptor.setDaemon(true);
            acceptor.start();
        }

        int port() {
            return listener.getLocalPort();
        }

        int linkCount() {
            return links.size();
        }

        Link link(int index) {
            assertThat(links).as("relayed connections").hasSizeGreaterThan(index);
            return links.get(index);
        }

        synchronized int count(Frame kind) {
            return frames[kind.ordinal()];
        }

        /** Waits, without sleeping, until the relay has seen at least `atLeast` frames of `kind`. */
        synchronized void awaitFrames(Frame kind, int atLeast) throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
            while (frames[kind.ordinal()] < atLeast) {
                long remaining = deadline - System.nanoTime();
                assertThat(remaining).as("relay seeing %d %s frames, saw %d", atLeast, kind, frames[kind.ordinal()])
                        .isPositive();
                TimeUnit.NANOSECONDS.timedWait(this, remaining);
            }
        }

        private synchronized void record(Frame kind) {
            frames[kind.ordinal()]++;
            notifyAll();
        }

        private void acceptConnections() {
            try {
                while (true) {
                    Socket client = listener.accept();
                    Socket backend = new Socket(InetAddress.getLoopbackAddress(), backendPort);
                    Link link = new Link(client, backend);
                    links.add(link);
                    link.start();
                }
            } catch (IOException closed) {
                // The listener was closed by close().
            }
        }

        @Override
        public void close() {
            closeQuietly(listener);
            for (Link link : links) {
                closeQuietly(link.client);
                closeQuietly(link.backend);
            }
        }

        private static void closeQuietly(Closeable closeable) {
            try {
                closeable.close();
            } catch (IOException ignored) {
                // Already closed by the other direction.
            }
        }

        /** One relayed connection, whose directions can be cut independently. */
        private final class Link {

            private final Socket client;

            private final Socket backend;

            private volatile boolean dropClientFrames;

            private volatile boolean dropServerFrames;

            Link(Socket client, Socket backend) {
                this.client = client;
                this.backend = backend;
            }

            void start() {
                startPump(true, "market-relay-upstream");
                startPump(false, "market-relay-downstream");
            }

            private void startPump(boolean fromClient, String name) {
                Thread pump = new Thread(() -> pump(fromClient), name);
                pump.setDaemon(true);
                pump.start();
            }

            /** Copies whole frames in one direction until either side closes, then closes both. */
            private void pump(boolean fromClient) {
                Socket from = fromClient ? client : backend;
                Socket to = fromClient ? backend : client;
                try {
                    DataInputStream in = new DataInputStream(from.getInputStream());
                    OutputStream out = to.getOutputStream();
                    if (fromClient) {
                        byte[] preface = new byte[CLIENT_PREFACE_LENGTH];
                        in.readFully(preface);
                        out.write(preface);
                        out.flush();
                    }
                    byte[] header = new byte[FRAME_HEADER_LENGTH];
                    while (true) {
                        in.readFully(header);
                        int length = (header[0] & 0xff) << 16 | (header[1] & 0xff) << 8 | header[2] & 0xff;
                        byte[] frame = Arrays.copyOf(header, FRAME_HEADER_LENGTH + length);
                        in.readFully(frame, FRAME_HEADER_LENGTH, length);
                        recordConnectionFrame(fromClient, header[3], (header[4] & ACK_FLAG) != 0);
                        if (!(fromClient ? dropClientFrames : dropServerFrames)) {
                            out.write(frame);
                            out.flush();
                        }
                    }
                } catch (IOException closed) {
                    // One side closed the connection; the finally block tears down the other.
                } finally {
                    closeQuietly(from);
                    closeQuietly(to);
                }
            }

            private void recordConnectionFrame(boolean fromClient, byte type, boolean ack) {
                if (type == PING_TYPE) {
                    if (fromClient) {
                        record(ack ? Frame.CLIENT_PING_ACK : Frame.CLIENT_PING);
                    } else {
                        record(ack ? Frame.SERVER_PING_ACK : Frame.SERVER_PING);
                    }
                } else if (type == GOAWAY_TYPE && !fromClient) {
                    record(Frame.SERVER_GOAWAY);
                }
            }
        }
    }
}
