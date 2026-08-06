/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_api;

import static org.assertj.core.api.Assertions.assertThat;

import io.grpc.Attributes;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ClientInterceptors;
import io.grpc.ForwardingClientCall;
import io.grpc.ForwardingClientCallListener;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.ServerMethodDefinition;
import io.grpc.ServerServiceDefinition;
import io.grpc.Status;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

public class GrpcApiInterceptorsTest {
    private static final MethodDescriptor.Marshaller<String> MARSHALLER = new MethodDescriptor.Marshaller<String>() {
        @Override
        public InputStream stream(String value) {
            return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public String parse(InputStream stream) {
            try {
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            } catch (java.io.IOException exception) {
                throw new AssertionError(exception);
            }
        }
    };

    private static final MethodDescriptor<String, String> METHOD = MethodDescriptor.create(
            MethodDescriptor.MethodType.UNARY, "demo.Echo/Say", MARSHALLER, MARSHALLER);

    @Test
    void clientInterceptorsWrapCallsInDeclaredOrderAndDelegateMessages() {
        RecordingCall call = new RecordingCall();
        Channel base = new Channel() {
            @Override
            public String authority() {
                return "demo";
            }

            @Override
            public <ReqT, RespT> ClientCall<ReqT, RespT> newCall(
                    MethodDescriptor<ReqT, RespT> method, CallOptions callOptions) {
                assertThat(method).isEqualTo(METHOD);
                return (ClientCall<ReqT, RespT>) call;
            }
        };
        AtomicBoolean first = new AtomicBoolean();
        AtomicBoolean second = new AtomicBoolean();
        ClientInterceptor markFirst = new MarkingInterceptor(first);
        ClientInterceptor markSecond = new MarkingInterceptor(second);
        Channel reverse = ClientInterceptors.intercept(base, markFirst, markSecond);
        ClientCall<String, String> intercepted = reverse.newCall(METHOD, CallOptions.DEFAULT);
        intercepted.start(new ClientCall.Listener<String>() {}, new Metadata());
        intercepted.sendMessage("hello");
        assertThat(first).isTrue();
        assertThat(second).isTrue();
        assertThat(call.started).isTrue();
        assertThat(call.message).isEqualTo("hello");

        first.set(false);
        second.set(false);
        ClientInterceptors.interceptForward(base, Arrays.asList(markFirst, markSecond))
                .newCall(METHOD, CallOptions.DEFAULT);
        assertThat(first).isTrue();
        assertThat(second).isTrue();
    }

    @Test
    void forwardingCallsAndListenersRetainUnderlyingSemantics() {
        RecordingCall delegate = new RecordingCall();
        ClientCall<String, String> forwarding = new ForwardingClientCall.SimpleForwardingClientCall<String, String>(delegate) {};
        AtomicBoolean delivered = new AtomicBoolean();
        ClientCall.Listener<String> listener = new ForwardingClientCallListener.SimpleForwardingClientCallListener<String>(
                new ClientCall.Listener<String>() {
                    @Override
                    public void onMessage(String message) {
                        delivered.set(true);
                    }
                }) {};
        forwarding.start(listener, new Metadata());
        forwarding.sendMessage("forwarded");
        forwarding.request(2);
        forwarding.setMessageCompression(true);
        forwarding.halfClose();
        forwarding.cancel("finished", null);
        listener.onHeaders(new Metadata());
        listener.onMessage("response");
        listener.onReady();
        listener.onClose(Status.OK, new Metadata());
        assertThat(delegate.started).isTrue();
        assertThat(delegate.message).isEqualTo("forwarded");
        assertThat(delegate.requested).isEqualTo(2);
        assertThat(delegate.messageCompression).isTrue();
        assertThat(delegate.halfClosed).isTrue();
        assertThat(delegate.cancelled).isTrue();
        assertThat(delivered).isTrue();
        assertThat(forwarding.getAttributes()).isEqualTo(Attributes.EMPTY);
        assertThat(forwarding.isReady()).isTrue();
        assertThat(forwarding.toString()).contains("delegate");
        assertThat(listener.toString()).contains("delegate");
    }

    @Test
    void serverInterceptorsKeepMethodDefinitionsAndInvokeHandler() {
        AtomicBoolean invoked = new AtomicBoolean();
        ServerCallHandler<String, String> handler = (call, headers) -> {
            invoked.set(true);
            return new ServerCall.Listener<String>() {};
        };
        ServerMethodDefinition<String, String> definition = ServerMethodDefinition.create(METHOD, handler);
        ServerServiceDefinition service = ServerServiceDefinition.builder("demo.Echo").addMethod(definition).build();
        ServerServiceDefinition wrapped = ServerInterceptors.intercept(service, new PassingServerInterceptor());
        assertThat(wrapped.getServiceDescriptor().getName()).isEqualTo("demo.Echo");
        assertThat(wrapped.getMethod(METHOD.getFullMethodName()).getMethodDescriptor()).isEqualTo(METHOD);
        assertThat(wrapped.getMethods()).hasSize(1);
        @SuppressWarnings("unchecked")
        ServerMethodDefinition<String, String> wrappedMethod = (ServerMethodDefinition<String, String>)
                (ServerMethodDefinition<?, ?>) wrapped.getMethod(METHOD.getFullMethodName());
        wrappedMethod.getServerCallHandler().startCall(new ServerCall<String, String>() {
                    @Override public void request(int count) {}
                    @Override public void sendHeaders(Metadata headers) {}
                    @Override public void sendMessage(String message) {}
                    @Override public void close(Status status, Metadata trailers) {}
                    @Override public boolean isCancelled() { return false; }
                    @Override public MethodDescriptor<String, String> getMethodDescriptor() { return METHOD; }
                }, new Metadata());
        assertThat(invoked).isTrue();
        assertThat(definition.withServerCallHandler(handler).getServerCallHandler()).isSameAs(handler);
    }

    private static final class MarkingInterceptor implements ClientInterceptor {
        private final AtomicBoolean invoked;

        private MarkingInterceptor(AtomicBoolean invoked) {
            this.invoked = invoked;
        }

        @Override
        public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
                MethodDescriptor<ReqT, RespT> method, CallOptions options, Channel next) {
            invoked.set(true);
            return next.newCall(method, options);
        }
    }

    private static final class PassingServerInterceptor implements ServerInterceptor {
        @Override
        public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
                ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
            return next.startCall(call, headers);
        }
    }

    private static final class RecordingCall extends ClientCall<String, String> {
        private boolean started;
        private boolean cancelled;
        private boolean halfClosed;
        private boolean messageCompression;
        private int requested;
        private String message;

        @Override public void start(Listener<String> listener, Metadata headers) { started = true; }
        @Override public void request(int count) { requested += count; }
        @Override public void cancel(String message, Throwable cause) { cancelled = true; }
        @Override public void halfClose() { halfClosed = true; }
        @Override public void setMessageCompression(boolean enabled) { messageCompression = enabled; }
        @Override public void sendMessage(String message) { this.message = message; }
        @Override public boolean isReady() { return true; }
        @Override public Attributes getAttributes() { return Attributes.EMPTY; }
    }
}
