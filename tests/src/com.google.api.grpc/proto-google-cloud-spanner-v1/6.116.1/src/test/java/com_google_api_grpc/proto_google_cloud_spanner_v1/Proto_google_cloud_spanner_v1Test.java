/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_api_grpc.proto_google_cloud_spanner_v1;

import com.google.protobuf.ByteString;
import com.google.protobuf.Duration;
import com.google.protobuf.ListValue;
import com.google.protobuf.NullValue;
import com.google.protobuf.Struct;
import com.google.protobuf.Timestamp;
import com.google.protobuf.Value;
import com.google.rpc.Code;
import com.google.rpc.Status;
import com.google.spanner.v1.BatchCreateSessionsRequest;
import com.google.spanner.v1.BatchCreateSessionsResponse;
import com.google.spanner.v1.BatchWriteRequest;
import com.google.spanner.v1.BatchWriteResponse;
import com.google.spanner.v1.CacheUpdate;
import com.google.spanner.v1.ChangeStreamRecord;
import com.google.spanner.v1.CommitRequest;
import com.google.spanner.v1.CommitResponse;
import com.google.spanner.v1.CreateSessionRequest;
import com.google.spanner.v1.DatabaseName;
import com.google.spanner.v1.DeleteSessionRequest;
import com.google.spanner.v1.DirectedReadOptions;
import com.google.spanner.v1.ExecuteBatchDmlRequest;
import com.google.spanner.v1.ExecuteBatchDmlResponse;
import com.google.spanner.v1.ExecuteSqlRequest;
import com.google.spanner.v1.GetSessionRequest;
import com.google.spanner.v1.Group;
import com.google.spanner.v1.KeyRange;
import com.google.spanner.v1.KeyRecipe;
import com.google.spanner.v1.KeySet;
import com.google.spanner.v1.ListSessionsRequest;
import com.google.spanner.v1.ListSessionsResponse;
import com.google.spanner.v1.MultiplexedSessionPrecommitToken;
import com.google.spanner.v1.Mutation;
import com.google.spanner.v1.PartialResultSet;
import com.google.spanner.v1.Partition;
import com.google.spanner.v1.PartitionOptions;
import com.google.spanner.v1.PartitionQueryRequest;
import com.google.spanner.v1.PartitionReadRequest;
import com.google.spanner.v1.PartitionResponse;
import com.google.spanner.v1.PlanNode;
import com.google.spanner.v1.QueryPlan;
import com.google.spanner.v1.Range;
import com.google.spanner.v1.ReadRequest;
import com.google.spanner.v1.RecipeList;
import com.google.spanner.v1.RequestOptions;
import com.google.spanner.v1.ResultSet;
import com.google.spanner.v1.ResultSetMetadata;
import com.google.spanner.v1.ResultSetStats;
import com.google.spanner.v1.RollbackRequest;
import com.google.spanner.v1.RoutingHint;
import com.google.spanner.v1.Session;
import com.google.spanner.v1.SessionName;
import com.google.spanner.v1.StructType;
import com.google.spanner.v1.Tablet;
import com.google.spanner.v1.Transaction;
import com.google.spanner.v1.TransactionOptions;
import com.google.spanner.v1.TransactionSelector;
import com.google.spanner.v1.Type;
import com.google.spanner.v1.TypeAnnotationCode;
import com.google.spanner.v1.TypeCode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class Proto_google_cloud_spanner_v1Test {
    private static final String PROJECT = "project-one";
    private static final String INSTANCE = "instance-one";
    private static final String DATABASE = "database-one";
    private static final String SESSION = "session-one";

    @Test
    void resourceNamesRoundTripAndExposeFields() {
        DatabaseName databaseName = DatabaseName.of(PROJECT, INSTANCE, DATABASE);
        SessionName sessionName = SessionName.of(PROJECT, INSTANCE, DATABASE, SESSION);

        assertThat(databaseName.toString())
                .isEqualTo("projects/project-one/instances/instance-one/databases/database-one");
        assertThat(DatabaseName.isParsableFrom(databaseName.toString())).isTrue();
        assertThat(DatabaseName.parse(databaseName.toString())).isEqualTo(databaseName);
        assertThat(databaseName.getFieldValuesMap())
                .containsEntry("project", PROJECT)
                .containsEntry("instance", INSTANCE)
                .containsEntry("database", DATABASE);

        assertThat(sessionName.toString())
                .isEqualTo("projects/project-one/instances/instance-one/databases/database-one/sessions/session-one");
        assertThat(SessionName.parse(sessionName.toString()).getSession()).isEqualTo(SESSION);
        assertThat(SessionName.toStringList(List.of(sessionName))).containsExactly(sessionName.toString());
        assertThat(SessionName.parseList(List.of(sessionName.toString()))).containsExactly(sessionName);
    }

    @Test
    void sessionManagementRequestsCarryLifecycleInputsAndPagedResults() {
        String database = DatabaseName.format(PROJECT, INSTANCE, DATABASE);
        Session firstSession = Session.newBuilder()
                .setName(SessionName.format(PROJECT, INSTANCE, DATABASE, "session-one"))
                .putLabels("env", "test")
                .build();
        Session secondSession = Session.newBuilder()
                .setName(SessionName.format(PROJECT, INSTANCE, DATABASE, "session-two"))
                .putLabels("env", "test")
                .build();
        CreateSessionRequest createRequest = CreateSessionRequest.newBuilder()
                .setDatabase(database)
                .setSession(firstSession)
                .build();
        BatchCreateSessionsRequest batchCreateRequest = BatchCreateSessionsRequest.newBuilder()
                .setDatabase(database)
                .setSessionTemplate(firstSession)
                .setSessionCount(2)
                .build();
        BatchCreateSessionsResponse batchCreateResponse = BatchCreateSessionsResponse.newBuilder()
                .addSession(firstSession)
                .addSession(secondSession)
                .build();
        ListSessionsRequest listRequest = ListSessionsRequest.newBuilder()
                .setDatabase(database)
                .setPageSize(2)
                .setPageToken("next-page")
                .setFilter("labels.env:test")
                .build();
        ListSessionsResponse listResponse = ListSessionsResponse.newBuilder()
                .addSessions(firstSession)
                .addSessions(secondSession)
                .setNextPageToken("final-page")
                .build();
        GetSessionRequest getRequest = GetSessionRequest.newBuilder().setName(firstSession.getName()).build();
        DeleteSessionRequest deleteRequest = DeleteSessionRequest.newBuilder().setName(firstSession.getName()).build();

        assertThat(createRequest.getDatabase()).isEqualTo(database);
        assertThat(createRequest.getSession().getLabelsMap()).containsEntry("env", "test");
        assertThat(batchCreateRequest.getSessionTemplate().getName()).isEqualTo(firstSession.getName());
        assertThat(batchCreateRequest.getSessionCount()).isEqualTo(2);
        assertThat(batchCreateResponse.getSessionList())
                .extracting(Session::getName)
                .containsExactly(firstSession.getName(), secondSession.getName());
        assertThat(listRequest.getFilter()).isEqualTo("labels.env:test");
        assertThat(listRequest.getPageSize()).isEqualTo(2);
        assertThat(listResponse.getSessionsList())
                .extracting(Session::getName)
                .containsExactly(firstSession.getName(), secondSession.getName());
        assertThat(listResponse.getNextPageToken()).isEqualTo("final-page");
        assertThat(getRequest.getName()).isEqualTo(firstSession.getName());
        assertThat(deleteRequest.getName()).isEqualTo(firstSession.getName());
    }

    @Test
    void typeDefinitionsRepresentScalarsArraysStructsAndProtoAnnotations() {
        Type int64Type = Type.newBuilder().setCode(TypeCode.INT64).build();
        Type stringArrayType = Type.newBuilder()
                .setCode(TypeCode.ARRAY)
                .setArrayElementType(stringType())
                .build();
        StructType rowType = StructType.newBuilder()
                .addFields(field("SingerId", int64Type))
                .addFields(field("SingerName", stringArrayType))
                .build();
        Type protoType = Type.newBuilder()
                .setCode(TypeCode.PROTO)
                .setTypeAnnotation(TypeAnnotationCode.PG_JSONB)
                .setProtoTypeFqn("google.spanner.v1.ResultSet")
                .build();
        Type structType = Type.newBuilder().setCode(TypeCode.STRUCT).setStructType(rowType).build();

        assertThat(structType.getStructType().getFieldsList())
                .extracting(StructType.Field::getName)
                .containsExactly("SingerId", "SingerName");
        assertThat(structType.getStructType().getFields(1).getType().getArrayElementType().getCode())
                .isEqualTo(TypeCode.STRING);
        assertThat(protoType.getProtoTypeFqn()).isEqualTo("google.spanner.v1.ResultSet");
        assertThat(TypeCode.forNumber(TypeCode.NUMERIC.getNumber())).isEqualTo(TypeCode.NUMERIC);
        assertThat(TypeAnnotationCode.valueOf("PG_JSONB")).isEqualTo(TypeAnnotationCode.PG_JSONB);
    }

    @Test
    void transactionsAndSelectorsCoverOneofModesAndPrecommitTokens() {
        ByteString previousTransactionId = ByteString.copyFromUtf8("previous-transaction");
        TransactionOptions readWrite = TransactionOptions.newBuilder()
                .setReadWrite(TransactionOptions.ReadWrite.newBuilder()
                        .setReadLockMode(TransactionOptions.ReadWrite.ReadLockMode.OPTIMISTIC)
                        .setMultiplexedSessionPreviousTransactionId(previousTransactionId))
                .setExcludeTxnFromChangeStreams(true)
                .setIsolationLevel(TransactionOptions.IsolationLevel.REPEATABLE_READ)
                .build();
        TransactionOptions readOnly = TransactionOptions.newBuilder()
                .setReadOnly(TransactionOptions.ReadOnly.newBuilder()
                        .setExactStaleness(Duration.newBuilder().setSeconds(15))
                        .setReturnReadTimestamp(true))
                .build();
        TransactionSelector beginSelector = TransactionSelector.newBuilder().setBegin(readWrite).build();
        TransactionSelector singleUseSelector = TransactionSelector.newBuilder().setSingleUse(readOnly).build();
        TransactionSelector idSelector = TransactionSelector.newBuilder()
                .setId(ByteString.copyFromUtf8("transaction-id"))
                .build();
        MultiplexedSessionPrecommitToken token = MultiplexedSessionPrecommitToken.newBuilder()
                .setPrecommitToken(ByteString.copyFromUtf8("token"))
                .setSeqNum(7)
                .build();
        Transaction transaction = Transaction.newBuilder()
                .setId(ByteString.copyFromUtf8("transaction-id"))
                .setReadTimestamp(timestamp(100, 25))
                .setPrecommitToken(token)
                .build();

        assertThat(readWrite.getModeCase()).isEqualTo(TransactionOptions.ModeCase.READ_WRITE);
        assertThat(readWrite.getReadWrite().getMultiplexedSessionPreviousTransactionId())
                .isEqualTo(previousTransactionId);
        assertThat(readOnly.getReadOnly().getTimestampBoundCase())
                .isEqualTo(TransactionOptions.ReadOnly.TimestampBoundCase.EXACT_STALENESS);
        assertThat(beginSelector.getSelectorCase()).isEqualTo(TransactionSelector.SelectorCase.BEGIN);
        assertThat(singleUseSelector.getSingleUse().getReadOnly().getReturnReadTimestamp()).isTrue();
        assertThat(idSelector.getId().toStringUtf8()).isEqualTo("transaction-id");
        assertThat(transaction.getPrecommitToken().getSeqNum()).isEqualTo(7);
    }

    @Test
    void mutationsRepresentAllWriteDeleteAndQueueOperations() {
        ListValue row = listValue(stringValue("singer-1"), stringValue("Joni Mitchell"));
        KeySet keySet = KeySet.newBuilder()
                .addKeys(listValue(stringValue("singer-1")))
                .addRanges(KeyRange.newBuilder()
                        .setStartClosed(listValue(stringValue("singer-2")))
                        .setEndOpen(listValue(stringValue("singer-9"))))
                .build();
        Mutation.Write write = Mutation.Write.newBuilder()
                .setTable("Singers")
                .addAllColumns(List.of("SingerId", "SingerName"))
                .addValues(row)
                .build();
        Mutation insert = Mutation.newBuilder().setInsert(write).build();
        Mutation update = Mutation.newBuilder().setUpdate(write).build();
        Mutation insertOrUpdate = Mutation.newBuilder().setInsertOrUpdate(write).build();
        Mutation replace = Mutation.newBuilder().setReplace(write).build();
        Mutation delete = Mutation.newBuilder()
                .setDelete(Mutation.Delete.newBuilder().setTable("Singers").setKeySet(keySet))
                .build();
        Mutation send = Mutation.newBuilder()
                .setSend(Mutation.Send.newBuilder()
                        .setQueue("email-queue")
                        .setKey(listValue(stringValue("message-1")))
                        .setDeliverTime(timestamp(200, 0))
                        .setPayload(structValue("template", stringValue("welcome"))))
                .build();
        Mutation ack = Mutation.newBuilder()
                .setAck(Mutation.Ack.newBuilder()
                        .setQueue("email-queue")
                        .setKey(listValue(stringValue("message-1")))
                        .setIgnoreNotFound(true))
                .build();

        assertThat(insert.getOperationCase()).isEqualTo(Mutation.OperationCase.INSERT);
        assertThat(update.getOperationCase()).isEqualTo(Mutation.OperationCase.UPDATE);
        assertThat(insertOrUpdate.getOperationCase()).isEqualTo(Mutation.OperationCase.INSERT_OR_UPDATE);
        assertThat(replace.getOperationCase()).isEqualTo(Mutation.OperationCase.REPLACE);
        assertThat(delete.getDelete().getKeySet().getRanges(0).getStartKeyTypeCase())
                .isEqualTo(KeyRange.StartKeyTypeCase.START_CLOSED);
        assertThat(send.getSend().getPayload().getStructValue().getFieldsMap()).containsKey("template");
        assertThat(ack.getAck().getIgnoreNotFound()).isTrue();
    }

    @Test
    void executeSqlAndReadRequestsCarryOptionsParametersAndRoutingHints() {
        RequestOptions requestOptions = RequestOptions.newBuilder()
                .setPriority(RequestOptions.Priority.PRIORITY_MEDIUM)
                .setRequestTag("request-tag")
                .setTransactionTag("transaction-tag")
                .setClientContext(RequestOptions.ClientContext.newBuilder()
                        .putSecureContext("tenant", stringValue("tenant-a")))
                .build();
        DirectedReadOptions directedReadOptions = DirectedReadOptions.newBuilder()
                .setIncludeReplicas(DirectedReadOptions.IncludeReplicas.newBuilder()
                        .addReplicaSelections(DirectedReadOptions.ReplicaSelection.newBuilder()
                                .setLocation("us-central1")
                                .setType(DirectedReadOptions.ReplicaSelection.Type.READ_ONLY))
                        .setAutoFailoverDisabled(true))
                .build();
        RoutingHint routingHint = routingHint();
        TransactionSelector transaction = TransactionSelector.newBuilder()
                .setId(ByteString.copyFromUtf8("transaction-id"))
                .build();
        ExecuteSqlRequest executeSqlRequest = ExecuteSqlRequest.newBuilder()
                .setSession(sessionName())
                .setTransaction(transaction)
                .setSql("SELECT SingerName FROM Singers WHERE SingerId = @id")
                .setParams(Struct.newBuilder().putFields("id", stringValue("singer-1")))
                .putParamTypes("id", stringType())
                .setResumeToken(ByteString.copyFromUtf8("resume-token"))
                .setQueryMode(ExecuteSqlRequest.QueryMode.PROFILE)
                .setPartitionToken(ByteString.copyFromUtf8("partition-token"))
                .setSeqno(42)
                .setQueryOptions(ExecuteSqlRequest.QueryOptions.newBuilder()
                        .setOptimizerVersion("latest")
                        .setOptimizerStatisticsPackage("auto"))
                .setRequestOptions(requestOptions)
                .setDirectedReadOptions(directedReadOptions)
                .setDataBoostEnabled(true)
                .setLastStatement(true)
                .setRoutingHint(routingHint)
                .build();
        ReadRequest readRequest = ReadRequest.newBuilder()
                .setSession(sessionName())
                .setTransaction(transaction)
                .setTable("Singers")
                .setIndex("SingersByName")
                .addAllColumns(List.of("SingerId", "SingerName"))
                .setKeySet(KeySet.newBuilder().setAll(true))
                .setLimit(10)
                .setResumeToken(ByteString.copyFromUtf8("read-resume-token"))
                .setPartitionToken(ByteString.copyFromUtf8("read-partition-token"))
                .setRequestOptions(requestOptions)
                .setDirectedReadOptions(directedReadOptions)
                .setDataBoostEnabled(true)
                .setOrderBy(ReadRequest.OrderBy.ORDER_BY_NO_ORDER)
                .setLockHint(ReadRequest.LockHint.LOCK_HINT_EXCLUSIVE)
                .setRoutingHint(routingHint)
                .build();

        assertThat(executeSqlRequest.getParamTypesOrThrow("id").getCode()).isEqualTo(TypeCode.STRING);
        assertThat(executeSqlRequest.getQueryOptions().getOptimizerVersion()).isEqualTo("latest");
        assertThat(executeSqlRequest.getRequestOptions().getClientContext().getSecureContextMap())
                .containsKey("tenant");
        assertThat(executeSqlRequest.getDirectedReadOptions().getReplicasCase())
                .isEqualTo(DirectedReadOptions.ReplicasCase.INCLUDE_REPLICAS);
        assertThat(executeSqlRequest.getRoutingHint().getSkippedTabletUidCount()).isEqualTo(1);
        assertThat(readRequest.getColumnsList()).containsExactly("SingerId", "SingerName");
        assertThat(readRequest.getKeySet().getAll()).isTrue();
        assertThat(readRequest.getOrderBy()).isEqualTo(ReadRequest.OrderBy.ORDER_BY_NO_ORDER);
        assertThat(readRequest.getLockHint()).isEqualTo(ReadRequest.LockHint.LOCK_HINT_EXCLUSIVE);
    }

    @Test
    void batchPartitionCommitAndRollbackMessagesComposeRequestResponsePayloads() {
        TransactionSelector transaction = TransactionSelector.newBuilder()
                .setId(ByteString.copyFromUtf8("transaction-id"))
                .build();
        RequestOptions requestOptions = RequestOptions.newBuilder()
                .setPriority(RequestOptions.Priority.PRIORITY_LOW)
                .build();
        ExecuteBatchDmlRequest.Statement statement = ExecuteBatchDmlRequest.Statement.newBuilder()
                .setSql("UPDATE Singers SET SingerName = @name WHERE SingerId = @id")
                .setParams(Struct.newBuilder()
                        .putFields("id", stringValue("singer-1"))
                        .putFields("name", stringValue("Nina Simone")))
                .putParamTypes("id", stringType())
                .putParamTypes("name", stringType())
                .build();
        ExecuteBatchDmlRequest batchRequest = ExecuteBatchDmlRequest.newBuilder()
                .setSession(sessionName())
                .setTransaction(transaction)
                .addStatements(statement)
                .setSeqno(43)
                .setRequestOptions(requestOptions)
                .setLastStatements(true)
                .build();
        ResultSetStats stats = ResultSetStats.newBuilder().setRowCountExact(1).build();
        ExecuteBatchDmlResponse batchResponse = ExecuteBatchDmlResponse.newBuilder()
                .addResultSets(ResultSet.newBuilder().setStats(stats))
                .setStatus(Status.newBuilder().setCode(0).setMessage("OK"))
                .setPrecommitToken(precommitToken())
                .build();
        PartitionOptions partitionOptions = PartitionOptions.newBuilder()
                .setPartitionSizeBytes(1024)
                .setMaxPartitions(4)
                .build();
        PartitionQueryRequest partitionQuery = PartitionQueryRequest.newBuilder()
                .setSession(sessionName())
                .setTransaction(transaction)
                .setSql("SELECT SingerId FROM Singers")
                .setParams(Struct.newBuilder().putFields("unused", nullValue()))
                .putParamTypes("unused", stringType())
                .setPartitionOptions(partitionOptions)
                .build();
        PartitionReadRequest partitionRead = PartitionReadRequest.newBuilder()
                .setSession(sessionName())
                .setTransaction(transaction)
                .setTable("Singers")
                .setIndex("SingersByName")
                .addAllColumns(List.of("SingerId"))
                .setKeySet(KeySet.newBuilder().setAll(true))
                .setPartitionOptions(partitionOptions)
                .build();
        PartitionResponse partitionResponse = PartitionResponse.newBuilder()
                .addPartitions(Partition.newBuilder().setPartitionToken(ByteString.copyFromUtf8("partition-token")))
                .setTransaction(Transaction.newBuilder().setId(ByteString.copyFromUtf8("transaction-id")))
                .build();
        Mutation mutation = Mutation.newBuilder().setInsert(writeMutation()).build();
        CommitRequest commitRequest = CommitRequest.newBuilder()
                .setSession(sessionName())
                .setTransactionId(ByteString.copyFromUtf8("transaction-id"))
                .addMutations(mutation)
                .setReturnCommitStats(true)
                .setMaxCommitDelay(Duration.newBuilder().setSeconds(1))
                .setRequestOptions(requestOptions)
                .setPrecommitToken(precommitToken())
                .build();
        CommitResponse commitResponse = CommitResponse.newBuilder()
                .setCommitTimestamp(timestamp(300, 123))
                .setCommitStats(CommitResponse.CommitStats.newBuilder().setMutationCount(1))
                .setPrecommitToken(precommitToken())
                .setSnapshotTimestamp(timestamp(299, 0))
                .build();
        RollbackRequest rollbackRequest = RollbackRequest.newBuilder()
                .setSession(sessionName())
                .setTransactionId(ByteString.copyFromUtf8("transaction-id"))
                .build();

        assertThat(batchRequest.getStatements(0).getParamTypesMap()).containsKeys("id", "name");
        assertThat(batchResponse.getResultSets(0).getStats().getRowCountCase())
                .isEqualTo(ResultSetStats.RowCountCase.ROW_COUNT_EXACT);
        assertThat(partitionQuery.getPartitionOptions().getMaxPartitions()).isEqualTo(4);
        assertThat(partitionRead.getColumnsList()).containsExactly("SingerId");
        assertThat(partitionResponse.getPartitions(0).getPartitionToken().toStringUtf8()).isEqualTo("partition-token");
        assertThat(commitRequest.getTransactionCase()).isEqualTo(CommitRequest.TransactionCase.TRANSACTION_ID);
        assertThat(commitResponse.getCommitStats().getMutationCount()).isEqualTo(1);
        assertThat(rollbackRequest.getTransactionId().toStringUtf8()).isEqualTo("transaction-id");
    }

    @Test
    void batchWriteRequestsGroupIndependentMutationSetsAndReportPerGroupResults() {
        RequestOptions requestOptions = RequestOptions.newBuilder()
                .setPriority(RequestOptions.Priority.PRIORITY_HIGH)
                .setRequestTag("batch-write-tag")
                .build();
        BatchWriteRequest.MutationGroup firstGroup = BatchWriteRequest.MutationGroup.newBuilder()
                .addMutations(Mutation.newBuilder().setInsert(writeMutation()))
                .build();
        BatchWriteRequest.MutationGroup secondGroup = BatchWriteRequest.MutationGroup.newBuilder()
                .addMutations(Mutation.newBuilder()
                        .setDelete(Mutation.Delete.newBuilder()
                                .setTable("Singers")
                                .setKeySet(KeySet.newBuilder().addKeys(listValue(stringValue("singer-2"))))))
                .build();
        BatchWriteRequest request = BatchWriteRequest.newBuilder()
                .setSession(sessionName())
                .setRequestOptions(requestOptions)
                .addMutationGroups(firstGroup)
                .addMutationGroups(secondGroup)
                .setExcludeTxnFromChangeStreams(true)
                .build();
        BatchWriteResponse success = BatchWriteResponse.newBuilder()
                .addIndexes(0)
                .setStatus(Status.newBuilder().setCode(Code.OK.getNumber()).setMessage("OK"))
                .setCommitTimestamp(timestamp(350, 0))
                .build();
        BatchWriteResponse retryableFailure = BatchWriteResponse.newBuilder()
                .addIndexes(1)
                .setStatus(Status.newBuilder().setCode(Code.ABORTED.getNumber()).setMessage("retry transaction"))
                .build();

        assertThat(request.getSession()).isEqualTo(sessionName());
        assertThat(request.getRequestOptions().getRequestTag()).isEqualTo("batch-write-tag");
        assertThat(request.getMutationGroupsList())
                .extracting(BatchWriteRequest.MutationGroup::getMutationsCount)
                .containsExactly(1, 1);
        assertThat(request.getExcludeTxnFromChangeStreams()).isTrue();
        assertThat(success.getIndexesList()).containsExactly(0);
        assertThat(success.getStatus().getCode()).isEqualTo(Code.OK.getNumber());
        assertThat(success.getCommitTimestamp().getSeconds()).isEqualTo(350);
        assertThat(retryableFailure.getIndexesList()).containsExactly(1);
        assertThat(retryableFailure.getStatus().getCode()).isEqualTo(Code.ABORTED.getNumber());
    }

    @Test
    void resultSetsRepresentMetadataRowsStatsPlansAndPartialChunks() {
        PlanNode.ShortRepresentation shortRepresentation = PlanNode.ShortRepresentation.newBuilder()
                .setDescription("Distributed Union")
                .putSubqueries("child", 1)
                .build();
        PlanNode planNode = PlanNode.newBuilder()
                .setIndex(0)
                .setKind(PlanNode.Kind.RELATIONAL)
                .setDisplayName("Root")
                .addChildLinks(PlanNode.ChildLink.newBuilder()
                        .setChildIndex(1)
                        .setType("Input")
                        .setVariable("row"))
                .setShortRepresentation(shortRepresentation)
                .setMetadata(Struct.newBuilder().putFields("rows", numberValue(1)))
                .setExecutionStats(Struct.newBuilder().putFields("cpu", stringValue("1 ms")))
                .build();
        QueryPlan queryPlan = QueryPlan.newBuilder().addPlanNodes(planNode).build();
        ResultSetMetadata metadata = ResultSetMetadata.newBuilder()
                .setRowType(StructType.newBuilder()
                        .addFields(field("SingerId", stringType()))
                        .addFields(field("SingerName", stringType())))
                .setTransaction(Transaction.newBuilder().setId(ByteString.copyFromUtf8("transaction-id")))
                .setUndeclaredParameters(StructType.newBuilder().addFields(field("limit", int64Type())))
                .build();
        ResultSetStats stats = ResultSetStats.newBuilder()
                .setQueryPlan(queryPlan)
                .setQueryStats(Struct.newBuilder().putFields("elapsed_time", stringValue("2 ms")))
                .setRowCountLowerBound(1)
                .build();
        ResultSet resultSet = ResultSet.newBuilder()
                .setMetadata(metadata)
                .addRows(listValue(stringValue("singer-1"), stringValue("Ella Fitzgerald")))
                .setStats(stats)
                .build();
        PartialResultSet partialResultSet = PartialResultSet.newBuilder()
                .setMetadata(metadata)
                .addValues(stringValue("partial"))
                .setChunkedValue(true)
                .setResumeToken(ByteString.copyFromUtf8("resume"))
                .setStats(stats)
                .setPrecommitToken(precommitToken())
                .setLast(true)
                .build();

        assertThat(resultSet.getMetadata().getRowType().getFieldsList())
                .extracting(StructType.Field::getName)
                .containsExactly("SingerId", "SingerName");
        assertThat(resultSet.getRows(0).getValues(1).getStringValue()).isEqualTo("Ella Fitzgerald");
        assertThat(resultSet.getStats().getRowCountCase()).isEqualTo(ResultSetStats.RowCountCase.ROW_COUNT_LOWER_BOUND);
        assertThat(resultSet.getStats().getQueryPlan().getPlanNodes(0).getShortRepresentation().getSubqueriesMap())
                .containsEntry("child", 1);
        assertThat(partialResultSet.getChunkedValue()).isTrue();
        assertThat(partialResultSet.getPrecommitToken().getSeqNum()).isEqualTo(9);
        assertThat(partialResultSet.getLast()).isTrue();
    }

    @Test
    void sessionsChangeStreamRecordsAndLocationCacheMessagesPreserveNestedData() {
        Session session = Session.newBuilder()
                .setName(sessionName())
                .setCreateTime(timestamp(10, 0))
                .setApproximateLastUseTime(timestamp(20, 0))
                .putLabels("env", "test")
                .setCreatorRole("roles/spanner.databaseUser")
                .setMultiplexed(true)
                .build();
        ChangeStreamRecord.DataChangeRecord.ModValue key = ChangeStreamRecord.DataChangeRecord.ModValue.newBuilder()
                .setColumnMetadataIndex(0)
                .setValue(stringValue("singer-1"))
                .build();
        ChangeStreamRecord.DataChangeRecord.Mod mod = ChangeStreamRecord.DataChangeRecord.Mod.newBuilder()
                .addKeys(key)
                .addOldValues(ChangeStreamRecord.DataChangeRecord.ModValue.newBuilder()
                        .setColumnMetadataIndex(1)
                        .setValue(stringValue("old-name")))
                .addNewValues(ChangeStreamRecord.DataChangeRecord.ModValue.newBuilder()
                        .setColumnMetadataIndex(1)
                        .setValue(stringValue("new-name")))
                .build();
        ChangeStreamRecord.DataChangeRecord dataChangeRecord = ChangeStreamRecord.DataChangeRecord.newBuilder()
                .setCommitTimestamp(timestamp(400, 0))
                .setRecordSequence("00000001")
                .setServerTransactionId("server-transaction")
                .setIsLastRecordInTransactionInPartition(true)
                .setTable("Singers")
                .addColumnMetadata(ChangeStreamRecord.DataChangeRecord.ColumnMetadata.newBuilder()
                        .setName("SingerId")
                        .setType(stringType())
                        .setIsPrimaryKey(true)
                        .setOrdinalPosition(1))
                .addMods(mod)
                .setModType(ChangeStreamRecord.DataChangeRecord.ModType.UPDATE)
                .setValueCaptureType(ChangeStreamRecord.DataChangeRecord.ValueCaptureType.NEW_ROW_AND_OLD_VALUES)
                .setNumberOfRecordsInTransaction(1)
                .setNumberOfPartitionsInTransaction(1)
                .setTransactionTag("transaction-tag")
                .setIsSystemTransaction(false)
                .build();
        ChangeStreamRecord dataRecord = ChangeStreamRecord.newBuilder().setDataChangeRecord(dataChangeRecord).build();
        ChangeStreamRecord heartbeatRecord = ChangeStreamRecord.newBuilder()
                .setHeartbeatRecord(ChangeStreamRecord.HeartbeatRecord.newBuilder().setTimestamp(timestamp(401, 0)))
                .build();
        ChangeStreamRecord partitionStart = ChangeStreamRecord.newBuilder()
                .setPartitionStartRecord(ChangeStreamRecord.PartitionStartRecord.newBuilder()
                        .setStartTimestamp(timestamp(402, 0))
                        .setRecordSequence("00000002")
                        .addAllPartitionTokens(List.of("partition-a", "partition-b")))
                .build();
        ChangeStreamRecord partitionEnd = ChangeStreamRecord.newBuilder()
                .setPartitionEndRecord(ChangeStreamRecord.PartitionEndRecord.newBuilder()
                        .setEndTimestamp(timestamp(403, 0))
                        .setRecordSequence("00000003")
                        .setPartitionToken("partition-a"))
                .build();
        ChangeStreamRecord partitionEvent = ChangeStreamRecord.newBuilder()
                .setPartitionEventRecord(ChangeStreamRecord.PartitionEventRecord.newBuilder()
                        .setCommitTimestamp(timestamp(404, 0))
                        .setRecordSequence("00000004")
                        .setPartitionToken("partition-b")
                        .addMoveInEvents(ChangeStreamRecord.PartitionEventRecord.MoveInEvent.newBuilder()
                                .setSourcePartitionToken("partition-a"))
                        .addMoveOutEvents(ChangeStreamRecord.PartitionEventRecord.MoveOutEvent.newBuilder()
                                .setDestinationPartitionToken("partition-c")))
                .build();
        CacheUpdate cacheUpdate = locationCacheUpdate();

        assertThat(session.getLabelsMap()).containsEntry("env", "test");
        assertThat(session.getMultiplexed()).isTrue();
        assertThat(dataRecord.getRecordCase()).isEqualTo(ChangeStreamRecord.RecordCase.DATA_CHANGE_RECORD);
        assertThat(dataRecord.getDataChangeRecord().getMods(0).getNewValues(0).getValue().getStringValue())
                .isEqualTo("new-name");
        assertThat(heartbeatRecord.getRecordCase()).isEqualTo(ChangeStreamRecord.RecordCase.HEARTBEAT_RECORD);
        assertThat(partitionStart.getPartitionStartRecord().getPartitionTokensList())
                .containsExactly("partition-a", "partition-b");
        assertThat(partitionEnd.getPartitionEndRecord().getPartitionToken()).isEqualTo("partition-a");
        assertThat(partitionEvent.getPartitionEventRecord().getMoveOutEvents(0).getDestinationPartitionToken())
                .isEqualTo("partition-c");
        assertThat(cacheUpdate.getGroup(0).getTablets(0).getRole()).isEqualTo(Tablet.Role.READ_WRITE);
        assertThat(cacheUpdate.getKeyRecipes().getRecipe(0).getPart(0).getValueTypeCase())
                .isEqualTo(KeyRecipe.Part.ValueTypeCase.IDENTIFIER);
    }

    private static String sessionName() {
        return SessionName.format(PROJECT, INSTANCE, DATABASE, SESSION);
    }

    private static StructType.Field field(String name, Type type) {
        return StructType.Field.newBuilder().setName(name).setType(type).build();
    }

    private static Type stringType() {
        return Type.newBuilder().setCode(TypeCode.STRING).build();
    }

    private static Type int64Type() {
        return Type.newBuilder().setCode(TypeCode.INT64).build();
    }

    private static Timestamp timestamp(long seconds, int nanos) {
        return Timestamp.newBuilder().setSeconds(seconds).setNanos(nanos).build();
    }

    private static Value stringValue(String value) {
        return Value.newBuilder().setStringValue(value).build();
    }

    private static Value numberValue(double value) {
        return Value.newBuilder().setNumberValue(value).build();
    }

    private static Value nullValue() {
        return Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build();
    }

    private static Value structValue(String fieldName, Value fieldValue) {
        return Value.newBuilder()
                .setStructValue(Struct.newBuilder().putFields(fieldName, fieldValue))
                .build();
    }

    private static ListValue listValue(Value... values) {
        return ListValue.newBuilder().addAllValues(List.of(values)).build();
    }

    private static Mutation.Write writeMutation() {
        return Mutation.Write.newBuilder()
                .setTable("Singers")
                .addAllColumns(List.of("SingerId", "SingerName"))
                .addValues(listValue(stringValue("singer-1"), stringValue("Nina Simone")))
                .build();
    }

    private static MultiplexedSessionPrecommitToken precommitToken() {
        return MultiplexedSessionPrecommitToken.newBuilder()
                .setPrecommitToken(ByteString.copyFromUtf8("precommit-token"))
                .setSeqNum(9)
                .build();
    }

    private static RoutingHint routingHint() {
        return RoutingHint.newBuilder()
                .setOperationUid(101)
                .setDatabaseId(202)
                .setSchemaGeneration(ByteString.copyFromUtf8("schema-generation"))
                .setKey(ByteString.copyFromUtf8("encoded-key"))
                .setLimitKey(ByteString.copyFromUtf8("encoded-limit-key"))
                .setGroupUid(303)
                .setSplitId(404)
                .setTabletUid(505)
                .addSkippedTabletUid(RoutingHint.SkippedTablet.newBuilder()
                        .setTabletUid(606)
                        .setIncarnation(ByteString.copyFromUtf8("incarnation")))
                .setClientLocation("us-central1-a")
                .build();
    }

    private static CacheUpdate locationCacheUpdate() {
        Tablet tablet = Tablet.newBuilder()
                .setTabletUid(505)
                .setServerAddress("127.0.0.1:9010")
                .setLocation("us-central1")
                .setRole(Tablet.Role.READ_WRITE)
                .setIncarnation(ByteString.copyFromUtf8("tablet-incarnation"))
                .setDistance(1)
                .setSkip(false)
                .build();
        Group group = Group.newBuilder()
                .setGroupUid(303)
                .addTablets(tablet)
                .setLeaderIndex(0)
                .setGeneration(ByteString.copyFromUtf8("group-generation"))
                .build();
        Range range = Range.newBuilder()
                .setStartKey(ByteString.copyFromUtf8("start-key"))
                .setLimitKey(ByteString.copyFromUtf8("limit-key"))
                .setGroupUid(303)
                .setSplitId(404)
                .setGeneration(ByteString.copyFromUtf8("range-generation"))
                .build();
        KeyRecipe.Part part = KeyRecipe.Part.newBuilder()
                .setOrder(KeyRecipe.Part.Order.ASCENDING)
                .setNullOrder(KeyRecipe.Part.NullOrder.NOT_NULL)
                .setType(stringType())
                .setIdentifier("SingerId")
                .addStructIdentifiers(0)
                .build();
        KeyRecipe recipe = KeyRecipe.newBuilder()
                .setTableName("Singers")
                .addPart(part)
                .build();
        RecipeList recipeList = RecipeList.newBuilder()
                .setSchemaGeneration(ByteString.copyFromUtf8("schema-generation"))
                .addRecipe(recipe)
                .build();

        return CacheUpdate.newBuilder()
                .setDatabaseId(202)
                .addRange(range)
                .addGroup(group)
                .setKeyRecipes(recipeList)
                .build();
    }
}
