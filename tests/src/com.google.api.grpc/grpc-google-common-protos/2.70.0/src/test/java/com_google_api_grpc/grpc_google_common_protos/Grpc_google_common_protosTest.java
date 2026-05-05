/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_api_grpc.grpc_google_common_protos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.google.cloud.location.GetLocationRequest;
import com.google.cloud.location.ListLocationsRequest;
import com.google.cloud.location.ListLocationsResponse;
import com.google.cloud.location.Location;
import com.google.cloud.location.LocationsGrpc;
import com.google.cloud.location.LocationsProto;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.longrunning.CancelOperationRequest;
import com.google.longrunning.DeleteOperationRequest;
import com.google.longrunning.GetOperationRequest;
import com.google.longrunning.ListOperationsRequest;
import com.google.longrunning.ListOperationsResponse;
import com.google.longrunning.Operation;
import com.google.longrunning.OperationsGrpc;
import com.google.longrunning.OperationsProto;
import com.google.longrunning.WaitOperationRequest;
import com.google.protobuf.Any;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Duration;
import com.google.protobuf.Empty;
import com.google.protobuf.StringValue;
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
import io.grpc.Status;
import io.grpc.protobuf.ProtoMethodDescriptorSupplier;
import io.grpc.protobuf.ProtoServiceDescriptorSupplier;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

public class Grpc_google_common_protosTest {
    private static final String OPERATIONS_SERVICE = "google.longrunning.Operations";
    private static final String LOCATIONS_SERVICE = "google.cloud.location.Locations";

    @Test
    void operationsMethodDescriptorsExposeUnaryGoogleLongrunningContract() {
        MethodDescriptor<ListOperationsRequest, ListOperationsResponse> listMethod =
                OperationsGrpc.getListOperationsMethod();
        MethodDescriptor<GetOperationRequest, Operation> getMethod = OperationsGrpc.getGetOperationMethod();
        MethodDescriptor<DeleteOperationRequest, Empty> deleteMethod = OperationsGrpc.getDeleteOperationMethod();
        MethodDescriptor<CancelOperationRequest, Empty> cancelMethod = OperationsGrpc.getCancelOperationMethod();
        MethodDescriptor<WaitOperationRequest, Operation> waitMethod = OperationsGrpc.getWaitOperationMethod();

        assertUnaryMethod(listMethod, OPERATIONS_SERVICE, "ListOperations");
        assertUnaryMethod(getMethod, OPERATIONS_SERVICE, "GetOperation");
        assertUnaryMethod(deleteMethod, OPERATIONS_SERVICE, "DeleteOperation");
        assertUnaryMethod(cancelMethod, OPERATIONS_SERVICE, "CancelOperation");
        assertUnaryMethod(waitMethod, OPERATIONS_SERVICE, "WaitOperation");

        ServiceDescriptor serviceDescriptor = OperationsGrpc.getServiceDescriptor();
        assertThat(serviceDescriptor.getName()).isEqualTo(OPERATIONS_SERVICE);
        assertThat(serviceDescriptor.getMethods())
                .containsExactlyInAnyOrder(listMethod, getMethod, deleteMethod, cancelMethod, waitMethod);

        ServerServiceDefinition serviceDefinition = OperationsGrpc.bindService(new OperationsGrpc.AsyncService() { });
        assertThat(serviceDefinition.getServiceDescriptor().getName()).isEqualTo(OPERATIONS_SERVICE);
        assertThat(serviceDefinition.getMethod(listMethod.getFullMethodName())).isNotNull();
        assertThat(serviceDefinition.getMethod(getMethod.getFullMethodName())).isNotNull();
        assertThat(serviceDefinition.getMethod(deleteMethod.getFullMethodName())).isNotNull();
        assertThat(serviceDefinition.getMethod(cancelMethod.getFullMethodName())).isNotNull();
        assertThat(serviceDefinition.getMethod(waitMethod.getFullMethodName())).isNotNull();
    }

    @Test
    void locationsMethodDescriptorsExposeUnaryGoogleCloudLocationContract() {
        MethodDescriptor<ListLocationsRequest, ListLocationsResponse> listMethod =
                LocationsGrpc.getListLocationsMethod();
        MethodDescriptor<GetLocationRequest, Location> getMethod = LocationsGrpc.getGetLocationMethod();

        assertUnaryMethod(listMethod, LOCATIONS_SERVICE, "ListLocations");
        assertUnaryMethod(getMethod, LOCATIONS_SERVICE, "GetLocation");

        ServiceDescriptor serviceDescriptor = LocationsGrpc.getServiceDescriptor();
        assertThat(serviceDescriptor.getName()).isEqualTo(LOCATIONS_SERVICE);
        assertThat(serviceDescriptor.getMethods()).containsExactlyInAnyOrder(listMethod, getMethod);

        ServerServiceDefinition serviceDefinition = LocationsGrpc.bindService(new LocationsGrpc.AsyncService() { });
        assertThat(serviceDefinition.getServiceDescriptor().getName()).isEqualTo(LOCATIONS_SERVICE);
        assertThat(serviceDefinition.getMethod(listMethod.getFullMethodName())).isNotNull();
        assertThat(serviceDefinition.getMethod(getMethod.getFullMethodName())).isNotNull();
    }

    @Test
    void schemaDescriptorsExposeUnderlyingProtobufDefinitions() {
        Descriptors.ServiceDescriptor operationsService = assertServiceSchema(
                OperationsGrpc.getServiceDescriptor(),
                OperationsProto.getDescriptor(),
                OPERATIONS_SERVICE);
        assertMethodSchema(
                OperationsGrpc.getListOperationsMethod(),
                operationsService,
                "ListOperations",
                ListOperationsRequest.getDescriptor(),
                ListOperationsResponse.getDescriptor());
        assertMethodSchema(
                OperationsGrpc.getGetOperationMethod(),
                operationsService,
                "GetOperation",
                GetOperationRequest.getDescriptor(),
                Operation.getDescriptor());
        assertMethodSchema(
                OperationsGrpc.getDeleteOperationMethod(),
                operationsService,
                "DeleteOperation",
                DeleteOperationRequest.getDescriptor(),
                Empty.getDescriptor());
        assertMethodSchema(
                OperationsGrpc.getCancelOperationMethod(),
                operationsService,
                "CancelOperation",
                CancelOperationRequest.getDescriptor(),
                Empty.getDescriptor());
        assertMethodSchema(
                OperationsGrpc.getWaitOperationMethod(),
                operationsService,
                "WaitOperation",
                WaitOperationRequest.getDescriptor(),
                Operation.getDescriptor());

        Descriptors.ServiceDescriptor locationsService = assertServiceSchema(
                LocationsGrpc.getServiceDescriptor(),
                LocationsProto.getDescriptor(),
                LOCATIONS_SERVICE);
        assertMethodSchema(
                LocationsGrpc.getListLocationsMethod(),
                locationsService,
                "ListLocations",
                ListLocationsRequest.getDescriptor(),
                ListLocationsResponse.getDescriptor());
        assertMethodSchema(
                LocationsGrpc.getGetLocationMethod(),
                locationsService,
                "GetLocation",
                GetLocationRequest.getDescriptor(),
                Location.getDescriptor());
    }

    @Test
    void operationsStubsSendExpectedRequestsAndReturnMappedResponses() throws Exception {
        ListOperationsRequest listRequest = ListOperationsRequest.newBuilder()
                .setName("projects/test/locations/global")
                .setFilter("done = true")
                .setPageSize(25)
                .setPageToken("page-token")
                .build();
        Operation completedOperation = operation("operations/completed", true);
        ListOperationsResponse listResponse = ListOperationsResponse.newBuilder()
                .addOperations(completedOperation)
                .setNextPageToken("next-page")
                .build();
        GetOperationRequest getRequest = GetOperationRequest.newBuilder()
                .setName("operations/completed")
                .build();
        Operation runningOperation = operation("operations/running", false);
        DeleteOperationRequest deleteRequest = DeleteOperationRequest.newBuilder()
                .setName("operations/old")
                .build();
        CancelOperationRequest cancelRequest = CancelOperationRequest.newBuilder()
                .setName("operations/running")
                .build();
        WaitOperationRequest waitRequest = WaitOperationRequest.newBuilder()
                .setName("operations/running")
                .setTimeout(Duration.newBuilder().setSeconds(2).build())
                .build();

        assertMarshallerRoundTrip(OperationsGrpc.getListOperationsMethod(), listRequest, listResponse);
        assertMarshallerRoundTrip(OperationsGrpc.getGetOperationMethod(), getRequest, completedOperation);
        assertMarshallerRoundTrip(OperationsGrpc.getDeleteOperationMethod(), deleteRequest, Empty.getDefaultInstance());
        assertMarshallerRoundTrip(OperationsGrpc.getCancelOperationMethod(), cancelRequest, Empty.getDefaultInstance());
        assertMarshallerRoundTrip(OperationsGrpc.getWaitOperationMethod(), waitRequest, runningOperation);

        FakeChannel channel = new FakeChannel()
                .respondTo(OperationsGrpc.getListOperationsMethod(), listRequest, listResponse)
                .respondTo(OperationsGrpc.getGetOperationMethod(), getRequest, completedOperation)
                .respondTo(OperationsGrpc.getDeleteOperationMethod(), deleteRequest, Empty.getDefaultInstance())
                .respondTo(OperationsGrpc.getCancelOperationMethod(), cancelRequest, Empty.getDefaultInstance())
                .respondTo(OperationsGrpc.getWaitOperationMethod(), waitRequest, runningOperation);

        OperationsGrpc.OperationsBlockingStub blockingStub = OperationsGrpc.newBlockingStub(channel);
        assertThat(blockingStub.listOperations(listRequest)).isEqualTo(listResponse);
        assertThat(blockingStub.getOperation(getRequest)).isEqualTo(completedOperation);
        assertThat(blockingStub.deleteOperation(deleteRequest)).isEqualTo(Empty.getDefaultInstance());
        assertThat(blockingStub.cancelOperation(cancelRequest)).isEqualTo(Empty.getDefaultInstance());

        OperationsGrpc.OperationsBlockingV2Stub blockingV2Stub = OperationsGrpc.newBlockingV2Stub(channel);
        assertThat(blockingV2Stub.waitOperation(waitRequest)).isEqualTo(runningOperation);

        assertThat(channel.calledMethodNames())
                .containsEntry(OperationsGrpc.getListOperationsMethod().getFullMethodName(), 1)
                .containsEntry(OperationsGrpc.getGetOperationMethod().getFullMethodName(), 1)
                .containsEntry(OperationsGrpc.getDeleteOperationMethod().getFullMethodName(), 1)
                .containsEntry(OperationsGrpc.getCancelOperationMethod().getFullMethodName(), 1)
                .containsEntry(OperationsGrpc.getWaitOperationMethod().getFullMethodName(), 1);
    }

    @Test
    void locationsStubsSendExpectedRequestsAndReturnMappedResponses() throws Exception {
        Location europeWest = Location.newBuilder()
                .setName("projects/test/locations/europe-west1")
                .setLocationId("europe-west1")
                .setDisplayName("Belgium")
                .putLabels("region", "europe")
                .setMetadata(Any.pack(StringValue.of("low-carbon")))
                .build();
        Location usCentral = Location.newBuilder()
                .setName("projects/test/locations/us-central1")
                .setLocationId("us-central1")
                .setDisplayName("Iowa")
                .putLabels("region", "us")
                .build();
        ListLocationsRequest listRequest = ListLocationsRequest.newBuilder()
                .setName("projects/test")
                .setFilter("labels.region:*")
                .setPageSize(10)
                .setPageToken("locations-page")
                .build();
        ListLocationsResponse listResponse = ListLocationsResponse.newBuilder()
                .addLocations(europeWest)
                .addLocations(usCentral)
                .setNextPageToken("next-location-page")
                .build();
        GetLocationRequest getRequest = GetLocationRequest.newBuilder()
                .setName("projects/test/locations/europe-west1")
                .build();

        assertMarshallerRoundTrip(LocationsGrpc.getListLocationsMethod(), listRequest, listResponse);
        assertMarshallerRoundTrip(LocationsGrpc.getGetLocationMethod(), getRequest, europeWest);

        FakeChannel channel = new FakeChannel()
                .respondTo(LocationsGrpc.getListLocationsMethod(), listRequest, listResponse)
                .respondTo(LocationsGrpc.getGetLocationMethod(), getRequest, europeWest);

        LocationsGrpc.LocationsBlockingStub blockingStub = LocationsGrpc.newBlockingStub(channel);
        assertThat(blockingStub.listLocations(listRequest)).isEqualTo(listResponse);

        LocationsGrpc.LocationsBlockingV2Stub blockingV2Stub = LocationsGrpc.newBlockingV2Stub(channel);
        assertThat(blockingV2Stub.getLocation(getRequest)).isEqualTo(europeWest);

        assertThat(channel.calledMethodNames())
                .containsEntry(LocationsGrpc.getListLocationsMethod().getFullMethodName(), 1)
                .containsEntry(LocationsGrpc.getGetLocationMethod().getFullMethodName(), 1);
    }

    @Test
    void futureAndAsyncStubsCompleteUnaryCalls() throws Exception {
        GetOperationRequest operationRequest = GetOperationRequest.newBuilder()
                .setName("operations/future")
                .build();
        Operation operationResponse = operation("operations/future", true);
        GetLocationRequest locationRequest = GetLocationRequest.newBuilder()
                .setName("projects/test/locations/asia-northeast1")
                .build();
        Location locationResponse = Location.newBuilder()
                .setName("projects/test/locations/asia-northeast1")
                .setLocationId("asia-northeast1")
                .setDisplayName("Tokyo")
                .build();
        ListOperationsRequest asyncListRequest = ListOperationsRequest.newBuilder()
                .setName("projects/test/locations/global")
                .build();
        ListOperationsResponse asyncListResponse = ListOperationsResponse.newBuilder()
                .addOperations(operation("operations/async", false))
                .build();

        FakeChannel channel = new FakeChannel()
                .respondTo(OperationsGrpc.getGetOperationMethod(), operationRequest, operationResponse)
                .respondTo(LocationsGrpc.getGetLocationMethod(), locationRequest, locationResponse)
                .respondTo(OperationsGrpc.getListOperationsMethod(), asyncListRequest, asyncListResponse);

        ListenableFuture<Operation> operationFuture =
                OperationsGrpc.newFutureStub(channel).getOperation(operationRequest);
        ListenableFuture<Location> locationFuture = LocationsGrpc.newFutureStub(channel).getLocation(locationRequest);

        assertThat(operationFuture.get(5, TimeUnit.SECONDS)).isEqualTo(operationResponse);
        assertThat(locationFuture.get(5, TimeUnit.SECONDS)).isEqualTo(locationResponse);

        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<ListOperationsResponse> asyncResponse = new AtomicReference<>();
        AtomicReference<Throwable> asyncError = new AtomicReference<>();
        OperationsGrpc.newStub(channel).listOperations(asyncListRequest, new StreamObserver<ListOperationsResponse>() {
            @Override
            public void onNext(ListOperationsResponse value) {
                asyncResponse.set(value);
            }

            @Override
            public void onError(Throwable throwable) {
                asyncError.set(throwable);
                completed.countDown();
            }

            @Override
            public void onCompleted() {
                completed.countDown();
            }
        });

        assertThat(completed.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(asyncError.get()).isNull();
        assertThat(asyncResponse.get()).isEqualTo(asyncListResponse);
    }

    @Test
    void boundAsyncServicesDispatchUnaryRequestsToImplementations() {
        WaitOperationRequest waitRequest = WaitOperationRequest.newBuilder()
                .setName("operations/server-dispatch")
                .setTimeout(Duration.newBuilder().setSeconds(1).build())
                .build();
        Operation waitResponse = operation("operations/server-dispatch", true);
        ServerServiceDefinition operationsService = OperationsGrpc.bindService(new OperationsGrpc.AsyncService() {
            @Override
            public void waitOperation(
                    WaitOperationRequest request,
                    StreamObserver<Operation> responseObserver) {
                assertThat(request).isEqualTo(waitRequest);
                responseObserver.onNext(waitResponse);
                responseObserver.onCompleted();
            }
        });

        Operation dispatchedOperation =
                invokeUnary(operationsService, OperationsGrpc.getWaitOperationMethod(), waitRequest);

        assertThat(dispatchedOperation).isEqualTo(waitResponse);

        GetLocationRequest locationRequest = GetLocationRequest.newBuilder()
                .setName("projects/test/locations/australia-southeast1")
                .build();
        Location locationResponse = Location.newBuilder()
                .setName("projects/test/locations/australia-southeast1")
                .setLocationId("australia-southeast1")
                .setDisplayName("Sydney")
                .build();
        ServerServiceDefinition locationsService = LocationsGrpc.bindService(new LocationsGrpc.AsyncService() {
            @Override
            public void getLocation(
                    GetLocationRequest request,
                    StreamObserver<Location> responseObserver) {
                assertThat(request).isEqualTo(locationRequest);
                responseObserver.onNext(locationResponse);
                responseObserver.onCompleted();
            }
        });

        Location dispatchedLocation =
                invokeUnary(locationsService, LocationsGrpc.getGetLocationMethod(), locationRequest);

        assertThat(dispatchedLocation).isEqualTo(locationResponse);
    }

    private static Operation operation(String name, boolean done) {
        return Operation.newBuilder()
                .setName(name)
                .setDone(done)
                .setResponse(Any.pack(StringValue.of(name + " response")))
                .build();
    }

    private static <RequestT, ResponseT> void assertUnaryMethod(
            MethodDescriptor<RequestT, ResponseT> method,
            String serviceName,
            String methodName) {
        assertThat(method.getType()).isEqualTo(MethodDescriptor.MethodType.UNARY);
        assertThat(method.getServiceName()).isEqualTo(serviceName);
        assertThat(method.getFullMethodName())
                .isEqualTo(MethodDescriptor.generateFullMethodName(serviceName, methodName));
        assertThat(method.getRequestMarshaller()).isNotNull();
        assertThat(method.getResponseMarshaller()).isNotNull();
        assertThat(method.getSchemaDescriptor()).isNotNull();
    }

    private static Descriptors.ServiceDescriptor assertServiceSchema(
            ServiceDescriptor serviceDescriptor,
            Descriptors.FileDescriptor fileDescriptor,
            String serviceName) {
        assertThat(serviceDescriptor.getSchemaDescriptor()).isInstanceOf(ProtoServiceDescriptorSupplier.class);
        ProtoServiceDescriptorSupplier serviceSupplier =
                (ProtoServiceDescriptorSupplier) serviceDescriptor.getSchemaDescriptor();

        assertThat(serviceSupplier.getFileDescriptor()).isEqualTo(fileDescriptor);
        Descriptors.ServiceDescriptor protoService = serviceSupplier.getServiceDescriptor();
        assertThat(protoService.getFullName()).isEqualTo(serviceName);
        assertThat(protoService.getFile()).isEqualTo(fileDescriptor);
        return protoService;
    }

    private static <RequestT, ResponseT> void assertMethodSchema(
            MethodDescriptor<RequestT, ResponseT> grpcMethod,
            Descriptors.ServiceDescriptor protoService,
            String methodName,
            Descriptors.Descriptor requestType,
            Descriptors.Descriptor responseType) {
        assertThat(grpcMethod.getSchemaDescriptor()).isInstanceOf(ProtoMethodDescriptorSupplier.class);
        ProtoMethodDescriptorSupplier methodSupplier =
                (ProtoMethodDescriptorSupplier) grpcMethod.getSchemaDescriptor();

        Descriptors.MethodDescriptor protoMethod = methodSupplier.getMethodDescriptor();
        assertThat(protoMethod).isEqualTo(protoService.findMethodByName(methodName));
        assertThat(protoMethod.getInputType()).isEqualTo(requestType);
        assertThat(protoMethod.getOutputType()).isEqualTo(responseType);
        assertThat(methodSupplier.getServiceDescriptor()).isEqualTo(protoService);
        assertThat(methodSupplier.getFileDescriptor()).isEqualTo(protoService.getFile());
    }

    private static <RequestT, ResponseT> void assertMarshallerRoundTrip(
            MethodDescriptor<RequestT, ResponseT> method,
            RequestT request,
            ResponseT response) throws IOException {
        try (InputStream input = method.streamRequest(request)) {
            assertThat(method.parseRequest(input)).isEqualTo(request);
        }
        try (InputStream input = method.streamResponse(response)) {
            assertThat(method.parseResponse(input)).isEqualTo(response);
        }
    }

    @SuppressWarnings("unchecked")
    private static <RequestT, ResponseT> ResponseT invokeUnary(
            ServerServiceDefinition serviceDefinition,
            MethodDescriptor<RequestT, ResponseT> method,
            RequestT request) {
        ServerMethodDefinition<RequestT, ResponseT> methodDefinition =
                (ServerMethodDefinition<RequestT, ResponseT>) serviceDefinition.getMethod(method.getFullMethodName());
        assertThat(methodDefinition)
                .as("server method definition for %s", method.getFullMethodName())
                .isNotNull();
        ServerCallHandler<RequestT, ResponseT> callHandler = methodDefinition.getServerCallHandler();
        CapturingServerCall<RequestT, ResponseT> serverCall = new CapturingServerCall<>(method);

        ServerCall.Listener<RequestT> listener = callHandler.startCall(serverCall, new Metadata());
        listener.onMessage(request);
        listener.onHalfClose();

        assertThat(serverCall.status()).isEqualTo(Status.OK);
        return serverCall.response();
    }

    private static final class FakeChannel extends Channel {
        private final Map<String, CallDefinition<?, ?>> definitions = new HashMap<>();
        private final Map<String, Integer> calledMethodNames = new HashMap<>();

        private <RequestT, ResponseT> FakeChannel respondTo(
                MethodDescriptor<RequestT, ResponseT> method,
                RequestT expectedRequest,
                ResponseT response) {
            definitions.put(method.getFullMethodName(), new CallDefinition<>(expectedRequest, response));
            return this;
        }

        @Override
        public <RequestT, ResponseT> ClientCall<RequestT, ResponseT> newCall(
                MethodDescriptor<RequestT, ResponseT> method,
                CallOptions callOptions) {
            calledMethodNames.merge(method.getFullMethodName(), 1, Integer::sum);
            CallDefinition<?, ?> definition = definitions.get(method.getFullMethodName());
            assertThat(definition)
                    .as("registered fake response for %s", method.getFullMethodName())
                    .isNotNull();
            return new FakeClientCall<>(method, definition);
        }

        @Override
        public String authority() {
            return "fake.grpc-google-common-protos.test";
        }

        private Map<String, Integer> calledMethodNames() {
            return calledMethodNames;
        }
    }

    private static final class CapturingServerCall<RequestT, ResponseT> extends ServerCall<RequestT, ResponseT> {
        private final MethodDescriptor<RequestT, ResponseT> method;
        private ResponseT response;
        private Status status;

        private CapturingServerCall(MethodDescriptor<RequestT, ResponseT> method) {
            this.method = method;
        }

        @Override
        public void request(int messageCount) {
            assertThat(messageCount).isPositive();
        }

        @Override
        public void sendHeaders(Metadata headers) {
            assertThat(headers).isNotNull();
        }

        @Override
        public void sendMessage(ResponseT message) {
            this.response = message;
        }

        @Override
        public void close(Status status, Metadata trailers) {
            assertThat(trailers).isNotNull();
            this.status = status;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public MethodDescriptor<RequestT, ResponseT> getMethodDescriptor() {
            return method;
        }

        private ResponseT response() {
            return response;
        }

        private Status status() {
            return status;
        }
    }

    private static final class FakeClientCall<RequestT, ResponseT> extends ClientCall<RequestT, ResponseT> {
        private final MethodDescriptor<RequestT, ResponseT> method;
        private final CallDefinition<?, ?> definition;
        private Listener<ResponseT> listener;
        private RequestT request;

        private FakeClientCall(MethodDescriptor<RequestT, ResponseT> method, CallDefinition<?, ?> definition) {
            this.method = method;
            this.definition = definition;
        }

        @Override
        public void start(Listener<ResponseT> listener, Metadata headers) {
            this.listener = listener;
            listener.onHeaders(new Metadata());
        }

        @Override
        public void request(int messageCount) {
            assertThat(messageCount).isPositive();
        }

        @Override
        public void cancel(String message, Throwable cause) {
            fail("Unexpected cancellation for " + method.getFullMethodName());
        }

        @Override
        public void halfClose() {
            assertThat(request)
                    .as("request sent to %s", method.getFullMethodName())
                    .isEqualTo(definition.expectedRequest());
            listener.onMessage(method.parseResponse(method.streamResponse(response())));
            listener.onClose(Status.OK, new Metadata());
        }

        @Override
        public void sendMessage(RequestT message) {
            this.request = method.parseRequest(method.streamRequest(message));
        }

        @SuppressWarnings("unchecked")
        private ResponseT response() {
            return (ResponseT) definition.response();
        }
    }

    private static final class CallDefinition<RequestT, ResponseT> {
        private final RequestT expectedRequest;
        private final ResponseT response;

        private CallDefinition(RequestT expectedRequest, ResponseT response) {
            this.expectedRequest = expectedRequest;
            this.response = response;
        }

        private RequestT expectedRequest() {
            return expectedRequest;
        }

        private ResponseT response() {
            return response;
        }
    }
}
