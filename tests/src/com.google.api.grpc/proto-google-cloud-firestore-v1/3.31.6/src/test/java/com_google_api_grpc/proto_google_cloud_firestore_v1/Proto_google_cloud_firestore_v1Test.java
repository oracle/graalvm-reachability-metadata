/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_api_grpc.proto_google_cloud_firestore_v1;

import com.google.firestore.v1.AggregationResult;
import com.google.firestore.v1.AnyPathName;
import com.google.firestore.v1.ArrayValue;
import com.google.firestore.v1.BatchGetDocumentsRequest;
import com.google.firestore.v1.BatchWriteRequest;
import com.google.firestore.v1.CommitRequest;
import com.google.firestore.v1.Cursor;
import com.google.firestore.v1.DatabaseRootName;
import com.google.firestore.v1.Document;
import com.google.firestore.v1.DocumentChange;
import com.google.firestore.v1.DocumentMask;
import com.google.firestore.v1.DocumentPathName;
import com.google.firestore.v1.DocumentRootName;
import com.google.firestore.v1.DocumentTransform;
import com.google.firestore.v1.ExecutionStats;
import com.google.firestore.v1.ExplainMetrics;
import com.google.firestore.v1.ExplainOptions;
import com.google.firestore.v1.GetDocumentRequest;
import com.google.firestore.v1.ListDocumentsRequest;
import com.google.firestore.v1.ListenRequest;
import com.google.firestore.v1.ListenResponse;
import com.google.firestore.v1.MapValue;
import com.google.firestore.v1.PartitionQueryRequest;
import com.google.firestore.v1.PartitionQueryResponse;
import com.google.firestore.v1.PlanSummary;
import com.google.firestore.v1.Precondition;
import com.google.firestore.v1.RunAggregationQueryRequest;
import com.google.firestore.v1.RunAggregationQueryResponse;
import com.google.firestore.v1.RunQueryRequest;
import com.google.firestore.v1.RunQueryResponse;
import com.google.firestore.v1.StructuredAggregationQuery;
import com.google.firestore.v1.StructuredQuery;
import com.google.firestore.v1.Target;
import com.google.firestore.v1.TargetChange;
import com.google.firestore.v1.TransactionOptions;
import com.google.firestore.v1.Value;
import com.google.firestore.v1.Write;
import com.google.protobuf.ByteString;
import com.google.protobuf.DoubleValue;
import com.google.protobuf.Duration;
import com.google.protobuf.Int32Value;
import com.google.protobuf.NullValue;
import com.google.protobuf.Struct;
import com.google.protobuf.Timestamp;
import com.google.type.LatLng;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Proto_google_cloud_firestore_v1Test {
    private static final String PROJECT = "test-project";
    private static final String DATABASE = "(default)";
    private static final String DATABASE_NAME = "projects/test-project/databases/(default)";
    private static final String DOCUMENT_NAME = DATABASE_NAME + "/documents/users/alice";

    @Test
    void resourceNamesFormatParseAndExposeFields() {
        DatabaseRootName databaseRootName = DatabaseRootName.of(PROJECT, DATABASE);
        DocumentRootName documentRootName = DocumentRootName.parse(DATABASE_NAME + "/documents");
        DocumentPathName documentPathName = DocumentPathName.newBuilder()
                .setProject(PROJECT)
                .setDatabase(DATABASE)
                .setDocumentPath("users/alice")
                .build();
        AnyPathName anyPathName = AnyPathName.parse(DOCUMENT_NAME + "/messages/message-1");

        assertThat(databaseRootName.toString()).isEqualTo(DATABASE_NAME);
        assertThat(documentRootName).isEqualTo(DocumentRootName.of(PROJECT, DATABASE));
        assertThat(DocumentPathName.format(PROJECT, DATABASE, "users/alice")).isEqualTo(DOCUMENT_NAME);
        assertThat(documentPathName.getFieldValuesMap())
                .containsEntry("project", PROJECT)
                .containsEntry("database", DATABASE)
                .containsEntry("documentPath", "users/alice");
        assertThat(anyPathName.getDocument()).isEqualTo("users");
        assertThat(anyPathName.getAnyPath()).isEqualTo("alice/messages/message-1");
        assertThat(AnyPathName.isParsableFrom(anyPathName.toString())).isTrue();
        assertThat(DocumentPathName.toStringList(List.of(documentPathName))).containsExactly(DOCUMENT_NAME);
        assertThat(DocumentPathName.parseList(List.of(DOCUMENT_NAME))).containsExactly(documentPathName);
    }

    @Test
    void documentValuesSupportAllFirestoreValueKindsAndBuilderCopies() {
        Timestamp updateTime = Timestamp.newBuilder().setSeconds(1_700_000_000L).setNanos(123_000_000).build();
        Value stringValue = Value.newBuilder().setStringValue("Alice").build();
        Value arrayValue = Value.newBuilder()
                .setArrayValue(ArrayValue.newBuilder()
                        .addValues(Value.newBuilder().setIntegerValue(1L))
                        .addValues(Value.newBuilder().setDoubleValue(2.5D)))
                .build();
        Value mapValue = Value.newBuilder()
                .setMapValue(MapValue.newBuilder()
                        .putFields("nested", Value.newBuilder().setBooleanValue(true).build()))
                .build();
        Value geoPointValue = Value.newBuilder()
                .setGeoPointValue(LatLng.newBuilder().setLatitude(37.422).setLongitude(-122.084))
                .build();

        Document document = Document.newBuilder()
                .setName(DOCUMENT_NAME)
                .putFields("null", Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build())
                .putFields("active", Value.newBuilder().setBooleanValue(true).build())
                .putFields("age", Value.newBuilder().setIntegerValue(42L).build())
                .putFields("score", Value.newBuilder().setDoubleValue(98.75D).build())
                .putFields("updated", Value.newBuilder().setTimestampValue(updateTime).build())
                .putFields("name", stringValue)
                .putFields("payload", Value.newBuilder().setBytesValue(ByteString.copyFromUtf8("payload")).build())
                .putFields("manager", Value.newBuilder().setReferenceValue(DOCUMENT_NAME).build())
                .putFields("location", geoPointValue)
                .putFields("numbers", arrayValue)
                .putFields("metadata", mapValue)
                .setCreateTime(updateTime)
                .setUpdateTime(updateTime)
                .build();

        Document editedDocument = document.toBuilder()
                .putFields("name", stringValue.toBuilder().setStringValue("Alice Smith").build())
                .removeFields("null")
                .build();

        assertThat(document.getFieldsCount()).isEqualTo(11);
        assertThat(document.getFieldsOrThrow("name").getValueTypeCase()).isEqualTo(Value.ValueTypeCase.STRING_VALUE);
        assertThat(document.getFieldsOrThrow("numbers").getArrayValue().getValuesList())
                .extracting(Value::getValueTypeCase)
                .containsExactly(Value.ValueTypeCase.INTEGER_VALUE, Value.ValueTypeCase.DOUBLE_VALUE);
        assertThat(document.getFieldsOrThrow("metadata").getMapValue().getFieldsOrThrow("nested").getBooleanValue())
                .isTrue();
        assertThat(document.getFieldsOrThrow("location").getGeoPointValue().getLatitude()).isEqualTo(37.422D);
        assertThat(editedDocument.getFieldsOrThrow("name").getStringValue()).isEqualTo("Alice Smith");
        assertThat(editedDocument.containsFields("null")).isFalse();
    }

    @Test
    void structuredQueriesRepresentFiltersOrderingCursorsAndVectorSearch() {
        StructuredQuery.FieldReference ageField = StructuredQuery.FieldReference.newBuilder().setFieldPath("age").build();
        StructuredQuery.FieldReference tagsField = StructuredQuery.FieldReference.newBuilder().setFieldPath("tags").build();
        StructuredQuery.Filter ageFilter = StructuredQuery.Filter.newBuilder()
                .setFieldFilter(StructuredQuery.FieldFilter.newBuilder()
                        .setField(ageField)
                        .setOp(StructuredQuery.FieldFilter.Operator.GREATER_THAN_OR_EQUAL)
                        .setValue(Value.newBuilder().setIntegerValue(21L)))
                .build();
        StructuredQuery.Filter tagsFilter = StructuredQuery.Filter.newBuilder()
                .setUnaryFilter(StructuredQuery.UnaryFilter.newBuilder()
                        .setField(tagsField)
                        .setOp(StructuredQuery.UnaryFilter.Operator.IS_NOT_NULL))
                .build();
        StructuredQuery.Filter compositeFilter = StructuredQuery.Filter.newBuilder()
                .setCompositeFilter(StructuredQuery.CompositeFilter.newBuilder()
                        .setOp(StructuredQuery.CompositeFilter.Operator.AND)
                        .addFilters(ageFilter)
                        .addFilters(tagsFilter))
                .build();
        Cursor startCursor = Cursor.newBuilder()
                .addValues(Value.newBuilder().setIntegerValue(21L))
                .setBefore(true)
                .build();
        StructuredQuery.FindNearest findNearest = StructuredQuery.FindNearest.newBuilder()
                .setVectorField(StructuredQuery.FieldReference.newBuilder().setFieldPath("embedding"))
                .setQueryVector(Value.newBuilder()
                        .setArrayValue(ArrayValue.newBuilder()
                                .addValues(Value.newBuilder().setDoubleValue(0.1D))
                                .addValues(Value.newBuilder().setDoubleValue(0.2D))))
                .setDistanceMeasure(StructuredQuery.FindNearest.DistanceMeasure.COSINE)
                .setLimit(Int32Value.of(10))
                .setDistanceResultField("distance")
                .setDistanceThreshold(DoubleValue.of(0.75D))
                .build();

        StructuredQuery query = StructuredQuery.newBuilder()
                .setSelect(StructuredQuery.Projection.newBuilder()
                        .addFields(StructuredQuery.FieldReference.newBuilder().setFieldPath("name"))
                        .addFields(ageField))
                .addFrom(StructuredQuery.CollectionSelector.newBuilder()
                        .setCollectionId("users")
                        .setAllDescendants(true))
                .setWhere(compositeFilter)
                .addOrderBy(StructuredQuery.Order.newBuilder()
                        .setField(ageField)
                        .setDirection(StructuredQuery.Direction.DESCENDING))
                .setStartAt(startCursor)
                .setEndAt(startCursor.toBuilder().setBefore(false))
                .setOffset(5)
                .setLimit(Int32Value.of(20))
                .setFindNearest(findNearest)
                .build();

        assertThat(query.getSelect().getFieldsList()).extracting(StructuredQuery.FieldReference::getFieldPath)
                .containsExactly("name", "age");
        assertThat(query.getWhere().getCompositeFilter().getFiltersList())
                .extracting(StructuredQuery.Filter::getFilterTypeCase)
                .containsExactly(StructuredQuery.Filter.FilterTypeCase.FIELD_FILTER,
                        StructuredQuery.Filter.FilterTypeCase.UNARY_FILTER);
        assertThat(query.getOrderBy(0).getDirection()).isEqualTo(StructuredQuery.Direction.DESCENDING);
        assertThat(query.getStartAt().getBefore()).isTrue();
        assertThat(query.getEndAt().getBefore()).isFalse();
        assertThat(query.getFindNearest().getQueryVector().getArrayValue().getValuesCount()).isEqualTo(2);
        assertThat(query.getFindNearest().getDistanceThreshold().getValue()).isEqualTo(0.75D);
    }

    @Test
    void queryExplainMetricsExposePlanSummaryAndExecutionStats() {
        Struct indexUsed = Struct.newBuilder()
                .putFields("query_scope", com.google.protobuf.Value.newBuilder().setStringValue("Collection").build())
                .putFields("properties", com.google.protobuf.Value.newBuilder()
                        .setStringValue("(age DESC, __name__ ASC)")
                        .build())
                .build();
        Struct debugStats = Struct.newBuilder()
                .putFields("index_entries_scanned", com.google.protobuf.Value.newBuilder().setNumberValue(5D).build())
                .putFields("documents_scanned", com.google.protobuf.Value.newBuilder().setNumberValue(2D).build())
                .build();
        ExecutionStats executionStats = ExecutionStats.newBuilder()
                .setResultsReturned(2L)
                .setExecutionDuration(Duration.newBuilder().setNanos(12_000_000))
                .setReadOperations(3L)
                .setDebugStats(debugStats)
                .build();
        ExplainMetrics explainMetrics = ExplainMetrics.newBuilder()
                .setPlanSummary(PlanSummary.newBuilder().addIndexesUsed(indexUsed))
                .setExecutionStats(executionStats)
                .build();
        RunQueryResponse response = RunQueryResponse.newBuilder()
                .setDocument(Document.newBuilder()
                        .setName(DOCUMENT_NAME)
                        .putFields("name", Value.newBuilder().setStringValue("Alice").build()))
                .setReadTime(timestamp(45))
                .setSkippedResults(1)
                .setExplainMetrics(explainMetrics)
                .build();
        RunQueryResponse doneResponse = response.toBuilder()
                .clearDocument()
                .setDone(true)
                .build();

        assertThat(response.hasExplainMetrics()).isTrue();
        assertThat(response.getExplainMetrics().getPlanSummary().getIndexesUsed(0).getFieldsOrThrow("query_scope")
                .getStringValue()).isEqualTo("Collection");
        assertThat(response.getExplainMetrics().getExecutionStats().getResultsReturned()).isEqualTo(2L);
        assertThat(response.getExplainMetrics().getExecutionStats().getExecutionDuration().getNanos())
                .isEqualTo(12_000_000);
        assertThat(response.getExplainMetrics().getExecutionStats().getReadOperations()).isEqualTo(3L);
        assertThat(response.getExplainMetrics().getExecutionStats().getDebugStats()
                .getFieldsOrThrow("index_entries_scanned").getNumberValue()).isEqualTo(5D);
        assertThat(response.getDocument().getFieldsOrThrow("name").getStringValue()).isEqualTo("Alice");
        assertThat(response.getSkippedResults()).isEqualTo(1);
        assertThat(doneResponse.getContinuationSelectorCase())
                .isEqualTo(RunQueryResponse.ContinuationSelectorCase.DONE);
        assertThat(doneResponse.getDone()).isTrue();
        assertThat(doneResponse.hasDocument()).isFalse();
        assertThat(doneResponse.hasExplainMetrics()).isTrue();
    }

    @Test
    void aggregationQueriesAndResponsesPreserveOperatorAndResultMaps() {
        StructuredQuery structuredQuery = StructuredQuery.newBuilder()
                .addFrom(StructuredQuery.CollectionSelector.newBuilder().setCollectionId("users"))
                .build();
        StructuredAggregationQuery.Aggregation count = StructuredAggregationQuery.Aggregation.newBuilder()
                .setCount(StructuredAggregationQuery.Aggregation.Count.newBuilder())
                .setAlias("user_count")
                .build();
        StructuredAggregationQuery.Aggregation sum = StructuredAggregationQuery.Aggregation.newBuilder()
                .setSum(StructuredAggregationQuery.Aggregation.Sum.newBuilder()
                        .setField(StructuredQuery.FieldReference.newBuilder().setFieldPath("age")))
                .setAlias("total_age")
                .build();
        StructuredAggregationQuery aggregationQuery = StructuredAggregationQuery.newBuilder()
                .setStructuredQuery(structuredQuery)
                .addAggregations(count)
                .addAggregations(sum)
                .build();
        RunAggregationQueryRequest request = RunAggregationQueryRequest.newBuilder()
                .setParent(DATABASE_NAME + "/documents")
                .setStructuredAggregationQuery(aggregationQuery)
                .setExplainOptions(ExplainOptions.newBuilder().setAnalyze(true))
                .setNewTransaction(TransactionOptions.newBuilder()
                        .setReadOnly(TransactionOptions.ReadOnly.newBuilder().setReadTime(timestamp(12))))
                .build();
        AggregationResult result = AggregationResult.newBuilder()
                .putAggregateFields("user_count", Value.newBuilder().setIntegerValue(3L).build())
                .putAggregateFields("total_age", Value.newBuilder().setIntegerValue(126L).build())
                .build();
        RunAggregationQueryResponse response = RunAggregationQueryResponse.newBuilder()
                .setResult(result)
                .setTransaction(ByteString.copyFromUtf8("tx-1"))
                .setReadTime(timestamp(13))
                .build();

        assertThat(request.getQueryTypeCase())
                .isEqualTo(RunAggregationQueryRequest.QueryTypeCase.STRUCTURED_AGGREGATION_QUERY);
        assertThat(request.getConsistencySelectorCase())
                .isEqualTo(RunAggregationQueryRequest.ConsistencySelectorCase.NEW_TRANSACTION);
        assertThat(request.getStructuredAggregationQuery().getAggregationsList())
                .extracting(StructuredAggregationQuery.Aggregation::getOperatorCase)
                .containsExactly(StructuredAggregationQuery.Aggregation.OperatorCase.COUNT,
                        StructuredAggregationQuery.Aggregation.OperatorCase.SUM);
        assertThat(response.getResult().getAggregateFieldsOrThrow("total_age").getIntegerValue()).isEqualTo(126L);
        assertThat(response.getReadTime().getSeconds()).isEqualTo(13L);
    }

    @Test
    void documentRequestsUseConsistencySelectorsMasksAndPaging() {
        DocumentMask documentMask = DocumentMask.newBuilder().addFieldPaths("name").addFieldPaths("age").build();
        GetDocumentRequest getRequest = GetDocumentRequest.newBuilder()
                .setName(DOCUMENT_NAME)
                .setMask(documentMask)
                .setReadTime(timestamp(20))
                .build();
        ListDocumentsRequest listRequest = ListDocumentsRequest.newBuilder()
                .setParent(DATABASE_NAME + "/documents")
                .setCollectionId("users")
                .setPageSize(25)
                .setPageToken("page-1")
                .setOrderBy("age desc")
                .setMask(documentMask)
                .setShowMissing(true)
                .setTransaction(ByteString.copyFromUtf8("tx-2"))
                .build();
        BatchGetDocumentsRequest batchGetRequest = BatchGetDocumentsRequest.newBuilder()
                .setDatabase(DATABASE_NAME)
                .addDocuments(DOCUMENT_NAME)
                .setMask(documentMask)
                .setNewTransaction(TransactionOptions.newBuilder()
                        .setReadWrite(TransactionOptions.ReadWrite.newBuilder()
                                .setRetryTransaction(ByteString.copyFromUtf8("retry"))))
                .build();
        RunQueryRequest runQueryRequest = RunQueryRequest.newBuilder()
                .setParent(DATABASE_NAME + "/documents")
                .setStructuredQuery(StructuredQuery.newBuilder()
                        .addFrom(StructuredQuery.CollectionSelector.newBuilder().setCollectionId("users")))
                .setTransaction(ByteString.copyFromUtf8("tx-3"))
                .setExplainOptions(ExplainOptions.newBuilder().setAnalyze(false))
                .build();

        assertThat(getRequest.getConsistencySelectorCase()).isEqualTo(GetDocumentRequest.ConsistencySelectorCase.READ_TIME);
        assertThat(getRequest.getMask().getFieldPathsList()).containsExactly("name", "age");
        assertThat(listRequest.getConsistencySelectorCase()).isEqualTo(ListDocumentsRequest.ConsistencySelectorCase.TRANSACTION);
        assertThat(listRequest.getShowMissing()).isTrue();
        assertThat(batchGetRequest.getConsistencySelectorCase())
                .isEqualTo(BatchGetDocumentsRequest.ConsistencySelectorCase.NEW_TRANSACTION);
        assertThat(batchGetRequest.getNewTransaction().getModeCase()).isEqualTo(TransactionOptions.ModeCase.READ_WRITE);
        assertThat(runQueryRequest.getQueryTypeCase()).isEqualTo(RunQueryRequest.QueryTypeCase.STRUCTURED_QUERY);
        assertThat(runQueryRequest.getConsistencySelectorCase()).isEqualTo(RunQueryRequest.ConsistencySelectorCase.TRANSACTION);
    }

    @Test
    void partitionQueriesUsePagingReadTimesAndReturnSplitCursors() {
        StructuredQuery query = StructuredQuery.newBuilder()
                .addFrom(StructuredQuery.CollectionSelector.newBuilder().setCollectionId("users"))
                .addOrderBy(StructuredQuery.Order.newBuilder()
                        .setField(StructuredQuery.FieldReference.newBuilder().setFieldPath("name"))
                        .setDirection(StructuredQuery.Direction.ASCENDING))
                .build();
        Cursor middlePartition = Cursor.newBuilder()
                .addValues(Value.newBuilder().setStringValue("m"))
                .setBefore(true)
                .build();
        Cursor finalPartition = Cursor.newBuilder()
                .addValues(Value.newBuilder().setStringValue("t"))
                .build();
        PartitionQueryRequest request = PartitionQueryRequest.newBuilder()
                .setParent(DATABASE_NAME + "/documents")
                .setStructuredQuery(query)
                .setPartitionCount(3L)
                .setPageSize(2)
                .setPageToken("partition-page-1")
                .setReadTime(timestamp(40))
                .build();
        PartitionQueryResponse response = PartitionQueryResponse.newBuilder()
                .addPartitions(middlePartition)
                .addPartitions(finalPartition)
                .setNextPageToken("partition-page-2")
                .build();

        assertThat(request.getQueryTypeCase()).isEqualTo(PartitionQueryRequest.QueryTypeCase.STRUCTURED_QUERY);
        assertThat(request.getConsistencySelectorCase())
                .isEqualTo(PartitionQueryRequest.ConsistencySelectorCase.READ_TIME);
        assertThat(request.getPartitionCount()).isEqualTo(3L);
        assertThat(request.getPageSize()).isEqualTo(2);
        assertThat(request.getPageToken()).isEqualTo("partition-page-1");
        assertThat(request.getStructuredQuery().getOrderBy(0).getField().getFieldPath()).isEqualTo("name");
        assertThat(response.getPartitionsList())
                .extracting(partition -> partition.getValues(0).getStringValue())
                .containsExactly("m", "t");
        assertThat(response.getPartitions(0).getBefore()).isTrue();
        assertThat(response.getNextPageToken()).isEqualTo("partition-page-2");
    }

    @Test
    void writesTransformsCommitAndBatchRequestsTrackOperationOneofs() {
        Document document = Document.newBuilder()
                .setName(DOCUMENT_NAME)
                .putFields("visits", Value.newBuilder().setIntegerValue(1L).build())
                .build();
        DocumentTransform.FieldTransform incrementVisits = DocumentTransform.FieldTransform.newBuilder()
                .setFieldPath("visits")
                .setIncrement(Value.newBuilder().setIntegerValue(1L))
                .build();
        DocumentTransform.FieldTransform appendTag = DocumentTransform.FieldTransform.newBuilder()
                .setFieldPath("tags")
                .setAppendMissingElements(ArrayValue.newBuilder()
                        .addValues(Value.newBuilder().setStringValue("active")))
                .build();
        Write updateWrite = Write.newBuilder()
                .setUpdate(document)
                .setUpdateMask(DocumentMask.newBuilder().addFieldPaths("visits"))
                .addUpdateTransforms(incrementVisits)
                .setCurrentDocument(Precondition.newBuilder().setExists(true))
                .build();
        Write transformWrite = Write.newBuilder()
                .setTransform(DocumentTransform.newBuilder()
                        .setDocument(DOCUMENT_NAME)
                        .addFieldTransforms(appendTag))
                .build();
        Write deleteWrite = Write.newBuilder()
                .setDelete(DOCUMENT_NAME)
                .setCurrentDocument(Precondition.newBuilder().setUpdateTime(timestamp(25)))
                .build();
        CommitRequest commitRequest = CommitRequest.newBuilder()
                .setDatabase(DATABASE_NAME)
                .addWrites(updateWrite)
                .addWrites(transformWrite)
                .setTransaction(ByteString.copyFromUtf8("tx-commit"))
                .build();
        BatchWriteRequest batchWriteRequest = BatchWriteRequest.newBuilder()
                .setDatabase(DATABASE_NAME)
                .addWrites(deleteWrite)
                .putLabels("source", "test")
                .build();

        assertThat(updateWrite.getOperationCase()).isEqualTo(Write.OperationCase.UPDATE);
        assertThat(updateWrite.getUpdateTransforms(0).getTransformTypeCase())
                .isEqualTo(DocumentTransform.FieldTransform.TransformTypeCase.INCREMENT);
        assertThat(transformWrite.getOperationCase()).isEqualTo(Write.OperationCase.TRANSFORM);
        assertThat(transformWrite.getTransform().getFieldTransforms(0).getTransformTypeCase())
                .isEqualTo(DocumentTransform.FieldTransform.TransformTypeCase.APPEND_MISSING_ELEMENTS);
        assertThat(deleteWrite.getOperationCase()).isEqualTo(Write.OperationCase.DELETE);
        assertThat(deleteWrite.getCurrentDocument().getConditionTypeCase())
                .isEqualTo(Precondition.ConditionTypeCase.UPDATE_TIME);
        assertThat(commitRequest.getWritesList()).extracting(Write::getOperationCase)
                .containsExactly(Write.OperationCase.UPDATE, Write.OperationCase.TRANSFORM);
        assertThat(batchWriteRequest.getLabelsMap()).containsEntry("source", "test");
    }

    @Test
    void listenTargetsAndResponsesModelWatchStreamState() {
        Target queryTarget = Target.newBuilder()
                .setQuery(Target.QueryTarget.newBuilder()
                        .setParent(DATABASE_NAME + "/documents")
                        .setStructuredQuery(StructuredQuery.newBuilder()
                                .addFrom(StructuredQuery.CollectionSelector.newBuilder().setCollectionId("users"))))
                .setTargetId(7)
                .setOnce(true)
                .setExpectedCount(Int32Value.of(1))
                .setResumeToken(ByteString.copyFromUtf8("resume-token"))
                .build();
        Target documentsTarget = Target.newBuilder()
                .setDocuments(Target.DocumentsTarget.newBuilder().addDocuments(DOCUMENT_NAME))
                .setTargetId(8)
                .setReadTime(timestamp(30))
                .build();
        ListenRequest addTargetRequest = ListenRequest.newBuilder()
                .setDatabase(DATABASE_NAME)
                .setAddTarget(queryTarget)
                .putLabels("purpose", "coverage")
                .build();
        ListenRequest removeTargetRequest = ListenRequest.newBuilder()
                .setDatabase(DATABASE_NAME)
                .setRemoveTarget(7)
                .build();
        DocumentChange documentChange = DocumentChange.newBuilder()
                .setDocument(Document.newBuilder().setName(DOCUMENT_NAME))
                .addTargetIds(7)
                .addRemovedTargetIds(8)
                .build();
        ListenResponse targetChangeResponse = ListenResponse.newBuilder()
                .setTargetChange(TargetChange.newBuilder()
                        .setTargetChangeType(TargetChange.TargetChangeType.CURRENT)
                        .addTargetIds(7)
                        .setResumeToken(ByteString.copyFromUtf8("resume-token-2"))
                        .setReadTime(timestamp(31)))
                .build();
        ListenResponse documentChangeResponse = ListenResponse.newBuilder()
                .setDocumentChange(documentChange)
                .build();

        assertThat(queryTarget.getTargetTypeCase()).isEqualTo(Target.TargetTypeCase.QUERY);
        assertThat(queryTarget.getResumeTypeCase()).isEqualTo(Target.ResumeTypeCase.RESUME_TOKEN);
        assertThat(documentsTarget.getTargetTypeCase()).isEqualTo(Target.TargetTypeCase.DOCUMENTS);
        assertThat(documentsTarget.getResumeTypeCase()).isEqualTo(Target.ResumeTypeCase.READ_TIME);
        assertThat(addTargetRequest.getTargetChangeCase()).isEqualTo(ListenRequest.TargetChangeCase.ADD_TARGET);
        assertThat(addTargetRequest.getLabelsMap()).containsEntry("purpose", "coverage");
        assertThat(removeTargetRequest.getTargetChangeCase()).isEqualTo(ListenRequest.TargetChangeCase.REMOVE_TARGET);
        assertThat(targetChangeResponse.getResponseTypeCase()).isEqualTo(ListenResponse.ResponseTypeCase.TARGET_CHANGE);
        assertThat(targetChangeResponse.getTargetChange().getTargetChangeType())
                .isEqualTo(TargetChange.TargetChangeType.CURRENT);
        assertThat(documentChangeResponse.getResponseTypeCase()).isEqualTo(ListenResponse.ResponseTypeCase.DOCUMENT_CHANGE);
        assertThat(documentChangeResponse.getDocumentChange().getRemovedTargetIdsList()).containsExactly(8);
    }

    private static Timestamp timestamp(long seconds) {
        return Timestamp.newBuilder().setSeconds(seconds).build();
    }
}
