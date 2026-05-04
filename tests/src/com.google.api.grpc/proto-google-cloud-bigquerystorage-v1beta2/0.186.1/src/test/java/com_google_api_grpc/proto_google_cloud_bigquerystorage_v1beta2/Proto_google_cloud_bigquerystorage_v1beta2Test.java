/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_api_grpc.proto_google_cloud_bigquerystorage_v1beta2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.api.ClientProto;
import com.google.api.FieldBehavior;
import com.google.api.FieldBehaviorProto;
import com.google.api.HttpRule;
import com.google.api.ResourceDescriptor;
import com.google.api.ResourceProto;
import com.google.api.ResourceReference;
import com.google.cloud.bigquery.storage.v1beta2.AppendRowsRequest;
import com.google.cloud.bigquery.storage.v1beta2.AppendRowsResponse;
import com.google.cloud.bigquery.storage.v1beta2.ArrowRecordBatch;
import com.google.cloud.bigquery.storage.v1beta2.ArrowSchema;
import com.google.cloud.bigquery.storage.v1beta2.ArrowSerializationOptions;
import com.google.cloud.bigquery.storage.v1beta2.AvroRows;
import com.google.cloud.bigquery.storage.v1beta2.AvroSchema;
import com.google.cloud.bigquery.storage.v1beta2.BatchCommitWriteStreamsRequest;
import com.google.cloud.bigquery.storage.v1beta2.BatchCommitWriteStreamsResponse;
import com.google.cloud.bigquery.storage.v1beta2.CreateReadSessionRequest;
import com.google.cloud.bigquery.storage.v1beta2.CreateWriteStreamRequest;
import com.google.cloud.bigquery.storage.v1beta2.DataFormat;
import com.google.cloud.bigquery.storage.v1beta2.FinalizeWriteStreamRequest;
import com.google.cloud.bigquery.storage.v1beta2.FinalizeWriteStreamResponse;
import com.google.cloud.bigquery.storage.v1beta2.FlushRowsRequest;
import com.google.cloud.bigquery.storage.v1beta2.FlushRowsResponse;
import com.google.cloud.bigquery.storage.v1beta2.GetWriteStreamRequest;
import com.google.cloud.bigquery.storage.v1beta2.ProjectName;
import com.google.cloud.bigquery.storage.v1beta2.ProtoRows;
import com.google.cloud.bigquery.storage.v1beta2.ProtoSchema;
import com.google.cloud.bigquery.storage.v1beta2.ReadRowsRequest;
import com.google.cloud.bigquery.storage.v1beta2.ReadRowsResponse;
import com.google.cloud.bigquery.storage.v1beta2.ReadSession;
import com.google.cloud.bigquery.storage.v1beta2.ReadStream;
import com.google.cloud.bigquery.storage.v1beta2.ReadStreamName;
import com.google.cloud.bigquery.storage.v1beta2.SplitReadStreamRequest;
import com.google.cloud.bigquery.storage.v1beta2.SplitReadStreamResponse;
import com.google.cloud.bigquery.storage.v1beta2.StorageError;
import com.google.cloud.bigquery.storage.v1beta2.StorageProto;
import com.google.cloud.bigquery.storage.v1beta2.StreamProto;
import com.google.cloud.bigquery.storage.v1beta2.StreamStats;
import com.google.cloud.bigquery.storage.v1beta2.TableFieldSchema;
import com.google.cloud.bigquery.storage.v1beta2.TableName;
import com.google.cloud.bigquery.storage.v1beta2.TableSchema;
import com.google.cloud.bigquery.storage.v1beta2.ThrottleState;
import com.google.cloud.bigquery.storage.v1beta2.WriteStream;
import com.google.cloud.bigquery.storage.v1beta2.WriteStreamName;
import com.google.protobuf.ByteString;
import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.Descriptors.MethodDescriptor;
import com.google.protobuf.Descriptors.ServiceDescriptor;
import com.google.protobuf.Int64Value;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Timestamp;
import com.google.rpc.Status;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class Proto_google_cloud_bigquerystorage_v1beta2Test {
    private static final String PROJECT = "sample-project";
    private static final String DATASET = "analytics";
    private static final String TABLE = "events";
    private static final String LOCATION = "us";
    private static final String SESSION = "session-1";
    private static final String READ_STREAM = "stream-1";
    private static final String WRITE_STREAM = "committed";

    @Test
    void resourceNamesFormatParseAndExposeFieldValues() {
        ProjectName projectName = ProjectName.of(PROJECT);
        assertEquals("projects/sample-project", projectName.toString());
        assertEquals(PROJECT, ProjectName.parse(projectName.toString()).getProject());
        assertEquals(Map.of("project", PROJECT), projectName.getFieldValuesMap());
        assertEquals(PROJECT, projectName.getFieldValue("project"));
        assertTrue(ProjectName.isParsableFrom(projectName.toString()));
        assertFalse(ProjectName.isParsableFrom("organizations/sample-project"));

        TableName tableName = TableName.of(PROJECT, DATASET, TABLE);
        assertEquals("projects/sample-project/datasets/analytics/tables/events", tableName.toString());
        TableName parsedTableName = TableName.parse(TableName.format(PROJECT, DATASET, TABLE));
        assertEquals(PROJECT, parsedTableName.getProject());
        assertEquals(DATASET, parsedTableName.getDataset());
        assertEquals(TABLE, parsedTableName.getTable());
        assertEquals("events", parsedTableName.getFieldValue("table"));
        assertEquals(parsedTableName, parsedTableName.toBuilder().build());
        assertNotEquals(parsedTableName, TableName.of(PROJECT, DATASET, "sessions"));

        ReadStreamName readStreamName = ReadStreamName.of(PROJECT, LOCATION, SESSION, READ_STREAM);
        assertEquals("projects/sample-project/locations/us/sessions/session-1/streams/stream-1",
                readStreamName.toString());
        assertEquals(readStreamName, ReadStreamName.parse(readStreamName.toString()));
        assertEquals(READ_STREAM, readStreamName.getFieldValuesMap().get("stream"));

        WriteStreamName writeStreamName = WriteStreamName.of(PROJECT, DATASET, TABLE, WRITE_STREAM);
        assertEquals("projects/sample-project/datasets/analytics/tables/events/streams/committed",
                writeStreamName.toString());
        assertEquals(writeStreamName, WriteStreamName.parse(writeStreamName.toString()));
        assertEquals(WRITE_STREAM, writeStreamName.getFieldValue("stream"));

        List<TableName> names = List.of(tableName, TableName.of(PROJECT, DATASET, "users"));
        List<String> formattedNames = TableName.toStringList(names);
        assertIterableEquals(List.of(tableName.toString(), "projects/sample-project/datasets/analytics/tables/users"),
                formattedNames);
        assertIterableEquals(names, TableName.parseList(formattedNames));
    }

    @Test
    void tableSchemaPreservesNestedFieldsThroughBinaryRoundTrip() throws Exception {
        TableFieldSchema structField = TableFieldSchema.newBuilder()
                .setName("payload")
                .setType(TableFieldSchema.Type.STRUCT)
                .setMode(TableFieldSchema.Mode.REPEATED)
                .setDescription("event payload")
                .addFields(TableFieldSchema.newBuilder()
                        .setName("id")
                        .setType(TableFieldSchema.Type.INT64)
                        .setMode(TableFieldSchema.Mode.REQUIRED)
                        .setDescription("primary key"))
                .addFields(TableFieldSchema.newBuilder()
                        .setName("properties")
                        .setType(TableFieldSchema.Type.STRUCT)
                        .addFields(TableFieldSchema.newBuilder()
                                .setName("json_body")
                                .setType(TableFieldSchema.Type.JSON)
                                .setMode(TableFieldSchema.Mode.NULLABLE)))
                .build();
        TableSchema schema = TableSchema.newBuilder()
                .addFields(TableFieldSchema.newBuilder()
                        .setName("event_time")
                        .setType(TableFieldSchema.Type.TIMESTAMP)
                        .setMode(TableFieldSchema.Mode.NULLABLE))
                .addFields(structField)
                .build();

        TableSchema parsed = TableSchema.parseFrom(schema.toByteArray());
        assertEquals(schema, parsed);
        assertEquals(2, parsed.getFieldsCount());
        assertEquals(TableFieldSchema.Type.TIMESTAMP, parsed.getFields(0).getType());
        assertEquals(TableFieldSchema.Mode.REPEATED, parsed.getFields(1).getMode());
        assertEquals("properties", parsed.getFields(1).getFields(1).getName());
        assertEquals(TableFieldSchema.Type.JSON, parsed.getFields(1).getFields(1).getFields(0).getType());
        assertEquals("TableSchema", TableSchema.getDescriptor().getName());
    }

    @Test
    void readSessionRequestCarriesSchemaReadOptionsAndStreamMetadata() throws Exception {
        Timestamp snapshotTime = Timestamp.newBuilder().setSeconds(1_700_000_000L).setNanos(123_000_000).build();
        ArrowSerializationOptions arrowOptions = ArrowSerializationOptions.newBuilder()
                .setFormat(ArrowSerializationOptions.Format.ARROW_0_15)
                .build();
        ReadSession.TableReadOptions readOptions = ReadSession.TableReadOptions.newBuilder()
                .addSelectedFields("user_id")
                .addSelectedFields("event_time")
                .setRowRestriction("event_date >= '2024-01-01'")
                .setArrowSerializationOptions(arrowOptions)
                .build();
        ReadSession readSession = ReadSession.newBuilder()
                .setName("projects/sample-project/locations/us/sessions/session-1")
                .setExpireTime(Timestamp.newBuilder().setSeconds(1_700_003_600L))
                .setDataFormat(DataFormat.ARROW)
                .setArrowSchema(ArrowSchema.newBuilder().setSerializedSchema(ByteString.copyFromUtf8("arrow-schema")))
                .setTable(TableName.format(PROJECT, DATASET, TABLE))
                .setTableModifiers(ReadSession.TableModifiers.newBuilder().setSnapshotTime(snapshotTime))
                .setReadOptions(readOptions)
                .addStreams(ReadStream.newBuilder()
                        .setName(ReadStreamName.format(PROJECT, LOCATION, SESSION, READ_STREAM)))
                .build();
        CreateReadSessionRequest request = CreateReadSessionRequest.newBuilder()
                .setParent(ProjectName.format(PROJECT))
                .setReadSession(readSession)
                .setMaxStreamCount(3)
                .build();

        CreateReadSessionRequest parsed = CreateReadSessionRequest.parseFrom(
                new ByteArrayInputStream(request.toByteArray()));
        assertEquals(ProjectName.format(PROJECT), parsed.getParent());
        assertEquals(3, parsed.getMaxStreamCount());
        assertEquals(DataFormat.ARROW, parsed.getReadSession().getDataFormat());
        assertEquals(ReadSession.SchemaCase.ARROW_SCHEMA, parsed.getReadSession().getSchemaCase());
        assertEquals("event_time", parsed.getReadSession().getReadOptions().getSelectedFields(1));
        assertTrue(parsed.getReadSession().getReadOptions().hasArrowSerializationOptions());
        assertEquals(ArrowSerializationOptions.Format.ARROW_0_15,
                parsed.getReadSession().getReadOptions().getArrowSerializationOptions().getFormat());
        assertEquals(snapshotTime, parsed.getReadSession().getTableModifiers().getSnapshotTime());
        assertEquals(READ_STREAM, ReadStreamName.parse(parsed.getReadSession().getStreams(0).getName()).getStream());
    }

    @Test
    void avroReadSessionCarriesAvroSchemaAndStreamMetadata() {
        String avroSchemaJson = """
                {"type":"record","name":"Event","fields":[{"name":"user_id","type":"long"}]}
                """;
        ReadSession readSession = ReadSession.newBuilder()
                .setName("projects/sample-project/locations/us/sessions/session-1")
                .setDataFormat(DataFormat.AVRO)
                .setAvroSchema(AvroSchema.newBuilder().setSchema(avroSchemaJson))
                .setTable(TableName.format(PROJECT, DATASET, TABLE))
                .addStreams(ReadStream.newBuilder()
                        .setName(ReadStreamName.format(PROJECT, LOCATION, SESSION, READ_STREAM)))
                .build();
        CreateReadSessionRequest request = CreateReadSessionRequest.newBuilder()
                .setParent(ProjectName.format(PROJECT))
                .setReadSession(readSession)
                .setMaxStreamCount(1)
                .build();

        assertEquals(ProjectName.format(PROJECT), request.getParent());
        assertEquals(1, request.getMaxStreamCount());
        assertEquals(DataFormat.AVRO, request.getReadSession().getDataFormat());
        assertEquals(ReadSession.SchemaCase.AVRO_SCHEMA, request.getReadSession().getSchemaCase());
        assertTrue(request.getReadSession().hasAvroSchema());
        assertFalse(request.getReadSession().hasArrowSchema());
        assertEquals(avroSchemaJson, request.getReadSession().getAvroSchema().getSchema());
        assertEquals(TableName.format(PROJECT, DATASET, TABLE), request.getReadSession().getTable());
        assertEquals(READ_STREAM, ReadStreamName.parse(request.getReadSession().getStreams(0).getName()).getStream());
    }

    @Test
    void storageProtoDescriptorExposesServicesStreamingAndHttpBindings() {
        FileDescriptor descriptor = StorageProto.getDescriptor();
        ServiceDescriptor readService = descriptor.findServiceByName("BigQueryRead");
        ServiceDescriptor writeService = descriptor.findServiceByName("BigQueryWrite");
        assertNotNull(readService);
        assertNotNull(writeService);
        assertEquals("bigquerystorage.googleapis.com", readService.getOptions().getExtension(ClientProto.defaultHost));
        assertEquals("https://www.googleapis.com/auth/bigquery,https://www.googleapis.com/auth/cloud-platform",
                readService.getOptions().getExtension(ClientProto.oauthScopes));
        assertEquals("bigquerystorage.googleapis.com", writeService.getOptions().getExtension(ClientProto.defaultHost));
        assertTrue(writeService.getOptions().getExtension(ClientProto.oauthScopes).contains(
                "https://www.googleapis.com/auth/bigquery.insertdata"));

        MethodDescriptor createReadSession = readService.findMethodByName("CreateReadSession");
        assertNotNull(createReadSession);
        assertEquals(CreateReadSessionRequest.getDescriptor(), createReadSession.getInputType());
        assertEquals(ReadSession.getDescriptor(), createReadSession.getOutputType());
        assertFalse(createReadSession.isClientStreaming());
        assertFalse(createReadSession.isServerStreaming());
        assertTrue(createReadSession.getOptions().hasExtension(com.google.api.AnnotationsProto.http));
        HttpRule createReadSessionHttp = createReadSession.getOptions()
                .getExtension(com.google.api.AnnotationsProto.http);
        assertEquals(HttpRule.PatternCase.POST, createReadSessionHttp.getPatternCase());
        assertEquals("/v1beta2/{read_session.table=projects/*/datasets/*/tables/*}", createReadSessionHttp.getPost());
        assertEquals("*", createReadSessionHttp.getBody());

        MethodDescriptor readRows = readService.findMethodByName("ReadRows");
        assertNotNull(readRows);
        assertEquals(ReadRowsRequest.getDescriptor(), readRows.getInputType());
        assertEquals(ReadRowsResponse.getDescriptor(), readRows.getOutputType());
        assertFalse(readRows.isClientStreaming());
        assertTrue(readRows.isServerStreaming());
        HttpRule readRowsHttp = readRows.getOptions().getExtension(com.google.api.AnnotationsProto.http);
        assertEquals(HttpRule.PatternCase.GET, readRowsHttp.getPatternCase());
        assertEquals("/v1beta2/{read_stream=projects/*/locations/*/sessions/*/streams/*}", readRowsHttp.getGet());

        MethodDescriptor appendRows = writeService.findMethodByName("AppendRows");
        assertNotNull(appendRows);
        assertEquals(AppendRowsRequest.getDescriptor(), appendRows.getInputType());
        assertEquals(AppendRowsResponse.getDescriptor(), appendRows.getOutputType());
        assertTrue(appendRows.isClientStreaming());
        assertTrue(appendRows.isServerStreaming());
        HttpRule appendRowsHttp = appendRows.getOptions().getExtension(com.google.api.AnnotationsProto.http);
        assertEquals(HttpRule.PatternCase.POST, appendRowsHttp.getPatternCase());
        assertEquals("/v1beta2/{write_stream=projects/*/datasets/*/tables/*/streams/*}", appendRowsHttp.getPost());
        assertEquals("*", appendRowsHttp.getBody());
    }

    @Test
    void descriptorsExposeResourceReferencesFieldBehaviorsAndResourcePatterns() {
        FileDescriptor streamDescriptor = StreamProto.getDescriptor();
        List<ResourceDescriptor> resourceDefinitions = streamDescriptor.getOptions()
                .getExtension(ResourceProto.resourceDefinition);
        assertEquals(1, resourceDefinitions.size());
        assertEquals("bigquery.googleapis.com/Table", resourceDefinitions.get(0).getType());
        assertIterableEquals(List.of("projects/{project}/datasets/{dataset}/tables/{table}"),
                resourceDefinitions.get(0).getPatternList());

        ResourceDescriptor readSessionResource = ReadSession.getDescriptor().getOptions()
                .getExtension(ResourceProto.resource);
        assertEquals("bigquerystorage.googleapis.com/ReadSession", readSessionResource.getType());
        assertIterableEquals(List.of("projects/{project}/locations/{location}/sessions/{session}"),
                readSessionResource.getPatternList());

        ResourceDescriptor readStreamResource = ReadStream.getDescriptor().getOptions()
                .getExtension(ResourceProto.resource);
        assertEquals("bigquerystorage.googleapis.com/ReadStream", readStreamResource.getType());
        assertIterableEquals(List.of("projects/{project}/locations/{location}/sessions/{session}/streams/{stream}"),
                readStreamResource.getPatternList());

        ResourceDescriptor writeStreamResource = WriteStream.getDescriptor().getOptions()
                .getExtension(ResourceProto.resource);
        assertEquals("bigquerystorage.googleapis.com/WriteStream", writeStreamResource.getType());
        assertIterableEquals(List.of("projects/{project}/datasets/{dataset}/tables/{table}/streams/{stream}"),
                writeStreamResource.getPatternList());

        Descriptor createReadSessionRequest = CreateReadSessionRequest.getDescriptor();
        FieldDescriptor parentField = createReadSessionRequest.findFieldByName("parent");
        assertIterableEquals(List.of(FieldBehavior.REQUIRED), parentField.getOptions()
                .getExtension(FieldBehaviorProto.fieldBehavior));
        ResourceReference parentReference = parentField.getOptions().getExtension(ResourceProto.resourceReference);
        assertEquals("cloudresourcemanager.googleapis.com/Project", parentReference.getType());

        FieldDescriptor readSessionField = createReadSessionRequest.findFieldByName("read_session");
        assertIterableEquals(List.of(FieldBehavior.REQUIRED), readSessionField.getOptions()
                .getExtension(FieldBehaviorProto.fieldBehavior));

        FieldDescriptor tableField = ReadSession.getDescriptor().findFieldByName("table");
        assertIterableEquals(List.of(FieldBehavior.IMMUTABLE), tableField.getOptions()
                .getExtension(FieldBehaviorProto.fieldBehavior));
        assertEquals("bigquery.googleapis.com/Table", tableField.getOptions()
                .getExtension(ResourceProto.resourceReference).getType());
    }

    @Test
    void protoSchemaAndAppendRowsRequestPreserveProtoPayloads() throws Exception {
        DescriptorProtos.DescriptorProto rowDescriptor = DescriptorProtos.DescriptorProto.newBuilder()
                .setName("EventRow")
                .addField(DescriptorProtos.FieldDescriptorProto.newBuilder()
                        .setName("user_id")
                        .setNumber(1)
                        .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT64))
                .addField(DescriptorProtos.FieldDescriptorProto.newBuilder()
                        .setName("event_name")
                        .setNumber(2)
                        .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING))
                .build();
        ProtoRows rows = ProtoRows.newBuilder()
                .addSerializedRows(ByteString.copyFrom(new byte[] {8, 7, 18, 5, 108, 111, 103, 105, 110}))
                .addSerializedRows(ByteString.copyFrom(new byte[] {8, 9, 18, 4, 118, 105, 101, 119}))
                .build();
        AppendRowsRequest request = AppendRowsRequest.newBuilder()
                .setWriteStream(WriteStreamName.format(PROJECT, DATASET, TABLE, WRITE_STREAM))
                .setOffset(Int64Value.of(42L))
                .setProtoRows(AppendRowsRequest.ProtoData.newBuilder()
                        .setWriterSchema(ProtoSchema.newBuilder().setProtoDescriptor(rowDescriptor))
                        .setRows(rows))
                .setTraceId("trace-append")
                .build();

        AppendRowsRequest parsed = AppendRowsRequest.parseFrom(request.toByteString());
        assertEquals(AppendRowsRequest.RowsCase.PROTO_ROWS, parsed.getRowsCase());
        assertEquals(42L, parsed.getOffset().getValue());
        assertEquals("trace-append", parsed.getTraceId());
        assertEquals("EventRow", parsed.getProtoRows().getWriterSchema().getProtoDescriptor().getName());
        assertEquals("event_name", parsed.getProtoRows().getWriterSchema().getProtoDescriptor().getField(1).getName());
        assertEquals(2, parsed.getProtoRows().getRows().getSerializedRowsCount());
        assertEquals(ByteString.copyFrom(new byte[] {8, 9, 18, 4, 118, 105, 101, 119}),
                parsed.getProtoRows().getRows().getSerializedRows(1));
    }

    @Test
    void appendRowsResponsesRepresentSuccessErrorsAndUpdatedSchemas() throws Exception {
        TableSchema updatedSchema = TableSchema.newBuilder()
                .addFields(TableFieldSchema.newBuilder().setName("user_id").setType(TableFieldSchema.Type.INT64))
                .build();
        AppendRowsResponse success = AppendRowsResponse.newBuilder()
                .setAppendResult(AppendRowsResponse.AppendResult.newBuilder().setOffset(Int64Value.of(42L)))
                .setUpdatedSchema(updatedSchema)
                .build();

        AppendRowsResponse parsedSuccess = AppendRowsResponse.parseFrom(success.toByteArray());
        assertEquals(AppendRowsResponse.ResponseCase.APPEND_RESULT, parsedSuccess.getResponseCase());
        assertEquals(42L, parsedSuccess.getAppendResult().getOffset().getValue());
        assertTrue(parsedSuccess.hasUpdatedSchema());
        assertEquals("user_id", parsedSuccess.getUpdatedSchema().getFields(0).getName());

        AppendRowsResponse error = parsedSuccess.toBuilder()
                .setError(Status.newBuilder().setCode(3).setMessage("invalid argument"))
                .build();
        assertEquals(AppendRowsResponse.ResponseCase.ERROR, error.getResponseCase());
        assertFalse(error.hasAppendResult());
        assertEquals("invalid argument", error.getError().getMessage());
        assertTrue(error.hasUpdatedSchema());
    }

    @Test
    void readRowsRequestResponseAndSplitOperationsUseStreamPayloads() throws Exception {
        ReadRowsRequest request = ReadRowsRequest.newBuilder()
                .setReadStream(ReadStreamName.format(PROJECT, LOCATION, SESSION, READ_STREAM))
                .setOffset(10L)
                .build();
        assertEquals(request, ReadRowsRequest.parseFrom(request.toByteArray()));

        StreamStats stats = StreamStats.newBuilder()
                .setProgress(StreamStats.Progress.newBuilder().setAtResponseStart(0.25D).setAtResponseEnd(0.5D))
                .build();
        ReadRowsResponse avroResponse = ReadRowsResponse.newBuilder()
                .setAvroRows(AvroRows.newBuilder().setSerializedBinaryRows(ByteString.copyFromUtf8("avro")))
                .setRowCount(2L)
                .setStats(stats)
                .setThrottleState(ThrottleState.newBuilder().setThrottlePercent(15))
                .setAvroSchema(AvroSchema.newBuilder().setSchema("{\"type\":\"record\",\"name\":\"Row\"}"))
                .build();

        ReadRowsResponse parsedAvro = ReadRowsResponse.parseFrom(avroResponse.toByteString());
        assertEquals(ReadRowsResponse.RowsCase.AVRO_ROWS, parsedAvro.getRowsCase());
        assertEquals(ReadRowsResponse.SchemaCase.AVRO_SCHEMA, parsedAvro.getSchemaCase());
        assertEquals(2L, parsedAvro.getRowCount());
        assertEquals(ByteString.copyFromUtf8("avro"), parsedAvro.getAvroRows().getSerializedBinaryRows());
        assertEquals(0.5D, parsedAvro.getStats().getProgress().getAtResponseEnd(), 0.0001D);
        assertEquals(15, parsedAvro.getThrottleState().getThrottlePercent());

        ReadRowsResponse arrowResponse = parsedAvro.toBuilder()
                .setArrowRecordBatch(ArrowRecordBatch.newBuilder()
                        .setSerializedRecordBatch(ByteString.copyFromUtf8("arrow")))
                .setArrowSchema(ArrowSchema.newBuilder().setSerializedSchema(ByteString.copyFromUtf8("arrow-schema")))
                .setRowCount(4L)
                .build();
        assertEquals(ReadRowsResponse.RowsCase.ARROW_RECORD_BATCH, arrowResponse.getRowsCase());
        assertEquals(ReadRowsResponse.SchemaCase.ARROW_SCHEMA, arrowResponse.getSchemaCase());
        assertEquals(4L, arrowResponse.getRowCount());
        assertEquals(ByteString.copyFromUtf8("arrow"), arrowResponse.getArrowRecordBatch().getSerializedRecordBatch());

        SplitReadStreamRequest splitRequest = SplitReadStreamRequest.newBuilder()
                .setName(request.getReadStream())
                .setFraction(0.33D)
                .build();
        SplitReadStreamResponse splitResponse = SplitReadStreamResponse.newBuilder()
                .setPrimaryStream(ReadStream.newBuilder().setName(request.getReadStream()))
                .setRemainderStream(ReadStream.newBuilder()
                        .setName(ReadStreamName.format(PROJECT, LOCATION, SESSION, "stream-2")))
                .build();
        assertEquals(0.33D, SplitReadStreamRequest.parseFrom(splitRequest.toByteArray()).getFraction(), 0.0001D);
        assertEquals("stream-2", ReadStreamName.parse(splitResponse.getRemainderStream().getName()).getStream());
    }

    @Test
    void writeStreamLifecycleMessagesRoundTrip() throws Exception {
        TableSchema tableSchema = TableSchema.newBuilder()
                .addFields(TableFieldSchema.newBuilder()
                        .setName("event_name")
                        .setType(TableFieldSchema.Type.STRING)
                        .setMode(TableFieldSchema.Mode.NULLABLE))
                .build();
        Timestamp createTime = Timestamp.newBuilder().setSeconds(1_700_000_001L).build();
        Timestamp commitTime = Timestamp.newBuilder().setSeconds(1_700_000_120L).build();
        WriteStream writeStream = WriteStream.newBuilder()
                .setName(WriteStreamName.format(PROJECT, DATASET, TABLE, WRITE_STREAM))
                .setType(WriteStream.Type.COMMITTED)
                .setCreateTime(createTime)
                .setCommitTime(commitTime)
                .setTableSchema(tableSchema)
                .build();

        CreateWriteStreamRequest createRequest = CreateWriteStreamRequest.newBuilder()
                .setParent(TableName.format(PROJECT, DATASET, TABLE))
                .setWriteStream(writeStream)
                .build();
        GetWriteStreamRequest getRequest = GetWriteStreamRequest.newBuilder()
                .setName(writeStream.getName())
                .build();
        FinalizeWriteStreamRequest finalizeRequest = FinalizeWriteStreamRequest.newBuilder()
                .setName(writeStream.getName())
                .build();
        FinalizeWriteStreamResponse finalizeResponse = FinalizeWriteStreamResponse.newBuilder()
                .setRowCount(5L)
                .build();
        FlushRowsRequest flushRequest = FlushRowsRequest.newBuilder()
                .setWriteStream(writeStream.getName())
                .setOffset(Int64Value.of(5L))
                .build();
        FlushRowsResponse flushResponse = FlushRowsResponse.newBuilder()
                .setOffset(5L)
                .build();
        BatchCommitWriteStreamsRequest commitRequest = BatchCommitWriteStreamsRequest.newBuilder()
                .setParent(TableName.format(PROJECT, DATASET, TABLE))
                .addWriteStreams(writeStream.getName())
                .build();
        BatchCommitWriteStreamsResponse commitResponse = BatchCommitWriteStreamsResponse.newBuilder()
                .setCommitTime(commitTime)
                .addStreamErrors(StorageError.newBuilder()
                        .setCode(StorageError.StorageErrorCode.STREAM_ALREADY_COMMITTED)
                        .setEntity(writeStream.getName())
                        .setErrorMessage("already committed"))
                .build();

        assertEquals(writeStream, WriteStream.parseFrom(writeStream.toByteArray()));
        assertEquals(WriteStream.Type.COMMITTED, createRequest.getWriteStream().getType());
        assertEquals(writeStream.getName(), GetWriteStreamRequest.parseFrom(getRequest.toByteArray()).getName());
        assertEquals(writeStream.getName(),
                FinalizeWriteStreamRequest.parseFrom(finalizeRequest.toByteArray()).getName());
        assertEquals(5L, FinalizeWriteStreamResponse.parseFrom(finalizeResponse.toByteArray()).getRowCount());
        assertEquals(5L, FlushRowsRequest.parseFrom(flushRequest.toByteArray()).getOffset().getValue());
        assertEquals(5L, FlushRowsResponse.parseFrom(flushResponse.toByteArray()).getOffset());
        assertEquals(writeStream.getName(),
                BatchCommitWriteStreamsRequest.parseFrom(commitRequest.toByteArray()).getWriteStreams(0));
        assertEquals(StorageError.StorageErrorCode.STREAM_ALREADY_COMMITTED,
                BatchCommitWriteStreamsResponse.parseFrom(commitResponse.toByteArray()).getStreamErrors(0).getCode());
        assertEquals(commitTime, commitResponse.getCommitTime());
    }

    @Test
    void serializationOptionsEnumsAndParsersExposeExpectedValues() throws Exception {
        ArrowSerializationOptions arrowOptions = ArrowSerializationOptions.newBuilder()
                .setFormat(ArrowSerializationOptions.Format.ARROW_0_14)
                .build();
        assertEquals(ArrowSerializationOptions.Format.ARROW_0_14,
                ArrowSerializationOptions.parseFrom(arrowOptions.toByteArray()).getFormat());
        assertSame(DataFormat.ARROW, DataFormat.forNumber(DataFormat.ARROW_VALUE));
        assertSame(TableFieldSchema.Type.JSON,
                TableFieldSchema.Type.valueOf(TableFieldSchema.Type.JSON.getValueDescriptor()));
        assertSame(ArrowSerializationOptions.Format.ARROW_0_15,
                ArrowSerializationOptions.Format.forNumber(ArrowSerializationOptions.Format.ARROW_0_15_VALUE));
        assertSame(StorageError.StorageErrorCode.STREAM_FINALIZED,
                StorageError.StorageErrorCode.internalGetValueMap().findValueByNumber(
                        StorageError.StorageErrorCode.STREAM_FINALIZED_VALUE));

        assertThrows(InvalidProtocolBufferException.class,
                () -> ReadSession.parser().parseFrom(ByteString.copyFrom(new byte[] {-1, 0, 1})));
        assertEquals(ReadSession.getDefaultInstance(), ReadSession.parseFrom(ByteString.EMPTY));
        assertEquals("ReadSession", ReadSession.getDefaultInstance().getDescriptorForType().getName());
    }
}
