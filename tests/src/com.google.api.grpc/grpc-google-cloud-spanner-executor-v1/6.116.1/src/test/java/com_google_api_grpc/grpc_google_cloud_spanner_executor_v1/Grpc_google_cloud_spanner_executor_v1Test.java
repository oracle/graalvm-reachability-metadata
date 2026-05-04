/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_api_grpc.grpc_google_cloud_spanner_executor_v1;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.spanner.executor.v1.QueryAction;
import com.google.spanner.executor.v1.QueryResult;
import com.google.spanner.executor.v1.SpannerAction;
import com.google.spanner.executor.v1.SpannerActionOutcome;
import com.google.spanner.executor.v1.SpannerAsyncActionRequest;
import com.google.spanner.executor.v1.SpannerAsyncActionResponse;
import com.google.spanner.executor.v1.SpannerExecutorProxyGrpc;
import com.google.spanner.executor.v1.Value;
import com.google.spanner.executor.v1.ValueList;
import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors;
import com.google.rpc.Status;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerMethodDefinition;
import io.grpc.ServerServiceDefinition;
import io.grpc.ServiceDescriptor;
import io.grpc.protobuf.ProtoMethodDescriptorSupplier;
import io.grpc.protobuf.ProtoServiceDescriptorSupplier;
import io.grpc.stub.BlockingClientCall;
import io.grpc.stub.StreamObserver;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

public class Grpc_google_cloud_spanner_executor_v1Test {
    private static final int ACTION_ID = 17;

    @Test
    void methodDescriptorDefinesBidiStreamingExecutorContract() {
        MethodDescriptor<SpannerAsyncActionRequest, SpannerAsyncActionResponse> method =
                SpannerExecutorProxyGrpc.getExecuteActionAsyncMethod();
        SpannerAsyncActionRequest request = request(ACTION_ID, "SELECT 1");
        SpannerAsyncActionResponse response = response(ACTION_ID, 0, "OK");

        assertThat(SpannerExecutorProxyGrpc.SERVICE_NAME).isEqualTo("google.spanner.executor.v1.SpannerExecutorProxy");
        assertThat(method.getType()).isEqualTo(MethodDescriptor.MethodType.BIDI_STREAMING);
        assertThat(method.getServiceName()).isEqualTo(SpannerExecutorProxyGrpc.SERVICE_NAME);
        assertThat(method.getBareMethodName()).isEqualTo("ExecuteActionAsync");
        assertThat(method.getFullMethodName())
                .isEqualTo("google.spanner.executor.v1.SpannerExecutorProxy/ExecuteActionAsync");
        assertThat(method.isSampledToLocalTracing()).isTrue();

        assertThat(parseRequest(method, request)).isEqualTo(request);
        assertThat(parseResponse(method, response)).isEqualTo(response);
    }

    @Test
    void serviceDescriptorAndBoundServiceExposeSingleExecutorMethod() {
        ServiceDescriptor serviceDescriptor = SpannerExecutorProxyGrpc.getServiceDescriptor();
        SpannerExecutorProxyGrpc.AsyncService service = new SpannerExecutorProxyGrpc.AsyncService() {
            @Override
            public StreamObserver<SpannerAsyncActionRequest> executeActionAsync(
                    StreamObserver<SpannerAsyncActionResponse> responseObserver) {
                return new StreamObserver<>() {
                    @Override
                    public void onNext(SpannerAsyncActionRequest request) {
                        responseObserver.onNext(response(request.getActionId(), 0, "dispatched"));
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
            }
        };
        ServerServiceDefinition serviceDefinition = SpannerExecutorProxyGrpc.bindService(service);

        assertThat(serviceDescriptor.getName()).isEqualTo(SpannerExecutorProxyGrpc.SERVICE_NAME);
        assertThat(serviceDescriptor.getMethods())
                .singleElement()
                .extracting(MethodDescriptor::getFullMethodName)
                .isEqualTo(SpannerExecutorProxyGrpc.getExecuteActionAsyncMethod().getFullMethodName());
        assertThat(serviceDefinition.getServiceDescriptor().getName()).isEqualTo(serviceDescriptor.getName());
        String fullMethodName = SpannerExecutorProxyGrpc.getExecuteActionAsyncMethod().getFullMethodName();
        assertThat(serviceDefinition.getMethod(fullMethodName)).isNotNull();
    }

    @Test
    void protobufSchemaDescriptorsExposeExecutorServiceAndStreamingMethod() {
        Object serviceSchemaDescriptor = SpannerExecutorProxyGrpc.getServiceDescriptor().getSchemaDescriptor();
        assertThat(serviceSchemaDescriptor).isInstanceOf(ProtoServiceDescriptorSupplier.class);
        ProtoServiceDescriptorSupplier serviceSupplier = (ProtoServiceDescriptorSupplier) serviceSchemaDescriptor;
        Descriptors.FileDescriptor fileDescriptor = serviceSupplier.getFileDescriptor();
        Descriptors.ServiceDescriptor protoService = serviceSupplier.getServiceDescriptor();

        Object methodSchemaDescriptor = SpannerExecutorProxyGrpc.getExecuteActionAsyncMethod().getSchemaDescriptor();
        assertThat(methodSchemaDescriptor).isInstanceOf(ProtoMethodDescriptorSupplier.class);
        ProtoMethodDescriptorSupplier methodSupplier = (ProtoMethodDescriptorSupplier) methodSchemaDescriptor;
        Descriptors.MethodDescriptor protoMethod = methodSupplier.getMethodDescriptor();

        assertThat(fileDescriptor.getPackage()).isEqualTo("google.spanner.executor.v1");
        assertThat(fileDescriptor.getServices()).contains(protoService);
        assertThat(protoService.getFullName()).isEqualTo(SpannerExecutorProxyGrpc.SERVICE_NAME);
        assertThat(protoMethod.getService()).isEqualTo(protoService);
        assertThat(protoMethod.getName()).isEqualTo("ExecuteActionAsync");
        assertThat(protoMethod.isClientStreaming()).isTrue();
        assertThat(protoMethod.isServerStreaming()).isTrue();
        assertThat(protoMethod.getInputType()).isEqualTo(SpannerAsyncActionRequest.getDescriptor());
        assertThat(protoMethod.getOutputType()).isEqualTo(SpannerAsyncActionResponse.getDescriptor());
    }

    @Test
    void boundAsyncServiceDispatchesStreamingMessagesThroughGrpcHandler() {
        SpannerExecutorProxyGrpc.AsyncService service = new SpannerExecutorProxyGrpc.AsyncService() {
            @Override
            public StreamObserver<SpannerAsyncActionRequest> executeActionAsync(
                    StreamObserver<SpannerAsyncActionResponse> responseObserver) {
                return new StreamObserver<>() {
                    @Override
                    public void onNext(SpannerAsyncActionRequest request) {
                        SpannerAction action = request.getAction();
                        responseObserver.onNext(response(
                                request.getActionId(), 0, "sql=" + action.getQuery().getSql()));
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
            }
        };
        ServerMethodDefinition<SpannerAsyncActionRequest, SpannerAsyncActionResponse> methodDefinition =
                getExecuteActionMethodDefinition(SpannerExecutorProxyGrpc.bindService(service));
        RecordingServerCall serverCall = new RecordingServerCall(methodDefinition.getMethodDescriptor());

        ServerCallHandler<SpannerAsyncActionRequest, SpannerAsyncActionResponse> handler =
                methodDefinition.getServerCallHandler();
        ServerCall.Listener<SpannerAsyncActionRequest> listener = handler.startCall(serverCall, new Metadata());
        listener.onMessage(request(ACTION_ID, "SELECT 'grpc'"));
        listener.onHalfClose();
        listener.onComplete();

        assertThat(serverCall.sentHeaders).isTrue();
        assertThat(serverCall.messages)
                .singleElement()
                .satisfies(response -> {
                    assertThat(response.getActionId()).isEqualTo(ACTION_ID);
                    assertThat(response.getOutcome().getStatus().getMessage()).isEqualTo("sql=SELECT 'grpc'");
                });
        assertThat(serverCall.closedStatus.isOk()).isTrue();
    }

    @Test
    void implBaseDefaultServiceReportsUnimplementedStreamingMethod() {
        SpannerExecutorProxyGrpc.SpannerExecutorProxyImplBase service =
                new SpannerExecutorProxyGrpc.SpannerExecutorProxyImplBase() { };
        ServerMethodDefinition<SpannerAsyncActionRequest, SpannerAsyncActionResponse> methodDefinition =
                getExecuteActionMethodDefinition(service.bindService());
        RecordingServerCall serverCall = new RecordingServerCall(methodDefinition.getMethodDescriptor());

        methodDefinition.getServerCallHandler().startCall(serverCall, new Metadata());

        assertThat(serverCall.closedStatus).isNotNull();
        assertThat(serverCall.closedStatus.getCode()).isEqualTo(io.grpc.Status.Code.UNIMPLEMENTED);
        assertThat(serverCall.messages).isEmpty();
    }

    @Test
    void asyncStubSendsRequestWithConfiguredCallOptionsAndReceivesResponse() throws Exception {
        SpannerAsyncActionResponse expectedResponse = response(ACTION_ID, 0, "async-ok");
        RecordingChannel channel = new RecordingChannel(request -> expectedResponse);
        List<SpannerAsyncActionResponse> responses = new ArrayList<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch completed = new CountDownLatch(1);
        StreamObserver<SpannerAsyncActionResponse> responseObserver = new StreamObserver<>() {
            @Override
            public void onNext(SpannerAsyncActionResponse response) {
                responses.add(response);
            }

            @Override
            public void onError(Throwable throwable) {
                error.set(throwable);
                completed.countDown();
            }

            @Override
            public void onCompleted() {
                completed.countDown();
            }
        };

        StreamObserver<SpannerAsyncActionRequest> requestObserver = SpannerExecutorProxyGrpc.newStub(channel)
                .withCompression("gzip")
                .withDeadlineAfter(5, TimeUnit.SECONDS)
                .executeActionAsync(responseObserver);
        SpannerAsyncActionRequest request = request(ACTION_ID, "SELECT @value");
        requestObserver.onNext(request);
        requestObserver.onCompleted();

        assertThat(completed.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(error.get()).isNull();
        assertThat(channel.method.getFullMethodName())
                .isEqualTo(SpannerExecutorProxyGrpc.getExecuteActionAsyncMethod().getFullMethodName());
        assertThat(channel.callOptions.getCompressor()).isEqualTo("gzip");
        assertThat(channel.callOptions.getDeadline()).isNotNull();
        assertThat(channel.lastCall.messages).containsExactly(request);
        assertThat(responses).containsExactly(expectedResponse);
    }

    @Test
    void blockingV2StubUsesBidiStreamingCallWithBoundedReadsAndWrites() throws Exception {
        SpannerAsyncActionResponse expectedResponse = response(ACTION_ID, 0, "blocking-ok");
        RecordingChannel channel = new RecordingChannel(request -> expectedResponse);
        BlockingClientCall<SpannerAsyncActionRequest, SpannerAsyncActionResponse> call =
                SpannerExecutorProxyGrpc.newBlockingV2Stub(channel)
                        .withWaitForReady()
                        .executeActionAsync();
        SpannerAsyncActionRequest request = request(ACTION_ID, "SELECT TRUE");

        assertThat(call.write(request, 1, TimeUnit.SECONDS)).isTrue();
        SpannerAsyncActionResponse actualResponse = call.read(1, TimeUnit.SECONDS);
        call.halfClose();

        assertThat(channel.callOptions.isWaitForReady()).isTrue();
        assertThat(channel.lastCall.messages).containsExactly(request);
        assertThat(actualResponse).isEqualTo(expectedResponse);
    }

    private static SpannerAsyncActionRequest parseRequest(
            MethodDescriptor<SpannerAsyncActionRequest, SpannerAsyncActionResponse> method,
            SpannerAsyncActionRequest request) {
        try (InputStream stream = method.streamRequest(request)) {
            return method.parseRequest(stream);
        } catch (Exception exception) {
            throw new AssertionError("Failed to parse marshalled request", exception);
        }
    }

    private static SpannerAsyncActionResponse parseResponse(
            MethodDescriptor<SpannerAsyncActionRequest, SpannerAsyncActionResponse> method,
            SpannerAsyncActionResponse response) {
        try (InputStream stream = method.streamResponse(response)) {
            return method.parseResponse(stream);
        } catch (Exception exception) {
            throw new AssertionError("Failed to parse marshalled response", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private static ServerMethodDefinition<SpannerAsyncActionRequest, SpannerAsyncActionResponse>
            getExecuteActionMethodDefinition(ServerServiceDefinition serviceDefinition) {
        return (ServerMethodDefinition<SpannerAsyncActionRequest, SpannerAsyncActionResponse>) serviceDefinition
                .getMethod(SpannerExecutorProxyGrpc.getExecuteActionAsyncMethod().getFullMethodName());
    }

    private static SpannerAsyncActionRequest request(int actionId, String sql) {
        Value parameterValue = Value.newBuilder().setStringValue("native-image").build();
        QueryAction.Parameter parameter = QueryAction.Parameter.newBuilder()
                .setName("value")
                .setValue(parameterValue)
                .build();
        SpannerAction action = SpannerAction.newBuilder()
                .setDatabasePath("projects/p/instances/i/databases/d")
                .setQuery(QueryAction.newBuilder().setSql(sql).addParams(parameter))
                .build();
        return SpannerAsyncActionRequest.newBuilder()
                .setActionId(actionId)
                .setAction(action)
                .build();
    }

    private static SpannerAsyncActionResponse response(int actionId, int code, String message) {
        ValueList row = ValueList.newBuilder()
                .addValue(Value.newBuilder().setStringValue(message))
                .addValue(Value.newBuilder().setBytesValue(ByteString.copyFromUtf8("bytes")))
                .build();
        SpannerActionOutcome outcome = SpannerActionOutcome.newBuilder()
                .setStatus(Status.newBuilder().setCode(code).setMessage(message))
                .setQueryResult(QueryResult.newBuilder().addRow(row))
                .build();
        return SpannerAsyncActionResponse.newBuilder()
                .setActionId(actionId)
                .setOutcome(outcome)
                .build();
    }

    private static final class RecordingChannel extends Channel {
        private final Function<SpannerAsyncActionRequest, SpannerAsyncActionResponse> responseFactory;
        private MethodDescriptor<?, ?> method;
        private CallOptions callOptions;
        private RecordingClientCall lastCall;

        private RecordingChannel(Function<SpannerAsyncActionRequest, SpannerAsyncActionResponse> responseFactory) {
            this.responseFactory = responseFactory;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <RequestT, ResponseT> ClientCall<RequestT, ResponseT> newCall(
                MethodDescriptor<RequestT, ResponseT> methodDescriptor, CallOptions options) {
            method = methodDescriptor;
            callOptions = options;
            lastCall = new RecordingClientCall(responseFactory);
            return (ClientCall<RequestT, ResponseT>) lastCall;
        }

        @Override
        public String authority() {
            return "in-memory-spanner-executor.test";
        }
    }

    private static final class RecordingClientCall
            extends ClientCall<SpannerAsyncActionRequest, SpannerAsyncActionResponse> {
        private final Function<SpannerAsyncActionRequest, SpannerAsyncActionResponse> responseFactory;
        private final List<SpannerAsyncActionRequest> messages = new ArrayList<>();
        private Listener<SpannerAsyncActionResponse> listener;

        private RecordingClientCall(Function<SpannerAsyncActionRequest, SpannerAsyncActionResponse> responseFactory) {
            this.responseFactory = responseFactory;
        }

        @Override
        public void start(Listener<SpannerAsyncActionResponse> responseListener, Metadata headers) {
            listener = responseListener;
            listener.onHeaders(new Metadata());
        }

        @Override
        public void request(int numMessages) {
        }

        @Override
        public void cancel(String message, Throwable cause) {
            listener.onClose(io.grpc.Status.CANCELLED.withDescription(message).withCause(cause), new Metadata());
        }

        @Override
        public void halfClose() {
            listener.onClose(io.grpc.Status.OK, new Metadata());
        }

        @Override
        public void sendMessage(SpannerAsyncActionRequest message) {
            messages.add(message);
            listener.onMessage(responseFactory.apply(message));
        }

        @Override
        public boolean isReady() {
            return true;
        }
    }

    private static final class RecordingServerCall
            extends ServerCall<SpannerAsyncActionRequest, SpannerAsyncActionResponse> {
        private final MethodDescriptor<SpannerAsyncActionRequest, SpannerAsyncActionResponse> methodDescriptor;
        private final List<SpannerAsyncActionResponse> messages = new ArrayList<>();
        private boolean sentHeaders;
        private io.grpc.Status closedStatus;

        private RecordingServerCall(
                MethodDescriptor<SpannerAsyncActionRequest, SpannerAsyncActionResponse> methodDescriptor) {
            this.methodDescriptor = methodDescriptor;
        }

        @Override
        public void request(int numMessages) {
        }

        @Override
        public void sendHeaders(Metadata headers) {
            sentHeaders = true;
        }

        @Override
        public void sendMessage(SpannerAsyncActionResponse message) {
            messages.add(message);
        }

        @Override
        public void close(io.grpc.Status status, Metadata trailers) {
            closedStatus = status;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public MethodDescriptor<SpannerAsyncActionRequest, SpannerAsyncActionResponse> getMethodDescriptor() {
            return methodDescriptor;
        }
    }
}
