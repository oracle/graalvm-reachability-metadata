/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_netty;

import com.google.protobuf.Int64Value;
import com.google.protobuf.StringValue;
import io.grpc.CallOptions;
import io.grpc.ClientCall;
import io.grpc.Grpc;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.ServerServiceDefinition;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.ServerCalls;
import io.grpc.stub.StreamObserver;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollDomainSocketChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerDomainSocketChannel;
import io.netty.channel.unix.DomainSocketAddress;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs a meter-reading service over the grpc-netty Unix domain socket transport backed by
 * native epoll event loops (`EpollServerDomainSocketChannel` and `EpollDomainSocketChannel`).
 *
 * A local agent and a collector daemon on the same host talk through a socket file instead of
 * a TCP port, the way sidecars do: the agent uploads a batch of readings over a client-streaming
 * call, queries a meter total over a unary call, and gets `NOT_FOUND` for an unknown meter; the
 * client sees the socket path as the call's remote address and the server as its authority.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UnixSocketMeterReadingTest {

    private static final String SERVICE = "metering.Collector";

    private static final MethodDescriptor<StringValue, Int64Value> UPLOAD =
            MethodDescriptor.<StringValue, Int64Value>newBuilder()
                    .setType(MethodDescriptor.MethodType.CLIENT_STREAMING)
                    .setFullMethodName(MethodDescriptor.generateFullMethodName(SERVICE, "Upload"))
                    .setRequestMarshaller(ProtoUtils.marshaller(StringValue.getDefaultInstance()))
                    .setResponseMarshaller(ProtoUtils.marshaller(Int64Value.getDefaultInstance()))
                    .build();

    private static final MethodDescriptor<StringValue, Int64Value> TOTAL =
            MethodDescriptor.<StringValue, Int64Value>newBuilder()
                    .setType(MethodDescriptor.MethodType.UNARY)
                    .setFullMethodName(MethodDescriptor.generateFullMethodName(SERVICE, "Total"))
                    .setRequestMarshaller(ProtoUtils.marshaller(StringValue.getDefaultInstance()))
                    .setResponseMarshaller(ProtoUtils.marshaller(Int64Value.getDefaultInstance()))
                    .build();

    private final Collector collector = new Collector();

    private final List<String> serverAuthorities = new CopyOnWriteArrayList<>();

    private Path socketDirectory;

    private Path socketFile;

    private EventLoopGroup bossGroup;

    private EventLoopGroup workerGroup;

    private EventLoopGroup clientGroup;

    private Server server;

    private ManagedChannel channel;

    @BeforeAll
    void startServerAndChannel() throws Exception {
        assertThat(Epoll.isAvailable()).as("epoll native transport").isTrue();
        socketDirectory = Files.createTempDirectory("meter-uds");
        socketFile = socketDirectory.resolve("collector.sock");
        DomainSocketAddress address = new DomainSocketAddress(socketFile.toFile());

        bossGroup = new EpollEventLoopGroup(1);
        workerGroup = new EpollEventLoopGroup(2);
        clientGroup = new EpollEventLoopGroup(1);
        server = NettyServerBuilder.forAddress(address)
                .channelType(EpollServerDomainSocketChannel.class)
                .bossEventLoopGroup(bossGroup)
                .workerEventLoopGroup(workerGroup)
                .addService(ServerInterceptors.intercept(collector.bindService(), new AuthorityRecorder()))
                .build()
                .start();
        channel = NettyChannelBuilder.forAddress(address)
                .channelType(EpollDomainSocketChannel.class)
                .eventLoopGroup(clientGroup)
                .usePlaintext()
                .build();
    }

    @AfterAll
    void stopServerAndChannel() throws Exception {
        channel.shutdown();
        server.shutdown();
        assertThat(channel.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        assertThat(server.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        clientGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS).sync();
        workerGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS).sync();
        bossGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS).sync();
        Files.deleteIfExists(socketFile);
        Files.deleteIfExists(socketDirectory);
    }

    @Test
    void bindsTheServerToTheSocketFile() {
        assertThat(socketFile).exists();
        assertThat(server.getListenSockets())
                .singleElement()
                .isInstanceOfSatisfying(DomainSocketAddress.class,
                        listen -> assertThat(listen.path()).isEqualTo(socketFile.toString()));
    }

    @Test
    void uploadsABatchOfReadingsOverAClientStream() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Long> accepted = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        StreamObserver<Int64Value> replies = new StreamObserver<>() {
            @Override
            public void onNext(Int64Value value) {
                accepted.set(value.getValue());
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
        };

        ClientCall<StringValue, Int64Value> call = channel.newCall(UPLOAD,
                CallOptions.DEFAULT.withDeadlineAfter(10, TimeUnit.SECONDS));
        StreamObserver<StringValue> readings = ClientCalls.asyncClientStreamingCall(call, replies);
        readings.onNext(text("water-7=120"));
        readings.onNext(text("water-7=35"));
        readings.onNext(text("gas-2=48"));
        readings.onCompleted();

        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(failure.get()).isNull();
        assertThat(accepted.get()).isEqualTo(3L);
        assertThat(call.getAttributes().get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR))
                .isInstanceOfSatisfying(DomainSocketAddress.class,
                        remote -> assertThat(remote.path()).isEqualTo(socketFile.toString()));

        Int64Value water = ClientCalls.blockingUnaryCall(channel, TOTAL,
                CallOptions.DEFAULT.withDeadlineAfter(10, TimeUnit.SECONDS), text("water-7"));
        assertThat(water.getValue()).isEqualTo(155L);
        assertThat(serverAuthorities)
                .isNotEmpty()
                .allSatisfy(authority -> assertThat(authority).isEqualTo(socketFile.toString()));
    }

    @Test
    void reportsUnknownMetersAsNotFound() {
        assertThatThrownBy(() -> ClientCalls.blockingUnaryCall(channel, TOTAL,
                CallOptions.DEFAULT.withDeadlineAfter(10, TimeUnit.SECONDS), text("electric-404")))
                .isInstanceOfSatisfying(StatusRuntimeException.class, e -> {
                    assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.NOT_FOUND);
                    assertThat(e.getStatus().getDescription()).isEqualTo("no readings for electric-404");
                });
    }

    private static StringValue text(String value) {
        return StringValue.of(value);
    }

    /// Sums `meter=amount` readings per meter and answers totals.
    private static final class Collector {

        private final Map<String, Long> totals = new ConcurrentHashMap<>();

        ServerServiceDefinition bindService() {
            return ServerServiceDefinition.builder(SERVICE)
                    .addMethod(UPLOAD, ServerCalls.asyncClientStreamingCall(this::upload))
                    .addMethod(TOTAL, ServerCalls.asyncUnaryCall(this::total))
                    .build();
        }

        private StreamObserver<StringValue> upload(StreamObserver<Int64Value> responses) {
            return new StreamObserver<>() {
                private long count;

                @Override
                public void onNext(StringValue reading) {
                    String[] parts = reading.getValue().split("=", 2);
                    totals.merge(parts[0], Long.parseLong(parts[1]), Long::sum);
                    count++;
                }

                @Override
                public void onError(Throwable t) {
                }

                @Override
                public void onCompleted() {
                    responses.onNext(Int64Value.of(count));
                    responses.onCompleted();
                }
            };
        }

        private void total(StringValue meter, StreamObserver<Int64Value> responses) {
            Long total = totals.get(meter.getValue());
            if (total == null) {
                responses.onError(Status.NOT_FOUND
                        .withDescription("no readings for " + meter.getValue())
                        .asRuntimeException());
                return;
            }
            responses.onNext(Int64Value.of(total));
            responses.onCompleted();
        }
    }

    /// Records the `:authority` each call arrived with; for a socket file it is the socket path.
    private final class AuthorityRecorder implements ServerInterceptor {

        @Override
        public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
                Metadata headers, ServerCallHandler<ReqT, RespT> next) {
            serverAuthorities.add(call.getAuthority());
            return next.startCall(call, headers);
        }
    }
}
