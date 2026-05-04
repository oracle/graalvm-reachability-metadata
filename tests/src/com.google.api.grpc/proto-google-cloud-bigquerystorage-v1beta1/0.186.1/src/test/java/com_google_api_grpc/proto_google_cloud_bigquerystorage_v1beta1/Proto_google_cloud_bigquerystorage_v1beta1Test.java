/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_api_grpc.proto_google_cloud_bigquerystorage_v1beta1;

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
import com.google.api.HttpRule;
import com.google.cloud.bigquery.storage.v1beta1.ArrowProto;
import com.google.cloud.bigquery.storage.v1beta1.ArrowProto.ArrowRecordBatch;
import com.google.cloud.bigquery.storage.v1beta1.ArrowProto.ArrowSchema;
import com.google.cloud.bigquery.storage.v1beta1.AvroProto;
import com.google.cloud.bigquery.storage.v1beta1.AvroProto.AvroRows;
import com.google.cloud.bigquery.storage.v1beta1.AvroProto.AvroSchema;
import com.google.cloud.bigquery.storage.v1beta1.ProjectName;
import com.google.cloud.bigquery.storage.v1beta1.ReadOptions;
import com.google.cloud.bigquery.storage.v1beta1.ReadOptions.TableReadOptions;
import com.google.cloud.bigquery.storage.v1beta1.Storage;
import com.google.cloud.bigquery.storage.v1beta1.Storage.BatchCreateReadSessionStreamsRequest;
import com.google.cloud.bigquery.storage.v1beta1.Storage.BatchCreateReadSessionStreamsResponse;
import com.google.cloud.bigquery.storage.v1beta1.Storage.CreateReadSessionRequest;
import com.google.cloud.bigquery.storage.v1beta1.Storage.DataFormat;
import com.google.cloud.bigquery.storage.v1beta1.Storage.FinalizeStreamRequest;
import com.google.cloud.bigquery.storage.v1beta1.Storage.Progress;
import com.google.cloud.bigquery.storage.v1beta1.Storage.ReadRowsRequest;
import com.google.cloud.bigquery.storage.v1beta1.Storage.ReadRowsResponse;
import com.google.cloud.bigquery.storage.v1beta1.Storage.ReadSession;
import com.google.cloud.bigquery.storage.v1beta1.Storage.ShardingStrategy;
import com.google.cloud.bigquery.storage.v1beta1.Storage.SplitReadStreamRequest;
import com.google.cloud.bigquery.storage.v1beta1.Storage.SplitReadStreamResponse;
import com.google.cloud.bigquery.storage.v1beta1.Storage.Stream;
import com.google.cloud.bigquery.storage.v1beta1.Storage.StreamPosition;
import com.google.cloud.bigquery.storage.v1beta1.Storage.StreamStatus;
import com.google.cloud.bigquery.storage.v1beta1.Storage.ThrottleStatus;
import com.google.cloud.bigquery.storage.v1beta1.TableReferenceProto;
import com.google.cloud.bigquery.storage.v1beta1.TableReferenceProto.TableModifiers;
import com.google.cloud.bigquery.storage.v1beta1.TableReferenceProto.TableReference;
import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.Descriptors.MethodDescriptor;
import com.google.protobuf.Descriptors.ServiceDescriptor;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Timestamp;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class Proto_google_cloud_bigquerystorage_v1beta1Test {
    private static final String PROJECT = "sample-project";
    private static final String PARENT = "projects/" + PROJECT;
    private static final String DATASET = "analytics";
    private static final String TABLE = "events";
    private static final String SESSION = "projects/sample-project/locations/us/sessions/session-1";
    private static final String STREAM_ONE = SESSION + "/streams/stream-1";
    private static final String STREAM_TWO = SESSION + "/streams/stream-2";

    @Test
    void projectNameFormatsParsesAndExposesFieldValues() {
        ProjectName projectName = ProjectName.of(PROJECT);

        assertEquals(PARENT, projectName.toString());
        assertEquals(PARENT, ProjectName.format(PROJECT));
        assertEquals(PROJECT, ProjectName.parse(PARENT).getProject());
        assertEquals(Map.of("project", PROJECT), projectName.getFieldValuesMap());
        assertEquals(PROJECT, projectName.getFieldValue("project"));
        assertTrue(ProjectName.isParsableFrom(PARENT));
        assertFalse(ProjectName.isParsableFrom("organizations/" + PROJECT));
        assertEquals(projectName, projectName.toBuilder().build());
        assertNotEquals(projectName, ProjectName.of("other-project"));

        List<ProjectName> names = List.of(projectName, ProjectName.of("billing-project"));
        List<String> formatted = ProjectName.toStringList(names);
        assertIterableEquals(List.of(PARENT, "projects/billing-project"), formatted);
        assertIterableEquals(names, ProjectName.parseList(formatted));
    }

    @Test
    void createReadSessionRequestRoundTripsTableReferenceModifiersAndReadOptions() throws Exception {
        Timestamp snapshotTime = Timestamp.newBuilder().setSeconds(1_700_000_000L).setNanos(456_000_000).build();
        TableReference tableReference = tableReference();
        TableReadOptions readOptions = TableReadOptions.newBuilder()
                .addSelectedFields("user_id")
                .addSelectedFields("event_time")
                .setRowRestriction("event_date >= '2024-01-01'")
                .build();
        CreateReadSessionRequest request = CreateReadSessionRequest.newBuilder()
                .setTableReference(tableReference)
                .setParent(PARENT)
                .setTableModifiers(TableModifiers.newBuilder().setSnapshotTime(snapshotTime))
                .setRequestedStreams(3)
                .setReadOptions(readOptions)
                .setFormat(DataFormat.ARROW)
                .setShardingStrategy(ShardingStrategy.BALANCED)
                .build();

        CreateReadSessionRequest parsed = CreateReadSessionRequest.parseFrom(
                new ByteArrayInputStream(request.toByteArray()));

        assertEquals(tableReference, parsed.getTableReference());
        assertEquals(PROJECT, parsed.getTableReference().getProjectId());
        assertEquals(DATASET, parsed.getTableReference().getDatasetId());
        assertEquals(TABLE, parsed.getTableReference().getTableId());
        assertEquals(PARENT, parsed.getParent());
        assertEquals(snapshotTime, parsed.getTableModifiers().getSnapshotTime());
        assertEquals(3, parsed.getRequestedStreams());
        assertEquals(List.of("user_id", "event_time"), parsed.getReadOptions().getSelectedFieldsList());
        assertEquals("event_date >= '2024-01-01'", parsed.getReadOptions().getRowRestriction());
        assertSame(DataFormat.ARROW, parsed.getFormat());
        assertSame(ShardingStrategy.BALANCED, parsed.getShardingStrategy());
    }

    @Test
    void readSessionPreservesStreamsSchemasAndShardingThroughByteBufferRoundTrip() throws Exception {
        Timestamp expireTime = Timestamp.newBuilder().setSeconds(1_700_003_600L).build();
        ReadSession avroSession = ReadSession.newBuilder()
                .setName(SESSION)
                .setExpireTime(expireTime)
                .setAvroSchema(AvroSchema.newBuilder().setSchema("{\"type\":\"record\",\"name\":\"Row\"}"))
                .addStreams(stream(STREAM_ONE))
                .addStreams(stream(STREAM_TWO))
                .setTableReference(tableReference())
                .setTableModifiers(TableModifiers.newBuilder().setSnapshotTime(Timestamp.newBuilder().setSeconds(9L)))
                .setShardingStrategy(ShardingStrategy.LIQUID)
                .build();

        ReadSession parsedAvro = ReadSession.parseFrom(ByteBuffer.wrap(avroSession.toByteArray()));
        assertEquals(SESSION, parsedAvro.getName());
        assertEquals(expireTime, parsedAvro.getExpireTime());
        assertEquals(ReadSession.SchemaCase.AVRO_SCHEMA, parsedAvro.getSchemaCase());
        assertTrue(parsedAvro.hasAvroSchema());
        assertTrue(parsedAvro.getAvroSchema().getSchema().contains("\"name\":\"Row\""));
        assertEquals(2, parsedAvro.getStreamsCount());
        assertEquals(STREAM_TWO, parsedAvro.getStreams(1).getName());
        assertTrue(parsedAvro.hasTableReference());
        assertTrue(parsedAvro.hasTableModifiers());
        assertSame(ShardingStrategy.LIQUID, parsedAvro.getShardingStrategy());

        ReadSession arrowSession = parsedAvro.toBuilder()
                .setArrowSchema(ArrowSchema.newBuilder().setSerializedSchema(ByteString.copyFromUtf8("arrow-schema")))
                .build();
        assertEquals(ReadSession.SchemaCase.ARROW_SCHEMA, arrowSession.getSchemaCase());
        assertFalse(arrowSession.hasAvroSchema());
        assertEquals("arrow-schema", arrowSession.getArrowSchema().getSerializedSchema().toStringUtf8());
    }

    @Test
    void readRowsRequestAndResponseSupportPositionsStatusesAndOneofSwitching() throws Exception {
        StreamPosition position = StreamPosition.newBuilder()
                .setStream(stream(STREAM_ONE))
                .setOffset(10L)
                .build();
        ReadRowsRequest request = ReadRowsRequest.newBuilder()
                .setReadPosition(position)
                .build();
        assertEquals(position, ReadRowsRequest.parseFrom(request.toByteArray()).getReadPosition());

        Progress progress = Progress.newBuilder()
                .setAtResponseStart(0.25F)
                .setAtResponseEnd(0.75F)
                .build();
        StreamStatus status = StreamStatus.newBuilder()
                .setEstimatedRowCount(128L)
                .setFractionConsumed(0.5F)
                .setProgress(progress)
                .setIsSplittable(true)
                .build();
        ReadRowsResponse avroResponse = ReadRowsResponse.newBuilder()
                .setAvroRows(AvroRows.newBuilder()
                        .setSerializedBinaryRows(ByteString.copyFromUtf8("avro-rows"))
                        .setRowCount(2L))
                .setRowCount(2L)
                .setStatus(status)
                .setThrottleStatus(ThrottleStatus.newBuilder().setThrottlePercent(15))
                .setAvroSchema(AvroSchema.newBuilder().setSchema("{\"type\":\"record\",\"name\":\"Row\"}"))
                .build();

        ReadRowsResponse parsedAvro = ReadRowsResponse.parseFrom(avroResponse.toByteString());
        assertEquals(ReadRowsResponse.RowsCase.AVRO_ROWS, parsedAvro.getRowsCase());
        assertEquals(ReadRowsResponse.SchemaCase.AVRO_SCHEMA, parsedAvro.getSchemaCase());
        assertEquals(2L, parsedAvro.getAvroRows().getRowCount());
        assertEquals(2L, parsedAvro.getRowCount());
        assertEquals(128L, parsedAvro.getStatus().getEstimatedRowCount());
        assertEquals(0.75F, parsedAvro.getStatus().getProgress().getAtResponseEnd(), 0.0001F);
        assertTrue(parsedAvro.getStatus().getIsSplittable());
        assertEquals(15, parsedAvro.getThrottleStatus().getThrottlePercent());

        ReadRowsResponse arrowResponse = parsedAvro.toBuilder()
                .setArrowRecordBatch(ArrowRecordBatch.newBuilder()
                        .setSerializedRecordBatch(ByteString.copyFromUtf8("arrow-batch"))
                        .setRowCount(4L))
                .setArrowSchema(ArrowSchema.newBuilder().setSerializedSchema(ByteString.copyFromUtf8("arrow-schema")))
                .build();
        assertEquals(ReadRowsResponse.RowsCase.ARROW_RECORD_BATCH, arrowResponse.getRowsCase());
        assertEquals(ReadRowsResponse.SchemaCase.ARROW_SCHEMA, arrowResponse.getSchemaCase());
        assertFalse(arrowResponse.hasAvroRows());
        assertFalse(arrowResponse.hasAvroSchema());
        assertEquals(4L, arrowResponse.getArrowRecordBatch().getRowCount());
    }

    @Test
    void streamManagementMessagesRoundTripAndExposeNestedStreams() throws Exception {
        ReadSession session = ReadSession.newBuilder()
                .setName(SESSION)
                .setAvroSchema(AvroSchema.newBuilder().setSchema("{\"type\":\"record\",\"name\":\"Row\"}"))
                .addStreams(stream(STREAM_ONE))
                .build();
        BatchCreateReadSessionStreamsRequest batchRequest = BatchCreateReadSessionStreamsRequest.newBuilder()
                .setSession(session)
                .setRequestedStreams(2)
                .build();
        BatchCreateReadSessionStreamsResponse batchResponse = BatchCreateReadSessionStreamsResponse.newBuilder()
                .addStreams(stream(STREAM_ONE))
                .addStreams(stream(STREAM_TWO))
                .build();
        FinalizeStreamRequest finalizeRequest = FinalizeStreamRequest.newBuilder()
                .setStream(stream(STREAM_ONE))
                .build();
        SplitReadStreamRequest splitRequest = SplitReadStreamRequest.newBuilder()
                .setOriginalStream(stream(STREAM_ONE))
                .setFraction(0.33F)
                .build();
        SplitReadStreamResponse splitResponse = SplitReadStreamResponse.newBuilder()
                .setPrimaryStream(stream(STREAM_ONE))
                .setRemainderStream(stream(STREAM_TWO))
                .build();

        assertEquals(session, BatchCreateReadSessionStreamsRequest.parseFrom(
                batchRequest.toByteArray()).getSession());
        assertEquals(2, batchRequest.getRequestedStreams());
        assertEquals(STREAM_TWO, BatchCreateReadSessionStreamsResponse.parseFrom(
                batchResponse.toByteString()).getStreams(1).getName());
        assertEquals(STREAM_ONE, FinalizeStreamRequest.parseFrom(finalizeRequest.toByteArray()).getStream().getName());
        assertEquals(0.33F, SplitReadStreamRequest.parseFrom(splitRequest.toByteArray()).getFraction(), 0.0001F);
        assertEquals(STREAM_TWO, SplitReadStreamResponse.parseFrom(
                splitResponse.toByteArray()).getRemainderStream().getName());
    }

    @Test
    void enumValueFieldsPreserveUnrecognizedNumbersForForwardCompatibility() {
        int futureDataFormat = 10_001;
        int futureShardingStrategy = 10_002;
        CreateReadSessionRequest request = CreateReadSessionRequest.newBuilder()
                .setParent(PARENT)
                .setTableReference(tableReference())
                .setFormatValue(futureDataFormat)
                .setShardingStrategyValue(futureShardingStrategy)
                .build();

        assertEquals(futureDataFormat, request.getFormatValue());
        assertSame(DataFormat.UNRECOGNIZED, request.getFormat());
        assertEquals(futureShardingStrategy, request.getShardingStrategyValue());
        assertSame(ShardingStrategy.UNRECOGNIZED, request.getShardingStrategy());

        CreateReadSessionRequest knownRequest = request.toBuilder()
                .setFormat(DataFormat.AVRO)
                .setShardingStrategy(ShardingStrategy.BALANCED)
                .build();
        assertEquals(DataFormat.AVRO_VALUE, knownRequest.getFormatValue());
        assertSame(DataFormat.AVRO, knownRequest.getFormat());
        assertEquals(ShardingStrategy.BALANCED_VALUE, knownRequest.getShardingStrategyValue());
        assertSame(ShardingStrategy.BALANCED, knownRequest.getShardingStrategy());
    }

    @Test
    void generatedDescriptorsExposeMessagesServicesAndStreamingMethods() {
        FileDescriptor storageDescriptor = Storage.getDescriptor();
        assertEquals("google/cloud/bigquery/storage/v1beta1/storage.proto", storageDescriptor.getName());
        assertNotNull(storageDescriptor.findMessageTypeByName("ReadSession"));
        assertEquals(CreateReadSessionRequest.getDescriptor(),
                storageDescriptor.findMessageTypeByName("CreateReadSessionRequest"));
        assertEquals(ReadRowsResponse.getDescriptor(), storageDescriptor.findMessageTypeByName("ReadRowsResponse"));
        assertSame(storageDescriptor, CreateReadSessionRequest.getDescriptor().getFile());

        ServiceDescriptor service = storageDescriptor.findServiceByName("BigQueryStorage");
        assertNotNull(service);
        MethodDescriptor createReadSession = service.findMethodByName("CreateReadSession");
        MethodDescriptor readRows = service.findMethodByName("ReadRows");
        MethodDescriptor splitReadStream = service.findMethodByName("SplitReadStream");
        assertNotNull(createReadSession);
        assertNotNull(readRows);
        assertNotNull(splitReadStream);
        assertEquals(CreateReadSessionRequest.getDescriptor(), createReadSession.getInputType());
        assertEquals(ReadSession.getDescriptor(), createReadSession.getOutputType());
        assertFalse(createReadSession.isClientStreaming());
        assertFalse(createReadSession.isServerStreaming());
        assertEquals(ReadRowsRequest.getDescriptor(), readRows.getInputType());
        assertEquals(ReadRowsResponse.getDescriptor(), readRows.getOutputType());
        assertFalse(readRows.isClientStreaming());
        assertTrue(readRows.isServerStreaming());
        assertEquals(SplitReadStreamRequest.getDescriptor(), splitReadStream.getInputType());
        assertEquals(SplitReadStreamResponse.getDescriptor(), splitReadStream.getOutputType());

        assertEquals("google/cloud/bigquery/storage/v1beta1/avro.proto", AvroProto.getDescriptor().getName());
        assertEquals("google/cloud/bigquery/storage/v1beta1/arrow.proto", ArrowProto.getDescriptor().getName());
        assertEquals("google/cloud/bigquery/storage/v1beta1/read_options.proto",
                ReadOptions.getDescriptor().getName());
        assertEquals("google/cloud/bigquery/storage/v1beta1/table_reference.proto",
                TableReferenceProto.getDescriptor().getName());
    }

    @Test
    void generatedDescriptorsExposeHttpBindingsAndClientOptions() {
        ServiceDescriptor service = Storage.getDescriptor().findServiceByName("BigQueryStorage");
        assertNotNull(service);
        assertEquals("bigquerystorage.googleapis.com", service.getOptions().getExtension(ClientProto.defaultHost));
        assertEquals("https://www.googleapis.com/auth/bigquery,https://www.googleapis.com/auth/cloud-platform",
                service.getOptions().getExtension(ClientProto.oauthScopes));

        MethodDescriptor createReadSession = service.findMethodByName("CreateReadSession");
        assertNotNull(createReadSession);
        HttpRule createHttp = createReadSession.getOptions().getExtension(AnnotationsProto.http);
        assertEquals("/v1beta1/{table_reference.project_id=projects/*}", createHttp.getPost());
        assertEquals("*", createHttp.getBody());
        assertEquals(1, createHttp.getAdditionalBindingsCount());
        assertEquals("/v1beta1/{table_reference.dataset_id=projects/*/datasets/*}",
                createHttp.getAdditionalBindings(0).getPost());
        assertEquals("*", createHttp.getAdditionalBindings(0).getBody());
        assertIterableEquals(List.of("table_reference,parent,requested_streams"),
                createReadSession.getOptions().getExtension(ClientProto.methodSignature));

        MethodDescriptor readRows = service.findMethodByName("ReadRows");
        assertNotNull(readRows);
        HttpRule readRowsHttp = readRows.getOptions().getExtension(AnnotationsProto.http);
        assertEquals("/v1beta1/{read_position.stream.name=projects/*/streams/*}", readRowsHttp.getGet());
        assertIterableEquals(List.of("read_position"),
                readRows.getOptions().getExtension(ClientProto.methodSignature));

        MethodDescriptor batchCreateReadSessionStreams = service.findMethodByName("BatchCreateReadSessionStreams");
        assertNotNull(batchCreateReadSessionStreams);
        HttpRule batchHttp = batchCreateReadSessionStreams.getOptions().getExtension(AnnotationsProto.http);
        assertEquals("/v1beta1/{session.name=projects/*/sessions/*}", batchHttp.getPost());
        assertEquals("*", batchHttp.getBody());
        assertIterableEquals(List.of("session,requested_streams"),
                batchCreateReadSessionStreams.getOptions().getExtension(ClientProto.methodSignature));

        MethodDescriptor finalizeStream = service.findMethodByName("FinalizeStream");
        assertNotNull(finalizeStream);
        HttpRule finalizeHttp = finalizeStream.getOptions().getExtension(AnnotationsProto.http);
        assertEquals("/v1beta1/{stream.name=projects/*/streams/*}", finalizeHttp.getPost());
        assertEquals("*", finalizeHttp.getBody());
        assertIterableEquals(List.of("stream"),
                finalizeStream.getOptions().getExtension(ClientProto.methodSignature));

        MethodDescriptor splitReadStream = service.findMethodByName("SplitReadStream");
        assertNotNull(splitReadStream);
        HttpRule splitHttp = splitReadStream.getOptions().getExtension(AnnotationsProto.http);
        assertEquals("/v1beta1/{original_stream.name=projects/*/streams/*}", splitHttp.getGet());
        assertIterableEquals(List.of("original_stream"),
                splitReadStream.getOptions().getExtension(ClientProto.methodSignature));
    }

    @Test
    void enumParsersAndDefaultInstancesBehaveConsistently() throws Exception {
        assertSame(DataFormat.ARROW, DataFormat.forNumber(DataFormat.ARROW_VALUE));
        assertSame(DataFormat.AVRO, DataFormat.valueOf(DataFormat.AVRO.getValueDescriptor()));
        assertSame(ShardingStrategy.BALANCED, ShardingStrategy.forNumber(ShardingStrategy.BALANCED_VALUE));
        assertSame(ShardingStrategy.LIQUID, ShardingStrategy.internalGetValueMap().findValueByNumber(
                ShardingStrategy.LIQUID_VALUE));
        assertEquals(DataFormat.ARROW.getValueDescriptor(), DataFormat.ARROW.getDescriptorForType()
                .findValueByName("ARROW"));

        assertEquals(ReadSession.getDefaultInstance(), ReadSession.parseFrom(ByteString.EMPTY));
        assertEquals("ReadSession", ReadSession.getDefaultInstance().getDescriptorForType().getName());
        assertThrows(InvalidProtocolBufferException.class,
                () -> ReadSession.parser().parseFrom(ByteString.copyFrom(new byte[] {-1, 0, 1})));
    }

    private static TableReference tableReference() {
        return TableReference.newBuilder()
                .setProjectId(PROJECT)
                .setDatasetId(DATASET)
                .setTableId(TABLE)
                .build();
    }

    private static Stream stream(String name) {
        return Stream.newBuilder().setName(name).build();
    }
}
