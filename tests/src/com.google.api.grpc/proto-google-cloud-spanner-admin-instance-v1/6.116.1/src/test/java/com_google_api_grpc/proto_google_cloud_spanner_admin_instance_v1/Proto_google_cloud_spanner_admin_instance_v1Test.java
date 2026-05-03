/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_api_grpc.proto_google_cloud_spanner_admin_instance_v1;

import java.util.List;

import com.google.iam.v1.Binding;
import com.google.iam.v1.GetIamPolicyRequest;
import com.google.iam.v1.GetPolicyOptions;
import com.google.iam.v1.Policy;
import com.google.iam.v1.SetIamPolicyRequest;
import com.google.iam.v1.TestIamPermissionsRequest;
import com.google.iam.v1.TestIamPermissionsResponse;
import com.google.longrunning.Operation;
import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors;
import com.google.protobuf.FieldMask;
import com.google.protobuf.Timestamp;
import com.google.spanner.admin.instance.v1.AutoscalingConfig;
import com.google.spanner.admin.instance.v1.CommonProto;
import com.google.spanner.admin.instance.v1.CreateInstanceConfigMetadata;
import com.google.spanner.admin.instance.v1.CreateInstanceConfigRequest;
import com.google.spanner.admin.instance.v1.CreateInstanceMetadata;
import com.google.spanner.admin.instance.v1.CreateInstancePartitionMetadata;
import com.google.spanner.admin.instance.v1.CreateInstancePartitionRequest;
import com.google.spanner.admin.instance.v1.CreateInstanceRequest;
import com.google.spanner.admin.instance.v1.DeleteInstanceConfigRequest;
import com.google.spanner.admin.instance.v1.DeleteInstancePartitionRequest;
import com.google.spanner.admin.instance.v1.DeleteInstanceRequest;
import com.google.spanner.admin.instance.v1.FreeInstanceMetadata;
import com.google.spanner.admin.instance.v1.FulfillmentPeriod;
import com.google.spanner.admin.instance.v1.GetInstanceConfigRequest;
import com.google.spanner.admin.instance.v1.GetInstancePartitionRequest;
import com.google.spanner.admin.instance.v1.GetInstanceRequest;
import com.google.spanner.admin.instance.v1.Instance;
import com.google.spanner.admin.instance.v1.InstanceConfig;
import com.google.spanner.admin.instance.v1.InstanceConfigName;
import com.google.spanner.admin.instance.v1.InstanceName;
import com.google.spanner.admin.instance.v1.InstancePartition;
import com.google.spanner.admin.instance.v1.InstancePartitionName;
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
import com.google.spanner.admin.instance.v1.MoveInstanceMetadata;
import com.google.spanner.admin.instance.v1.MoveInstanceRequest;
import com.google.spanner.admin.instance.v1.MoveInstanceResponse;
import com.google.spanner.admin.instance.v1.OperationProgress;
import com.google.spanner.admin.instance.v1.ProjectName;
import com.google.spanner.admin.instance.v1.ReplicaComputeCapacity;
import com.google.spanner.admin.instance.v1.ReplicaInfo;
import com.google.spanner.admin.instance.v1.ReplicaSelection;
import com.google.spanner.admin.instance.v1.SpannerInstanceAdminProto;
import com.google.spanner.admin.instance.v1.UpdateInstanceConfigMetadata;
import com.google.spanner.admin.instance.v1.UpdateInstanceConfigRequest;
import com.google.spanner.admin.instance.v1.UpdateInstanceMetadata;
import com.google.spanner.admin.instance.v1.UpdateInstancePartitionMetadata;
import com.google.spanner.admin.instance.v1.UpdateInstancePartitionRequest;
import com.google.spanner.admin.instance.v1.UpdateInstanceRequest;
import com.google.type.Expr;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Proto_google_cloud_spanner_admin_instance_v1Test {
    private static final String PROJECT = "sample-project";
    private static final String INSTANCE_ID = "test-instance";
    private static final String CONFIG_ID = "regional-us-central1";
    private static final String PARTITION_ID = "read-only-partition";

    @Test
    void resourceNamesRoundTripThroughGeneratedHelpers() {
        ProjectName projectName = ProjectName.of(PROJECT);
        InstanceConfigName configName = InstanceConfigName.of(PROJECT, CONFIG_ID);
        InstanceName instanceName = InstanceName.of(PROJECT, INSTANCE_ID);
        InstancePartitionName partitionName = InstancePartitionName.of(PROJECT, INSTANCE_ID, PARTITION_ID);

        assertThat(projectName.toString()).isEqualTo("projects/" + PROJECT);
        assertThat(ProjectName.parse(projectName.toString())).isEqualTo(projectName);
        assertThat(projectName.toBuilder().setProject("other-project").build().toString())
                .isEqualTo("projects/other-project");

        assertThat(InstanceConfigName.isParsableFrom(configName.toString())).isTrue();
        assertThat(InstanceConfigName.parse(configName.toString()).getFieldValuesMap())
                .containsEntry("project", PROJECT)
                .containsEntry("instance_config", CONFIG_ID);
        assertThat(InstanceConfigName.format(PROJECT, CONFIG_ID)).isEqualTo(configName.toString());

        assertThat(InstanceName.parse(instanceName.toString()).getInstance()).isEqualTo(INSTANCE_ID);
        assertThat(InstanceName.toStringList(List.of(instanceName))).containsExactly(instanceName.toString());
        assertThat(InstanceName.parseList(List.of(instanceName.toString()))).containsExactly(instanceName);

        assertThat(InstancePartitionName.parse(partitionName.toString()).getInstancePartition())
                .isEqualTo(PARTITION_ID);
        assertThat(partitionName.getFieldValue("instance_partition")).isEqualTo(PARTITION_ID);
    }

    @Test
    void descriptorsExposeCommonMessagesAndInstanceAdminService() {
        Descriptors.FileDescriptor commonDescriptor = CommonProto.getDescriptor();
        Descriptors.FileDescriptor adminDescriptor = SpannerInstanceAdminProto.getDescriptor();

        assertThat(commonDescriptor.findMessageTypeByName("OperationProgress").getFields())
                .extracting(Descriptors.FieldDescriptor::getName)
                .containsExactly("progress_percent", "start_time", "end_time");
        assertThat(adminDescriptor.findMessageTypeByName("Instance")
                .findFieldByName("autoscaling_config")
                .getMessageType()
                .getFullName())
                .isEqualTo("google.spanner.admin.instance.v1.AutoscalingConfig");
        assertThat(adminDescriptor.findServiceByName("InstanceAdmin").getMethods())
                .extracting(Descriptors.MethodDescriptor::getName)
                .contains(
                        "ListInstanceConfigs",
                        "CreateInstanceConfig",
                        "ListInstances",
                        "CreateInstance",
                        "CreateInstancePartition",
                        "MoveInstance");
    }

    @Test
    void buildsInstanceConfigWithReplicasLabelsAndEnumFields() {
        ReplicaInfo leaderReplica = ReplicaInfo.newBuilder()
                .setLocation("us-central1")
                .setType(ReplicaInfo.ReplicaType.READ_WRITE)
                .setDefaultLeaderLocation(true)
                .build();
        ReplicaInfo readOnlyReplica = ReplicaInfo.newBuilder()
                .setLocation("us-east1")
                .setType(ReplicaInfo.ReplicaType.READ_ONLY)
                .build();

        InstanceConfig instanceConfig = InstanceConfig.newBuilder()
                .setName(InstanceConfigName.format(PROJECT, CONFIG_ID))
                .setDisplayName("Regional test config")
                .setConfigType(InstanceConfig.Type.USER_MANAGED)
                .addReplicas(leaderReplica)
                .addOptionalReplicas(readOnlyReplica)
                .setBaseConfig(InstanceConfigName.format(PROJECT, "regional-us-east1"))
                .putLabels("env", "test")
                .putLabels("team", "spanner")
                .setEtag("config-etag")
                .addLeaderOptions("us-central1")
                .setReconciling(true)
                .setState(InstanceConfig.State.CREATING)
                .setFreeInstanceAvailability(InstanceConfig.FreeInstanceAvailability.AVAILABLE)
                .setQuorumType(InstanceConfig.QuorumType.REGION)
                .setStorageLimitPerProcessingUnit(1024L)
                .build();

        assertThat(instanceConfig.getName()).isEqualTo(InstanceConfigName.format(PROJECT, CONFIG_ID));
        assertThat(instanceConfig.getReplicasList()).containsExactly(leaderReplica);
        assertThat(instanceConfig.getOptionalReplicas(0).getType()).isEqualTo(ReplicaInfo.ReplicaType.READ_ONLY);
        assertThat(instanceConfig.getLabelsMap()).containsEntry("env", "test").containsEntry("team", "spanner");
        assertThat(instanceConfig.getLeaderOptionsList()).containsExactly("us-central1");
        assertThat(instanceConfig.getState()).isEqualTo(InstanceConfig.State.CREATING);
        assertThat(instanceConfig.getConfigTypeValue()).isEqualTo(InstanceConfig.Type.USER_MANAGED.getNumber());
        assertThat(instanceConfig.toBuilder().setState(InstanceConfig.State.READY).build().getState())
                .isEqualTo(InstanceConfig.State.READY);
    }

    @Test
    void buildsAutoscaledInstanceWithOneofComputeCapacityAndFreeMetadata() {
        Timestamp createdAt = Timestamp.newBuilder().setSeconds(1_700_000_000L).build();
        Timestamp updatedAt = Timestamp.newBuilder().setSeconds(1_700_000_100L).build();
        AutoscalingConfig.AutoscalingLimits limits = AutoscalingConfig.AutoscalingLimits.newBuilder()
                .setMinProcessingUnits(1_000)
                .setMaxProcessingUnits(4_000)
                .build();
        AutoscalingConfig.AutoscalingTargets targets = AutoscalingConfig.AutoscalingTargets.newBuilder()
                .setHighPriorityCpuUtilizationPercent(65)
                .setTotalCpuUtilizationPercent(75)
                .setStorageUtilizationPercent(80)
                .build();
        ReplicaSelection selection = ReplicaSelection.newBuilder().setLocation("us-east1").build();
        AutoscalingConfig.AsymmetricAutoscalingOption.AutoscalingConfigOverrides overrides =
                AutoscalingConfig.AsymmetricAutoscalingOption.AutoscalingConfigOverrides.newBuilder()
                        .setAutoscalingLimits(AutoscalingConfig.AutoscalingLimits.newBuilder()
                                .setMinProcessingUnits(500)
                                .setMaxProcessingUnits(2_000))
                        .setAutoscalingTargetHighPriorityCpuUtilizationPercent(55)
                        .setAutoscalingTargetTotalCpuUtilizationPercent(60)
                        .setDisableHighPriorityCpuAutoscaling(false)
                        .setDisableTotalCpuAutoscaling(false)
                        .build();
        AutoscalingConfig autoscalingConfig = AutoscalingConfig.newBuilder()
                .setAutoscalingLimits(limits)
                .setAutoscalingTargets(targets)
                .addAsymmetricAutoscalingOptions(AutoscalingConfig.AsymmetricAutoscalingOption.newBuilder()
                        .setReplicaSelection(selection)
                        .setOverrides(overrides))
                .build();
        ReplicaComputeCapacity replicaCapacity = ReplicaComputeCapacity.newBuilder()
                .setReplicaSelection(selection)
                .setProcessingUnits(1_000)
                .build();
        FreeInstanceMetadata freeMetadata = FreeInstanceMetadata.newBuilder()
                .setExpireTime(updatedAt)
                .setUpgradeTime(updatedAt)
                .setExpireBehavior(FreeInstanceMetadata.ExpireBehavior.FREE_TO_PROVISIONED)
                .build();

        Instance instance = Instance.newBuilder()
                .setName(InstanceName.format(PROJECT, INSTANCE_ID))
                .setConfig(InstanceConfigName.format(PROJECT, CONFIG_ID))
                .setDisplayName("Integration Test")
                .setProcessingUnits(1_000)
                .addReplicaComputeCapacity(replicaCapacity)
                .setAutoscalingConfig(autoscalingConfig)
                .setState(Instance.State.READY)
                .putLabels("component", "admin")
                .setInstanceType(Instance.InstanceType.FREE_INSTANCE)
                .setCreateTime(createdAt)
                .setUpdateTime(updatedAt)
                .setFreeInstanceMetadata(freeMetadata)
                .setEdition(Instance.Edition.ENTERPRISE)
                .setDefaultBackupScheduleType(Instance.DefaultBackupScheduleType.NONE)
                .build();

        assertThat(instance.getProcessingUnits()).isEqualTo(1_000);
        assertThat(instance.getAutoscalingConfig().getAutoscalingLimits().getMinLimitCase())
                .isEqualTo(AutoscalingConfig.AutoscalingLimits.MinLimitCase.MIN_PROCESSING_UNITS);
        assertThat(instance.getAutoscalingConfig().getAutoscalingLimits().getMaxLimitCase())
                .isEqualTo(AutoscalingConfig.AutoscalingLimits.MaxLimitCase.MAX_PROCESSING_UNITS);
        assertThat(instance.getAutoscalingConfig().getAsymmetricAutoscalingOptions(0).getOverrides()
                .getAutoscalingTargetTotalCpuUtilizationPercent())
                .isEqualTo(60);
        assertThat(instance.getReplicaComputeCapacity(0).getComputeCapacityCase())
                .isEqualTo(ReplicaComputeCapacity.ComputeCapacityCase.PROCESSING_UNITS);
        assertThat(instance.getLabelsMap()).containsEntry("component", "admin");
        assertThat(instance.getFreeInstanceMetadata().getExpireBehavior())
                .isEqualTo(FreeInstanceMetadata.ExpireBehavior.FREE_TO_PROVISIONED);
        assertThat(instance.getDefaultBackupScheduleType()).isEqualTo(Instance.DefaultBackupScheduleType.NONE);
    }

    @Test
    void createsInstanceAdminRequestsResponsesAndOperationMetadata() {
        InstanceConfig config = InstanceConfig.newBuilder()
                .setName(InstanceConfigName.format(PROJECT, CONFIG_ID))
                .setDisplayName("Regional test config")
                .build();
        Instance instance = Instance.newBuilder()
                .setName(InstanceName.format(PROJECT, INSTANCE_ID))
                .setConfig(config.getName())
                .setDisplayName("Integration Test")
                .setNodeCount(1)
                .build();
        FieldMask displayNameMask = FieldMask.newBuilder().addPaths("display_name").build();
        Timestamp startTime = Timestamp.newBuilder().setSeconds(1_700_000_000L).build();
        OperationProgress progress = OperationProgress.newBuilder()
                .setProgressPercent(42)
                .setStartTime(startTime)
                .setEndTime(Timestamp.newBuilder().setSeconds(1_700_000_060L))
                .build();
        Operation operation = Operation.newBuilder()
                .setName(config.getName() + "/operations/create-config")
                .setDone(false)
                .build();

        ListInstanceConfigsRequest listConfigs = ListInstanceConfigsRequest.newBuilder()
                .setParent(ProjectName.format(PROJECT))
                .setPageSize(10)
                .setPageToken("next-config-page")
                .build();
        ListInstanceConfigsResponse listConfigsResponse = ListInstanceConfigsResponse.newBuilder()
                .addInstanceConfigs(config)
                .setNextPageToken("next-config-page")
                .build();
        CreateInstanceConfigRequest createConfig = CreateInstanceConfigRequest.newBuilder()
                .setParent(ProjectName.format(PROJECT))
                .setInstanceConfig(config)
                .setInstanceConfigId(CONFIG_ID)
                .setValidateOnly(true)
                .build();
        UpdateInstanceConfigRequest updateConfig = UpdateInstanceConfigRequest.newBuilder()
                .setInstanceConfig(config)
                .setUpdateMask(displayNameMask)
                .setValidateOnly(true)
                .build();
        DeleteInstanceConfigRequest deleteConfig = DeleteInstanceConfigRequest.newBuilder()
                .setName(config.getName())
                .setEtag("config-etag")
                .setValidateOnly(true)
                .build();
        ListInstanceConfigOperationsRequest listConfigOperations = ListInstanceConfigOperationsRequest.newBuilder()
                .setParent(ProjectName.format(PROJECT))
                .setFilter("done=false")
                .setPageSize(5)
                .build();
        ListInstanceConfigOperationsResponse listConfigOperationsResponse =
                ListInstanceConfigOperationsResponse.newBuilder()
                        .addOperations(operation)
                        .setNextPageToken("next-operation-page")
                        .build();
        GetInstanceConfigRequest getConfig = GetInstanceConfigRequest.newBuilder().setName(config.getName()).build();
        CreateInstanceConfigMetadata createConfigMetadata = CreateInstanceConfigMetadata.newBuilder()
                .setInstanceConfig(config)
                .setProgress(progress)
                .setCancelTime(startTime)
                .build();
        UpdateInstanceConfigMetadata updateConfigMetadata = UpdateInstanceConfigMetadata.newBuilder()
                .setInstanceConfig(config)
                .setProgress(progress)
                .setCancelTime(startTime)
                .build();

        ListInstancesRequest listInstances = ListInstancesRequest.newBuilder()
                .setParent(ProjectName.format(PROJECT))
                .setFilter("labels.component:admin")
                .setInstanceDeadline(Timestamp.newBuilder().setSeconds(30L))
                .build();
        ListInstancesResponse listInstancesResponse = ListInstancesResponse.newBuilder()
                .addInstances(instance)
                .addUnreachable("projects/other-project")
                .setNextPageToken("next-instance-page")
                .build();
        CreateInstanceRequest createInstance = CreateInstanceRequest.newBuilder()
                .setParent(ProjectName.format(PROJECT))
                .setInstance(instance)
                .setInstanceId(INSTANCE_ID)
                .build();
        UpdateInstanceRequest updateInstance = UpdateInstanceRequest.newBuilder()
                .setInstance(instance)
                .setFieldMask(displayNameMask)
                .build();
        DeleteInstanceRequest deleteInstance = DeleteInstanceRequest.newBuilder().setName(instance.getName()).build();
        GetInstanceRequest getInstance = GetInstanceRequest.newBuilder().setName(instance.getName()).build();
        CreateInstanceMetadata createInstanceMetadata = CreateInstanceMetadata.newBuilder()
                .setInstance(instance)
                .setStartTime(startTime)
                .setExpectedFulfillmentPeriod(FulfillmentPeriod.FULFILLMENT_PERIOD_NORMAL)
                .build();
        UpdateInstanceMetadata updateInstanceMetadata = UpdateInstanceMetadata.newBuilder()
                .setInstance(instance)
                .setStartTime(startTime)
                .setExpectedFulfillmentPeriod(FulfillmentPeriod.FULFILLMENT_PERIOD_EXTENDED)
                .build();

        assertThat(listConfigs.getParent()).isEqualTo(ProjectName.format(PROJECT));
        assertThat(listConfigsResponse.getInstanceConfigsList()).containsExactly(config);
        assertThat(createConfig.getInstanceConfigId()).isEqualTo(CONFIG_ID);
        assertThat(updateConfig.getUpdateMask().getPathsList()).containsExactly("display_name");
        assertThat(deleteConfig.getEtag()).isEqualTo("config-etag");
        assertThat(listConfigOperations.getFilter()).isEqualTo("done=false");
        assertThat(listConfigOperationsResponse.getOperations(0).getName()).contains("create-config");
        assertThat(getConfig.getName()).isEqualTo(config.getName());
        assertThat(createConfigMetadata.getProgress().getProgressPercent()).isEqualTo(42);
        assertThat(updateConfigMetadata.getInstanceConfig()).isEqualTo(config);

        assertThat(listInstances.getInstanceDeadline().getSeconds()).isEqualTo(30L);
        assertThat(listInstancesResponse.getInstancesList()).containsExactly(instance);
        assertThat(listInstancesResponse.getUnreachableList()).containsExactly("projects/other-project");
        assertThat(createInstance.getInstanceId()).isEqualTo(INSTANCE_ID);
        assertThat(updateInstance.getFieldMask()).isEqualTo(displayNameMask);
        assertThat(deleteInstance.getName()).isEqualTo(instance.getName());
        assertThat(getInstance.getName()).isEqualTo(instance.getName());
        assertThat(createInstanceMetadata.getExpectedFulfillmentPeriod())
                .isEqualTo(FulfillmentPeriod.FULFILLMENT_PERIOD_NORMAL);
        assertThat(updateInstanceMetadata.getExpectedFulfillmentPeriod())
                .isEqualTo(FulfillmentPeriod.FULFILLMENT_PERIOD_EXTENDED);
    }

    @Test
    void createsIamPolicyRequestsForInstanceAdminResources() {
        String instanceResource = InstanceName.format(PROJECT, INSTANCE_ID);
        Binding adminBinding = Binding.newBuilder()
                .setRole("roles/spanner.admin")
                .addMembers("user:admin@example.com")
                .build();
        Binding conditionalReaderBinding = Binding.newBuilder()
                .setRole("roles/spanner.viewer")
                .addMembers("group:readers@example.com")
                .setCondition(Expr.newBuilder()
                        .setTitle("limited-access")
                        .setDescription("Only grant viewer access for approved requests.")
                        .setExpression("request.auth.claims.approved == true"))
                .build();
        Policy policy = Policy.newBuilder()
                .setVersion(3)
                .addBindings(adminBinding)
                .addBindings(conditionalReaderBinding)
                .setEtag(ByteString.copyFromUtf8("policy-etag"))
                .build();

        SetIamPolicyRequest setPolicy = SetIamPolicyRequest.newBuilder()
                .setResource(instanceResource)
                .setPolicy(policy)
                .setUpdateMask(FieldMask.newBuilder().addPaths("bindings").addPaths("etag"))
                .build();
        GetIamPolicyRequest getPolicy = GetIamPolicyRequest.newBuilder()
                .setResource(instanceResource)
                .setOptions(GetPolicyOptions.newBuilder().setRequestedPolicyVersion(3))
                .build();
        TestIamPermissionsRequest testPermissions = TestIamPermissionsRequest.newBuilder()
                .setResource(instanceResource)
                .addPermissions("spanner.instances.get")
                .addPermissions("spanner.instances.update")
                .build();
        TestIamPermissionsResponse permissionsResponse = TestIamPermissionsResponse.newBuilder()
                .addPermissions("spanner.instances.get")
                .build();
        Descriptors.ServiceDescriptor instanceAdminService =
                SpannerInstanceAdminProto.getDescriptor().findServiceByName("InstanceAdmin");

        assertThat(setPolicy.getResource()).isEqualTo(instanceResource);
        assertThat(setPolicy.getPolicy().getVersion()).isEqualTo(3);
        assertThat(setPolicy.getPolicy().getBindingsList()).containsExactly(adminBinding, conditionalReaderBinding);
        assertThat(setPolicy.getPolicy().getBindings(1).getCondition().getExpression())
                .isEqualTo("request.auth.claims.approved == true");
        assertThat(setPolicy.getUpdateMask().getPathsList()).containsExactly("bindings", "etag");
        assertThat(getPolicy.getOptions().getRequestedPolicyVersion()).isEqualTo(3);
        assertThat(testPermissions.getPermissionsList())
                .containsExactly("spanner.instances.get", "spanner.instances.update");
        assertThat(permissionsResponse.getPermissionsList()).containsExactly("spanner.instances.get");
        assertThat(instanceAdminService.findMethodByName("SetIamPolicy").getInputType().getFullName())
                .isEqualTo("google.iam.v1.SetIamPolicyRequest");
        assertThat(instanceAdminService.findMethodByName("GetIamPolicy").getOutputType().getFullName())
                .isEqualTo("google.iam.v1.Policy");
        assertThat(instanceAdminService.findMethodByName("TestIamPermissions").getOutputType().getFullName())
                .isEqualTo("google.iam.v1.TestIamPermissionsResponse");
    }

    @Test
    void buildsProcessingUnitInstancePartitionWithAutoscalingAndBackupReferences() {
        AutoscalingConfig autoscalingConfig = AutoscalingConfig.newBuilder()
                .setAutoscalingLimits(AutoscalingConfig.AutoscalingLimits.newBuilder()
                        .setMinProcessingUnits(300)
                        .setMaxProcessingUnits(1_200))
                .setAutoscalingTargets(AutoscalingConfig.AutoscalingTargets.newBuilder()
                        .setHighPriorityCpuUtilizationPercent(60)
                        .setStorageUtilizationPercent(85))
                .build();
        String backup = "projects/sample-project/instances/test-instance/backups/nightly";

        InstancePartition partition = InstancePartition.newBuilder()
                .setName(InstancePartitionName.format(PROJECT, INSTANCE_ID, PARTITION_ID))
                .setConfig(InstanceConfigName.format(PROJECT, CONFIG_ID))
                .setDisplayName("Autoscaled Partition")
                .setProcessingUnits(600)
                .setAutoscalingConfig(autoscalingConfig)
                .setState(InstancePartition.State.READY)
                .addReferencingBackups(backup)
                .build();
        InstancePartition capacityCleared = partition.toBuilder().clearProcessingUnits().build();

        assertThat(partition.getComputeCapacityCase())
                .isEqualTo(InstancePartition.ComputeCapacityCase.PROCESSING_UNITS);
        assertThat(partition.hasProcessingUnits()).isTrue();
        assertThat(partition.hasNodeCount()).isFalse();
        assertThat(partition.getProcessingUnits()).isEqualTo(600);
        assertThat(partition.hasAutoscalingConfig()).isTrue();
        assertThat(partition.getAutoscalingConfig().getAutoscalingLimits().getMaxLimitCase())
                .isEqualTo(AutoscalingConfig.AutoscalingLimits.MaxLimitCase.MAX_PROCESSING_UNITS);
        assertThat(partition.getAutoscalingConfig().getAutoscalingTargets().getStorageUtilizationPercent())
                .isEqualTo(85);
        assertThat(partition.getReferencingBackupsList()).containsExactly(backup);
        assertThat(capacityCleared.getComputeCapacityCase())
                .isEqualTo(InstancePartition.ComputeCapacityCase.COMPUTECAPACITY_NOT_SET);
        assertThat(capacityCleared.hasAutoscalingConfig()).isTrue();
    }

    @Test
    void createsInstancePartitionMessagesAndMoveInstanceMetadata() {
        FieldMask partitionMask = FieldMask.newBuilder().addPaths("display_name").addPaths("node_count").build();
        Timestamp startTime = Timestamp.newBuilder().setSeconds(1_700_000_000L).build();
        InstancePartition partition = InstancePartition.newBuilder()
                .setName(InstancePartitionName.format(PROJECT, INSTANCE_ID, PARTITION_ID))
                .setConfig(InstanceConfigName.format(PROJECT, CONFIG_ID))
                .setDisplayName("Read Only Partition")
                .setNodeCount(2)
                .setState(InstancePartition.State.READY)
                .setCreateTime(startTime)
                .setUpdateTime(Timestamp.newBuilder().setSeconds(1_700_000_100L))
                .addReferencingDatabases("projects/sample-project/instances/test-instance/databases/app")
                .setEtag("partition-etag")
                .build();
        Operation partitionOperation = Operation.newBuilder()
                .setName(partition.getName() + "/operations/create-partition")
                .setDone(true)
                .build();

        CreateInstancePartitionRequest createPartition = CreateInstancePartitionRequest.newBuilder()
                .setParent(InstanceName.format(PROJECT, INSTANCE_ID))
                .setInstancePartitionId(PARTITION_ID)
                .setInstancePartition(partition)
                .build();
        UpdateInstancePartitionRequest updatePartition = UpdateInstancePartitionRequest.newBuilder()
                .setInstancePartition(partition)
                .setFieldMask(partitionMask)
                .build();
        DeleteInstancePartitionRequest deletePartition = DeleteInstancePartitionRequest.newBuilder()
                .setName(partition.getName())
                .setEtag("partition-etag")
                .build();
        GetInstancePartitionRequest getPartition = GetInstancePartitionRequest.newBuilder()
                .setName(partition.getName())
                .build();
        ListInstancePartitionsRequest listPartitions = ListInstancePartitionsRequest.newBuilder()
                .setParent(InstanceName.format(PROJECT, INSTANCE_ID))
                .setPageSize(3)
                .setPageToken("partition-token")
                .build();
        ListInstancePartitionsResponse listPartitionsResponse = ListInstancePartitionsResponse.newBuilder()
                .addInstancePartitions(partition)
                .setNextPageToken("next-partition-page")
                .build();
        ListInstancePartitionOperationsRequest listPartitionOperations =
                ListInstancePartitionOperationsRequest.newBuilder()
                .setParent(InstanceName.format(PROJECT, INSTANCE_ID))
                .setFilter("metadata.instance_partition.name:" + PARTITION_ID)
                .setPageSize(2)
                .build();
        ListInstancePartitionOperationsResponse listPartitionOperationsResponse =
                ListInstancePartitionOperationsResponse.newBuilder()
                        .addOperations(partitionOperation)
                        .setNextPageToken("next-partition-operation-page")
                        .build();
        CreateInstancePartitionMetadata createPartitionMetadata = CreateInstancePartitionMetadata.newBuilder()
                .setInstancePartition(partition)
                .setStartTime(startTime)
                .setCancelTime(startTime)
                .setEndTime(Timestamp.newBuilder().setSeconds(1_700_000_300L))
                .build();
        UpdateInstancePartitionMetadata updatePartitionMetadata = UpdateInstancePartitionMetadata.newBuilder()
                .setInstancePartition(partition)
                .setStartTime(startTime)
                .build();
        MoveInstanceRequest moveRequest = MoveInstanceRequest.newBuilder()
                .setName(InstanceName.format(PROJECT, INSTANCE_ID))
                .setTargetConfig(InstanceConfigName.format(PROJECT, "regional-us-east1"))
                .build();
        MoveInstanceMetadata moveMetadata = MoveInstanceMetadata.newBuilder()
                .setTargetConfig(moveRequest.getTargetConfig())
                .setProgress(OperationProgress.newBuilder().setProgressPercent(100))
                .setCancelTime(startTime)
                .build();
        MoveInstanceResponse moveResponse = MoveInstanceResponse.newBuilder().build();

        assertThat(partition.getComputeCapacityCase()).isEqualTo(InstancePartition.ComputeCapacityCase.NODE_COUNT);
        assertThat(createPartition.getInstancePartitionId()).isEqualTo(PARTITION_ID);
        assertThat(updatePartition.getFieldMask().getPathsList()).containsExactly("display_name", "node_count");
        assertThat(deletePartition.getEtag()).isEqualTo("partition-etag");
        assertThat(getPartition.getName()).isEqualTo(partition.getName());
        assertThat(listPartitions.getPageToken()).isEqualTo("partition-token");
        assertThat(listPartitionsResponse.getInstancePartitionsList()).containsExactly(partition);
        assertThat(listPartitionOperations.getFilter()).contains(PARTITION_ID);
        assertThat(listPartitionOperationsResponse.getOperations(0)).isEqualTo(partitionOperation);
        assertThat(createPartitionMetadata.getEndTime().getSeconds()).isEqualTo(1_700_000_300L);
        assertThat(updatePartitionMetadata.getInstancePartition()).isEqualTo(partition);
        assertThat(moveRequest.getTargetConfig()).endsWith("regional-us-east1");
        assertThat(moveMetadata.getProgress().getProgressPercent()).isEqualTo(100);
        assertThat(moveResponse).isEqualTo(MoveInstanceResponse.getDefaultInstance());
    }
}
