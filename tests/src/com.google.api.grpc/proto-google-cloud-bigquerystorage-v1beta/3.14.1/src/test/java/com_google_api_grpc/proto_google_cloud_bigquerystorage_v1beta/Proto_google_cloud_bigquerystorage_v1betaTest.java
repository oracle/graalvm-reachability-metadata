/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_api_grpc.proto_google_cloud_bigquerystorage_v1beta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.api.AnnotationsProto;
import com.google.api.ClientProto;
import com.google.api.FieldBehavior;
import com.google.api.FieldBehaviorProto;
import com.google.api.HttpRule;
import com.google.api.ResourceDescriptor;
import com.google.api.ResourceProto;
import com.google.cloud.bigquery.storage.v1beta.BatchCreateMetastorePartitionsRequest;
import com.google.cloud.bigquery.storage.v1beta.BatchCreateMetastorePartitionsResponse;
import com.google.cloud.bigquery.storage.v1beta.BatchDeleteMetastorePartitionsRequest;
import com.google.cloud.bigquery.storage.v1beta.BatchSizeTooLargeError;
import com.google.cloud.bigquery.storage.v1beta.BatchUpdateMetastorePartitionsRequest;
import com.google.cloud.bigquery.storage.v1beta.BatchUpdateMetastorePartitionsResponse;
import com.google.cloud.bigquery.storage.v1beta.CreateMetastorePartitionRequest;
import com.google.cloud.bigquery.storage.v1beta.FieldSchema;
import com.google.cloud.bigquery.storage.v1beta.ListMetastorePartitionsRequest;
import com.google.cloud.bigquery.storage.v1beta.ListMetastorePartitionsResponse;
import com.google.cloud.bigquery.storage.v1beta.MetastorePartition;
import com.google.cloud.bigquery.storage.v1beta.MetastorePartitionList;
import com.google.cloud.bigquery.storage.v1beta.MetastorePartitionProto;
import com.google.cloud.bigquery.storage.v1beta.MetastorePartitionServiceProto;
import com.google.cloud.bigquery.storage.v1beta.MetastorePartitionValues;
import com.google.cloud.bigquery.storage.v1beta.ReadStream;
import com.google.cloud.bigquery.storage.v1beta.SerDeInfo;
import com.google.cloud.bigquery.storage.v1beta.StorageDescriptor;
import com.google.cloud.bigquery.storage.v1beta.StreamList;
import com.google.cloud.bigquery.storage.v1beta.StreamMetastorePartitionsRequest;
import com.google.cloud.bigquery.storage.v1beta.StreamMetastorePartitionsResponse;
import com.google.cloud.bigquery.storage.v1beta.TableName;
import com.google.cloud.bigquery.storage.v1beta.UpdateMetastorePartitionRequest;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.Descriptors.MethodDescriptor;
import com.google.protobuf.Descriptors.ServiceDescriptor;
import com.google.protobuf.FieldMask;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Timestamp;
import com.google.rpc.Code;
import com.google.rpc.Status;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class Proto_google_cloud_bigquerystorage_v1betaTest {
    private static final String PROJECT = "sample-project";
    private static final String DATASET = "analytics";
    private static final String TABLE = "events";
    private static final String PARENT = "projects/" + PROJECT + "/datasets/" + DATASET + "/tables/" + TABLE;
    private static final String LOCATION_PARENT = "projects/" + PROJECT + "/locations/us/datasets/" + DATASET
            + "/tables/" + TABLE;
    private static final String TRACE_ID = "trace-2026-05-04";
    private static final String STREAM_ONE = "projects/sample-project/locations/us/sessions/session-1/streams/stream-1";
    private static final String STREAM_TWO = "projects/sample-project/locations/us/sessions/session-1/streams/stream-2";

    @Test
    void tableNameFormatsParsesAndExposesFieldValues() {
        TableName tableName = TableName.of(PROJECT, DATASET, TABLE);

        assertEquals(PARENT, tableName.toString());
        assertEquals(PARENT, TableName.format(PROJECT, DATASET, TABLE));
        assertEquals(PROJECT, TableName.parse(PARENT).getProject());
        assertEquals(DATASET, TableName.parse(PARENT).getDataset());
        assertEquals(TABLE, TableName.parse(PARENT).getTable());
        assertEquals(Map.of("project", PROJECT, "dataset", DATASET, "table", TABLE), tableName.getFieldValuesMap());
        assertEquals(DATASET, tableName.getFieldValue("dataset"));
        assertTrue(TableName.isParsableFrom(PARENT));
        assertFalse(TableName.isParsableFrom(LOCATION_PARENT));
        assertEquals(tableName, tableName.toBuilder().build());
        assertNotEquals(tableName, TableName.of(PROJECT, DATASET, "other"));

        TableName rebuilt = TableName.newBuilder()
                .setProject(PROJECT)
                .setDataset(DATASET)
                .setTable(TABLE)
                .build();
        assertEquals(tableName, rebuilt);

        List<TableName> names = List.of(tableName, TableName.of("billing-project", "warehouse", "orders"));
        List<String> formatted = TableName.toStringList(names);
        assertIterableEquals(List.of(PARENT, "projects/billing-project/datasets/warehouse/tables/orders"), formatted);
        assertIterableEquals(names, TableName.parseList(formatted));
    }

    @Test
    void metastorePartitionRoundTripsNestedStorageDescriptorFieldsAndMaps() throws Exception {
        Timestamp createTime = Timestamp.newBuilder().setSeconds(1_700_000_000L).setNanos(123_000_000).build();
        SerDeInfo serdeInfo = SerDeInfo.newBuilder()
                .setName("orc-serde")
                .setSerializationLibrary("org.apache.hadoop.hive.ql.io.orc.OrcSerde")
                .putParameters("serialization.format", "1")
                .putParameters("field.delim", "|")
                .build();
        StorageDescriptor storageDescriptor = StorageDescriptor.newBuilder()
                .setLocationUri("gs://warehouse/events/dt=2026-05-04/region=emea")
                .setInputFormat("org.apache.hadoop.hive.ql.io.orc.OrcInputFormat")
                .setOutputFormat("org.apache.hadoop.hive.ql.io.orc.OrcOutputFormat")
                .setSerdeInfo(serdeInfo)
                .build();
        MetastorePartition partition = MetastorePartition.newBuilder()
                .addValues("2026-05-04")
                .addValues("emea")
                .setCreateTime(createTime)
                .setStorageDescriptor(storageDescriptor)
                .putParameters("source", "daily-ingest")
                .putParameters("quality", "gold")
                .addFields(FieldSchema.newBuilder().setName("user_id").setType("STRING"))
                .addFields(FieldSchema.newBuilder().setName("event_count").setType("INT64"))
                .build();

        MetastorePartition parsed = MetastorePartition.parseFrom(new ByteArrayInputStream(partition.toByteArray()));

        assertIterableEquals(List.of("2026-05-04", "emea"), parsed.getValuesList());
        assertEquals(ByteString.copyFromUtf8("emea"), parsed.getValuesBytes(1));
        assertTrue(parsed.hasCreateTime());
        assertEquals(createTime, parsed.getCreateTime());
        assertTrue(parsed.hasStorageDescriptor());
        assertEquals("gs://warehouse/events/dt=2026-05-04/region=emea",
                parsed.getStorageDescriptor().getLocationUri());
        assertTrue(parsed.getStorageDescriptor().hasSerdeInfo());
        assertEquals("org.apache.hadoop.hive.ql.io.orc.OrcSerde",
                parsed.getStorageDescriptor().getSerdeInfo().getSerializationLibrary());
        assertEquals("|", parsed.getStorageDescriptor().getSerdeInfo().getParametersOrThrow("field.delim"));
        assertEquals("fallback", parsed.getStorageDescriptor().getSerdeInfo().getParametersOrDefault(
                "missing", "fallback"));
        assertEquals("gold", parsed.getParametersOrThrow("quality"));
        assertTrue(parsed.containsParameters("source"));
        assertEquals(2, parsed.getFieldsCount());
        assertEquals("event_count", parsed.getFields(1).getName());
        assertEquals("INT64", parsed.getFields(1).getType());

        StorageDescriptor movedStorage = parsed.getStorageDescriptor().toBuilder()
                .setLocationUri("gs://warehouse/events/dt=2026-05-04/region=apac")
                .setSerdeInfo(parsed.getStorageDescriptor().getSerdeInfo().toBuilder()
                        .putParameters("compression", "zstd"))
                .build();
        MetastorePartition modified = parsed.toBuilder()
                .setValues(1, "apac")
                .removeParameters("quality")
                .putParameters("quality", "silver")
                .setStorageDescriptor(movedStorage)
                .build();
        assertIterableEquals(List.of("2026-05-04", "apac"), modified.getValuesList());
        assertEquals("silver", modified.getParametersOrThrow("quality"));
        assertEquals("gs://warehouse/events/dt=2026-05-04/region=apac",
                modified.getStorageDescriptor().getLocationUri());
    }

    @Test
    void nestedBuildersConstructAndMutateBatchCreateRequestsInPlace() {
        BatchCreateMetastorePartitionsRequest.Builder batchBuilder = BatchCreateMetastorePartitionsRequest
                .newBuilder()
                .setParent(LOCATION_PARENT)
                .setTraceId(TRACE_ID);
        CreateMetastorePartitionRequest.Builder createBuilder = batchBuilder.addRequestsBuilder()
                .setParent(LOCATION_PARENT);
        MetastorePartition.Builder partitionBuilder = createBuilder.getMetastorePartitionBuilder()
                .addAllValues(List.of("2026-05-06", "amer"))
                .putAllParameters(Map.of("owner", "etl", "status", "new"));
        partitionBuilder.getCreateTimeBuilder().setSeconds(1_700_100_000L);
        partitionBuilder.getStorageDescriptorBuilder()
                .setLocationUri("gs://warehouse/events/dt=2026-05-06/region=amer")
                .setInputFormat("org.apache.hadoop.mapred.TextInputFormat")
                .getSerdeInfoBuilder()
                .setName("json-serde")
                .setSerializationLibrary("org.apache.hive.hcatalog.data.JsonSerDe")
                .putAllParameters(Map.of(
                        "ignore.malformed.json", "true",
                        "timestamp.formats", "yyyy-MM-dd HH:mm:ss"));
        partitionBuilder.addFieldsBuilder().setName("payload").setType("STRING");
        partitionBuilder.addFieldsBuilder().setName("ingested_at").setType("TIMESTAMP");
        partitionBuilder.getFieldsBuilder(0).setType("JSON");

        BatchCreateMetastorePartitionsRequest request = batchBuilder.build();

        assertEquals(1, request.getRequestsCount());
        MetastorePartition partition = request.getRequests(0).getMetastorePartition();
        assertTrue(partition.hasCreateTime());
        assertEquals(1_700_100_000L, partition.getCreateTime().getSeconds());
        assertIterableEquals(List.of("2026-05-06", "amer"), partition.getValuesList());
        assertEquals("JSON", partition.getFields(0).getType());
        assertEquals("TIMESTAMP", partition.getFields(1).getType());
        assertEquals("etl", partition.getParametersOrThrow("owner"));
        assertEquals("org.apache.hadoop.mapred.TextInputFormat", partition.getStorageDescriptor().getInputFormat());
        assertEquals("true", partition.getStorageDescriptor().getSerdeInfo().getParametersOrThrow(
                "ignore.malformed.json"));

        BatchCreateMetastorePartitionsRequest.Builder amendedBuilder = request.toBuilder();
        amendedBuilder.getRequestsBuilder(0)
                .getMetastorePartitionBuilder()
                .removeParameters("status")
                .putParameters("status", "queued")
                .getStorageDescriptorBuilder()
                .clearInputFormat();
        BatchCreateMetastorePartitionsRequest amended = amendedBuilder.build();

        MetastorePartition amendedPartition = amended.getRequests(0).getMetastorePartition();
        assertEquals("queued", amendedPartition.getParametersOrThrow("status"));
        assertEquals("", amendedPartition.getStorageDescriptor().getInputFormat());
        assertEquals("org.apache.hadoop.mapred.TextInputFormat", partition.getStorageDescriptor().getInputFormat());
    }

    @Test
    void createUpdateDeleteAndBatchMessagesPreserveRequestsMasksAndResponses() throws Exception {
        MetastorePartition firstPartition = partition("2026-05-04", "emea");
        MetastorePartition secondPartition = partition("2026-05-05", "apac");
        CreateMetastorePartitionRequest createRequest = CreateMetastorePartitionRequest.newBuilder()
                .setParent(LOCATION_PARENT)
                .setMetastorePartition(firstPartition)
                .build();
        BatchCreateMetastorePartitionsRequest batchCreate = BatchCreateMetastorePartitionsRequest.newBuilder()
                .setParent(LOCATION_PARENT)
                .addRequests(createRequest)
                .addRequests(CreateMetastorePartitionRequest.newBuilder()
                        .setParent(LOCATION_PARENT)
                        .setMetastorePartition(secondPartition))
                .setSkipExistingPartitions(true)
                .setTraceId(TRACE_ID)
                .build();

        BatchCreateMetastorePartitionsRequest parsedCreate = BatchCreateMetastorePartitionsRequest.parseFrom(
                ByteBuffer.wrap(batchCreate.toByteArray()));
        assertEquals(LOCATION_PARENT, parsedCreate.getParent());
        assertEquals(2, parsedCreate.getRequestsCount());
        assertTrue(parsedCreate.getSkipExistingPartitions());
        assertEquals(TRACE_ID, parsedCreate.getTraceId());
        assertEquals(firstPartition, parsedCreate.getRequests(0).getMetastorePartition());

        FieldMask updateMask = FieldMask.newBuilder()
                .addPaths("storage_descriptor.location_uri")
                .addPaths("parameters")
                .build();
        UpdateMetastorePartitionRequest updateRequest = UpdateMetastorePartitionRequest.newBuilder()
                .setMetastorePartition(firstPartition.toBuilder().putParameters("status", "corrected"))
                .setUpdateMask(updateMask)
                .build();
        BatchUpdateMetastorePartitionsRequest batchUpdate = BatchUpdateMetastorePartitionsRequest.newBuilder()
                .setParent(LOCATION_PARENT)
                .addRequests(updateRequest)
                .setTraceId(TRACE_ID)
                .build();

        BatchUpdateMetastorePartitionsRequest parsedUpdate = BatchUpdateMetastorePartitionsRequest.parseFrom(
                batchUpdate.toByteString());
        assertEquals(1, parsedUpdate.getRequestsCount());
        assertTrue(parsedUpdate.getRequests(0).hasUpdateMask());
        assertEquals(List.of("storage_descriptor.location_uri", "parameters"),
                parsedUpdate.getRequests(0).getUpdateMask().getPathsList());
        assertEquals("corrected", parsedUpdate.getRequests(0).getMetastorePartition().getParametersOrThrow("status"));

        BatchDeleteMetastorePartitionsRequest batchDelete = BatchDeleteMetastorePartitionsRequest.newBuilder()
                .setParent(LOCATION_PARENT)
                .addPartitionValues(MetastorePartitionValues.newBuilder().addValues("2026-05-04").addValues("emea"))
                .addPartitionValues(MetastorePartitionValues.newBuilder().addValues("2026-05-05").addValues("apac"))
                .setTraceId(TRACE_ID)
                .build();
        BatchDeleteMetastorePartitionsRequest parsedDelete = BatchDeleteMetastorePartitionsRequest.parseFrom(
                new ByteArrayInputStream(batchDelete.toByteArray()));
        assertEquals(2, parsedDelete.getPartitionValuesCount());
        assertIterableEquals(List.of("2026-05-05", "apac"), parsedDelete.getPartitionValues(1).getValuesList());

        BatchCreateMetastorePartitionsResponse createResponse = BatchCreateMetastorePartitionsResponse.newBuilder()
                .addPartitions(firstPartition)
                .addPartitions(secondPartition)
                .build();
        assertEquals("apac", BatchCreateMetastorePartitionsResponse.parseFrom(
                createResponse.toByteArray()).getPartitions(1).getValues(1));

        BatchUpdateMetastorePartitionsResponse updateResponse = BatchUpdateMetastorePartitionsResponse.newBuilder()
                .addPartitions(firstPartition)
                .build();
        assertEquals(firstPartition, BatchUpdateMetastorePartitionsResponse.parseDelimitedFrom(
                delimited(updateResponse)).getPartitions(0));
    }

    @Test
    void listRequestResponseAndOneofSwitchBetweenPartitionAndStreamLists() throws Exception {
        ListMetastorePartitionsRequest request = ListMetastorePartitionsRequest.newBuilder()
                .setParent(LOCATION_PARENT)
                .setFilter("dt >= DATE '2026-05-01' AND region IS NOT NULL")
                .setTraceId(TRACE_ID)
                .build();
        ListMetastorePartitionsRequest parsedRequest = ListMetastorePartitionsRequest.parseFrom(request.toByteString());
        assertEquals(LOCATION_PARENT, parsedRequest.getParent());
        assertEquals("dt >= DATE '2026-05-01' AND region IS NOT NULL", parsedRequest.getFilter());
        assertEquals(TRACE_ID, parsedRequest.getTraceId());

        MetastorePartitionList partitionList = MetastorePartitionList.newBuilder()
                .addPartitions(partition("2026-05-04", "emea"))
                .addPartitions(partition("2026-05-05", "apac"))
                .build();
        ListMetastorePartitionsResponse partitionsResponse = ListMetastorePartitionsResponse.newBuilder()
                .setPartitions(partitionList)
                .build();
        ListMetastorePartitionsResponse parsedPartitions = ListMetastorePartitionsResponse.parseFrom(
                ByteBuffer.wrap(partitionsResponse.toByteArray()));
        assertSame(ListMetastorePartitionsResponse.ResponseCase.PARTITIONS, parsedPartitions.getResponseCase());
        assertTrue(parsedPartitions.hasPartitions());
        assertFalse(parsedPartitions.hasStreams());
        assertEquals(2, parsedPartitions.getPartitions().getPartitionsCount());
        assertEquals("apac", parsedPartitions.getPartitions().getPartitions(1).getValues(1));

        StreamList streamList = StreamList.newBuilder()
                .addStreams(ReadStream.newBuilder().setName(STREAM_ONE))
                .addStreams(ReadStream.newBuilder().setName(STREAM_TWO))
                .build();
        ListMetastorePartitionsResponse streamsResponse = parsedPartitions.toBuilder()
                .setStreams(streamList)
                .build();
        assertSame(ListMetastorePartitionsResponse.ResponseCase.STREAMS, streamsResponse.getResponseCase());
        assertFalse(streamsResponse.hasPartitions());
        assertTrue(streamsResponse.hasStreams());
        assertEquals(STREAM_TWO, streamsResponse.getStreams().getStreams(1).getName());

        ListMetastorePartitionsResponse cleared = streamsResponse.toBuilder().clearResponse().build();
        assertSame(ListMetastorePartitionsResponse.ResponseCase.RESPONSE_NOT_SET, cleared.getResponseCase());
    }

    @Test
    void streamingMessagesAndStructuredErrorsRoundTrip() throws Exception {
        StreamMetastorePartitionsRequest request = StreamMetastorePartitionsRequest.newBuilder()
                .setParent(LOCATION_PARENT)
                .addMetastorePartitions(partition("2026-05-04", "emea"))
                .addMetastorePartitions(partition("2026-05-05", "apac"))
                .setSkipExistingPartitions(true)
                .build();
        StreamMetastorePartitionsRequest parsedRequest = StreamMetastorePartitionsRequest.parseDelimitedFrom(
                delimited(request));
        assertEquals(LOCATION_PARENT, parsedRequest.getParent());
        assertTrue(parsedRequest.getSkipExistingPartitions());
        assertEquals(2, parsedRequest.getMetastorePartitionsCount());
        assertEquals("2026-05-05", parsedRequest.getMetastorePartitions(1).getValues(0));

        StreamMetastorePartitionsResponse response = StreamMetastorePartitionsResponse.newBuilder()
                .setTotalPartitionsStreamedCount(2L)
                .setTotalPartitionsInsertedCount(1L)
                .build();
        StreamMetastorePartitionsResponse parsedResponse = StreamMetastorePartitionsResponse.parseFrom(
                response.toByteString());
        assertEquals(2L, parsedResponse.getTotalPartitionsStreamedCount());
        assertEquals(1L, parsedResponse.getTotalPartitionsInsertedCount());

        BatchSizeTooLargeError error = BatchSizeTooLargeError.newBuilder()
                .setMaxBatchSize(900L)
                .setErrorMessage("batch contains too many partitions")
                .build();
        BatchSizeTooLargeError parsedError = BatchSizeTooLargeError.parser().parseFrom(error.toByteArray());
        assertEquals(900L, parsedError.getMaxBatchSize());
        assertEquals("batch contains too many partitions", parsedError.getErrorMessage());
    }

    @Test
    void structuredErrorsCanBeAttachedToRpcStatusDetails() throws Exception {
        BatchSizeTooLargeError error = BatchSizeTooLargeError.newBuilder()
                .setMaxBatchSize(900L)
                .setErrorMessage("batch contains too many partitions")
                .build();

        Status status = Status.newBuilder()
                .setCode(Code.INVALID_ARGUMENT_VALUE)
                .setMessage("cannot commit metastore partitions")
                .addDetails(Any.pack(error))
                .build();

        assertEquals(Code.INVALID_ARGUMENT_VALUE, status.getCode());
        assertEquals(Code.INVALID_ARGUMENT, Code.forNumber(status.getCode()));
        assertEquals("cannot commit metastore partitions", status.getMessage());
        assertEquals(1, status.getDetailsCount());

        Any detail = status.getDetails(0);
        assertEquals("type.googleapis.com/google.cloud.bigquery.storage.v1beta.BatchSizeTooLargeError",
                detail.getTypeUrl());
        assertTrue(detail.is(BatchSizeTooLargeError.class));
        assertFalse(detail.is(MetastorePartition.class));

        BatchSizeTooLargeError unpacked = detail.unpack(BatchSizeTooLargeError.class);
        assertEquals(900L, unpacked.getMaxBatchSize());
        assertEquals("batch contains too many partitions", unpacked.getErrorMessage());
    }

    @Test
    void generatedDescriptorsExposeProtosMessagesServicesAndStreamingMethods() {
        FileDescriptor partitionDescriptor = MetastorePartitionProto.getDescriptor();
        assertEquals("google/cloud/bigquery/storage/v1beta/partition.proto", partitionDescriptor.getName());
        assertEquals(FieldSchema.getDescriptor(), partitionDescriptor.findMessageTypeByName("FieldSchema"));
        assertEquals(MetastorePartition.getDescriptor(),
                partitionDescriptor.findMessageTypeByName("MetastorePartition"));
        assertEquals(ReadStream.getDescriptor(), partitionDescriptor.findMessageTypeByName("ReadStream"));
        assertSame(partitionDescriptor, MetastorePartition.getDescriptor().getFile());

        FileDescriptor serviceDescriptor = MetastorePartitionServiceProto.getDescriptor();
        assertEquals("google/cloud/bigquery/storage/v1beta/metastore_partition.proto", serviceDescriptor.getName());
        assertEquals(BatchCreateMetastorePartitionsRequest.getDescriptor(),
                serviceDescriptor.findMessageTypeByName("BatchCreateMetastorePartitionsRequest"));
        assertEquals(ListMetastorePartitionsResponse.getDescriptor(),
                serviceDescriptor.findMessageTypeByName("ListMetastorePartitionsResponse"));

        ServiceDescriptor service = serviceDescriptor.findServiceByName("MetastorePartitionService");
        assertNotNull(service);
        assertEquals("bigquerystorage.googleapis.com", service.getOptions().getExtension(ClientProto.defaultHost));
        assertEquals("https://www.googleapis.com/auth/bigquery,https://www.googleapis.com/auth/cloud-platform",
                service.getOptions().getExtension(ClientProto.oauthScopes));

        MethodDescriptor batchCreate = service.findMethodByName("BatchCreateMetastorePartitions");
        MethodDescriptor batchDelete = service.findMethodByName("BatchDeleteMetastorePartitions");
        MethodDescriptor batchUpdate = service.findMethodByName("BatchUpdateMetastorePartitions");
        MethodDescriptor list = service.findMethodByName("ListMetastorePartitions");
        MethodDescriptor stream = service.findMethodByName("StreamMetastorePartitions");
        assertNotNull(batchCreate);
        assertNotNull(batchDelete);
        assertNotNull(batchUpdate);
        assertNotNull(list);
        assertNotNull(stream);
        assertEquals(BatchCreateMetastorePartitionsRequest.getDescriptor(), batchCreate.getInputType());
        assertEquals(BatchCreateMetastorePartitionsResponse.getDescriptor(), batchCreate.getOutputType());
        assertEquals(BatchDeleteMetastorePartitionsRequest.getDescriptor(), batchDelete.getInputType());
        assertEquals(BatchUpdateMetastorePartitionsRequest.getDescriptor(), batchUpdate.getInputType());
        assertEquals(BatchUpdateMetastorePartitionsResponse.getDescriptor(), batchUpdate.getOutputType());
        assertEquals(ListMetastorePartitionsRequest.getDescriptor(), list.getInputType());
        assertEquals(ListMetastorePartitionsResponse.getDescriptor(), list.getOutputType());
        assertEquals(StreamMetastorePartitionsRequest.getDescriptor(), stream.getInputType());
        assertEquals(StreamMetastorePartitionsResponse.getDescriptor(), stream.getOutputType());
        assertFalse(batchCreate.isClientStreaming());
        assertFalse(batchCreate.isServerStreaming());
        assertFalse(list.isClientStreaming());
        assertFalse(list.isServerStreaming());
        assertTrue(stream.isClientStreaming());
        assertTrue(stream.isServerStreaming());
    }

    @Test
    void generatedDescriptorsExposeHttpBindingsMethodSignaturesAndResourceOptions() {
        ServiceDescriptor service = MetastorePartitionServiceProto.getDescriptor()
                .findServiceByName("MetastorePartitionService");
        assertNotNull(service);

        MethodDescriptor batchCreate = service.findMethodByName("BatchCreateMetastorePartitions");
        HttpRule createHttp = batchCreate.getOptions().getExtension(AnnotationsProto.http);
        assertEquals("/v1beta/{parent=projects/*/datasets/*/tables/*}/partitions:batchCreate", createHttp.getPost());
        assertEquals("*", createHttp.getBody());

        MethodDescriptor batchDelete = service.findMethodByName("BatchDeleteMetastorePartitions");
        HttpRule deleteHttp = batchDelete.getOptions().getExtension(AnnotationsProto.http);
        assertEquals("/v1beta/{parent=projects/*/datasets/*/tables/*}/partitions:batchDelete", deleteHttp.getPost());
        assertEquals("*", deleteHttp.getBody());

        MethodDescriptor batchUpdate = service.findMethodByName("BatchUpdateMetastorePartitions");
        HttpRule updateHttp = batchUpdate.getOptions().getExtension(AnnotationsProto.http);
        assertEquals("/v1beta/{parent=projects/*/datasets/*/tables/*}/partitions:batchUpdate", updateHttp.getPost());
        assertEquals("*", updateHttp.getBody());

        MethodDescriptor list = service.findMethodByName("ListMetastorePartitions");
        HttpRule listHttp = list.getOptions().getExtension(AnnotationsProto.http);
        assertEquals("/v1beta/{parent=projects/*/locations/*/datasets/*/tables/*}/partitions:list",
                listHttp.getGet());
        assertIterableEquals(List.of("parent"), list.getOptions().getExtension(ClientProto.methodSignature));

        List<ResourceDescriptor> resourceDefinitions = MetastorePartitionServiceProto.getDescriptor()
                .getOptions()
                .getExtension(ResourceProto.resourceDefinition);
        assertEquals(1, resourceDefinitions.size());
        assertEquals("bigquery.googleapis.com/Table", resourceDefinitions.get(0).getType());
        assertIterableEquals(List.of("projects/{project}/datasets/{dataset}/tables/{table}"),
                resourceDefinitions.get(0).getPatternList());

        ResourceDescriptor readStreamResource = ReadStream.getDescriptor().getOptions()
                .getExtension(ResourceProto.resource);
        assertEquals("bigquerystorage.googleapis.com/ReadStream", readStreamResource.getType());
        assertIterableEquals(List.of("projects/{project}/locations/{location}/sessions/{session}/streams/{stream}"),
                readStreamResource.getPatternList());
    }

    @Test
    void generatedFieldDescriptorsExposeBehaviorAndParserDefaults() throws Exception {
        Descriptor partitionDescriptor = MetastorePartition.getDescriptor();
        FieldDescriptor valuesField = partitionDescriptor.findFieldByName("values");
        FieldDescriptor createTimeField = partitionDescriptor.findFieldByName("create_time");
        FieldDescriptor storageDescriptorField = partitionDescriptor.findFieldByName("storage_descriptor");
        assertNotNull(valuesField);
        assertNotNull(createTimeField);
        assertNotNull(storageDescriptorField);
        assertTrue(valuesField.getOptions().getExtension(FieldBehaviorProto.fieldBehavior)
                .contains(FieldBehavior.REQUIRED));
        assertTrue(createTimeField.getOptions().getExtension(FieldBehaviorProto.fieldBehavior)
                .contains(FieldBehavior.OUTPUT_ONLY));
        assertTrue(storageDescriptorField.getOptions().getExtension(FieldBehaviorProto.fieldBehavior)
                .contains(FieldBehavior.OPTIONAL));

        FieldDescriptor parentField = BatchCreateMetastorePartitionsRequest.getDescriptor().findFieldByName("parent");
        assertNotNull(parentField);
        assertEquals("bigquery.googleapis.com/Table",
                parentField.getOptions().getExtension(ResourceProto.resourceReference).getType());
        assertTrue(parentField.getOptions().getExtension(FieldBehaviorProto.fieldBehavior)
                .contains(FieldBehavior.REQUIRED));

        FieldDescriptor readStreamNameField = ReadStream.getDescriptor().findFieldByName("name");
        assertNotNull(readStreamNameField);
        assertTrue(readStreamNameField.getOptions().getExtension(FieldBehaviorProto.fieldBehavior)
                .contains(FieldBehavior.IDENTIFIER));

        assertEquals(MetastorePartition.getDefaultInstance(), MetastorePartition.parseFrom(ByteString.EMPTY));
        assertEquals("MetastorePartition", MetastorePartition.getDefaultInstance().getDescriptorForType().getName());
        assertThrows(InvalidProtocolBufferException.class,
                () -> MetastorePartition.parser().parseFrom(ByteString.copyFrom(new byte[] {-1, 0, 1})));
    }

    private static MetastorePartition partition(String date, String region) {
        return MetastorePartition.newBuilder()
                .addValues(date)
                .addValues(region)
                .setStorageDescriptor(StorageDescriptor.newBuilder()
                        .setLocationUri("gs://warehouse/events/dt=" + date + "/region=" + region)
                        .setSerdeInfo(SerDeInfo.newBuilder()
                                .setName("parquet-serde")
                                .setSerializationLibrary(
                                        "org.apache.hadoop.hive.ql.io.parquet.serde.ParquetHiveSerDe")))
                .putParameters("region", region)
                .addFields(FieldSchema.newBuilder().setName("event_id").setType("STRING"))
                .build();
    }

    private static ByteArrayInputStream delimited(com.google.protobuf.MessageLite message) throws Exception {
        byte[] payload = message.toByteArray();
        ByteString.Output output = ByteString.newOutput();
        message.writeDelimitedTo(output);
        assertTrue(output.size() > payload.length);
        return new ByteArrayInputStream(output.toByteString().toByteArray());
    }
}
