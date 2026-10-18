/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_netty;

import com.google.protobuf.ByteString;
import com.google.protobuf.BytesValue;
import com.google.protobuf.Int32Value;
import com.google.protobuf.Int64Value;
import io.grpc.CallOptions;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerServiceDefinition;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.ClientResponseObserver;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.ServerCalls;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises grpc-netty HTTP/2 flow-control backpressure, where the fixed stream windows set by
 * `NettyChannelBuilder.flowControlWindow` and `NettyServerBuilder.flowControlWindow` stall a sender
 * until the receiver asks for more messages.
 *
 * A video service streams segments to a player that has not yet requested any: the server's
 * `ServerCallStreamObserver.isReady()` turns false once the window and the outbound buffer are
 * full, and its on-ready handler resumes the stream when the player requests segments one by one.
 * In the other direction a camera uploads recording chunks to an archive whose storage is not yet
 * mounted, so the camera's `ClientCallStreamObserver` stalls until the archive requests chunks.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VideoSegmentFlowControlTest {

    private static final String SERVICE = "video.Library";

    private static final MethodDescriptor<Int32Value, BytesValue> WATCH =
            MethodDescriptor.<Int32Value, BytesValue>newBuilder()
                    .setType(MethodDescriptor.MethodType.SERVER_STREAMING)
                    .setFullMethodName(MethodDescriptor.generateFullMethodName(SERVICE, "Watch"))
                    .setRequestMarshaller(ProtoUtils.marshaller(Int32Value.getDefaultInstance()))
                    .setResponseMarshaller(ProtoUtils.marshaller(BytesValue.getDefaultInstance()))
                    .build();

    private static final MethodDescriptor<BytesValue, Int64Value> ARCHIVE =
            MethodDescriptor.<BytesValue, Int64Value>newBuilder()
                    .setType(MethodDescriptor.MethodType.CLIENT_STREAMING)
                    .setFullMethodName(MethodDescriptor.generateFullMethodName(SERVICE, "Archive"))
                    .setRequestMarshaller(ProtoUtils.marshaller(BytesValue.getDefaultInstance()))
                    .setResponseMarshaller(ProtoUtils.marshaller(Int64Value.getDefaultInstance()))
                    .build();

    /** The HTTP/2 stream window both peers advertise; far smaller than one transfer. */
    private static final int WINDOW_BYTES = 32 * 1024;

    private static final int SEGMENT_BYTES = 4 * 1024;

    private static final int SEGMENTS = 64;

    private static final long WAIT_SECONDS = 15;

    private final VideoLibrary library = new VideoLibrary();

    private Server server;

    private ManagedChannel channel;

    @BeforeAll
    void start() throws Exception {
        server = NettyServerBuilder.forAddress(new InetSocketAddress("127.0.0.1", 0))
                .flowControlWindow(WINDOW_BYTES)
                .addService(library.bindService())
                .build()
                .start();
        channel = NettyChannelBuilder.forAddress("127.0.0.1", server.getPort())
                .usePlaintext()
                .flowControlWindow(WINDOW_BYTES)
                .build();
    }

    @AfterAll
    void stop() throws Exception {
        channel.shutdownNow();
        server.shutdown();
        assertThat(channel.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
        assertThat(server.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void serverStallsUntilThePlayerRequestsSegments() throws Exception {
        Player player = new Player();
        ClientCalls.asyncServerStreamingCall(
                channel.newCall(WATCH, CallOptions.DEFAULT.withDeadlineAfter(60, TimeUnit.SECONDS)),
                Int32Value.of(SEGMENTS), player);

        assertThat(library.watchPaused.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
        int sentBeforeStall = library.sentBeforeStall.get();
        assertThat(sentBeforeStall).isBetween(1, SEGMENTS - 1);
        assertThat(player.segments).isEmpty();

        player.startPlayback();

        assertThat(player.done.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
        assertThat(player.failure.get()).isNull();
        assertThat(player.segments).hasSize(SEGMENTS);
        for (int index = 0; index < SEGMENTS; index++) {
            assertThat(player.segments.get(index)).isEqualTo(segment(index).getValue());
        }
        assertThat(library.watchReadyRounds.get()).isGreaterThan(1);
    }

    @Test
    void cameraStallsUntilTheArchiveRequestsChunks() throws Exception {
        Camera camera = new Camera();
        ClientCalls.asyncClientStreamingCall(
                channel.newCall(ARCHIVE, CallOptions.DEFAULT.withDeadlineAfter(60, TimeUnit.SECONDS)),
                camera);

        assertThat(camera.paused.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
        int sentBeforeStall = camera.sentBeforeStall.get();
        assertThat(sentBeforeStall).isBetween(1, SEGMENTS - 1);
        ServerCallStreamObserver<Int64Value> archive = library.archives.poll(WAIT_SECONDS, TimeUnit.SECONDS);
        assertThat(archive).isNotNull();
        assertThat(library.archivedChunks.get()).isZero();

        archive.request(1);

        assertThat(camera.done.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
        assertThat(camera.failure.get()).isNull();
        assertThat(camera.storedBytes.get()).isEqualTo((long) SEGMENTS * SEGMENT_BYTES);
        assertThat(library.archivedChunks.get()).isEqualTo(SEGMENTS);
        assertThat(camera.readyRounds.get()).isGreaterThan(1);
    }

    /** Builds segment `index`: `SEGMENT_BYTES` bytes all holding the low byte of the index. */
    private static BytesValue segment(int index) {
        byte[] bytes = new byte[SEGMENT_BYTES];
        Arrays.fill(bytes, (byte) index);
        return BytesValue.of(ByteString.copyFrom(bytes));
    }

    /** Streams video segments while the player keeps up and archives uploaded recordings. */
    private static final class VideoLibrary {

        final CountDownLatch watchPaused = new CountDownLatch(1);

        final AtomicInteger sentBeforeStall = new AtomicInteger();

        final AtomicInteger watchReadyRounds = new AtomicInteger();

        final BlockingQueue<ServerCallStreamObserver<Int64Value>> archives = new LinkedBlockingQueue<>();

        final AtomicInteger archivedChunks = new AtomicInteger();

        ServerServiceDefinition bindService() {
            return ServerServiceDefinition.builder(SERVICE)
                    .addMethod(WATCH, ServerCalls.asyncServerStreamingCall(this::watch))
                    .addMethod(ARCHIVE, ServerCalls.asyncClientStreamingCall(this::archive))
                    .build();
        }

        private void watch(Int32Value count, StreamObserver<BytesValue> segments) {
            ServerCallStreamObserver<BytesValue> stream = (ServerCallStreamObserver<BytesValue>) segments;
            int total = count.getValue();
            AtomicInteger next = new AtomicInteger();
            stream.setOnReadyHandler(() -> {
                watchReadyRounds.incrementAndGet();
                while (stream.isReady() && next.get() < total) {
                    stream.onNext(segment(next.getAndIncrement()));
                }
                if (next.get() == total) {
                    stream.onCompleted();
                    next.incrementAndGet();
                } else if (next.get() < total && watchPaused.getCount() == 1) {
                    sentBeforeStall.set(next.get());
                    watchPaused.countDown();
                }
            });
        }

        private StreamObserver<BytesValue> archive(StreamObserver<Int64Value> receipt) {
            ServerCallStreamObserver<Int64Value> stream = (ServerCallStreamObserver<Int64Value>) receipt;
            stream.disableAutoRequest();
            archives.add(stream);
            return new StreamObserver<BytesValue>() {
                private long stored;

                @Override
                public void onNext(BytesValue chunk) {
                    stored += chunk.getValue().size();
                    archivedChunks.incrementAndGet();
                    stream.request(1);
                }

                @Override
                public void onError(Throwable t) {
                    receipt.onError(t);
                }

                @Override
                public void onCompleted() {
                    receipt.onNext(Int64Value.of(stored));
                    receipt.onCompleted();
                }
            };
        }
    }

    /** Receives segments only after playback starts, then asks for one segment at a time. */
    private static final class Player implements ClientResponseObserver<Int32Value, BytesValue> {

        final List<ByteString> segments = new CopyOnWriteArrayList<>();

        final AtomicReference<Throwable> failure = new AtomicReference<>();

        final CountDownLatch done = new CountDownLatch(1);

        private volatile ClientCallStreamObserver<Int32Value> call;

        @Override
        public void beforeStart(ClientCallStreamObserver<Int32Value> requestStream) {
            call = requestStream;
            requestStream.disableAutoRequestWithInitial(0);
        }

        void startPlayback() {
            call.request(1);
        }

        @Override
        public void onNext(BytesValue segment) {
            segments.add(segment.getValue());
            call.request(1);
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

    /** Uploads recording chunks only while the call is ready, resuming from its on-ready handler. */
    private static final class Camera implements ClientResponseObserver<BytesValue, Int64Value> {

        final CountDownLatch paused = new CountDownLatch(1);

        final AtomicInteger sentBeforeStall = new AtomicInteger();

        final AtomicInteger readyRounds = new AtomicInteger();

        final AtomicLong storedBytes = new AtomicLong(-1);

        final AtomicReference<Throwable> failure = new AtomicReference<>();

        final CountDownLatch done = new CountDownLatch(1);

        @Override
        public void beforeStart(ClientCallStreamObserver<BytesValue> requestStream) {
            AtomicInteger next = new AtomicInteger();
            requestStream.setOnReadyHandler(() -> {
                readyRounds.incrementAndGet();
                while (requestStream.isReady() && next.get() < SEGMENTS) {
                    requestStream.onNext(segment(next.getAndIncrement()));
                }
                if (next.get() == SEGMENTS) {
                    requestStream.onCompleted();
                    next.incrementAndGet();
                } else if (next.get() < SEGMENTS && paused.getCount() == 1) {
                    sentBeforeStall.set(next.get());
                    paused.countDown();
                }
            });
        }

        @Override
        public void onNext(Int64Value stored) {
            storedBytes.set(stored.getValue());
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
}
