/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_inprocess;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerServiceDefinition;
import io.grpc.Status;
import io.grpc.inprocess.AnonymousInProcessSocketAddress;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.inprocess.InProcessSocketAddress;

public class Grpc_inprocessTest {
    private static final String SERVICE_NAME = "reachability.InProcessService";
    private static final Metadata.Key<String> REQUEST_ID_KEY = Metadata.Key.of(
            "x-request-id", Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> TRAILER_KEY = Metadata.Key.of(
            "x-server-trailer", Metadata.ASCII_STRING_MARSHALLER);
    private static final MethodDescriptor.Marshaller<String> STRING_MARSHALLER = new MethodDescriptor.Marshaller<>() {
        @Override
        public InputStream stream(String value) {
            return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public String parse(InputStream stream) {
            try {
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException ex) {
                throw new IllegalStateException("Unable to parse in-process message", ex);
            }
        }
    };
    private static final MethodDescriptor<String, String> UNARY_METHOD = method(
            "Unary", MethodDescriptor.MethodType.UNARY);
    private static final MethodDescriptor<String, String> SERVER_STREAMING_METHOD = method(
            "ServerStreaming", MethodDescriptor.MethodType.SERVER_STREAMING);
    private static final MethodDescriptor<String, String> CLIENT_STREAMING_METHOD = method(
            "ClientStreaming", MethodDescriptor.MethodType.CLIENT_STREAMING);
    private static final MethodDescriptor<String, String> BIDI_STREAMING_METHOD = method(
            "BidiStreaming", MethodDescriptor.MethodType.BIDI_STREAMING);

    @Test
    void unaryCallUsesGeneratedNameAndPropagatesMetadataAndTrailers() throws Exception {
        String serverName = InProcessServerBuilder.generateName();
        AtomicReference<String> observedRequestId = new AtomicReference<>();
        Server server = InProcessServerBuilder.forName(serverName)
                .directExecutor()
                .addService(ServerServiceDefinition.builder(SERVICE_NAME)
                        .addMethod(UNARY_METHOD, unaryHandler(observedRequestId))
                        .build())
                .build()
                .start();
        ManagedChannel channel = InProcessChannelBuilder.forName(serverName)
                .directExecutor()
                .intercept(requestIdInterceptor("request-123"))
                .build();
        try {
            RecordedCall<String> response = invoke(channel, UNARY_METHOD, List.of("native-image"), 1);

            assertThat(response.awaitStatus().getCode()).isEqualTo(Status.Code.OK);
            assertThat(response.messages()).containsExactly("unary:native-image:request-123");
            assertThat(response.trailers().get(TRAILER_KEY)).isEqualTo("unary-complete");
            assertThat(observedRequestId).hasValue("request-123");
        } finally {
            shutdown(channel, server);
        }
    }

    @Test
    void namedSocketAddressConnectsUsingEquivalentAddress() throws Exception {
        InProcessSocketAddress listenAddress = new InProcessSocketAddress(
                "reachability-" + InProcessServerBuilder.generateName());
        InProcessSocketAddress dialAddress = new InProcessSocketAddress(listenAddress.getName());
        Server server = InProcessServerBuilder.forAddress(listenAddress)
                .directExecutor()
                .addService(ServerServiceDefinition.builder(SERVICE_NAME)
                        .addMethod(UNARY_METHOD, echoHandler())
                        .build())
                .build()
                .start();
        ManagedChannel channel = InProcessChannelBuilder.forAddress(dialAddress)
                .directExecutor()
                .build();
        try {
            RecordedCall<String> response = invoke(channel, UNARY_METHOD, List.of("named-address"), 1);

            assertThat(dialAddress).isEqualTo(listenAddress);
            assertThat(dialAddress.hashCode()).isEqualTo(listenAddress.hashCode());
            assertThat(dialAddress.toString()).isEqualTo(listenAddress.getName());
            assertThat(response.awaitStatus().getCode()).isEqualTo(Status.Code.OK);
            assertThat(response.messages()).containsExactly("address:named-address");
        } finally {
            shutdown(channel, server);
        }
    }

    @Test
    void anonymousSocketAddressSupportsAllStreamingCallTypes() throws Exception {
        AnonymousInProcessSocketAddress address = new AnonymousInProcessSocketAddress();
        Server server = InProcessServerBuilder.forAddress(address)
                .directExecutor()
                .addService(streamingService())
                .build()
                .start();
        ManagedChannel channel = InProcessChannelBuilder.forAddress(address)
                .directExecutor()
                .build();
        try {
            RecordedCall<String> serverStreaming = invoke(channel, SERVER_STREAMING_METHOD, List.of("alpha"), 3);
            RecordedCall<String> clientStreaming = invoke(channel, CLIENT_STREAMING_METHOD,
                    List.of("one", "two", "three"), 1);
            RecordedCall<String> bidiStreaming = invoke(channel, BIDI_STREAMING_METHOD,
                    List.of("left", "right"), 2);

            assertThat(serverStreaming.awaitStatus().getCode()).isEqualTo(Status.Code.OK);
            assertThat(serverStreaming.messages()).containsExactly("alpha-1", "alpha-2", "alpha-3");
            assertThat(clientStreaming.awaitStatus().getCode()).isEqualTo(Status.Code.OK);
            assertThat(clientStreaming.messages()).containsExactly("one|two|three");
            assertThat(bidiStreaming.awaitStatus().getCode()).isEqualTo(Status.Code.OK);
            assertThat(bidiStreaming.messages()).containsExactly("ack:left", "ack:right");
        } finally {
            shutdown(channel, server);
        }
    }

    @Test
    void missingInProcessServerFailsCallsWithUnavailableStatus() throws Exception {
        String missingServerName = InProcessServerBuilder.generateName();
        ManagedChannel channel = InProcessChannelBuilder.forName(missingServerName)
                .directExecutor()
                .propagateCauseWithStatus(true)
                .build();
        try {
            RecordedCall<String> response = startCall(channel, UNARY_METHOD, 1);

            Status status = response.awaitStatus();
            assertThat(status.getCode()).isEqualTo(Status.Code.UNAVAILABLE);
            assertThat(status.getDescription()).contains("Could not find server");
        } finally {
            shutdown(channel, null);
        }
    }

    @Test
    void serverRejectsRequestsThatExceedInboundMetadataLimit() throws Exception {
        String serverName = InProcessServerBuilder.generateName();
        AtomicReference<String> observedRequestId = new AtomicReference<>();
        Server server = InProcessServerBuilder.forName(serverName)
                .directExecutor()
                .maxInboundMetadataSize(128)
                .addService(ServerServiceDefinition.builder(SERVICE_NAME)
                        .addMethod(UNARY_METHOD, unaryHandler(observedRequestId))
                        .build())
                .build()
                .start();
        ManagedChannel channel = InProcessChannelBuilder.forName(serverName)
                .directExecutor()
                .intercept(requestIdInterceptor("x".repeat(1024)))
                .build();
        try {
            RecordedCall<String> response = startCall(channel, UNARY_METHOD, 1);

            Status status = response.awaitStatus();
            assertThat(status.getCode()).isEqualTo(Status.Code.RESOURCE_EXHAUSTED);
            assertThat(status.getDescription()).contains("Request metadata larger than");
            assertThat(response.messages()).isEmpty();
            assertThat(observedRequestId.get()).isNull();
        } finally {
            shutdown(channel, server);
        }
    }

    private static MethodDescriptor<String, String> method(String methodName, MethodDescriptor.MethodType methodType) {
        return MethodDescriptor.<String, String>newBuilder()
                .setType(methodType)
                .setFullMethodName(MethodDescriptor.generateFullMethodName(SERVICE_NAME, methodName))
                .setRequestMarshaller(STRING_MARSHALLER)
                .setResponseMarshaller(STRING_MARSHALLER)
                .build();
    }

    private static ClientInterceptor requestIdInterceptor(String requestId) {
        return new ClientInterceptor() {
            @Override
            public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method,
                    CallOptions callOptions, Channel next) {
                return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {
                    @Override
                    public void start(Listener<RespT> responseListener, Metadata headers) {
                        headers.put(REQUEST_ID_KEY, requestId);
                        super.start(responseListener, headers);
                    }
                };
            }
        };
    }

    private static ServerCallHandler<String, String> echoHandler() {
        return (call, headers) -> {
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
                    call.sendMessage("address:" + request);
                    call.close(Status.OK, new Metadata());
                }
            };
        };
    }

    private static ServerCallHandler<String, String> unaryHandler(AtomicReference<String> observedRequestId) {
        return (call, headers) -> {
            String requestId = headers.get(REQUEST_ID_KEY);
            observedRequestId.set(requestId);
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
                    Metadata trailers = new Metadata();
                    trailers.put(TRAILER_KEY, "unary-complete");
                    call.sendMessage("unary:" + request + ':' + requestId);
                    call.close(Status.OK, trailers);
                }
            };
        };
    }

    private static ServerServiceDefinition streamingService() {
        return ServerServiceDefinition.builder(SERVICE_NAME)
                .addMethod(SERVER_STREAMING_METHOD, serverStreamingHandler())
                .addMethod(CLIENT_STREAMING_METHOD, clientStreamingHandler())
                .addMethod(BIDI_STREAMING_METHOD, bidiStreamingHandler())
                .build();
    }

    private static ServerCallHandler<String, String> serverStreamingHandler() {
        return (call, headers) -> {
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
                    call.sendMessage(request + "-1");
                    call.sendMessage(request + "-2");
                    call.sendMessage(request + "-3");
                    call.close(Status.OK, new Metadata());
                }
            };
        };
    }

    private static ServerCallHandler<String, String> clientStreamingHandler() {
        return (call, headers) -> {
            List<String> messages = new ArrayList<>();
            call.sendHeaders(new Metadata());
            call.request(1);
            return new ServerCall.Listener<>() {
                @Override
                public void onMessage(String message) {
                    messages.add(message);
                    call.request(1);
                }

                @Override
                public void onHalfClose() {
                    call.sendMessage(String.join("|", messages));
                    call.close(Status.OK, new Metadata());
                }
            };
        };
    }

    private static ServerCallHandler<String, String> bidiStreamingHandler() {
        return (call, headers) -> {
            call.sendHeaders(new Metadata());
            call.request(1);
            return new ServerCall.Listener<>() {
                @Override
                public void onMessage(String message) {
                    call.sendMessage("ack:" + message);
                    call.request(1);
                }

                @Override
                public void onHalfClose() {
                    call.close(Status.OK, new Metadata());
                }
            };
        };
    }

    private static RecordedCall<String> invoke(ManagedChannel channel, MethodDescriptor<String, String> method,
            List<String> requests, int expectedResponses) {
        StartedCall startedCall = startCallForSending(channel, method, expectedResponses);
        for (String request : requests) {
            startedCall.call().sendMessage(request);
        }
        startedCall.call().halfClose();
        return startedCall.recordedCall();
    }

    private static RecordedCall<String> startCall(ManagedChannel channel, MethodDescriptor<String, String> method,
            int expectedResponses) {
        return startCallForSending(channel, method, expectedResponses).recordedCall();
    }

    private static StartedCall startCallForSending(ManagedChannel channel, MethodDescriptor<String, String> method,
            int expectedResponses) {
        RecordedCall<String> recordedCall = new RecordedCall<>();
        ClientCall<String, String> call = channel.newCall(method,
                CallOptions.DEFAULT.withDeadlineAfter(5, TimeUnit.SECONDS));
        call.start(recordedCall, new Metadata());
        call.request(expectedResponses + 1);
        return new StartedCall(call, recordedCall);
    }

    private record StartedCall(ClientCall<String, String> call, RecordedCall<String> recordedCall) {
    }

    private static void shutdown(ManagedChannel channel, Server server) throws InterruptedException {
        channel.shutdownNow();
        assertThat(channel.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        if (server != null) {
            server.shutdownNow();
            assertThat(server.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static final class RecordedCall<T> extends ClientCall.Listener<T> {
        private final CountDownLatch closed = new CountDownLatch(1);
        private final List<T> messages = new ArrayList<>();
        private final AtomicReference<Status> status = new AtomicReference<>();
        private final AtomicReference<Metadata> trailers = new AtomicReference<>(new Metadata());

        @Override
        public void onMessage(T message) {
            messages.add(message);
        }

        @Override
        public void onClose(Status status, Metadata trailers) {
            this.status.set(status);
            this.trailers.set(trailers);
            closed.countDown();
        }

        Status awaitStatus() throws InterruptedException {
            assertThat(closed.await(5, TimeUnit.SECONDS)).isTrue();
            return status.get();
        }

        List<T> messages() {
            return List.copyOf(messages);
        }

        Metadata trailers() {
            return trailers.get();
        }
    }
}
