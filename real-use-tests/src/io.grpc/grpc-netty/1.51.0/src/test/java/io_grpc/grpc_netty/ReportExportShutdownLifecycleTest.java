/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_netty;

import com.google.protobuf.StringValue;
import io.grpc.CallOptions;
import io.grpc.ConnectivityState;
import io.grpc.Context;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerServiceDefinition;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.ServerCalls;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the graceful and forced shutdown lifecycle of grpc-netty servers and channels,
 * where `shutdown()` drains in-flight calls behind an HTTP/2 GOAWAY and `shutdownNow()` closes
 * the Netty connection or resets the stream under them.
 *
 * A reporting service streams report exports in chunks, and each export stays open until the
 * application decides to finish it. Every test opens an export, lets the first chunk arrive,
 * and then retires either the `NettyServerBuilder` server or the `NettyChannelBuilder` channel:
 * a graceful shutdown lets the open export complete while refusing new calls, and a forced
 * shutdown cancels the export on both sides: a server resets the stream with RST_STREAM before
 * closing the connection, and a channel fails the export locally with `UNAVAILABLE`.
 */
class ReportExportShutdownLifecycleTest {

    private static final String SERVICE = "reporting.ReportExports";

    private static final long WAIT_SECONDS = 10;

    private static final MethodDescriptor<StringValue, StringValue> EXPORT =
            MethodDescriptor.<StringValue, StringValue>newBuilder()
                    .setType(MethodDescriptor.MethodType.SERVER_STREAMING)
                    .setFullMethodName(MethodDescriptor.generateFullMethodName(SERVICE, "Export"))
                    .setRequestMarshaller(ProtoUtils.marshaller(StringValue.getDefaultInstance()))
                    .setResponseMarshaller(ProtoUtils.marshaller(StringValue.getDefaultInstance()))
                    .build();

    private static final MethodDescriptor<StringValue, StringValue> PING =
            MethodDescriptor.<StringValue, StringValue>newBuilder()
                    .setType(MethodDescriptor.MethodType.UNARY)
                    .setFullMethodName(MethodDescriptor.generateFullMethodName(SERVICE, "Ping"))
                    .setRequestMarshaller(ProtoUtils.marshaller(StringValue.getDefaultInstance()))
                    .setResponseMarshaller(ProtoUtils.marshaller(StringValue.getDefaultInstance()))
                    .build();

    @Test
    void gracefulServerShutdownDrainsOpenExportAndRefusesNewCalls() throws Exception {
        ExportDesk desk = new ExportDesk();
        Server server = startServer(desk);
        ManagedChannel channel = openChannel(server);
        try {
            assertThat(ping(channel, "warm-up")).isEqualTo("pong:warm-up");
            assertThat(channel.getState(false)).isEqualTo(ConnectivityState.READY);

            ExportReceiver receiver = ExportReceiver.start(channel, "q3-revenue");
            PendingExport pending = desk.awaitExport();
            assertThat(receiver.awaitFirstChunk()).isTrue();

            server.shutdown();
            assertThat(server.isShutdown()).isTrue();
            assertThat(server.isTerminated()).isFalse();

            // The GOAWAY retires the connection, so the channel leaves READY while the export runs.
            awaitState(channel, ConnectivityState.IDLE);
            assertThatThrownBy(() -> ping(channel, "late"))
                    .isInstanceOfSatisfying(StatusRuntimeException.class, failure ->
                            assertThat(failure.getStatus().getCode()).isEqualTo(Status.Code.UNAVAILABLE));
            assertThat(server.awaitTermination(200, TimeUnit.MILLISECONDS)).isFalse();

            pending.finish(3);
            assertThat(receiver.awaitDone()).isTrue();
            assertThat(receiver.status().getCode()).isEqualTo(Status.Code.OK);
            assertThat(receiver.chunks()).containsExactly(
                    "q3-revenue#0", "q3-revenue#1", "q3-revenue#2", "q3-revenue#3");
            assertThat(pending.wasCancelled()).isFalse();

            assertThat(server.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
            assertThat(server.isTerminated()).isTrue();
        } finally {
            channel.shutdownNow().awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS);
            server.shutdownNow().awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS);
        }
    }

    @Test
    void forcedServerShutdownFailsOpenExportAndCancelsItOnTheServer() throws Exception {
        ExportDesk desk = new ExportDesk();
        Server server = startServer(desk);
        ManagedChannel channel = openChannel(server);
        try {
            ExportReceiver receiver = ExportReceiver.start(channel, "audit-log");
            PendingExport pending = desk.awaitExport();
            assertThat(receiver.awaitFirstChunk()).isTrue();

            server.shutdownNow();
            assertThat(server.isShutdown()).isTrue();

            assertThat(receiver.awaitDone()).isTrue();
            assertThat(receiver.status().getCode()).isEqualTo(Status.Code.CANCELLED);
            assertThat(receiver.status().getDescription()).contains("RST_STREAM");
            assertThat(receiver.chunks()).containsExactly("audit-log#0");
            assertThat(pending.awaitCancelled()).isTrue();
            assertThat(pending.contextCancelled()).isTrue();

            assertThat(server.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
        } finally {
            channel.shutdownNow().awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS);
            server.shutdownNow().awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS);
        }
    }

    @Test
    void gracefulChannelShutdownFinishesOpenExportAndRejectsNewCalls() throws Exception {
        ExportDesk desk = new ExportDesk();
        Server server = startServer(desk);
        ManagedChannel channel = openChannel(server);
        try {
            ExportReceiver receiver = ExportReceiver.start(channel, "payroll");
            PendingExport pending = desk.awaitExport();
            assertThat(receiver.awaitFirstChunk()).isTrue();

            channel.shutdown();
            assertThat(channel.isShutdown()).isTrue();
            assertThat(channel.isTerminated()).isFalse();
            assertThatThrownBy(() -> ping(channel, "after-shutdown"))
                    .isInstanceOfSatisfying(StatusRuntimeException.class, failure -> {
                        assertThat(failure.getStatus().getCode()).isEqualTo(Status.Code.UNAVAILABLE);
                        assertThat(failure.getStatus().getDescription()).contains("shutdown");
                    });
            assertThat(channel.awaitTermination(200, TimeUnit.MILLISECONDS)).isFalse();

            pending.finish(2);
            assertThat(receiver.awaitDone()).isTrue();
            assertThat(receiver.status().getCode()).isEqualTo(Status.Code.OK);
            assertThat(receiver.chunks()).containsExactly("payroll#0", "payroll#1", "payroll#2");
            assertThat(pending.wasCancelled()).isFalse();
            assertThat(channel.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();

            ManagedChannel replacement = openChannel(server);
            try {
                assertThat(ping(replacement, "next-shift")).isEqualTo("pong:next-shift");
            } finally {
                replacement.shutdownNow().awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS);
            }
        } finally {
            channel.shutdownNow().awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS);
            server.shutdownNow().awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS);
        }
    }

    @Test
    void forcedChannelShutdownResetsOpenExportWhileServerKeepsServing() throws Exception {
        ExportDesk desk = new ExportDesk();
        Server server = startServer(desk);
        ManagedChannel channel = openChannel(server);
        try {
            ExportReceiver receiver = ExportReceiver.start(channel, "inventory");
            PendingExport pending = desk.awaitExport();
            assertThat(receiver.awaitFirstChunk()).isTrue();

            channel.shutdownNow();
            assertThat(receiver.awaitDone()).isTrue();
            assertThat(receiver.status().getCode()).isEqualTo(Status.Code.UNAVAILABLE);
            assertThat(receiver.status().getDescription()).contains("shutdownNow");
            assertThat(receiver.chunks()).containsExactly("inventory#0");
            assertThat(channel.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();

            assertThat(pending.awaitCancelled()).isTrue();
            assertThat(pending.contextCancelled()).isTrue();
            assertThat(server.isShutdown()).isFalse();

            ManagedChannel replacement = openChannel(server);
            try {
                assertThat(ping(replacement, "still-open")).isEqualTo("pong:still-open");
            } finally {
                replacement.shutdownNow().awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS);
            }
        } finally {
            channel.shutdownNow().awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS);
            server.shutdownNow().awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS);
        }
    }

    private static Server startServer(ExportDesk desk) throws Exception {
        return NettyServerBuilder.forAddress(new InetSocketAddress("127.0.0.1", 0))
                .addService(desk.bindService())
                .build()
                .start();
    }

    private static ManagedChannel openChannel(Server server) {
        return NettyChannelBuilder.forAddress("127.0.0.1", server.getPort())
                .usePlaintext()
                .build();
    }

    private static String ping(ManagedChannel channel, String text) {
        StringValue reply = ClientCalls.blockingUnaryCall(
                channel,
                PING,
                CallOptions.DEFAULT.withDeadlineAfter(WAIT_SECONDS, TimeUnit.SECONDS),
                StringValue.of(text));
        return reply.getValue();
    }

    /// Waits for `channel` to reach `target`, following state changes instead of sleeping.
    private static void awaitState(ManagedChannel channel, ConnectivityState target) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
        ConnectivityState state = channel.getState(false);
        while (state != target) {
            long remaining = deadline - System.nanoTime();
            assertThat(remaining).as("channel still %s, expected %s", state, target).isPositive();
            CountDownLatch changed = new CountDownLatch(1);
            channel.notifyWhenStateChanged(state, changed::countDown);
            changed.await(remaining, TimeUnit.NANOSECONDS);
            state = channel.getState(false);
        }
    }

    /// Server side of the reporting service: hands every open export to the application.
    static final class ExportDesk {

        private final BlockingQueue<PendingExport> exports = new LinkedBlockingQueue<>();

        ServerServiceDefinition bindService() {
            return ServerServiceDefinition.builder(SERVICE)
                    .addMethod(EXPORT, ServerCalls.asyncServerStreamingCall(this::export))
                    .addMethod(PING, ServerCalls.asyncUnaryCall(this::ping))
                    .build();
        }

        PendingExport awaitExport() throws InterruptedException {
            PendingExport pending = exports.poll(WAIT_SECONDS, TimeUnit.SECONDS);
            assertThat(pending).as("export reached the server").isNotNull();
            return pending;
        }

        private void export(StringValue request, StreamObserver<StringValue> responses) {
            ServerCallStreamObserver<StringValue> stream = (ServerCallStreamObserver<StringValue>) responses;
            PendingExport pending = new PendingExport(request.getValue(), stream, Context.current());
            stream.setOnCancelHandler(pending::markCancelled);
            stream.onNext(StringValue.of(request.getValue() + "#0"));
            exports.add(pending);
        }

        private void ping(StringValue request, StreamObserver<StringValue> responses) {
            responses.onNext(StringValue.of("pong:" + request.getValue()));
            responses.onCompleted();
        }
    }

    /// An export the server has started streaming but not yet finished.
    static final class PendingExport {

        private final String name;

        private final ServerCallStreamObserver<StringValue> stream;

        private final Context context;

        private final CountDownLatch cancelled = new CountDownLatch(1);

        PendingExport(String name, ServerCallStreamObserver<StringValue> stream, Context context) {
            this.name = name;
            this.stream = stream;
            this.context = context;
        }

        void finish(int remainingChunks) {
            for (int chunk = 1; chunk <= remainingChunks; chunk++) {
                stream.onNext(StringValue.of(name + "#" + chunk));
            }
            stream.onCompleted();
        }

        void markCancelled() {
            cancelled.countDown();
        }

        boolean awaitCancelled() throws InterruptedException {
            return cancelled.await(WAIT_SECONDS, TimeUnit.SECONDS);
        }

        boolean wasCancelled() {
            return cancelled.getCount() == 0;
        }

        boolean contextCancelled() {
            return context.isCancelled();
        }
    }

    /// Client side of one export: collects chunks and the final status.
    static final class ExportReceiver implements StreamObserver<StringValue> {

        private final List<String> chunks = new CopyOnWriteArrayList<>();

        private final CountDownLatch firstChunk = new CountDownLatch(1);

        private final CountDownLatch done = new CountDownLatch(1);

        private final AtomicReference<Status> status = new AtomicReference<>();

        static ExportReceiver start(ManagedChannel channel, String report) {
            ExportReceiver receiver = new ExportReceiver();
            ClientCalls.asyncServerStreamingCall(
                    channel.newCall(EXPORT, CallOptions.DEFAULT), StringValue.of(report), receiver);
            return receiver;
        }

        @Override
        public void onNext(StringValue chunk) {
            chunks.add(chunk.getValue());
            firstChunk.countDown();
        }

        @Override
        public void onError(Throwable failure) {
            status.set(Status.fromThrowable(failure));
            done.countDown();
        }

        @Override
        public void onCompleted() {
            status.set(Status.OK);
            done.countDown();
        }

        boolean awaitFirstChunk() throws InterruptedException {
            return firstChunk.await(WAIT_SECONDS, TimeUnit.SECONDS);
        }

        boolean awaitDone() throws InterruptedException {
            return done.await(WAIT_SECONDS, TimeUnit.SECONDS);
        }

        Status status() {
            return status.get();
        }

        List<String> chunks() {
            return chunks;
        }
    }
}
