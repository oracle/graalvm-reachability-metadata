/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.io.grpc.CallOptions;
import com.couchbase.client.core.deps.io.grpc.ClientCall;
import com.couchbase.client.core.deps.io.grpc.ManagedChannel;
import com.couchbase.client.core.deps.io.grpc.Metadata;
import com.couchbase.client.core.deps.io.grpc.MethodDescriptor;
import com.couchbase.client.core.deps.io.grpc.Server;
import com.couchbase.client.core.deps.io.grpc.ServerCall;
import com.couchbase.client.core.deps.io.grpc.ServerServiceDefinition;
import com.couchbase.client.core.deps.io.grpc.Status;
import com.couchbase.client.core.deps.io.grpc.netty.NettyChannelBuilder;
import com.couchbase.client.core.deps.io.grpc.netty.NettyServerBuilder;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class GrpcTestSupport implements AutoCloseable {
    private static final MethodDescriptor.Marshaller<String> STRINGS = new MethodDescriptor.Marshaller<>() {
        @Override
        public InputStream stream(String value) {
            return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public String parse(InputStream input) {
            try {
                return new String(input.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }
    };

    private static final MethodDescriptor<String, String> METHOD = MethodDescriptor.<String, String>newBuilder()
            .setType(MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("metadata.Echo/Reply")
            .setRequestMarshaller(STRINGS)
            .setResponseMarshaller(STRINGS)
            .build();

    private final Server server;
    private final ManagedChannel channel;

    GrpcTestSupport(boolean dns) throws IOException {
        ServerServiceDefinition service = ServerServiceDefinition.builder("metadata.Echo")
                .addMethod(METHOD, (call, headers) -> {
                    call.request(1);
                    return new ServerCall.Listener<String>() {
                        private String request;

                        @Override
                        public void onMessage(String value) {
                            request = value;
                        }

                        @Override
                        public void onHalfClose() {
                            if (!"wait".equals(request)) {
                                call.sendHeaders(new Metadata());
                                call.sendMessage("reply:" + request);
                                call.close(Status.OK, new Metadata());
                            }
                        }
                    };
                }).build();
        server = NettyServerBuilder.forAddress(new InetSocketAddress(dns ? "localhost" : "127.0.0.1", 0))
                .directExecutor().addService(service).build().start();
        NettyChannelBuilder builder = dns
                ? NettyChannelBuilder.forTarget("dns:///localhost:" + server.getPort())
                : NettyChannelBuilder.forAddress("127.0.0.1", server.getPort());
        channel = builder.directExecutor().usePlaintext().build();
    }

    CompletableFuture<String> reply(String request) {
        CompletableFuture<String> response = new CompletableFuture<>();
        ClientCall<String, String> call = channel.newCall(METHOD,
                CallOptions.DEFAULT.withDeadlineAfter(20, TimeUnit.SECONDS));
        call.start(new ClientCall.Listener<>() {
            private String message;

            @Override
            public void onMessage(String value) {
                message = value;
            }

            @Override
            public void onClose(Status status, Metadata trailers) {
                if (status.isOk()) {
                    response.complete(message);
                } else {
                    response.completeExceptionally(status.asRuntimeException(trailers));
                }
            }
        }, new Metadata());
        call.request(1);
        call.sendMessage(request);
        call.halfClose();
        return response;
    }

    @Override
    public void close() throws InterruptedException {
        try {
            channel.shutdownNow();
            assertTrue(channel.awaitTermination(10, TimeUnit.SECONDS));
        } finally {
            server.shutdownNow();
            assertTrue(server.awaitTermination(10, TimeUnit.SECONDS));
        }
    }
}
