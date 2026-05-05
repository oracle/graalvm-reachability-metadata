/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_api_grpc.grpc_google_cloud_spanner_admin_instance_v1;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.iam.v1.Binding;
import com.google.iam.v1.GetIamPolicyRequest;
import com.google.iam.v1.Policy;
import com.google.iam.v1.SetIamPolicyRequest;
import com.google.iam.v1.TestIamPermissionsRequest;
import com.google.iam.v1.TestIamPermissionsResponse;
import com.google.longrunning.Operation;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Empty;
import com.google.protobuf.MessageLite;
import com.google.spanner.admin.instance.v1.CreateInstanceConfigRequest;
import com.google.spanner.admin.instance.v1.CreateInstancePartitionRequest;
import com.google.spanner.admin.instance.v1.CreateInstanceRequest;
import com.google.spanner.admin.instance.v1.DeleteInstanceConfigRequest;
import com.google.spanner.admin.instance.v1.DeleteInstancePartitionRequest;
import com.google.spanner.admin.instance.v1.DeleteInstanceRequest;
import com.google.spanner.admin.instance.v1.GetInstanceConfigRequest;
import com.google.spanner.admin.instance.v1.GetInstancePartitionRequest;
import com.google.spanner.admin.instance.v1.GetInstanceRequest;
import com.google.spanner.admin.instance.v1.Instance;
import com.google.spanner.admin.instance.v1.InstanceAdminGrpc;
import com.google.spanner.admin.instance.v1.InstanceConfig;
import com.google.spanner.admin.instance.v1.InstancePartition;
import com.google.spanner.admin.instance.v1.ListInstanceConfigOperationsRequest;
import com.google.spanner.admin.instance.v1.ListInstanceConfigOperationsResponse;
import com.google.spanner.admin.instance.v1.ListInstanceConfigsRequest;
import com.google.spanner.admin.instance.v1.ListInstanceConfigsResponse;
import com.google.spanner.admin.instance.v1.ListInstancePartitionOperationsRequest;
import com.google.spanner.admin.instance.v1.ListInstancePartitionOperationsResponse;
import com.google.spanner.admin.instance.v1.ListInstancePartitionsRequest;
import com.google.spanner.admin.instance.v1.ListInstancePartitionsResponse;
import com.google.spanner.admin.instance.v1.ListInstancesRequest;
import com.google.spanner.admin.instance.v1.ListInstancesResponse;
import com.google.spanner.admin.instance.v1.MoveInstanceRequest;
import com.google.spanner.admin.instance.v1.UpdateInstanceConfigRequest;
import com.google.spanner.admin.instance.v1.UpdateInstancePartitionRequest;
import com.google.spanner.admin.instance.v1.UpdateInstanceRequest;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerMethodDefinition;
import io.grpc.ServerServiceDefinition;
import io.grpc.ServiceDescriptor;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.protobuf.ProtoMethodDescriptorSupplier;
import io.grpc.protobuf.ProtoServiceDescriptorSupplier;
import io.grpc.stub.StreamObserver;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

public class Grpc_google_cloud_spanner_admin_instance_v1Test {
    private static final String PROJECT = "projects/native-image-project";
    private static final String CONFIG = PROJECT + "/instanceConfigs/regional-us-central1";
    private static final String CUSTOM_CONFIG = PROJECT + "/instanceConfigs/custom-config";
    private static final String INSTANCE = PROJECT + "/instances/test-instance";
    private static final String PARTITION = INSTANCE + "/instancePartitions/test-partition";

    @Test
    void methodDescriptorsExposeUnarySpannerInstanceAdminContract() {
        List<UnaryMethodCase<?, ?>> methods = methodCases();

        assertThat(InstanceAdminGrpc.SERVICE_NAME).isEqualTo("google.spanner.admin.instance.v1.InstanceAdmin");
        assertThat(methods).hasSize(21);
        for (UnaryMethodCase<?, ?> methodCase : methods) {
            methodCase.assertUnaryDescriptor();
            methodCase.assertMarshallerRoundTripsMessages();
        }
    }

    @Test
    void serviceAndProtoDescriptorsExposeAllInstanceAdminMethods() {
        ServiceDescriptor serviceDescriptor = InstanceAdminGrpc.getServiceDescriptor();
        Object schemaDescriptor = serviceDescriptor.getSchemaDescriptor();

        assertThat(schemaDescriptor).isInstanceOf(ProtoServiceDescriptorSupplier.class);
        ProtoServiceDescriptorSupplier serviceSupplier = (ProtoServiceDescriptorSupplier) schemaDescriptor;
        Descriptors.FileDescriptor fileDescriptor = serviceSupplier.getFileDescriptor();
        Descriptors.ServiceDescriptor protoService = serviceSupplier.getServiceDescriptor();

        assertThat(serviceDescriptor.getName()).isEqualTo(InstanceAdminGrpc.SERVICE_NAME);
        assertThat(fileDescriptor.getPackage()).isEqualTo("google.spanner.admin.instance.v1");
        assertThat(fileDescriptor.getServices()).contains(protoService);
        assertThat(protoService.getFullName()).isEqualTo(InstanceAdminGrpc.SERVICE_NAME);
        assertThat(serviceDescriptor.getMethods())
                .extracting(MethodDescriptor::getFullMethodName)
                .containsExactlyElementsOf(fullMethodNames());

        for (UnaryMethodCase<?, ?> methodCase : methodCases()) {
            Object methodSchema = methodCase.method.getSchemaDescriptor();
            assertThat(methodSchema).isInstanceOf(ProtoMethodDescriptorSupplier.class);
            ProtoMethodDescriptorSupplier methodSupplier = (ProtoMethodDescriptorSupplier) methodSchema;
            Descriptors.MethodDescriptor protoMethod = methodSupplier.getMethodDescriptor();

            assertThat(protoMethod.getService()).isEqualTo(protoService);
            assertThat(protoMethod.getName()).isEqualTo(methodCase.bareMethodName);
            assertThat(protoMethod.isClientStreaming()).isFalse();
            assertThat(protoMethod.isServerStreaming()).isFalse();
        }
    }

    @Test
    void bindServiceRegistersEveryUnaryMethodAndDispatchesOverriddenImplementations() {
        InstanceAdminGrpc.AsyncService service = new InstanceAdminGrpc.AsyncService() {
            @Override
            public void listInstances(
                    ListInstancesRequest request, StreamObserver<ListInstancesResponse> responseObserver) {
                responseObserver.onNext(ListInstancesResponse.newBuilder()
                        .addInstances(instance(request.getParent() + "/instances/from-service"))
                        .build());
                responseObserver.onCompleted();
            }

            @Override
            public void updateInstancePartition(
                    UpdateInstancePartitionRequest request, StreamObserver<Operation> responseObserver) {
                responseObserver.onNext(operation("update-" + request.getInstancePartition().getDisplayName()));
                responseObserver.onCompleted();
            }
        };
        ServerServiceDefinition serviceDefinition = InstanceAdminGrpc.bindService(service);

        assertThat(serviceDefinition.getServiceDescriptor().getName()).isEqualTo(InstanceAdminGrpc.SERVICE_NAME);
        for (String fullMethodName : fullMethodNames()) {
            assertThat(serviceDefinition.getMethod(fullMethodName)).isNotNull();
        }

        ListInstancesResponse instances = dispatchUnary(
                serviceDefinition,
                InstanceAdminGrpc.getListInstancesMethod(),
                ListInstancesRequest.newBuilder().setParent(PROJECT).build());
        Operation updatePartition = dispatchUnary(
                serviceDefinition,
                InstanceAdminGrpc.getUpdateInstancePartitionMethod(),
                UpdateInstancePartitionRequest.newBuilder()
                        .setInstancePartition(instancePartition(PARTITION).toBuilder().setDisplayName("partition-a"))
                        .build());

        assertThat(instances.getInstancesList())
                .singleElement()
                .extracting(Instance::getName)
                .isEqualTo(PROJECT + "/instances/from-service");
        assertThat(updatePartition.getName()).isEqualTo("update-partition-a");
    }

    @Test
    void implBaseDefaultServiceReportsUnimplementedUnaryMethod() {
        InstanceAdminGrpc.InstanceAdminImplBase service = new InstanceAdminGrpc.InstanceAdminImplBase() { };
        ServerServiceDefinition serviceDefinition = service.bindService();
        ServerMethodDefinition<GetInstanceRequest, Instance> methodDefinition = getMethod(
                serviceDefinition, InstanceAdminGrpc.getGetInstanceMethod());
        RecordingServerCall<GetInstanceRequest, Instance> serverCall =
                new RecordingServerCall<>(methodDefinition.getMethodDescriptor());

        ServerCall.Listener<GetInstanceRequest> listener =
                methodDefinition.getServerCallHandler().startCall(serverCall, new Metadata());
        listener.onMessage(GetInstanceRequest.newBuilder().setName(INSTANCE).build());
        listener.onHalfClose();

        assertThat(serverCall.closedStatus).isNotNull();
        assertThat(serverCall.closedStatus.getCode()).isEqualTo(Status.Code.UNIMPLEMENTED);
        assertThat(serverCall.messages).isEmpty();
    }

    @Test
    void blockingStubInvokesEveryUnaryMethodWithConfiguredCallOptions() {
        List<UnaryMethodCase<?, ?>> methodCases = methodCases();
        RecordingChannel channel = new RecordingChannel(responsesByFullMethodName(methodCases));
        InstanceAdminGrpc.InstanceAdminBlockingStub stub = InstanceAdminGrpc.newBlockingStub(channel)
                .withCompression("gzip")
                .withDeadlineAfter(5, TimeUnit.SECONDS);

        assertThat(stub.listInstanceConfigs(listInstanceConfigsRequest())).isEqualTo(listInstanceConfigsResponse());
        assertThat(stub.getInstanceConfig(getInstanceConfigRequest())).isEqualTo(instanceConfig(CONFIG));
        assertThat(stub.createInstanceConfig(createInstanceConfigRequest())).isEqualTo(operation("create-config"));
        assertThat(stub.updateInstanceConfig(updateInstanceConfigRequest())).isEqualTo(operation("update-config"));
        assertThat(stub.deleteInstanceConfig(deleteInstanceConfigRequest())).isEqualTo(Empty.getDefaultInstance());
        assertThat(stub.listInstanceConfigOperations(listInstanceConfigOperationsRequest()))
                .isEqualTo(listInstanceConfigOperationsResponse());
        assertThat(stub.listInstances(listInstancesRequest())).isEqualTo(listInstancesResponse());
        assertThat(stub.listInstancePartitions(listInstancePartitionsRequest())).isEqualTo(listInstancePartitionsResponse());
        assertThat(stub.getInstance(getInstanceRequest())).isEqualTo(instance(INSTANCE));
        assertThat(stub.createInstance(createInstanceRequest())).isEqualTo(operation("create-instance"));
        assertThat(stub.updateInstance(updateInstanceRequest())).isEqualTo(operation("update-instance"));
        assertThat(stub.deleteInstance(deleteInstanceRequest())).isEqualTo(Empty.getDefaultInstance());
        assertThat(stub.setIamPolicy(setIamPolicyRequest())).isEqualTo(policy());
        assertThat(stub.getIamPolicy(getIamPolicyRequest())).isEqualTo(policy());
        assertThat(stub.testIamPermissions(testIamPermissionsRequest())).isEqualTo(testIamPermissionsResponse());
        assertThat(stub.getInstancePartition(getInstancePartitionRequest())).isEqualTo(instancePartition(PARTITION));
        assertThat(stub.createInstancePartition(createInstancePartitionRequest())).isEqualTo(operation("create-partition"));
        assertThat(stub.deleteInstancePartition(deleteInstancePartitionRequest())).isEqualTo(Empty.getDefaultInstance());
        assertThat(stub.updateInstancePartition(updateInstancePartitionRequest())).isEqualTo(operation("update-partition"));
        assertThat(stub.listInstancePartitionOperations(listInstancePartitionOperationsRequest()))
                .isEqualTo(listInstancePartitionOperationsResponse());
        assertThat(stub.moveInstance(moveInstanceRequest())).isEqualTo(operation("move-instance"));

        assertThat(channel.calls).hasSize(methodCases.size());
        assertThat(channel.calls).extracting(call -> call.method.getFullMethodName())
                .containsExactlyElementsOf(fullMethodNames());
        assertThat(channel.calls).extracting(call -> call.request)
                .containsExactlyElementsOf(methodCases.stream().map(method -> method.request).toList());
        assertThat(channel.calls).allSatisfy(call -> {
            assertThat(call.callOptions.getCompressor()).isEqualTo("gzip");
            assertThat(call.callOptions.getDeadline()).isNotNull();
        });
    }

    @Test
    void futureBlockingV2AndAsyncStubsUseUnaryClientCalls() throws Exception {
        RecordingChannel channel = new RecordingChannel(responsesByFullMethodName(methodCases()));

        ListenableFuture<Operation> future = InstanceAdminGrpc.newFutureStub(channel)
                .withWaitForReady()
                .createInstance(createInstanceRequest());
        Operation operation = future.get(1, TimeUnit.SECONDS);

        InstanceConfig config = InstanceAdminGrpc.newBlockingV2Stub(channel)
                .withDeadlineAfter(5, TimeUnit.SECONDS)
                .getInstanceConfig(getInstanceConfigRequest());

        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<TestIamPermissionsResponse> asyncResponse = new AtomicReference<>();
        AtomicReference<Throwable> asyncError = new AtomicReference<>();
        InstanceAdminGrpc.newStub(channel)
                .withCompression("identity")
                .testIamPermissions(testIamPermissionsRequest(), new StreamObserver<>() {
                    @Override
                    public void onNext(TestIamPermissionsResponse response) {
                        asyncResponse.set(response);
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

        assertThat(operation).isEqualTo(operation("create-instance"));
        assertThat(config).isEqualTo(instanceConfig(CONFIG));
        assertThat(completed.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(asyncError.get()).isNull();
        assertThat(asyncResponse.get()).isEqualTo(testIamPermissionsResponse());
        assertThat(channel.calls).extracting(call -> call.method.getFullMethodName()).containsExactly(
                InstanceAdminGrpc.getCreateInstanceMethod().getFullMethodName(),
                InstanceAdminGrpc.getGetInstanceConfigMethod().getFullMethodName(),
                InstanceAdminGrpc.getTestIamPermissionsMethod().getFullMethodName());
        assertThat(channel.calls.get(0).callOptions.isWaitForReady()).isTrue();
        assertThat(channel.calls.get(1).callOptions.getDeadline()).isNotNull();
        assertThat(channel.calls.get(2).callOptions.getCompressor()).isEqualTo("identity");
    }

    @Test
    void futureStubCancellationPropagatesToClientCall() {
        CancellableChannel channel = new CancellableChannel();
        ListenableFuture<Instance> future = InstanceAdminGrpc.newFutureStub(channel).getInstance(getInstanceRequest());

        assertThat(channel.calls).hasSize(1);
        CancellableClientCall<?, ?> clientCall = channel.calls.get(0);
        assertThat(clientCall.recordedCall.method.getFullMethodName())
                .isEqualTo(InstanceAdminGrpc.getGetInstanceMethod().getFullMethodName());
        assertThat(clientCall.recordedCall.request).isEqualTo(getInstanceRequest());
        assertThat(clientCall.cancelled).isFalse();

        assertThat(future.cancel(true)).isTrue();

        assertThat(future.isCancelled()).isTrue();
        assertThat(clientCall.cancelled).isTrue();
    }

    @Test
    void blockingV2StubPropagatesGrpcStatusAsCheckedException() {
        FailingChannel channel = new FailingChannel(Status.UNAVAILABLE.withDescription("backend unavailable"));
        StatusException exception = null;

        try {
            InstanceAdminGrpc.newBlockingV2Stub(channel)
                    .withWaitForReady()
                    .getInstance(getInstanceRequest());
        } catch (StatusException caught) {
            exception = caught;
        }

        assertThat(exception).isNotNull();
        assertThat(exception.getStatus().getCode()).isEqualTo(Status.Code.UNAVAILABLE);
        assertThat(exception.getStatus().getDescription()).isEqualTo("backend unavailable");
        assertThat(channel.calls).hasSize(1);
        assertThat(channel.calls.get(0).method.getFullMethodName())
                .isEqualTo(InstanceAdminGrpc.getGetInstanceMethod().getFullMethodName());
        assertThat(channel.calls.get(0).request).isEqualTo(getInstanceRequest());
        assertThat(channel.calls.get(0).callOptions.isWaitForReady()).isTrue();
    }

    private static List<UnaryMethodCase<?, ?>> methodCases() {
        List<UnaryMethodCase<?, ?>> methods = new ArrayList<>();
        methods.add(new UnaryMethodCase<>("ListInstanceConfigs", InstanceAdminGrpc.getListInstanceConfigsMethod(),
                listInstanceConfigsRequest(), listInstanceConfigsResponse()));
        methods.add(new UnaryMethodCase<>("GetInstanceConfig", InstanceAdminGrpc.getGetInstanceConfigMethod(),
                getInstanceConfigRequest(), instanceConfig(CONFIG)));
        methods.add(new UnaryMethodCase<>("CreateInstanceConfig", InstanceAdminGrpc.getCreateInstanceConfigMethod(),
                createInstanceConfigRequest(), operation("create-config")));
        methods.add(new UnaryMethodCase<>("UpdateInstanceConfig", InstanceAdminGrpc.getUpdateInstanceConfigMethod(),
                updateInstanceConfigRequest(), operation("update-config")));
        methods.add(new UnaryMethodCase<>("DeleteInstanceConfig", InstanceAdminGrpc.getDeleteInstanceConfigMethod(),
                deleteInstanceConfigRequest(), Empty.getDefaultInstance()));
        methods.add(new UnaryMethodCase<>("ListInstanceConfigOperations",
                InstanceAdminGrpc.getListInstanceConfigOperationsMethod(), listInstanceConfigOperationsRequest(),
                listInstanceConfigOperationsResponse()));
        methods.add(new UnaryMethodCase<>("ListInstances", InstanceAdminGrpc.getListInstancesMethod(),
                listInstancesRequest(), listInstancesResponse()));
        methods.add(new UnaryMethodCase<>("ListInstancePartitions", InstanceAdminGrpc.getListInstancePartitionsMethod(),
                listInstancePartitionsRequest(), listInstancePartitionsResponse()));
        methods.add(new UnaryMethodCase<>("GetInstance", InstanceAdminGrpc.getGetInstanceMethod(),
                getInstanceRequest(), instance(INSTANCE)));
        methods.add(new UnaryMethodCase<>("CreateInstance", InstanceAdminGrpc.getCreateInstanceMethod(),
                createInstanceRequest(), operation("create-instance")));
        methods.add(new UnaryMethodCase<>("UpdateInstance", InstanceAdminGrpc.getUpdateInstanceMethod(),
                updateInstanceRequest(), operation("update-instance")));
        methods.add(new UnaryMethodCase<>("DeleteInstance", InstanceAdminGrpc.getDeleteInstanceMethod(),
                deleteInstanceRequest(), Empty.getDefaultInstance()));
        methods.add(new UnaryMethodCase<>("SetIamPolicy", InstanceAdminGrpc.getSetIamPolicyMethod(),
                setIamPolicyRequest(), policy()));
        methods.add(new UnaryMethodCase<>("GetIamPolicy", InstanceAdminGrpc.getGetIamPolicyMethod(),
                getIamPolicyRequest(), policy()));
        methods.add(new UnaryMethodCase<>("TestIamPermissions", InstanceAdminGrpc.getTestIamPermissionsMethod(),
                testIamPermissionsRequest(), testIamPermissionsResponse()));
        methods.add(new UnaryMethodCase<>("GetInstancePartition", InstanceAdminGrpc.getGetInstancePartitionMethod(),
                getInstancePartitionRequest(), instancePartition(PARTITION)));
        methods.add(new UnaryMethodCase<>("CreateInstancePartition", InstanceAdminGrpc.getCreateInstancePartitionMethod(),
                createInstancePartitionRequest(), operation("create-partition")));
        methods.add(new UnaryMethodCase<>("DeleteInstancePartition", InstanceAdminGrpc.getDeleteInstancePartitionMethod(),
                deleteInstancePartitionRequest(), Empty.getDefaultInstance()));
        methods.add(new UnaryMethodCase<>("UpdateInstancePartition", InstanceAdminGrpc.getUpdateInstancePartitionMethod(),
                updateInstancePartitionRequest(), operation("update-partition")));
        methods.add(new UnaryMethodCase<>("ListInstancePartitionOperations",
                InstanceAdminGrpc.getListInstancePartitionOperationsMethod(), listInstancePartitionOperationsRequest(),
                listInstancePartitionOperationsResponse()));
        methods.add(new UnaryMethodCase<>("MoveInstance", InstanceAdminGrpc.getMoveInstanceMethod(),
                moveInstanceRequest(), operation("move-instance")));
        return methods;
    }

    private static List<String> fullMethodNames() {
        return methodCases().stream().map(method -> method.method.getFullMethodName()).toList();
    }

    private static Map<String, Object> responsesByFullMethodName(List<UnaryMethodCase<?, ?>> methodCases) {
        Map<String, Object> responses = new LinkedHashMap<>();
        for (UnaryMethodCase<?, ?> methodCase : methodCases) {
            responses.put(methodCase.method.getFullMethodName(), methodCase.response);
        }
        return responses;
    }

    private static ListInstanceConfigsRequest listInstanceConfigsRequest() {
        return ListInstanceConfigsRequest.newBuilder().setParent(PROJECT).setPageSize(10).build();
    }

    private static ListInstanceConfigsResponse listInstanceConfigsResponse() {
        return ListInstanceConfigsResponse.newBuilder()
                .addInstanceConfigs(instanceConfig(CONFIG))
                .setNextPageToken("configs-page-2")
                .build();
    }

    private static GetInstanceConfigRequest getInstanceConfigRequest() {
        return GetInstanceConfigRequest.newBuilder().setName(CONFIG).build();
    }

    private static CreateInstanceConfigRequest createInstanceConfigRequest() {
        return CreateInstanceConfigRequest.newBuilder()
                .setParent(PROJECT)
                .setInstanceConfigId("custom-config")
                .setInstanceConfig(instanceConfig(CUSTOM_CONFIG))
                .build();
    }

    private static UpdateInstanceConfigRequest updateInstanceConfigRequest() {
        return UpdateInstanceConfigRequest.newBuilder()
                .setInstanceConfig(instanceConfig(CUSTOM_CONFIG).toBuilder().setDisplayName("Updated config"))
                .build();
    }

    private static DeleteInstanceConfigRequest deleteInstanceConfigRequest() {
        return DeleteInstanceConfigRequest.newBuilder().setName(CUSTOM_CONFIG).build();
    }

    private static ListInstanceConfigOperationsRequest listInstanceConfigOperationsRequest() {
        return ListInstanceConfigOperationsRequest.newBuilder().setParent(PROJECT).build();
    }

    private static ListInstanceConfigOperationsResponse listInstanceConfigOperationsResponse() {
        return ListInstanceConfigOperationsResponse.newBuilder()
                .addOperations(operation("config-operation"))
                .setNextPageToken("config-operations-page-2")
                .build();
    }

    private static ListInstancesRequest listInstancesRequest() {
        return ListInstancesRequest.newBuilder().setParent(PROJECT).setFilter("name:test-instance").build();
    }

    private static ListInstancesResponse listInstancesResponse() {
        return ListInstancesResponse.newBuilder()
                .addInstances(instance(INSTANCE))
                .addUnreachable("regional-europe-west1")
                .build();
    }

    private static ListInstancePartitionsRequest listInstancePartitionsRequest() {
        return ListInstancePartitionsRequest.newBuilder().setParent(INSTANCE).build();
    }

    private static ListInstancePartitionsResponse listInstancePartitionsResponse() {
        return ListInstancePartitionsResponse.newBuilder()
                .addInstancePartitions(instancePartition(PARTITION))
                .addUnreachable("regional-asia-east1")
                .build();
    }

    private static GetInstanceRequest getInstanceRequest() {
        return GetInstanceRequest.newBuilder().setName(INSTANCE).build();
    }

    private static CreateInstanceRequest createInstanceRequest() {
        return CreateInstanceRequest.newBuilder()
                .setParent(PROJECT)
                .setInstanceId("test-instance")
                .setInstance(instance(INSTANCE))
                .build();
    }

    private static UpdateInstanceRequest updateInstanceRequest() {
        return UpdateInstanceRequest.newBuilder()
                .setInstance(instance(INSTANCE).toBuilder().setDisplayName("Updated instance"))
                .build();
    }

    private static DeleteInstanceRequest deleteInstanceRequest() {
        return DeleteInstanceRequest.newBuilder().setName(INSTANCE).build();
    }

    private static SetIamPolicyRequest setIamPolicyRequest() {
        return SetIamPolicyRequest.newBuilder().setResource(INSTANCE).setPolicy(policy()).build();
    }

    private static GetIamPolicyRequest getIamPolicyRequest() {
        return GetIamPolicyRequest.newBuilder().setResource(INSTANCE).build();
    }

    private static TestIamPermissionsRequest testIamPermissionsRequest() {
        return TestIamPermissionsRequest.newBuilder()
                .setResource(INSTANCE)
                .addPermissions("spanner.instances.get")
                .addPermissions("spanner.instances.list")
                .build();
    }

    private static TestIamPermissionsResponse testIamPermissionsResponse() {
        return TestIamPermissionsResponse.newBuilder()
                .addPermissions("spanner.instances.get")
                .build();
    }

    private static GetInstancePartitionRequest getInstancePartitionRequest() {
        return GetInstancePartitionRequest.newBuilder().setName(PARTITION).build();
    }

    private static CreateInstancePartitionRequest createInstancePartitionRequest() {
        return CreateInstancePartitionRequest.newBuilder()
                .setParent(INSTANCE)
                .setInstancePartitionId("test-partition")
                .setInstancePartition(instancePartition(PARTITION))
                .build();
    }

    private static DeleteInstancePartitionRequest deleteInstancePartitionRequest() {
        return DeleteInstancePartitionRequest.newBuilder().setName(PARTITION).build();
    }

    private static UpdateInstancePartitionRequest updateInstancePartitionRequest() {
        return UpdateInstancePartitionRequest.newBuilder()
                .setInstancePartition(instancePartition(PARTITION).toBuilder().setDisplayName("Updated partition"))
                .build();
    }

    private static ListInstancePartitionOperationsRequest listInstancePartitionOperationsRequest() {
        return ListInstancePartitionOperationsRequest.newBuilder().setParent(INSTANCE).build();
    }

    private static ListInstancePartitionOperationsResponse listInstancePartitionOperationsResponse() {
        return ListInstancePartitionOperationsResponse.newBuilder()
                .addOperations(operation("partition-operation"))
                .addUnreachableInstancePartitions(PARTITION + "-offline")
                .build();
    }

    private static MoveInstanceRequest moveInstanceRequest() {
        return MoveInstanceRequest.newBuilder().setName(INSTANCE).setTargetConfig(CONFIG).build();
    }

    private static InstanceConfig instanceConfig(String name) {
        return InstanceConfig.newBuilder()
                .setName(name)
                .setDisplayName("Regional test config")
                .setConfigType(InstanceConfig.Type.USER_MANAGED)
                .setBaseConfig(CONFIG)
                .build();
    }

    private static Instance instance(String name) {
        return Instance.newBuilder()
                .setName(name)
                .setConfig(CONFIG)
                .setDisplayName("Test instance")
                .setNodeCount(1)
                .setState(Instance.State.READY)
                .setInstanceType(Instance.InstanceType.PROVISIONED)
                .build();
    }

    private static InstancePartition instancePartition(String name) {
        return InstancePartition.newBuilder()
                .setName(name)
                .setConfig(CONFIG)
                .setDisplayName("Test partition")
                .setProcessingUnits(1000)
                .setState(InstancePartition.State.READY)
                .build();
    }

    private static Operation operation(String name) {
        return Operation.newBuilder().setName(name).setDone(true).build();
    }

    private static Policy policy() {
        return Policy.newBuilder()
                .setVersion(1)
                .addBindings(Binding.newBuilder()
                        .setRole("roles/spanner.viewer")
                        .addMembers("user:native-image@example.com"))
                .build();
    }

    private static <RequestT, ResponseT> ResponseT dispatchUnary(
            ServerServiceDefinition serviceDefinition,
            MethodDescriptor<RequestT, ResponseT> methodDescriptor,
            RequestT request) {
        ServerMethodDefinition<RequestT, ResponseT> methodDefinition = getMethod(serviceDefinition, methodDescriptor);
        RecordingServerCall<RequestT, ResponseT> serverCall = new RecordingServerCall<>(methodDescriptor);
        ServerCall.Listener<RequestT> listener = methodDefinition.getServerCallHandler()
                .startCall(serverCall, new Metadata());

        listener.onMessage(request);
        listener.onHalfClose();
        listener.onComplete();

        assertThat(serverCall.sentHeaders).isTrue();
        assertThat(serverCall.closedStatus).isNotNull();
        assertThat(serverCall.closedStatus.isOk()).isTrue();
        assertThat(serverCall.messages).hasSize(1);
        return serverCall.messages.get(0);
    }

    @SuppressWarnings("unchecked")
    private static <RequestT, ResponseT> ServerMethodDefinition<RequestT, ResponseT> getMethod(
            ServerServiceDefinition serviceDefinition, MethodDescriptor<RequestT, ResponseT> methodDescriptor) {
        return (ServerMethodDefinition<RequestT, ResponseT>) serviceDefinition.getMethod(
                methodDescriptor.getFullMethodName());
    }

    private static final class UnaryMethodCase<RequestT extends MessageLite, ResponseT extends MessageLite> {
        private final String bareMethodName;
        private final MethodDescriptor<RequestT, ResponseT> method;
        private final RequestT request;
        private final ResponseT response;

        private UnaryMethodCase(
                String bareMethodName,
                MethodDescriptor<RequestT, ResponseT> method,
                RequestT request,
                ResponseT response) {
            this.bareMethodName = bareMethodName;
            this.method = method;
            this.request = request;
            this.response = response;
        }

        private void assertUnaryDescriptor() {
            assertThat(method.getType()).isEqualTo(MethodDescriptor.MethodType.UNARY);
            assertThat(method.getServiceName()).isEqualTo(InstanceAdminGrpc.SERVICE_NAME);
            assertThat(method.getBareMethodName()).isEqualTo(bareMethodName);
            assertThat(method.getFullMethodName()).isEqualTo(InstanceAdminGrpc.SERVICE_NAME + "/" + bareMethodName);
            assertThat(method.isSampledToLocalTracing()).isTrue();
        }

        private void assertMarshallerRoundTripsMessages() {
            try (InputStream requestStream = method.streamRequest(request);
                    InputStream responseStream = method.streamResponse(response)) {
                assertThat(method.parseRequest(requestStream)).isEqualTo(request);
                assertThat(method.parseResponse(responseStream)).isEqualTo(response);
            } catch (Exception exception) {
                throw new AssertionError("Failed to round-trip " + method.getFullMethodName(), exception);
            }
        }
    }

    private static final class RecordingChannel extends Channel {
        private final Map<String, Object> responsesByMethodName;
        private final List<RecordedCall> calls = new ArrayList<>();

        private RecordingChannel(Map<String, Object> responsesByMethodName) {
            this.responsesByMethodName = responsesByMethodName;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <RequestT, ResponseT> ClientCall<RequestT, ResponseT> newCall(
                MethodDescriptor<RequestT, ResponseT> methodDescriptor, CallOptions callOptions) {
            Object response = responsesByMethodName.get(methodDescriptor.getFullMethodName());
            RecordedCall call = new RecordedCall(methodDescriptor, callOptions);
            calls.add(call);
            return new RecordingClientCall<>(call, (ResponseT) response);
        }

        @Override
        public String authority() {
            return "in-memory-spanner-admin.test";
        }
    }

    private static final class RecordingClientCall<RequestT, ResponseT> extends ClientCall<RequestT, ResponseT> {
        private final RecordedCall recordedCall;
        private final ResponseT response;
        private Listener<ResponseT> listener;

        private RecordingClientCall(RecordedCall recordedCall, ResponseT response) {
            this.recordedCall = recordedCall;
            this.response = response;
        }

        @Override
        public void start(Listener<ResponseT> responseListener, Metadata headers) {
            listener = responseListener;
            listener.onHeaders(new Metadata());
        }

        @Override
        public void request(int numMessages) {
        }

        @Override
        public void cancel(String message, Throwable cause) {
            listener.onClose(Status.CANCELLED.withDescription(message).withCause(cause), new Metadata());
        }

        @Override
        public void halfClose() {
            listener.onMessage(response);
            listener.onClose(Status.OK, new Metadata());
        }

        @Override
        public void sendMessage(RequestT message) {
            recordedCall.request = message;
        }

        @Override
        public boolean isReady() {
            return true;
        }
    }

    private static final class CancellableChannel extends Channel {
        private final List<CancellableClientCall<?, ?>> calls = new ArrayList<>();

        @Override
        public <RequestT, ResponseT> ClientCall<RequestT, ResponseT> newCall(
                MethodDescriptor<RequestT, ResponseT> methodDescriptor, CallOptions callOptions) {
            RecordedCall recordedCall = new RecordedCall(methodDescriptor, callOptions);
            CancellableClientCall<RequestT, ResponseT> call = new CancellableClientCall<>(recordedCall);
            calls.add(call);
            return call;
        }

        @Override
        public String authority() {
            return "cancellable-spanner-admin.test";
        }
    }

    private static final class CancellableClientCall<RequestT, ResponseT> extends ClientCall<RequestT, ResponseT> {
        private final RecordedCall recordedCall;
        private boolean cancelled;

        private CancellableClientCall(RecordedCall recordedCall) {
            this.recordedCall = recordedCall;
        }

        @Override
        public void start(Listener<ResponseT> responseListener, Metadata headers) {
            responseListener.onHeaders(new Metadata());
        }

        @Override
        public void request(int numMessages) {
        }

        @Override
        public void cancel(String message, Throwable cause) {
            cancelled = true;
        }

        @Override
        public void halfClose() {
        }

        @Override
        public void sendMessage(RequestT message) {
            recordedCall.request = message;
        }

        @Override
        public boolean isReady() {
            return true;
        }
    }

    private static final class FailingChannel extends Channel {
        private final Status status;
        private final List<RecordedCall> calls = new ArrayList<>();

        private FailingChannel(Status status) {
            this.status = status;
        }

        @Override
        public <RequestT, ResponseT> ClientCall<RequestT, ResponseT> newCall(
                MethodDescriptor<RequestT, ResponseT> methodDescriptor, CallOptions callOptions) {
            RecordedCall call = new RecordedCall(methodDescriptor, callOptions);
            calls.add(call);
            return new FailingClientCall<>(call, status);
        }

        @Override
        public String authority() {
            return "failing-spanner-admin.test";
        }
    }

    private static final class FailingClientCall<RequestT, ResponseT> extends ClientCall<RequestT, ResponseT> {
        private final RecordedCall recordedCall;
        private final Status status;
        private Listener<ResponseT> listener;

        private FailingClientCall(RecordedCall recordedCall, Status status) {
            this.recordedCall = recordedCall;
            this.status = status;
        }

        @Override
        public void start(Listener<ResponseT> responseListener, Metadata headers) {
            listener = responseListener;
            listener.onHeaders(new Metadata());
        }

        @Override
        public void request(int numMessages) {
        }

        @Override
        public void cancel(String message, Throwable cause) {
            listener.onClose(Status.CANCELLED.withDescription(message).withCause(cause), new Metadata());
        }

        @Override
        public void halfClose() {
            listener.onClose(status, new Metadata());
        }

        @Override
        public void sendMessage(RequestT message) {
            recordedCall.request = message;
        }

        @Override
        public boolean isReady() {
            return true;
        }
    }

    private static final class RecordedCall {
        private final MethodDescriptor<?, ?> method;
        private final CallOptions callOptions;
        private Object request;

        private RecordedCall(MethodDescriptor<?, ?> method, CallOptions callOptions) {
            this.method = method;
            this.callOptions = callOptions;
        }
    }

    private static final class RecordingServerCall<RequestT, ResponseT> extends ServerCall<RequestT, ResponseT> {
        private final MethodDescriptor<RequestT, ResponseT> methodDescriptor;
        private final List<ResponseT> messages = new ArrayList<>();
        private boolean sentHeaders;
        private Status closedStatus;

        private RecordingServerCall(MethodDescriptor<RequestT, ResponseT> methodDescriptor) {
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
        public void sendMessage(ResponseT message) {
            messages.add(message);
        }

        @Override
        public void close(Status status, Metadata trailers) {
            closedStatus = status;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public MethodDescriptor<RequestT, ResponseT> getMethodDescriptor() {
            return methodDescriptor;
        }
    }
}
