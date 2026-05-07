/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_netty_shaded;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import io.grpc.CallOptions;
import io.grpc.ClientCall;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerServiceDefinition;
import io.grpc.Status;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.netty.shaded.io.netty.channel.DefaultEventLoopGroup;
import io.grpc.netty.shaded.io.netty.channel.EventLoopGroup;
import io.grpc.netty.shaded.io.netty.channel.local.LocalAddress;
import io.grpc.netty.shaded.io.netty.channel.local.LocalChannel;
import io.grpc.netty.shaded.io.netty.channel.local.LocalServerChannel;
import io.grpc.netty.shaded.io.netty.channel.nio.NioEventLoopGroup;
import io.grpc.netty.shaded.io.netty.channel.socket.nio.NioServerSocketChannel;
import io.grpc.netty.shaded.io.netty.channel.socket.nio.NioSocketChannel;

public class Grpc_netty_shadedTest {
    private static final String SERVICE_NAME = "nativeimage.Echo";
    private static final MethodDescriptor<String, String> UNARY_METHOD = MethodDescriptor.<String, String>newBuilder()
            .setType(MethodDescriptor.MethodType.UNARY)
            .setFullMethodName(MethodDescriptor.generateFullMethodName(SERVICE_NAME, "UnaryEcho"))
            .setRequestMarshaller(new StringMarshaller())
            .setResponseMarshaller(new StringMarshaller())
            .build();
    private static final MethodDescriptor<String, String> BIDI_METHOD = MethodDescriptor.<String, String>newBuilder()
            .setType(MethodDescriptor.MethodType.BIDI_STREAMING)
            .setFullMethodName(MethodDescriptor.generateFullMethodName(SERVICE_NAME, "BidirectionalEcho"))
            .setRequestMarshaller(new StringMarshaller())
            .setResponseMarshaller(new StringMarshaller())
            .build();

    @Test
    void plaintextNettyTransportHandlesUnaryCall() throws Exception {
        Server server = null;
        ManagedChannel channel = null;
        try {
            server = NettyServerBuilder.forPort(0)
                    .directExecutor()
                    .addService(echoService())
                    .build()
                    .start();
            channel = NettyChannelBuilder.forAddress("127.0.0.1", server.getPort())
                    .usePlaintext()
                    .directExecutor()
                    .maxInboundMessageSize(1024 * 1024)
                    .build();

            assertEquals("echo:plain", invokeUnary(channel, "plain"));
        } finally {
            shutdown(channel, server);
        }
    }

    @Test
    void customNioEventLoopsSupportBidirectionalStreaming() throws Exception {
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup(1);
        EventLoopGroup clientGroup = new NioEventLoopGroup(1);
        Server server = null;
        ManagedChannel channel = null;
        try {
            server = NettyServerBuilder.forPort(0)
                    .bossEventLoopGroup(bossGroup)
                    .workerEventLoopGroup(workerGroup)
                    .channelType(NioServerSocketChannel.class)
                    .directExecutor()
                    .addService(echoService())
                    .build()
                    .start();
            channel = NettyChannelBuilder.forAddress("127.0.0.1", server.getPort())
                    .eventLoopGroup(clientGroup)
                    .channelType(NioSocketChannel.class)
                    .usePlaintext()
                    .directExecutor()
                    .build();

            assertEquals(List.of("stream:one", "stream:two"), invokeBidirectionalStreaming(channel, "one", "two"));
        } finally {
            shutdown(channel, server);
            shutdownGracefully(clientGroup);
            shutdownGracefully(workerGroup);
            shutdownGracefully(bossGroup);
        }
    }

    @Test
    void localNettyTransportHandlesSocketAddressChannels() throws Exception {
        EventLoopGroup serverGroup = new DefaultEventLoopGroup(1);
        EventLoopGroup clientGroup = new DefaultEventLoopGroup(1);
        LocalAddress address = new LocalAddress("grpc-netty-shaded-" + UUID.randomUUID());
        Server server = null;
        ManagedChannel channel = null;
        try {
            server = NettyServerBuilder.forAddress(address)
                    .bossEventLoopGroup(serverGroup)
                    .workerEventLoopGroup(serverGroup)
                    .channelType(LocalServerChannel.class)
                    .directExecutor()
                    .addService(echoService())
                    .build()
                    .start();
            channel = NettyChannelBuilder.forAddress(address)
                    .eventLoopGroup(clientGroup)
                    .channelType(LocalChannel.class, LocalAddress.class)
                    .usePlaintext()
                    .overrideAuthority("localhost")
                    .directExecutor()
                    .build();

            assertEquals("echo:local", invokeUnary(channel, "local"));
        } finally {
            shutdown(channel, server);
            shutdownGracefully(clientGroup);
            shutdownGracefully(serverGroup);
        }
    }

    private static ServerServiceDefinition echoService() {
        return ServerServiceDefinition.builder(SERVICE_NAME)
                .addMethod(UNARY_METHOD, new UnaryEchoHandler())
                .addMethod(BIDI_METHOD, new BidirectionalEchoHandler())
                .build();
    }

    private static String invokeUnary(ManagedChannel channel, String request) throws InterruptedException {
        CountDownLatch closed = new CountDownLatch(1);
        AtomicReference<String> response = new AtomicReference<>();
        AtomicReference<Status> status = new AtomicReference<>();
        ClientCall<String, String> call = channel.newCall(
                UNARY_METHOD,
                CallOptions.DEFAULT.withDeadlineAfter(5, TimeUnit.SECONDS));
        call.start(new ClientCall.Listener<>() {
            @Override
            public void onMessage(String message) {
                response.set(message);
            }

            @Override
            public void onClose(Status closedStatus, Metadata trailers) {
                status.set(closedStatus);
                closed.countDown();
            }
        }, new Metadata());
        call.request(1);
        call.sendMessage(request);
        call.halfClose();

        assertTrue(closed.await(5, TimeUnit.SECONDS));
        assertEquals(Status.Code.OK, status.get().getCode());
        assertNotNull(response.get());
        return response.get();
    }

    private static List<String> invokeBidirectionalStreaming(ManagedChannel channel, String... requests)
            throws InterruptedException {
        CountDownLatch closed = new CountDownLatch(1);
        List<String> responses = Collections.synchronizedList(new ArrayList<>());
        AtomicReference<Status> status = new AtomicReference<>();
        ClientCall<String, String> call = channel.newCall(
                BIDI_METHOD,
                CallOptions.DEFAULT.withDeadlineAfter(5, TimeUnit.SECONDS));
        call.start(new ClientCall.Listener<>() {
            @Override
            public void onMessage(String message) {
                responses.add(message);
            }

            @Override
            public void onClose(Status closedStatus, Metadata trailers) {
                status.set(closedStatus);
                closed.countDown();
            }
        }, new Metadata());
        call.request(requests.length);
        for (String request : requests) {
            call.sendMessage(request);
        }
        call.halfClose();

        assertTrue(closed.await(5, TimeUnit.SECONDS));
        assertEquals(Status.Code.OK, status.get().getCode());
        return List.copyOf(responses);
    }

    private static void shutdown(ManagedChannel channel, Server server) throws InterruptedException {
        if (channel != null) {
            channel.shutdownNow();
            assertTrue(channel.awaitTermination(5, TimeUnit.SECONDS));
        }
        if (server != null) {
            server.shutdownNow();
            assertTrue(server.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private static void shutdownGracefully(EventLoopGroup group) throws InterruptedException {
        assertTrue(group.shutdownGracefully(0, 5, TimeUnit.SECONDS).await(5, TimeUnit.SECONDS));
    }

    private static final class UnaryEchoHandler implements ServerCallHandler<String, String> {
        @Override
        public ServerCall.Listener<String> startCall(ServerCall<String, String> call, Metadata headers) {
            call.sendHeaders(new Metadata());
            call.request(1);
            return new ServerCall.Listener<>() {
                private String request;

                @Override
                public void onMessage(String message) {
                    request = message;
                }

                @Override
                public void onHalfClose() {
                    call.sendMessage("echo:" + request);
                    call.close(Status.OK, new Metadata());
                }
            };
        }
    }

    private static final class BidirectionalEchoHandler implements ServerCallHandler<String, String> {
        @Override
        public ServerCall.Listener<String> startCall(ServerCall<String, String> call, Metadata headers) {
            call.sendHeaders(new Metadata());
            call.request(1);
            return new ServerCall.Listener<>() {
                @Override
                public void onMessage(String message) {
                    call.sendMessage("stream:" + message);
                    call.request(1);
                }

                @Override
                public void onHalfClose() {
                    call.close(Status.OK, new Metadata());
                }
            };
        }
    }

    private static final class StringMarshaller implements MethodDescriptor.Marshaller<String> {
        @Override
        public InputStream stream(String value) {
            return new ByteArrayInputStream(value.getBytes(UTF_8));
        }

        @Override
        public String parse(InputStream stream) {
            try {
                return new String(stream.readAllBytes(), UTF_8);
            } catch (IOException exception) {
                throw new IllegalArgumentException("Could not read gRPC message", exception);
            }
        }
    }
}
