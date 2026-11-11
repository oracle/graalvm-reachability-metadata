/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_api;

import static org.assertj.core.api.Assertions.assertThat;

import io.grpc.CallOptions;
import io.grpc.ClientCall;
import io.grpc.ForwardingServerCall.SimpleForwardingServerCall;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerServiceDefinition;
import io.grpc.Status;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

public class ForwardingServerCallInnerSimpleForwardingServerCallTest {
    private static final Metadata.Key<String> FORWARDED =
            Metadata.Key.of("x-forwarded", Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> RESULT =
            Metadata.Key.of("x-result", Metadata.ASCII_STRING_MARSHALLER);

    @Test
    void interceptorForwardsResponseAndDecoratesHeadersAndTrailers() throws Exception {
        MethodDescriptor.Marshaller<String> marshaller = new StringMarshaller();
        MethodDescriptor<String, String> method = MethodDescriptor.<String, String>newBuilder()
                .setType(MethodDescriptor.MethodType.UNARY)
                .setFullMethodName(MethodDescriptor.generateFullMethodName("example.Echo", "echo"))
                .setRequestMarshaller(marshaller)
                .setResponseMarshaller(marshaller)
                .build();
        List<String> intercepted = new ArrayList<>();
        ServerInterceptor interceptor = new ServerInterceptor() {
            @Override
            public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
                    ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
                ServerCall<ReqT, RespT> forwarding = new SimpleForwardingServerCall<>(call) {
                    @Override
                    public void sendHeaders(Metadata responseHeaders) {
                        responseHeaders.put(FORWARDED, "true");
                        super.sendHeaders(responseHeaders);
                    }

                    @Override
                    public void sendMessage(RespT message) {
                        intercepted.add("message:" + message);
                        super.sendMessage(message);
                    }

                    @Override
                    public void close(Status status, Metadata trailers) {
                        trailers.put(RESULT, status.getCode().name());
                        super.close(status, trailers);
                    }
                };
                assertThat(forwarding.getMethodDescriptor()).isSameAs(call.getMethodDescriptor());
                assertThat(forwarding.getAuthority()).isEqualTo(call.getAuthority());
                assertThat(forwarding.getAttributes()).isSameAs(call.getAttributes());
                assertThat(forwarding.isCancelled()).isFalse();
                assertThat(forwarding.isReady()).isEqualTo(call.isReady());
                assertThat(forwarding.toString()).contains("delegate=", call.toString());
                return next.startCall(forwarding, headers);
            }
        };
        ServerServiceDefinition service = ServerServiceDefinition.builder("example.Echo")
                .addMethod(method, (call, headers) -> {
                    call.request(1);
                    return new ServerCall.Listener<String>() {
                        private String request;

                        @Override
                        public void onMessage(String message) {
                            request = message;
                        }

                        @Override
                        public void onHalfClose() {
                            call.sendHeaders(new Metadata());
                            call.sendMessage("echo:" + request);
                            call.close(Status.OK, new Metadata());
                        }
                    };
                }).build();
        String name = InProcessServerBuilder.generateName();
        Server server = InProcessServerBuilder.forName(name).directExecutor()
                .addService(service).intercept(interceptor).build().start();
        ManagedChannel channel = null;
        try {
            channel = InProcessChannelBuilder.forName(name).directExecutor().build();
            CompletableFuture<Status> completed = new CompletableFuture<>();
            List<String> responses = new ArrayList<>();
            List<String> responseHeaders = new ArrayList<>();
            List<String> responseTrailers = new ArrayList<>();
            ClientCall<String, String> call = channel.newCall(method,
                    CallOptions.DEFAULT.withDeadlineAfter(30, TimeUnit.SECONDS));
            call.start(new ClientCall.Listener<String>() {
                @Override
                public void onHeaders(Metadata headers) {
                    responseHeaders.add(headers.get(FORWARDED));
                }

                @Override
                public void onMessage(String message) {
                    responses.add(message);
                }

                @Override
                public void onClose(Status status, Metadata trailers) {
                    responseTrailers.add(trailers.get(RESULT));
                    completed.complete(status);
                }
            }, new Metadata());
            call.request(1);
            call.sendMessage("hello");
            call.halfClose();

            assertThat(completed.get(30, TimeUnit.SECONDS).getCode()).isEqualTo(Status.Code.OK);
            assertThat(responses).containsExactly("echo:hello");
            assertThat(intercepted).containsExactly("message:echo:hello");
            assertThat(responseHeaders).containsExactly("true");
            assertThat(responseTrailers).containsExactly("OK");
        } finally {
            try {
                if (channel != null) {
                    channel.shutdownNow();
                    assertThat(channel.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
                }
            } finally {
                server.shutdownNow();
                assertThat(server.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
            }
        }
    }

    private static final class StringMarshaller implements MethodDescriptor.Marshaller<String> {
        @Override
        public InputStream stream(String value) {
            return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public String parse(InputStream stream) {
            try {
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }
    }
}
