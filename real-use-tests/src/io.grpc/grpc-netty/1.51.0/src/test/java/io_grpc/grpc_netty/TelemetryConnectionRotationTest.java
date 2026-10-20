/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_netty;

import com.google.protobuf.Int64Value;
import com.google.protobuf.StringValue;
import io.grpc.Attributes;
import io.grpc.CallOptions;
import io.grpc.ConnectivityState;
import io.grpc.Grpc;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerServiceDefinition;
import io.grpc.ServerTransportFilter;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.ServerCalls;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises grpc-netty server connection management, where `NettyServerBuilder`'s
 * `maxConnectionAge`, `maxConnectionAgeGrace` and `maxConnectionIdle` retire client connections
 * with HTTP/2 GOAWAY.
 *
 * A telemetry ingest service rotates aged connections without cutting off a sensor upload that is
 * still streaming: the client channel drops to `IDLE`, opens a fresh connection for its next call,
 * and the old connection only closes once the upload finishes. A second server drops connections
 * that sit idle. Connections are tracked by client port through a `ServerTransportFilter`.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TelemetryConnectionRotationTest {

    private static final String SERVICE = "telemetry.Ingest";

    private static final MethodDescriptor<Int64Value, Int64Value> UPLOAD =
            MethodDescriptor.<Int64Value, Int64Value>newBuilder()
                    .setType(MethodDescriptor.MethodType.BIDI_STREAMING)
                    .setFullMethodName(MethodDescriptor.generateFullMethodName(SERVICE, "Upload"))
                    .setRequestMarshaller(ProtoUtils.marshaller(Int64Value.getDefaultInstance()))
                    .setResponseMarshaller(ProtoUtils.marshaller(Int64Value.getDefaultInstance()))
                    .build();

    private static final MethodDescriptor<StringValue, StringValue> REGISTER =
            MethodDescriptor.<StringValue, StringValue>newBuilder()
                    .setType(MethodDescriptor.MethodType.UNARY)
                    .setFullMethodName(MethodDescriptor.generateFullMethodName(SERVICE, "Register"))
                    .setRequestMarshaller(ProtoUtils.marshaller(StringValue.getDefaultInstance()))
                    .setResponseMarshaller(ProtoUtils.marshaller(StringValue.getDefaultInstance()))
                    .build();

    private static final long WAIT_SECONDS = 15;

    private final ConnectionLog agingLog = new ConnectionLog();

    private final ConnectionLog idleLog = new ConnectionLog();

    private Server agingServer;

    private Server idleServer;

    @BeforeAll
    void startServers() throws Exception {
        agingServer = NettyServerBuilder.forAddress(new InetSocketAddress("127.0.0.1", 0))
                .maxConnectionAge(1, TimeUnit.SECONDS)
                .maxConnectionAgeGrace(60, TimeUnit.SECONDS)
                .addTransportFilter(agingLog)
                .addService(new IngestService().bindService())
                .build()
                .start();
        idleServer = NettyServerBuilder.forAddress(new InetSocketAddress("127.0.0.1", 0))
                .maxConnectionIdle(1, TimeUnit.SECONDS)
                .addTransportFilter(idleLog)
                .addService(new IngestService().bindService())
                .build()
                .start();
    }

    @AfterAll
    void stopServers() throws Exception {
        agingServer.shutdown();
        idleServer.shutdown();
        assertThat(agingServer.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
        assertThat(idleServer.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void agedConnectionDrainsTheRunningUploadWhileNewCallsUseAFreshConnection() throws Exception {
        ManagedChannel channel = plaintextChannel(agingServer);
        try {
            BlockingQueue<Long> acks = new LinkedBlockingQueue<>();
            AtomicReference<Throwable> failure = new AtomicReference<>();
            CountDownLatch uploadDone = new CountDownLatch(1);
            StreamObserver<Int64Value> samples = ClientCalls.asyncBidiStreamingCall(
                    channel.newCall(UPLOAD, CallOptions.DEFAULT.withDeadlineAfter(60, TimeUnit.SECONDS)),
                    new StreamObserver<Int64Value>() {
                        @Override
                        public void onNext(Int64Value total) {
                            acks.add(total.getValue());
                        }

                        @Override
                        public void onError(Throwable t) {
                            failure.set(t);
                            uploadDone.countDown();
                        }

                        @Override
                        public void onCompleted() {
                            uploadDone.countDown();
                        }
                    });

            samples.onNext(Int64Value.of(21));
            assertThat(acks.poll(WAIT_SECONDS, TimeUnit.SECONDS)).isEqualTo(21L);
            int uploadPort = agingLog.lastReadyPort();

            awaitState(channel, ConnectivityState.IDLE);
            assertThat(agingLog.terminatedPorts()).doesNotContain(uploadPort);

            StringValue registered = ClientCalls.blockingUnaryCall(channel, REGISTER,
                    CallOptions.DEFAULT.withDeadlineAfter(WAIT_SECONDS, TimeUnit.SECONDS), text("sensor-7"));
            assertThat(registered.getValue()).isEqualTo("registered sensor-7");
            int registerPort = agingLog.lastReadyPort();
            assertThat(registerPort).isNotEqualTo(uploadPort);

            samples.onNext(Int64Value.of(17));
            assertThat(acks.poll(WAIT_SECONDS, TimeUnit.SECONDS)).isEqualTo(38L);
            samples.onNext(Int64Value.of(4));
            assertThat(acks.poll(WAIT_SECONDS, TimeUnit.SECONDS)).isEqualTo(42L);
            samples.onCompleted();

            assertThat(uploadDone.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
            assertThat(failure.get()).isNull();
            agingLog.awaitTerminated(uploadPort);
            assertThat(agingLog.readyPorts()).contains(uploadPort, registerPort);
        } finally {
            closeChannel(channel);
        }
    }

    @Test
    void idleConnectionIsClosedAndTheChannelReconnectsOnTheNextCall() throws Exception {
        ManagedChannel channel = plaintextChannel(idleServer);
        try {
            StringValue first = ClientCalls.blockingUnaryCall(channel, REGISTER,
                    CallOptions.DEFAULT.withDeadlineAfter(WAIT_SECONDS, TimeUnit.SECONDS), text("sensor-1"));
            assertThat(first.getValue()).isEqualTo("registered sensor-1");
            int firstPort = idleLog.lastReadyPort();
            assertThat(channel.getState(false)).isEqualTo(ConnectivityState.READY);

            idleLog.awaitTerminated(firstPort);
            awaitState(channel, ConnectivityState.IDLE);

            StringValue second = ClientCalls.blockingUnaryCall(channel, REGISTER,
                    CallOptions.DEFAULT.withDeadlineAfter(WAIT_SECONDS, TimeUnit.SECONDS), text("sensor-2"));
            assertThat(second.getValue()).isEqualTo("registered sensor-2");
            int secondPort = idleLog.lastReadyPort();
            assertThat(secondPort).isNotEqualTo(firstPort);
            assertThat(channel.getState(false)).isEqualTo(ConnectivityState.READY);
        } finally {
            closeChannel(channel);
        }
    }

    private static ManagedChannel plaintextChannel(Server server) {
        return NettyChannelBuilder.forAddress("127.0.0.1", server.getPort())
                .usePlaintext()
                .build();
    }

    private static void closeChannel(ManagedChannel channel) throws InterruptedException {
        channel.shutdownNow();
        assertThat(channel.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
    }

    /** Waits, without sleeping, until the channel reports `target` as its connectivity state. */
    private static void awaitState(ManagedChannel channel, ConnectivityState target) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
        ConnectivityState state = channel.getState(false);
        while (state != target) {
            CountDownLatch changed = new CountDownLatch(1);
            channel.notifyWhenStateChanged(state, changed::countDown);
            long remaining = deadline - System.nanoTime();
            assertThat(remaining > 0 && changed.await(remaining, TimeUnit.NANOSECONDS))
                    .as("channel reaching %s, last seen %s", target, state)
                    .isTrue();
            state = channel.getState(false);
        }
    }

    private static StringValue text(String value) {
        return StringValue.newBuilder().setValue(value).build();
    }

    /** Sums sensor samples over a bidirectional stream and registers sensors with a unary call. */
    private static final class IngestService {

        ServerServiceDefinition bindService() {
            return ServerServiceDefinition.builder(SERVICE)
                    .addMethod(UPLOAD, ServerCalls.asyncBidiStreamingCall(this::upload))
                    .addMethod(REGISTER, ServerCalls.asyncUnaryCall(this::register))
                    .build();
        }

        private void register(StringValue sensor, StreamObserver<StringValue> response) {
            response.onNext(text("registered " + sensor.getValue()));
            response.onCompleted();
        }

        private StreamObserver<Int64Value> upload(StreamObserver<Int64Value> totals) {
            return new StreamObserver<Int64Value>() {
                private long total;

                @Override
                public void onNext(Int64Value sample) {
                    total += sample.getValue();
                    totals.onNext(Int64Value.of(total));
                }

                @Override
                public void onError(Throwable t) {
                    totals.onError(t);
                }

                @Override
                public void onCompleted() {
                    totals.onCompleted();
                }
            };
        }
    }

    /** Records the client port of every connection the server accepts and closes. */
    private static final class ConnectionLog extends ServerTransportFilter {

        private final List<Integer> ready = new CopyOnWriteArrayList<>();

        private final List<Integer> terminated = new CopyOnWriteArrayList<>();

        private final BlockingQueue<Integer> terminations = new LinkedBlockingQueue<>();

        @Override
        public Attributes transportReady(Attributes transportAttrs) {
            ready.add(clientPort(transportAttrs));
            return transportAttrs;
        }

        @Override
        public void transportTerminated(Attributes transportAttrs) {
            int port = clientPort(transportAttrs);
            terminated.add(port);
            terminations.add(port);
        }

        int lastReadyPort() {
            assertThat(ready).isNotEmpty();
            return ready.get(ready.size() - 1);
        }

        List<Integer> readyPorts() {
            return ready;
        }

        List<Integer> terminatedPorts() {
            return terminated;
        }

        void awaitTerminated(int port) throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
            while (!terminated.contains(port)) {
                long remaining = deadline - System.nanoTime();
                assertThat(remaining > 0 && terminations.poll(remaining, TimeUnit.NANOSECONDS) != null)
                        .as("connection from port %d closing", port)
                        .isTrue();
            }
        }

        private static int clientPort(Attributes transportAttrs) {
            InetSocketAddress remote = (InetSocketAddress) transportAttrs.get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR);
            assertThat(remote).isNotNull();
            return remote.getPort();
        }
    }
}
