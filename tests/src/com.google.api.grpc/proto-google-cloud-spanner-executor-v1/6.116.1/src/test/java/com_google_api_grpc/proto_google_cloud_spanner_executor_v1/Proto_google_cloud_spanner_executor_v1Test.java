/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_api_grpc.proto_google_cloud_spanner_executor_v1;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.longrunning.Operation;
import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.Descriptors.MethodDescriptor;
import com.google.protobuf.Descriptors.ServiceDescriptor;
import com.google.protobuf.Timestamp;
import com.google.rpc.Status;
import com.google.spanner.admin.database.v1.Backup;
import com.google.spanner.admin.database.v1.Database;
import com.google.spanner.admin.instance.v1.Instance;
import com.google.spanner.admin.instance.v1.InstanceConfig;
import com.google.spanner.admin.instance.v1.ReplicaInfo;
import com.google.spanner.executor.v1.AdaptMessageAction;
import com.google.spanner.executor.v1.AdminAction;
import com.google.spanner.executor.v1.AdminResult;
import com.google.spanner.executor.v1.BatchDmlAction;
import com.google.spanner.executor.v1.BatchPartition;
import com.google.spanner.executor.v1.ChangeStreamRecord;
import com.google.spanner.executor.v1.ChildPartitionsRecord;
import com.google.spanner.executor.v1.CloudBackupResponse;
import com.google.spanner.executor.v1.CloudDatabaseResponse;
import com.google.spanner.executor.v1.CloudExecutorProto;
import com.google.spanner.executor.v1.CloudInstanceConfigResponse;
import com.google.spanner.executor.v1.CloudInstanceResponse;
import com.google.spanner.executor.v1.ColumnMetadata;
import com.google.spanner.executor.v1.Concurrency;
import com.google.spanner.executor.v1.CreateCloudDatabaseAction;
import com.google.spanner.executor.v1.CreateCloudInstanceAction;
import com.google.spanner.executor.v1.CreateUserInstanceConfigAction;
import com.google.spanner.executor.v1.DataChangeRecord;
import com.google.spanner.executor.v1.DeleteUserInstanceConfigAction;
import com.google.spanner.executor.v1.DmlAction;
import com.google.spanner.executor.v1.ExecuteChangeStreamQuery;
import com.google.spanner.executor.v1.ExecutePartitionAction;
import com.google.spanner.executor.v1.FinishTransactionAction;
import com.google.spanner.executor.v1.GenerateDbPartitionsForQueryAction;
import com.google.spanner.executor.v1.GenerateDbPartitionsForReadAction;
import com.google.spanner.executor.v1.GetCloudInstanceConfigAction;
import com.google.spanner.executor.v1.HeartbeatRecord;
import com.google.spanner.executor.v1.KeyRange;
import com.google.spanner.executor.v1.KeySet;
import com.google.spanner.executor.v1.ListCloudInstanceConfigsAction;
import com.google.spanner.executor.v1.MutationAction;
import com.google.spanner.executor.v1.OperationResponse;
import com.google.spanner.executor.v1.PartitionedUpdateAction;
import com.google.spanner.executor.v1.QueryAction;
import com.google.spanner.executor.v1.QueryCancellationAction;
import com.google.spanner.executor.v1.QueryResult;
import com.google.spanner.executor.v1.ReadAction;
import com.google.spanner.executor.v1.ReadResult;
import com.google.spanner.executor.v1.SessionPoolOptions;
import com.google.spanner.executor.v1.SpannerAction;
import com.google.spanner.executor.v1.SpannerActionOutcome;
import com.google.spanner.executor.v1.SpannerAsyncActionRequest;
import com.google.spanner.executor.v1.SpannerAsyncActionResponse;
import com.google.spanner.executor.v1.SpannerOptions;
import com.google.spanner.executor.v1.StartBatchTransactionAction;
import com.google.spanner.executor.v1.StartTransactionAction;
import com.google.spanner.executor.v1.TableMetadata;
import com.google.spanner.executor.v1.TransactionExecutionOptions;
import com.google.spanner.executor.v1.UpdateUserInstanceConfigAction;
import com.google.spanner.executor.v1.Value;
import com.google.spanner.executor.v1.ValueList;
import com.google.spanner.executor.v1.WriteMutationsAction;
import com.google.spanner.v1.StructType;
import com.google.spanner.v1.Type;
import com.google.spanner.v1.TypeCode;
import java.nio.ByteBuffer;
import java.util.List;
import org.junit.jupiter.api.Test;

public class Proto_google_cloud_spanner_executor_v1Test {
    private static final String DATABASE_PATH = "projects/test-project/instances/test-instance/databases/test-db";

    @Test
    void valueMessagesSupportScalarNestedAndArrayRepresentations() throws Exception {
        Type int64Type = Type.newBuilder().setCode(TypeCode.INT64).build();
        Type stringType = Type.newBuilder().setCode(TypeCode.STRING).build();
        Type arrayType = Type.newBuilder().setCode(TypeCode.ARRAY).setArrayElementType(stringType).build();
        ValueList structValue = ValueList.newBuilder()
                .addValue(Value.newBuilder().setStringValue("Singer"))
                .addValue(Value.newBuilder().setIntValue(7L))
                .addValue(Value.newBuilder().setTimestampValue(timestamp(10L, 20)))
                .build();
        Value arrayValue = Value.newBuilder()
                .setArrayValue(ValueList.newBuilder()
                        .addValue(Value.newBuilder().setStringValue("alpha"))
                        .addValue(Value.newBuilder().setStringValue("beta")))
                .setArrayType(arrayType)
                .build();

        assertThat(Value.newBuilder().setIsNull(true).build().getValueTypeCase())
                .isEqualTo(Value.ValueTypeCase.IS_NULL);
        assertThat(Value.newBuilder().setBoolValue(true).build().getValueTypeCase())
                .isEqualTo(Value.ValueTypeCase.BOOL_VALUE);
        assertThat(Value.newBuilder().setDoubleValue(1.25D).build().getDoubleValue()).isEqualTo(1.25D);
        Value bytesValue = Value.newBuilder().setBytesValue(ByteString.copyFromUtf8("payload")).build();
        assertThat(bytesValue.getBytesValue().toStringUtf8()).isEqualTo("payload");
        assertThat(Value.newBuilder().setDateDaysValue(20_000).build().getValueTypeCase())
                .isEqualTo(Value.ValueTypeCase.DATE_DAYS_VALUE);
        assertThat(Value.newBuilder().setIsCommitTimestamp(true).build().hasIsCommitTimestamp()).isTrue();
        assertThat(Value.newBuilder().setStructValue(structValue).build().getStructValue().getValueList())
                .extracting(Value::getValueTypeCase)
                .containsExactly(Value.ValueTypeCase.STRING_VALUE, Value.ValueTypeCase.INT_VALUE,
                        Value.ValueTypeCase.TIMESTAMP_VALUE);
        assertThat(arrayValue.hasArrayType()).isTrue();
        assertThat(arrayValue.getArrayType()).isEqualTo(arrayType);
        assertThat(arrayValue.getArrayValue().getValueList()).extracting(Value::getStringValue)
                .containsExactly("alpha", "beta");

        Value serializedValue = Value.newBuilder().setIntValue(42L).setArrayType(int64Type).build();
        Value parsed = Value.parseFrom(serializedValue.toByteArray());
        assertThat(parsed.getIntValue()).isEqualTo(42L);
        assertThat(parsed.getArrayType().getCode()).isEqualTo(TypeCode.INT64);
    }

    @Test
    void queryReadDmlAndMutationActionsPreserveParametersKeysAndOptions() {
        Type int64Type = Type.newBuilder().setCode(TypeCode.INT64).build();
        QueryAction.Parameter singerId = QueryAction.Parameter.newBuilder()
                .setName("singerId")
                .setType(int64Type)
                .setValue(Value.newBuilder().setIntValue(1L))
                .build();
        QueryAction query = QueryAction.newBuilder()
                .setSql("SELECT SingerId, FirstName FROM Singers WHERE SingerId = @singerId")
                .addParams(singerId)
                .build();
        KeySet keySet = KeySet.newBuilder()
                .addPoint(ValueList.newBuilder().addValue(Value.newBuilder().setIntValue(1L)))
                .addRange(KeyRange.newBuilder()
                        .setStart(ValueList.newBuilder().addValue(Value.newBuilder().setIntValue(10L)))
                        .setLimit(ValueList.newBuilder().addValue(Value.newBuilder().setIntValue(20L)))
                        .setType(KeyRange.Type.CLOSED_OPEN))
                .build();
        ReadAction read = ReadAction.newBuilder()
                .setTable("Singers")
                .setIndex("SingersByLastName")
                .addAllColumn(List.of("SingerId", "FirstName", "LastName"))
                .setKeys(keySet)
                .setLimit(5)
                .build();
        MutationAction.InsertArgs insert = MutationAction.InsertArgs.newBuilder()
                .addColumn("SingerId")
                .addColumn("FirstName")
                .addType(int64Type)
                .addType(Type.newBuilder().setCode(TypeCode.STRING))
                .addValues(ValueList.newBuilder()
                        .addValue(Value.newBuilder().setIntValue(1L))
                        .addValue(Value.newBuilder().setStringValue("Marc")))
                .build();
        MutationAction mutation = MutationAction.newBuilder()
                .addMod(MutationAction.Mod.newBuilder().setTable("Singers").setInsert(insert))
                .addMod(MutationAction.Mod.newBuilder().setTable("Singers").setDeleteKeys(keySet))
                .build();
        DmlAction dml = DmlAction.newBuilder()
                .setUpdate(query)
                .setAutocommitIfSupported(true)
                .setLastStatement(false)
                .build();
        BatchDmlAction batchDml = BatchDmlAction.newBuilder().addUpdates(query).setLastStatements(true).build();

        assertThat(query.getParams(0).getValue().getIntValue()).isEqualTo(1L);
        assertThat(read.hasIndex()).isTrue();
        assertThat(read.getKeys().getRange(0).getType()).isEqualTo(KeyRange.Type.CLOSED_OPEN);
        assertThat(mutation.getMod(0).getInsert().getValues(0).getValue(1).getStringValue()).isEqualTo("Marc");
        assertThat(mutation.getMod(1).getDeleteKeys().getPointCount()).isEqualTo(1);
        assertThat(dml.hasAutocommitIfSupported()).isTrue();
        assertThat(dml.getAutocommitIfSupported()).isTrue();
        assertThat(dml.hasLastStatement()).isTrue();
        assertThat(batchDml.hasLastStatements()).isTrue();
    }

    @Test
    void actionEnvelopesPreserveOneofSelectionOptionsAndOutcomeFields() throws Exception {
        QueryAction query = QueryAction.newBuilder().setSql("SELECT 1").build();
        ReadAction read = ReadAction.newBuilder().setTable("Singers").setKeys(KeySet.newBuilder().setAll(true)).build();
        SpannerOptions options = SpannerOptions.newBuilder()
                .setSessionPoolOptions(SessionPoolOptions.newBuilder().setUseMultiplexed(true))
                .build();
        SpannerAction queryAction = SpannerAction.newBuilder()
                .setDatabasePath(DATABASE_PATH)
                .setSpannerOptions(options)
                .setQuery(query)
                .build();
        SpannerAction readAction = queryAction.toBuilder().setRead(read).build();
        SpannerActionOutcome outcome = SpannerActionOutcome.newBuilder()
                .setStatus(Status.newBuilder().setCode(0).setMessage("OK"))
                .setCommitTime(timestamp(123L, 456))
                .setTransactionRestarted(false)
                .setBatchTxnId(ByteString.copyFromUtf8("batch-txn"))
                .addDmlRowsModified(3L)
                .setSnapshotIsolationTxnReadTimestamp(987L)
                .build();
        SpannerAsyncActionRequest request = SpannerAsyncActionRequest.newBuilder()
                .setActionId(101)
                .setAction(readAction)
                .build();
        SpannerAsyncActionResponse response = SpannerAsyncActionResponse.newBuilder()
                .setActionId(request.getActionId())
                .setOutcome(outcome)
                .build();

        assertThat(queryAction.getActionCase()).isEqualTo(SpannerAction.ActionCase.QUERY);
        assertThat(readAction.getActionCase()).isEqualTo(SpannerAction.ActionCase.READ);
        assertThat(readAction.hasQuery()).isFalse();
        assertThat(readAction.getSpannerOptions().getSessionPoolOptions().getUseMultiplexed()).isTrue();
        assertThat(SpannerAsyncActionRequest.parseFrom(request.toByteArray())).isEqualTo(request);
        assertThat(SpannerAsyncActionResponse.parseFrom(ByteBuffer.wrap(response.toByteArray())).getOutcome())
                .isEqualTo(outcome);
        assertThat(response.getOutcome().getDmlRowsModifiedList()).containsExactly(3L);
    }

    @Test
    void transactionAndPartitionActionsCoverConcurrencyModesAndBatchFlowMessages() {
        Concurrency batchConcurrency = Concurrency.newBuilder()
                .setBatch(true)
                .setSnapshotEpochRead(true)
                .setSnapshotEpochRootTable("RootTable")
                .setBatchReadTimestampMicros(1_000L)
                .build();
        StartTransactionAction start = StartTransactionAction.newBuilder()
                .setConcurrency(batchConcurrency)
                .setTransactionSeed("worker-1-op-2")
                .setExecutionOptions(TransactionExecutionOptions.newBuilder()
                        .setOptimistic(true)
                        .setSerializableOptimistic(true)
                        .setExcludeTxnFromChangeStreams(true))
                .addTable(TableMetadata.newBuilder()
                        .setName("Singers")
                        .addColumn(column("SingerId", TypeCode.INT64))
                        .addKeyColumn(column("SingerId", TypeCode.INT64)))
                .build();
        StartBatchTransactionAction startNewBatch = StartBatchTransactionAction.newBuilder()
                .setBatchTxnTime(timestamp(500L, 0))
                .setCloudDatabaseRole("reader")
                .build();
        StartBatchTransactionAction attachExistingBatch = startNewBatch.toBuilder()
                .setTid(ByteString.copyFromUtf8("existing-transaction-id"))
                .build();
        BatchPartition partition = BatchPartition.newBuilder()
                .setPartition(ByteString.copyFromUtf8("serialized-partition"))
                .setPartitionToken(ByteString.copyFromUtf8("token"))
                .setTable("Singers")
                .setIndex("SingersByLastName")
                .build();
        GenerateDbPartitionsForReadAction readPartitions = GenerateDbPartitionsForReadAction.newBuilder()
                .setRead(ReadAction.newBuilder().setTable("Singers").setKeys(KeySet.newBuilder().setAll(true)))
                .addTable(start.getTable(0))
                .setDesiredBytesPerPartition(1_024L)
                .setMaxPartitionCount(4L)
                .build();
        GenerateDbPartitionsForQueryAction queryPartitions = GenerateDbPartitionsForQueryAction.newBuilder()
                .setQuery(QueryAction.newBuilder().setSql("SELECT * FROM Singers"))
                .setDesiredBytesPerPartition(2_048L)
                .build();
        ExecutePartitionAction executePartition = ExecutePartitionAction.newBuilder().setPartition(partition).build();
        PartitionedUpdateAction partitionedUpdate = PartitionedUpdateAction.newBuilder()
                .setUpdate(QueryAction.newBuilder().setSql("UPDATE Singers SET Active = TRUE WHERE SingerId = 1"))
                .setOptions(PartitionedUpdateAction.ExecutePartitionedUpdateOptions.newBuilder().setTag("maintenance"))
                .build();

        assertThat(start.getConcurrency().getConcurrencyModeCase()).isEqualTo(Concurrency.ConcurrencyModeCase.BATCH);
        assertThat(start.getExecutionOptions().getSerializableOptimistic()).isTrue();
        assertThat(startNewBatch.getParamCase()).isEqualTo(StartBatchTransactionAction.ParamCase.BATCH_TXN_TIME);
        assertThat(attachExistingBatch.getParamCase()).isEqualTo(StartBatchTransactionAction.ParamCase.TID);
        assertThat(attachExistingBatch.hasBatchTxnTime()).isFalse();
        assertThat(readPartitions.hasDesiredBytesPerPartition()).isTrue();
        assertThat(queryPartitions.getDesiredBytesPerPartition()).isEqualTo(2_048L);
        assertThat(executePartition.getPartition().getTable()).isEqualTo("Singers");
        assertThat(partitionedUpdate.getOptions().getTag()).isEqualTo("maintenance");
    }

    @Test
    void adminActionsAndResultsPreserveMapsOptionalFieldsAndExternalMessages() {
        CreateCloudInstanceAction createInstance = CreateCloudInstanceAction.newBuilder()
                .setProjectId("test-project")
                .setInstanceId("test-instance")
                .setInstanceConfigId("regional-us-central1")
                .setNodeCount(1)
                .putLabels("environment", "test")
                .setEdition(Instance.Edition.ENTERPRISE)
                .build();
        CreateCloudDatabaseAction createDatabase = CreateCloudDatabaseAction.newBuilder()
                .setProjectId("test-project")
                .setInstanceId("test-instance")
                .setDatabaseId("test-db")
                .addSdlStatement("CREATE TABLE Singers (SingerId INT64 NOT NULL) PRIMARY KEY (SingerId)")
                .setDialect("GOOGLESQL")
                .setProtoDescriptors(ByteString.copyFromUtf8("descriptor-set"))
                .build();
        AdminAction adminAction = AdminAction.newBuilder().setCreateCloudInstance(createInstance).build();
        AdminAction databaseAdminAction = adminAction.toBuilder().setCreateCloudDatabase(createDatabase).build();
        CloudDatabaseResponse databaseResponse = CloudDatabaseResponse.newBuilder()
                .addListedDatabases(Database.newBuilder().setName(DATABASE_PATH))
                .addListedDatabaseOperations(Operation.newBuilder().setName("operations/database-operation"))
                .setNextPageToken("next-db-page")
                .setDatabase(Database.newBuilder().setName(DATABASE_PATH))
                .build();
        AdminResult adminResult = AdminResult.newBuilder()
                .setDatabaseResponse(databaseResponse)
                .setBackupResponse(CloudBackupResponse.newBuilder()
                        .addListedBackups(Backup.newBuilder().setName("backup-1").setDatabase(DATABASE_PATH))
                        .setNextPageToken("next-backup-page"))
                .setOperationResponse(OperationResponse.newBuilder()
                        .setOperation(Operation.newBuilder().setName("operations/finished")))
                .setInstanceResponse(CloudInstanceResponse.newBuilder()
                        .addListedInstances(Instance.newBuilder()
                                .setName("projects/test-project/instances/test-instance")))
                .setInstanceConfigResponse(CloudInstanceConfigResponse.newBuilder()
                        .setInstanceConfig(InstanceConfig.newBuilder()
                                .setName("projects/test-project/instanceConfigs/regional-us-central1")))
                .build();

        assertThat(createInstance.hasNodeCount()).isTrue();
        assertThat(createInstance.getLabelsMap()).containsEntry("environment", "test");
        assertThat(adminAction.getActionCase()).isEqualTo(AdminAction.ActionCase.CREATE_CLOUD_INSTANCE);
        assertThat(databaseAdminAction.getActionCase()).isEqualTo(AdminAction.ActionCase.CREATE_CLOUD_DATABASE);
        assertThat(databaseAdminAction.hasCreateCloudInstance()).isFalse();
        assertThat(databaseAdminAction.getCreateCloudDatabase().hasDialect()).isTrue();
        assertThat(adminResult.getDatabaseResponse().getListedDatabases(0).getName()).isEqualTo(DATABASE_PATH);
        assertThat(adminResult.getBackupResponse().getNextPageToken()).isEqualTo("next-backup-page");
        assertThat(adminResult.getOperationResponse().getOperation().getName()).isEqualTo("operations/finished");
    }

    @Test
    void userInstanceConfigAdminActionsPreserveReplicaPagingAndLabels() {
        ReplicaInfo defaultLeader = ReplicaInfo.newBuilder()
                .setLocation("us-central1")
                .setType(ReplicaInfo.ReplicaType.READ_WRITE)
                .setDefaultLeaderLocation(true)
                .build();
        ReplicaInfo readOnlyReplica = ReplicaInfo.newBuilder()
                .setLocation("us-east1")
                .setType(ReplicaInfo.ReplicaType.READ_ONLY)
                .build();
        CreateUserInstanceConfigAction createUserConfig = CreateUserInstanceConfigAction.newBuilder()
                .setProjectId("test-project")
                .setUserConfigId("custom-config")
                .setBaseConfigId("nam3")
                .addReplicas(defaultLeader)
                .addReplicas(readOnlyReplica)
                .build();
        UpdateUserInstanceConfigAction updateUserConfig = UpdateUserInstanceConfigAction.newBuilder()
                .setProjectId("test-project")
                .setUserConfigId("custom-config")
                .setDisplayName("Custom test instance config")
                .putLabels("environment", "test")
                .build();
        ListCloudInstanceConfigsAction listConfigs = ListCloudInstanceConfigsAction.newBuilder()
                .setProjectId("test-project")
                .setPageSize(20)
                .setPageToken("next-config-page")
                .build();
        GetCloudInstanceConfigAction getConfig = GetCloudInstanceConfigAction.newBuilder()
                .setProjectId("test-project")
                .setInstanceConfigId("custom-config")
                .build();
        DeleteUserInstanceConfigAction deleteUserConfig = DeleteUserInstanceConfigAction.newBuilder()
                .setProjectId("test-project")
                .setUserConfigId("custom-config")
                .build();
        AdminAction createAction = AdminAction.newBuilder().setCreateUserInstanceConfig(createUserConfig).build();
        AdminAction updateAction = createAction.toBuilder().setUpdateUserInstanceConfig(updateUserConfig).build();
        AdminAction listAction = AdminAction.newBuilder().setListInstanceConfigs(listConfigs).build();
        AdminAction getAction = AdminAction.newBuilder().setGetCloudInstanceConfig(getConfig).build();
        AdminAction deleteAction = AdminAction.newBuilder().setDeleteUserInstanceConfig(deleteUserConfig).build();

        assertThat(createUserConfig.getReplicasList()).extracting(ReplicaInfo::getType)
                .containsExactly(ReplicaInfo.ReplicaType.READ_WRITE, ReplicaInfo.ReplicaType.READ_ONLY);
        assertThat(createUserConfig.getReplicas(0).getDefaultLeaderLocation()).isTrue();
        assertThat(createAction.getActionCase()).isEqualTo(AdminAction.ActionCase.CREATE_USER_INSTANCE_CONFIG);
        assertThat(updateAction.getActionCase()).isEqualTo(AdminAction.ActionCase.UPDATE_USER_INSTANCE_CONFIG);
        assertThat(updateAction.hasCreateUserInstanceConfig()).isFalse();
        assertThat(updateAction.getUpdateUserInstanceConfig().hasDisplayName()).isTrue();
        assertThat(updateAction.getUpdateUserInstanceConfig().getLabelsMap()).containsEntry("environment", "test");
        assertThat(listAction.getActionCase()).isEqualTo(AdminAction.ActionCase.LIST_INSTANCE_CONFIGS);
        assertThat(listAction.getListInstanceConfigs().hasPageSize()).isTrue();
        assertThat(listAction.getListInstanceConfigs().getPageToken()).isEqualTo("next-config-page");
        assertThat(getAction.getActionCase()).isEqualTo(AdminAction.ActionCase.GET_CLOUD_INSTANCE_CONFIG);
        assertThat(getAction.getGetCloudInstanceConfig().getInstanceConfigId()).isEqualTo("custom-config");
        assertThat(deleteAction.getActionCase()).isEqualTo(AdminAction.ActionCase.DELETE_USER_INSTANCE_CONFIG);
        assertThat(deleteAction.getDeleteUserInstanceConfig().getUserConfigId()).isEqualTo("custom-config");
    }

    @Test
    void resultsAndChangeStreamRecordsPreserveRowsTypesAndRecordOneofs() {
        StructType rowType = StructType.newBuilder()
                .addFields(StructType.Field.newBuilder().setName("SingerId").setType(type(TypeCode.INT64)))
                .addFields(StructType.Field.newBuilder().setName("Name").setType(type(TypeCode.STRING)))
                .build();
        ValueList row = ValueList.newBuilder()
                .addValue(Value.newBuilder().setIntValue(1L))
                .addValue(Value.newBuilder().setStringValue("Marc"))
                .build();
        ReadResult readResult = ReadResult.newBuilder()
                .setTable("Singers")
                .setIndex("SingersByLastName")
                .setRequestIndex(2)
                .addRow(row)
                .setRowType(rowType)
                .build();
        QueryResult queryResult = QueryResult.newBuilder().addRow(row).setRowType(rowType).build();
        DataChangeRecord dataChange = DataChangeRecord.newBuilder()
                .setCommitTime(timestamp(200L, 1))
                .setRecordSequence("00000001")
                .setTransactionId("txn-1")
                .setIsLastRecord(true)
                .setTable("Singers")
                .addColumnTypes(DataChangeRecord.ColumnType.newBuilder()
                        .setName("SingerId")
                        .setType("INT64")
                        .setIsPrimaryKey(true)
                        .setOrdinalPosition(1L))
                .addMods(DataChangeRecord.Mod.newBuilder()
                        .setKeys("{\"SingerId\":\"1\"}")
                        .setNewValues("{\"Name\":\"Marc\"}"))
                .setModType("INSERT")
                .setValueCaptureType("NEW_VALUES")
                .setRecordCount(1L)
                .setPartitionCount(1L)
                .setTransactionTag("tag-1")
                .build();
        ChangeStreamRecord dataRecord = ChangeStreamRecord.newBuilder().setDataChange(dataChange).build();
        ChangeStreamRecord childRecord = ChangeStreamRecord.newBuilder()
                .setChildPartition(ChildPartitionsRecord.newBuilder()
                        .setStartTime(timestamp(201L, 0))
                        .setRecordSequence("00000002")
                        .addChildPartitions(ChildPartitionsRecord.ChildPartition.newBuilder()
                                .setToken("child-token")
                                .addParentPartitionTokens("parent-token")))
                .build();
        ChangeStreamRecord heartbeatRecord = ChangeStreamRecord.newBuilder()
                .setHeartbeat(HeartbeatRecord.newBuilder().setHeartbeatTime(timestamp(202L, 0)))
                .build();
        ExecuteChangeStreamQuery changeStreamQuery = ExecuteChangeStreamQuery.newBuilder()
                .setName("SingersStream")
                .setStartTime(timestamp(100L, 0))
                .setEndTime(timestamp(300L, 0))
                .setPartitionToken("partition-token")
                .addAllReadOptions(List.of("partition_token=partition-token", "heartbeat_milliseconds=1000"))
                .setHeartbeatMilliseconds(1_000)
                .setDeadlineSeconds(30L)
                .setCloudDatabaseRole("reader")
                .build();

        assertThat(readResult.getRowType().getFieldsList()).extracting(StructType.Field::getName)
                .containsExactly("SingerId", "Name");
        assertThat(queryResult.getRow(0).getValue(1).getStringValue()).isEqualTo("Marc");
        assertThat(dataRecord.getRecordCase()).isEqualTo(ChangeStreamRecord.RecordCase.DATA_CHANGE);
        assertThat(childRecord.getRecordCase()).isEqualTo(ChangeStreamRecord.RecordCase.CHILD_PARTITION);
        assertThat(heartbeatRecord.getRecordCase()).isEqualTo(ChangeStreamRecord.RecordCase.HEARTBEAT);
        assertThat(changeStreamQuery.hasEndTime()).isTrue();
        assertThat(changeStreamQuery.getReadOptionsList()).contains("heartbeat_milliseconds=1000");
    }

    @Test
    void pgAdapterMessageActionsPreservePayloadAttachmentsAndExecutionMode() {
        AdaptMessageAction adapterMessage = AdaptMessageAction.newBuilder()
                .setDatabaseUri(DATABASE_PATH)
                .setProtocol("postgres")
                .setPayload(ByteString.copyFromUtf8("Parse/Bind/Execute payload"))
                .putAttachments("statement-name", "find-singer")
                .putAttachments("parameter-format", "text")
                .setQuery("SELECT SingerId FROM Singers WHERE SingerId = $1")
                .setPrepareThenExecute(true)
                .build();
        SpannerAction action = SpannerAction.newBuilder()
                .setDatabasePath(DATABASE_PATH)
                .setAdaptMessage(adapterMessage)
                .build();

        assertThat(adapterMessage.getDatabaseUri()).isEqualTo(DATABASE_PATH);
        assertThat(adapterMessage.getProtocol()).isEqualTo("postgres");
        assertThat(adapterMessage.getPayload().toStringUtf8()).contains("Parse/Bind/Execute");
        assertThat(adapterMessage.getAttachmentsMap())
                .containsEntry("statement-name", "find-singer")
                .containsEntry("parameter-format", "text");
        assertThat(adapterMessage.getQuery()).contains("SingerId");
        assertThat(adapterMessage.getPrepareThenExecute()).isTrue();
        assertThat(action.getActionCase()).isEqualTo(SpannerAction.ActionCase.ADAPT_MESSAGE);
        assertThat(action.getAdaptMessage().getAttachmentsOrThrow("statement-name")).isEqualTo("find-singer");
        assertThat(action.hasQuery()).isFalse();
    }

    @Test
    void finishWriteCancellationAndDescriptorApisExposeExpectedGeneratedMetadata() {
        WriteMutationsAction write = WriteMutationsAction.newBuilder()
                .setMutation(MutationAction.newBuilder()
                        .addMod(MutationAction.Mod.newBuilder()
                                .setTable("Singers")
                                .setUpdate(MutationAction.UpdateArgs.newBuilder()
                                        .addColumn("SingerId")
                                        .addColumn("Name")
                                        .addType(type(TypeCode.INT64))
                                        .addType(type(TypeCode.STRING))
                                        .addValues(ValueList.newBuilder()
                                                .addValue(Value.newBuilder().setIntValue(1L))
                                                .addValue(Value.newBuilder().setStringValue("New Name"))))))
                .build();
        FinishTransactionAction finish = FinishTransactionAction.newBuilder()
                .setMode(FinishTransactionAction.Mode.COMMIT)
                .build();
        QueryCancellationAction cancellation = QueryCancellationAction.newBuilder()
                .setLongRunningSql("SELECT * FROM Singers, Albums")
                .setCancelQuery("SELECT CANCEL_QUERY('$query_id')")
                .build();
        FileDescriptor descriptor = CloudExecutorProto.getDescriptor();
        ServiceDescriptor service = descriptor.findServiceByName("SpannerExecutorProxy");
        MethodDescriptor executeActionAsync = service.findMethodByName("ExecuteActionAsync");
        Descriptor spannerActionDescriptor = descriptor.findMessageTypeByName("SpannerAction");

        assertThat(write.getMutation().getMod(0).getUpdate().getValues(0).getValue(1).getStringValue())
                .isEqualTo("New Name");
        assertThat(finish.getMode()).isEqualTo(FinishTransactionAction.Mode.COMMIT);
        assertThat(cancellation.getCancelQuery()).contains("CANCEL_QUERY");
        assertThat(descriptor.getPackage()).isEqualTo("google.spanner.executor.v1");
        assertThat(service.getFullName()).isEqualTo("google.spanner.executor.v1.SpannerExecutorProxy");
        assertThat(executeActionAsync.isClientStreaming()).isTrue();
        assertThat(executeActionAsync.isServerStreaming()).isTrue();
        assertThat(executeActionAsync.getInputType().getFullName())
                .isEqualTo("google.spanner.executor.v1.SpannerAsyncActionRequest");
        assertThat(executeActionAsync.getOutputType().getFullName())
                .isEqualTo("google.spanner.executor.v1.SpannerAsyncActionResponse");
        assertThat(spannerActionDescriptor.findFieldByName("database_path").getNumber())
                .isEqualTo(SpannerAction.DATABASE_PATH_FIELD_NUMBER);
    }

    private static Type type(TypeCode typeCode) {
        return Type.newBuilder().setCode(typeCode).build();
    }

    private static ColumnMetadata column(String name, TypeCode typeCode) {
        return ColumnMetadata.newBuilder().setName(name).setType(type(typeCode)).build();
    }

    private static Timestamp timestamp(long seconds, int nanos) {
        return Timestamp.newBuilder().setSeconds(seconds).setNanos(nanos).build();
    }
}
