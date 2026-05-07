/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_stub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import com.google.common.util.concurrent.ListenableFuture;

import io.grpc.Attributes;
import io.grpc.CallCredentials;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ClientInterceptors;
import io.grpc.Deadline;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.ServerServiceDefinition;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.AbstractAsyncStub;
import io.grpc.stub.AbstractBlockingStub;
import io.grpc.stub.AbstractFutureStub;
import io.grpc.stub.AbstractStub;
import io.grpc.stub.BlockingClientCall;
import io.grpc.stub.CallStreamObserver;
import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.ClientResponseObserver;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.ServerCalls;
import io.grpc.stub.StreamObserver;
import io.grpc.stub.StreamObservers;

public class Grpc_stubTest {
    private static final String SERVICE_NAME = "reachability.StubService";
    private static final Metadata.Key<String> HEADER_KEY = Metadata.Key.of(
            "x-test-header", Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> TRAILER_KEY = Metadata.Key.of(
            "x-test-trailer", Metadata.ASCII_STRING_MARSHALLER);
    private static final CallOptions.Key<String> CUSTOM_OPTION = CallOptions.Key.create("reachability-option");
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
                throw new IllegalStateException("Unable to parse gRPC test message", ex);
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
    void abstractStubFactoriesCreateTypedStubsWithConfiguredCallOptions() {
        RecordingChannel originalChannel = new RecordingChannel(response("unused"));
        RecordingChannel replacementChannel = new RecordingChannel(response("replacement"));
        Executor executor = Runnable::run;
        CallCredentials credentials = new NoopCallCredentials();
        Deadline deadline = Deadline.after(30, TimeUnit.SECONDS);

        TestAsyncStub asyncStub = AbstractStub.newStub(TestAsyncStub::new, originalChannel)
                .withDeadline(deadline)
                .withDeadlineAfter(5, TimeUnit.SECONDS)
                .withDeadlineAfter(java.time.Duration.ofSeconds(10))
                .withExecutor(executor)
                .withCompression("gzip")
                .withOption(CUSTOM_OPTION, "custom-value")
                .withCallCredentials(credentials)
                .withWaitForReady()
                .withMaxInboundMessageSize(1024)
                .withMaxOutboundMessageSize(2048)
                .withOnReadyThreshold(512)
                .withChannel(replacementChannel);
        TestBlockingStub blockingStub = AbstractStub.newStub(TestBlockingStub::new, originalChannel,
                CallOptions.DEFAULT.withOption(CUSTOM_OPTION, "blocking"));
        TestFutureStub futureStub = AbstractStub.newStub(TestFutureStub::new, originalChannel);

        assertThat(asyncStub).isInstanceOf(TestAsyncStub.class);
        assertThat(blockingStub).isInstanceOf(TestBlockingStub.class);
        assertThat(futureStub).isInstanceOf(TestFutureStub.class);
        assertThat(asyncStub.getChannel()).isSameAs(replacementChannel);
        assertThat(asyncStub.getCallOptions().getDeadline()).isNotNull();
        assertThat(asyncStub.getCallOptions().getExecutor()).isSameAs(executor);
        assertThat(asyncStub.getCallOptions().getCompressor()).isEqualTo("gzip");
        assertThat(asyncStub.getCallOptions().getOption(CUSTOM_OPTION)).isEqualTo("custom-value");
        assertThat(asyncStub.getCallOptions().getCredentials()).isSameAs(credentials);
        assertThat(asyncStub.getCallOptions().isWaitForReady()).isTrue();
        assertThat(asyncStub.getCallOptions().getMaxInboundMessageSize()).isEqualTo(1024);
        assertThat(asyncStub.getCallOptions().getMaxOutboundMessageSize()).isEqualTo(2048);
        assertThat(asyncStub.getCallOptions().getOnReadyThreshold()).isEqualTo(512);
        assertThat(blockingStub.getCallOptions().getOption(CUSTOM_OPTION)).isEqualTo("blocking");
        assertThat(deadline.isExpired()).isFalse();
    }

    @Test
    void asyncUnaryAndServerStreamingCallsDeliverResponsesAndStatus() {
        RecordingClientCall unaryCall = new RecordingClientCall(response("unary-response"));
        RecordingStreamObserver<String> unaryObserver = new RecordingStreamObserver<>();
        RecordingClientCall streamingCall = new RecordingClientCall(
                responses(request -> List.of(request + "-1", request + "-2", request + "-3")));
        RecordingStreamObserver<String> streamingObserver = new RecordingStreamObserver<>();

        ClientCalls.asyncUnaryCall(unaryCall, "unary-request", unaryObserver);
        ClientCalls.asyncServerStreamingCall(streamingCall, "stream", streamingObserver);

        assertThat(unaryCall.sentMessages()).containsExactly("unary-request");
        assertThat(unaryCall.halfClosed()).isTrue();
        assertThat(unaryObserver.messages()).containsExactly("unary-response");
        assertThat(unaryObserver.completed()).isTrue();
        assertThat(streamingCall.sentMessages()).containsExactly("stream");
        assertThat(streamingObserver.messages()).containsExactly("stream-1", "stream-2", "stream-3");
        assertThat(streamingObserver.completed()).isTrue();
    }

    @Test
    void asyncClientAndBidirectionalStreamingCallsSupportFlowControlCallbacks() {
        RecordingClientCall clientStreamingCall = new RecordingClientCall(
                responses(request -> List.of("collected:" + request)), true);
        RecordingStreamObserver<String> clientStreamingResponses = new RecordingStreamObserver<>();
        StreamObserver<String> clientRequests = ClientCalls.asyncClientStreamingCall(
                clientStreamingCall, clientStreamingResponses);
        RecordingClientCall bidiCall = new RecordingClientCall(
                responses(request -> List.of("ack:" + request)));
        RecordingClientResponseObserver bidiResponses = new RecordingClientResponseObserver();

        StreamObserver<String> bidiRequests = ClientCalls.asyncBidiStreamingCall(bidiCall, bidiResponses);
        clientRequests.onNext("one");
        clientRequests.onNext("two");
        clientRequests.onCompleted();
        bidiRequests.onNext("left");
        bidiRequests.onNext("right");
        bidiRequests.onCompleted();

        assertThat(clientStreamingCall.sentMessages()).containsExactly("one", "two");
        assertThat(clientStreamingResponses.messages()).containsExactly("collected:one,two");
        assertThat(clientStreamingResponses.completed()).isTrue();
        assertThat(bidiResponses.beforeStartCalled()).isTrue();
        assertThat(bidiResponses.messages()).containsExactly("ack:left", "ack:right");
        assertThat(bidiResponses.completed()).isTrue();
        assertThat(bidiResponses.requestObserver().isReady()).isTrue();
        assertThat(bidiCall.messageCompression()).isTrue();
    }

    @Test
    void blockingAndFutureClientCallsReturnValuesAndPropagateStatuses() throws Exception {
        RecordingChannel unaryChannel = new RecordingChannel(response("blocking-response"));
        RecordingChannel streamingChannel = new RecordingChannel(
                responses(request -> List.of(request + "-A", request + "-B")));
        RecordingClientCall futureCall = new RecordingClientCall(response("future-response"));
        RecordingClientCall failingCall = new RecordingClientCall(
                failure(Status.INVALID_ARGUMENT.withDescription("bad request")));

        String blockingResponse = ClientCalls.blockingUnaryCall(
                unaryChannel, UNARY_METHOD, CallOptions.DEFAULT, "blocking-request");
        Iterator<String> stream = ClientCalls.blockingServerStreamingCall(
                streamingChannel, SERVER_STREAMING_METHOD, CallOptions.DEFAULT, "streaming-request");
        ListenableFuture<String> future = ClientCalls.futureUnaryCall(futureCall, "future-request");

        assertThat(blockingResponse).isEqualTo("blocking-response");
        List<String> streamedResponses = new ArrayList<>();
        stream.forEachRemaining(streamedResponses::add);
        assertThat(streamedResponses).containsExactly("streaming-request-A", "streaming-request-B");
        assertThat(future.get(5, TimeUnit.SECONDS)).isEqualTo("future-response");
        assertThatThrownBy(() -> ClientCalls.blockingUnaryCall(failingCall, "invalid"))
                .isInstanceOf(StatusRuntimeException.class)
                .hasMessageContaining("INVALID_ARGUMENT")
                .hasMessageContaining("bad request");
    }

    @Test
    void blockingV2CallsReadWriteHalfCloseAndCancel() throws Exception {
        RecordingChannel streamingChannel = new RecordingChannel(responses(request -> List.of("server:" + request)));
        BlockingClientCall<String, String> bidiCall = ClientCalls.blockingBidiStreamingCall(
                streamingChannel, BIDI_STREAMING_METHOD, CallOptions.DEFAULT);
        RecordingClientCall rawBidiCall = streamingChannel.lastCall();
        RecordingChannel serverStreamingChannel = new RecordingChannel(
                responses(request -> List.of(request + "-1", request + "-2")), true);
        BlockingClientCall<String, String> serverStreamingCall = ClientCalls.blockingV2ServerStreamingCall(
                serverStreamingChannel, SERVER_STREAMING_METHOD, CallOptions.DEFAULT, "initial");

        assertThat(bidiCall.write("alpha", 5, TimeUnit.SECONDS)).isTrue();
        assertThat(bidiCall.read(5, TimeUnit.SECONDS)).isEqualTo("server:alpha");
        bidiCall.halfClose();
        bidiCall.cancel("done", null);
        assertThat(rawBidiCall.halfClosed()).isTrue();
        assertThat(rawBidiCall.cancelMessage()).isEqualTo("done");
        assertThat(serverStreamingCall.read(5, TimeUnit.SECONDS)).isEqualTo("initial-1");
        assertThat(serverStreamingCall.read(5, TimeUnit.SECONDS)).isEqualTo("initial-2");
        assertThat(serverStreamingCall.read(5, TimeUnit.SECONDS)).isNull();
    }

    @Test
    void metadataInterceptorsAttachCaptureAndInjectMetadata() {
        Metadata fixedHeaders = new Metadata();
        fixedHeaders.put(HEADER_KEY, "attached-client-header");
        AtomicReference<Metadata> capturedHeaders = new AtomicReference<>();
        AtomicReference<Metadata> capturedTrailers = new AtomicReference<>();
        ClientInterceptor attachHeaders = MetadataUtils.newAttachHeadersInterceptor(fixedHeaders);
        ClientInterceptor captureMetadata = MetadataUtils.newCaptureMetadataInterceptor(
                capturedHeaders, capturedTrailers);
        RecordingChannel channel = new RecordingChannel(response("with-metadata"));
        Channel interceptedChannel = ClientInterceptors.intercept(channel, captureMetadata, attachHeaders);
        RecordingStreamObserver<String> observer = new RecordingStreamObserver<>();
        Metadata serverMetadata = new Metadata();
        serverMetadata.put(HEADER_KEY, "attached-server-header");
        ServerInterceptor serverInterceptor = MetadataUtils.newAttachMetadataServerInterceptor(serverMetadata);
        ServerServiceDefinition interceptedService = ServerInterceptors.intercept(
                ServerServiceDefinition.builder(SERVICE_NAME)
                        .addMethod(UNARY_METHOD, ServerCalls.asyncUnaryCall(
                                (request, responseObserver) -> responseObserver.onCompleted()))
                        .build(), serverInterceptor);

        ClientCalls.asyncUnaryCall(interceptedChannel.newCall(UNARY_METHOD, CallOptions.DEFAULT), "request", observer);

        assertThat(channel.lastCall().startHeaders().get(HEADER_KEY)).isEqualTo("attached-client-header");
        assertThat(capturedHeaders.get().get(HEADER_KEY)).isEqualTo("response-header");
        assertThat(capturedTrailers.get().get(TRAILER_KEY)).isEqualTo("response-trailer");
        assertThat(interceptedService.getMethods()).hasSize(1);
    }

    @Test
    void serverCallsHandleUnaryAndStreamingMethods() {
        RecordingServerCall unaryCall = new RecordingServerCall(UNARY_METHOD);
        ServerCall.Listener<String> unaryListener = ServerCalls.asyncUnaryCall(
                (String request, StreamObserver<String> responseObserver) -> {
                    ServerCallStreamObserver<String> serverObserver =
                            (ServerCallStreamObserver<String>) responseObserver;
                    serverObserver.setCompression("gzip");
                    serverObserver.setMessageCompression(true);
                    serverObserver.setOnReadyThreshold(128);
                    serverObserver.request(1);
                    responseObserver.onNext("hello " + request);
                    responseObserver.onCompleted();
                }).startCall(unaryCall, new Metadata());
        RecordingServerCall serverStreamingCall = new RecordingServerCall(SERVER_STREAMING_METHOD);
        ServerCall.Listener<String> serverStreamingListener = ServerCalls.asyncServerStreamingCall(
                (String request, StreamObserver<String> responseObserver) -> {
                    responseObserver.onNext(request + "-first");
                    responseObserver.onNext(request + "-second");
                    responseObserver.onCompleted();
                }).startCall(serverStreamingCall, new Metadata());

        unaryListener.onMessage("native");
        unaryListener.onHalfClose();
        serverStreamingListener.onMessage("stream");
        serverStreamingListener.onHalfClose();

        assertThat(unaryCall.messages()).containsExactly("hello native");
        assertThat(unaryCall.status().getCode()).isEqualTo(Status.Code.OK);
        assertThat(unaryCall.compression()).isEqualTo("gzip");
        assertThat(unaryCall.messageCompression()).isTrue();
        assertThat(unaryCall.onReadyThreshold()).isEqualTo(128);
        assertThat(unaryCall.requested()).isGreaterThanOrEqualTo(2);
        assertThat(serverStreamingCall.messages()).containsExactly("stream-first", "stream-second");
        assertThat(serverStreamingCall.status().getCode()).isEqualTo(Status.Code.OK);
    }

    @Test
    void serverCallsHandleClientAndBidirectionalStreamingMethods() {
        RecordingServerCall clientStreamingCall = new RecordingServerCall(CLIENT_STREAMING_METHOD);
        List<String> collected = new ArrayList<>();
        ServerCall.Listener<String> clientStreamingListener = ServerCalls.<String, String>asyncClientStreamingCall(
                responseObserver -> new StreamObserver<String>() {
                    @Override
                    public void onNext(String value) {
                        collected.add(value);
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        responseObserver.onError(throwable);
                    }

                    @Override
                    public void onCompleted() {
                        responseObserver.onNext(String.join(",", collected));
                        responseObserver.onCompleted();
                    }
                }).startCall(clientStreamingCall, new Metadata());
        RecordingServerCall bidiCall = new RecordingServerCall(BIDI_STREAMING_METHOD);
        AtomicBoolean closeHandlerCalled = new AtomicBoolean();
        AtomicBoolean readyHandlerCalled = new AtomicBoolean();
        ServerCall.Listener<String> bidiListener = ServerCalls.<String, String>asyncBidiStreamingCall(responseObserver -> {
            ServerCallStreamObserver<String> serverObserver = (ServerCallStreamObserver<String>) responseObserver;
            serverObserver.setOnCloseHandler(() -> closeHandlerCalled.set(true));
            serverObserver.setOnReadyHandler(() -> readyHandlerCalled.set(true));
            return new StreamObserver<String>() {
                @Override
                public void onNext(String value) {
                    responseObserver.onNext("ack:" + value);
                }

                @Override
                public void onError(Throwable throwable) {
                    responseObserver.onError(throwable);
                }

                @Override
                public void onCompleted() {
                    responseObserver.onCompleted();
                }
            };
        }).startCall(bidiCall, new Metadata());

        clientStreamingListener.onMessage("one");
        clientStreamingListener.onMessage("two");
        clientStreamingListener.onHalfClose();
        bidiListener.onReady();
        bidiListener.onMessage("left");
        bidiListener.onMessage("right");
        bidiListener.onHalfClose();
        bidiListener.onComplete();
        AtomicBoolean cancelHandlerCalled = new AtomicBoolean();
        ServerCall.Listener<String> cancelListener = ServerCalls.<String, String>asyncBidiStreamingCall(responseObserver -> {
            ServerCallStreamObserver<String> serverObserver = (ServerCallStreamObserver<String>) responseObserver;
            serverObserver.setOnCancelHandler(() -> cancelHandlerCalled.set(true));
            return new RecordingStreamObserver<String>();
        }).startCall(new RecordingServerCall(BIDI_STREAMING_METHOD), new Metadata());
        cancelListener.onCancel();

        assertThat(clientStreamingCall.messages()).containsExactly("one,two");
        assertThat(clientStreamingCall.status().getCode()).isEqualTo(Status.Code.OK);
        assertThat(bidiCall.messages()).containsExactly("ack:left", "ack:right");
        assertThat(bidiCall.status().getCode()).isEqualTo(Status.Code.OK);
        assertThat(readyHandlerCalled).isTrue();
        assertThat(cancelHandlerCalled).isTrue();
        assertThat(closeHandlerCalled).isTrue();
    }

    @Test
    void serverCallsReportUnimplementedMethods() {
        RecordingStreamObserver<String> unaryObserver = new RecordingStreamObserver<>();
        RecordingStreamObserver<String> streamingObserver = new RecordingStreamObserver<>();

        ServerCalls.asyncUnimplementedUnaryCall(UNARY_METHOD, unaryObserver);
        StreamObserver<String> requestObserver = ServerCalls.asyncUnimplementedStreamingCall(
                BIDI_STREAMING_METHOD, streamingObserver);
        requestObserver.onNext("ignored");
        requestObserver.onCompleted();

        assertThat(unaryObserver.error()).isInstanceOf(StatusRuntimeException.class);
        assertThat(Status.fromThrowable(unaryObserver.error()).getCode()).isEqualTo(Status.Code.UNIMPLEMENTED);
        assertThat(streamingObserver.error()).isInstanceOf(StatusRuntimeException.class);
        assertThat(Status.fromThrowable(streamingObserver.error()).getCode()).isEqualTo(Status.Code.UNIMPLEMENTED);
    }

    @Test
    void streamObserversCopyWithFlowControlOnlyWhenCallIsReady() {
        RecordingCallStreamObserver<String> callObserver = new RecordingCallStreamObserver<>();
        List<String> values = List.of("alpha", "bravo", "charlie");

        StreamObservers.copyWithFlowControl(values, callObserver);

        assertThat(callObserver.messages()).isEmpty();
        assertThat(callObserver.completed()).isFalse();
        callObserver.runReadyHandler();
        assertThat(callObserver.messages()).containsExactly("alpha");
        callObserver.setReady(true);
        callObserver.runReadyHandler();
        assertThat(callObserver.messages()).containsExactly("alpha", "bravo", "charlie");
        assertThat(callObserver.completed()).isTrue();
    }

    private static MethodDescriptor<String, String> method(String methodName, MethodDescriptor.MethodType methodType) {
        return MethodDescriptor.<String, String>newBuilder()
                .setType(methodType)
                .setFullMethodName(MethodDescriptor.generateFullMethodName(SERVICE_NAME, methodName))
                .setRequestMarshaller(STRING_MARSHALLER)
                .setResponseMarshaller(STRING_MARSHALLER)
                .build();
    }

    private static Function<String, List<String>> response(String response) {
        return request -> List.of(response);
    }

    private static Function<String, List<String>> responses(Function<String, List<String>> responses) {
        return responses;
    }

    private static Function<String, List<String>> failure(Status status) {
        return request -> {
            throw status.asRuntimeException();
        };
    }

    private static final class TestAsyncStub extends AbstractAsyncStub<TestAsyncStub> {
        private TestAsyncStub(Channel channel, CallOptions callOptions) {
            super(channel, callOptions);
        }

        @Override
        protected TestAsyncStub build(Channel channel, CallOptions callOptions) {
            return new TestAsyncStub(channel, callOptions);
        }
    }

    private static final class TestBlockingStub extends AbstractBlockingStub<TestBlockingStub> {
        private TestBlockingStub(Channel channel, CallOptions callOptions) {
            super(channel, callOptions);
        }

        @Override
        protected TestBlockingStub build(Channel channel, CallOptions callOptions) {
            return new TestBlockingStub(channel, callOptions);
        }
    }

    private static final class TestFutureStub extends AbstractFutureStub<TestFutureStub> {
        private TestFutureStub(Channel channel, CallOptions callOptions) {
            super(channel, callOptions);
        }

        @Override
        protected TestFutureStub build(Channel channel, CallOptions callOptions) {
            return new TestFutureStub(channel, callOptions);
        }
    }

    private static final class NoopCallCredentials extends CallCredentials {
        @Override
        public void applyRequestMetadata(RequestInfo requestInfo, Executor appExecutor, MetadataApplier applier) {
            applier.apply(new Metadata());
        }
    }

    private static final class RecordingChannel extends Channel {
        private final Function<String, List<String>> responder;
        private final boolean respondOnRequest;
        private RecordingClientCall lastCall;

        private RecordingChannel(Function<String, List<String>> responder) {
            this(responder, false);
        }

        private RecordingChannel(Function<String, List<String>> responder, boolean respondOnRequest) {
            this.responder = responder;
            this.respondOnRequest = respondOnRequest;
        }

        @Override
        public <ReqT, RespT> ClientCall<ReqT, RespT> newCall(
                MethodDescriptor<ReqT, RespT> methodDescriptor, CallOptions callOptions) {
            lastCall = new RecordingClientCall(responder, false, respondOnRequest);
            @SuppressWarnings("unchecked")
            ClientCall<ReqT, RespT> typedCall = (ClientCall<ReqT, RespT>) lastCall;
            return typedCall;
        }

        @Override
        public String authority() {
            return "localhost";
        }

        private RecordingClientCall lastCall() {
            return lastCall;
        }
    }

    private static final class RecordingClientCall extends ClientCall<String, String> {
        private final Function<String, List<String>> responder;
        private final boolean respondOnHalfClose;
        private final boolean respondOnRequest;
        private final List<String> sentMessages = new ArrayList<>();
        private final Queue<String> pendingResponses = new ArrayDeque<>();
        private Listener<String> listener;
        private Metadata startHeaders;
        private boolean halfClosed;
        private boolean messageCompression;
        private boolean closed;
        private int requestedResponses;
        private String cancelMessage;

        private RecordingClientCall(Function<String, List<String>> responder) {
            this(responder, false);
        }

        private RecordingClientCall(Function<String, List<String>> responder, boolean respondOnHalfClose) {
            this(responder, respondOnHalfClose, false);
        }

        private RecordingClientCall(Function<String, List<String>> responder, boolean respondOnHalfClose,
                boolean respondOnRequest) {
            this.responder = responder;
            this.respondOnHalfClose = respondOnHalfClose;
            this.respondOnRequest = respondOnRequest;
        }

        @Override
        public void start(Listener<String> responseListener, Metadata headers) {
            listener = responseListener;
            startHeaders = headers;
            Metadata responseHeaders = new Metadata();
            responseHeaders.put(HEADER_KEY, "response-header");
            listener.onHeaders(responseHeaders);
        }

        @Override
        public void request(int numMessages) {
            assertThat(numMessages).isPositive();
            if (respondOnRequest) {
                requestedResponses += numMessages;
                drainRequestedResponses();
            }
        }

        @Override
        public void cancel(String message, Throwable cause) {
            cancelMessage = message;
        }

        @Override
        public void halfClose() {
            halfClosed = true;
            if (respondOnRequest) {
                if (pendingResponses.isEmpty()) {
                    close(Status.OK);
                }
                return;
            }
            if (respondOnHalfClose) {
                deliverResponse(String.join(",", sentMessages));
            }
            close(Status.OK);
        }

        @Override
        public void sendMessage(String message) {
            sentMessages.add(message);
            if (respondOnRequest) {
                enqueueResponses(message);
                drainRequestedResponses();
            } else if (!respondOnHalfClose) {
                deliverResponse(message);
            }
        }

        private void enqueueResponses(String request) {
            try {
                pendingResponses.addAll(responder.apply(request));
            } catch (StatusRuntimeException ex) {
                close(ex.getStatus());
            }
        }

        private void drainRequestedResponses() {
            if (halfClosed && pendingResponses.isEmpty()) {
                close(Status.OK);
                return;
            }
            if (requestedResponses > 0 && !pendingResponses.isEmpty()) {
                requestedResponses--;
                listener.onMessage(pendingResponses.remove());
            }
        }

        private void deliverResponse(String request) {
            try {
                for (String response : responder.apply(request)) {
                    listener.onMessage(response);
                }
            } catch (StatusRuntimeException ex) {
                close(ex.getStatus());
            }
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setMessageCompression(boolean enabled) {
            messageCompression = enabled;
        }

        private void close(Status status) {
            if (closed) {
                return;
            }
            closed = true;
            Metadata trailers = new Metadata();
            trailers.put(TRAILER_KEY, "response-trailer");
            listener.onClose(status, trailers);
        }

        private List<String> sentMessages() {
            return List.copyOf(sentMessages);
        }

        private Metadata startHeaders() {
            return startHeaders;
        }

        private boolean halfClosed() {
            return halfClosed;
        }

        private boolean messageCompression() {
            return messageCompression;
        }

        private String cancelMessage() {
            return cancelMessage;
        }
    }

    private static final class RecordingStreamObserver<T> implements StreamObserver<T> {
        private final List<T> messages = new ArrayList<>();
        private Throwable error;
        private boolean completed;

        @Override
        public void onNext(T value) {
            messages.add(value);
        }

        @Override
        public void onError(Throwable throwable) {
            error = throwable;
        }

        @Override
        public void onCompleted() {
            completed = true;
        }

        private List<T> messages() {
            return List.copyOf(messages);
        }

        private Throwable error() {
            return error;
        }

        private boolean completed() {
            return completed;
        }
    }

    private static final class RecordingClientResponseObserver
            implements ClientResponseObserver<String, String> {
        private final List<String> messages = new ArrayList<>();
        private ClientCallStreamObserver<String> requestObserver;
        private boolean completed;

        @Override
        public void beforeStart(ClientCallStreamObserver<String> requestStream) {
            requestObserver = requestStream;
            requestStream.setMessageCompression(true);
            requestStream.setOnReadyHandler(() -> { });
        }

        @Override
        public void onNext(String value) {
            messages.add(value);
        }

        @Override
        public void onError(Throwable throwable) {
            throw new AssertionError("Unexpected client response error", throwable);
        }

        @Override
        public void onCompleted() {
            completed = true;
        }

        private ClientCallStreamObserver<String> requestObserver() {
            return requestObserver;
        }

        private boolean beforeStartCalled() {
            return requestObserver != null;
        }

        private List<String> messages() {
            return List.copyOf(messages);
        }

        private boolean completed() {
            return completed;
        }

    }

    private static final class RecordingServerCall extends ServerCall<String, String> {
        private final MethodDescriptor<String, String> methodDescriptor;
        private final List<String> messages = new ArrayList<>();
        private int requested;
        private Status status;
        private boolean messageCompression;
        private String compression;
        private int onReadyThreshold;

        private RecordingServerCall(MethodDescriptor<String, String> methodDescriptor) {
            this.methodDescriptor = methodDescriptor;
        }

        @Override
        public void request(int numMessages) {
            requested += numMessages;
        }

        @Override
        public void sendHeaders(Metadata headers) {
        }

        @Override
        public void sendMessage(String message) {
            messages.add(message);
        }

        @Override
        public void close(Status closeStatus, Metadata trailers) {
            status = closeStatus;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setMessageCompression(boolean enabled) {
            messageCompression = enabled;
        }

        @Override
        public void setCompression(String compressor) {
            compression = compressor;
        }

        @Override
        public void setOnReadyThreshold(int numBytes) {
            onReadyThreshold = numBytes;
        }

        @Override
        public MethodDescriptor<String, String> getMethodDescriptor() {
            return methodDescriptor;
        }

        @Override
        public Attributes getAttributes() {
            return Attributes.EMPTY;
        }

        private List<String> messages() {
            return List.copyOf(messages);
        }

        private int requested() {
            return requested;
        }

        private Status status() {
            return status;
        }

        private boolean messageCompression() {
            return messageCompression;
        }

        private String compression() {
            return compression;
        }

        private int onReadyThreshold() {
            return onReadyThreshold;
        }
    }

    private static final class RecordingCallStreamObserver<T> extends CallStreamObserver<T> {
        private final List<T> messages = new ArrayList<>();
        private final Queue<Boolean> readiness = new ArrayDeque<>(List.of(true, false, true));
        private Runnable onReadyHandler;
        private boolean completed;

        @Override
        public void onNext(T value) {
            messages.add(value);
        }

        @Override
        public void onError(Throwable throwable) {
            throw new AssertionError("Unexpected flow-control error", throwable);
        }

        @Override
        public void onCompleted() {
            completed = true;
        }

        @Override
        public boolean isReady() {
            return !readiness.isEmpty() && readiness.remove();
        }

        @Override
        public void setOnReadyHandler(Runnable handler) {
            onReadyHandler = handler;
        }

        @Override
        public void disableAutoInboundFlowControl() {
        }

        @Override
        public void request(int count) {
        }

        @Override
        public void setMessageCompression(boolean enabled) {
        }

        private void setReady(boolean ready) {
            readiness.add(ready);
        }

        private void runReadyHandler() {
            onReadyHandler.run();
        }

        private List<T> messages() {
            return List.copyOf(messages);
        }

        private boolean completed() {
            return completed;
        }
    }
}
