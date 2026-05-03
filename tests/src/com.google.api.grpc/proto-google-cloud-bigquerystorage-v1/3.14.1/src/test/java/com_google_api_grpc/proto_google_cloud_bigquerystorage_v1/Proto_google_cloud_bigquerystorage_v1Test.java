/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_api_grpc.proto_google_cloud_bigquerystorage_v1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.api.ClientProto;
import com.google.api.HttpRule;
import com.google.cloud.bigquery.storage.v1.AnnotationsProto;
import com.google.cloud.bigquery.storage.v1.AppendRowsRequest;
import com.google.cloud.bigquery.storage.v1.AppendRowsResponse;
import com.google.cloud.bigquery.storage.v1.ArrowRecordBatch;
import com.google.cloud.bigquery.storage.v1.ArrowSchema;
import com.google.cloud.bigquery.storage.v1.ArrowSerializationOptions;
import com.google.cloud.bigquery.storage.v1.AvroRows;
import com.google.cloud.bigquery.storage.v1.AvroSchema;
import com.google.cloud.bigquery.storage.v1.AvroSerializationOptions;
import com.google.cloud.bigquery.storage.v1.BatchCommitWriteStreamsRequest;
import com.google.cloud.bigquery.storage.v1.BatchCommitWriteStreamsResponse;
import com.google.cloud.bigquery.storage.v1.CreateReadSessionRequest;
import com.google.cloud.bigquery.storage.v1.CreateWriteStreamRequest;
import com.google.cloud.bigquery.storage.v1.DataFormat;
import com.google.cloud.bigquery.storage.v1.FinalizeWriteStreamRequest;
import com.google.cloud.bigquery.storage.v1.FinalizeWriteStreamResponse;
import com.google.cloud.bigquery.storage.v1.FlushRowsRequest;
import com.google.cloud.bigquery.storage.v1.FlushRowsResponse;
import com.google.cloud.bigquery.storage.v1.GetWriteStreamRequest;
import com.google.cloud.bigquery.storage.v1.ProjectName;
import com.google.cloud.bigquery.storage.v1.ProtoRows;
import com.google.cloud.bigquery.storage.v1.ProtoSchema;
import com.google.cloud.bigquery.storage.v1.ReadRowsRequest;
import com.google.cloud.bigquery.storage.v1.ReadRowsResponse;
import com.google.cloud.bigquery.storage.v1.ReadSession;
import com.google.cloud.bigquery.storage.v1.ReadStream;
import com.google.cloud.bigquery.storage.v1.ReadStreamName;
import com.google.cloud.bigquery.storage.v1.RowError;
import com.google.cloud.bigquery.storage.v1.SplitReadStreamRequest;
import com.google.cloud.bigquery.storage.v1.SplitReadStreamResponse;
import com.google.cloud.bigquery.storage.v1.StorageError;
import com.google.cloud.bigquery.storage.v1.StorageProto;
import com.google.cloud.bigquery.storage.v1.StreamStats;
import com.google.cloud.bigquery.storage.v1.TableFieldSchema;
import com.google.cloud.bigquery.storage.v1.TableName;
import com.google.cloud.bigquery.storage.v1.TableSchema;
import com.google.cloud.bigquery.storage.v1.ThrottleState;
import com.google.cloud.bigquery.storage.v1.WriteStream;
import com.google.cloud.bigquery.storage.v1.WriteStreamName;
import com.google.cloud.bigquery.storage.v1.WriteStreamView;
import com.google.protobuf.ByteString;
import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.Descriptors.MethodDescriptor;
import com.google.protobuf.Descriptors.ServiceDescriptor;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.Int64Value;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Timestamp;
import com.google.rpc.Status;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class Proto_google_cloud_bigquerystorage_v1Test {
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
    void tableSchemaPreservesNestedFieldsAndRangeTypesThroughBinaryRoundTrip() throws Exception {
        TableFieldSchema rangeField = TableFieldSchema.newBuilder()
                .setName("active_date")
                .setType(TableFieldSchema.Type.RANGE)
                .setMode(TableFieldSchema.Mode.NULLABLE)
                .setDescription("inclusive activity range")
                .setRangeElementType(TableFieldSchema.FieldElementType.newBuilder()
                        .setType(TableFieldSchema.Type.DATE))
                .build();
        TableFieldSchema structField = TableFieldSchema.newBuilder()
                .setName("payload")
                .setType(TableFieldSchema.Type.STRUCT)
                .setMode(TableFieldSchema.Mode.REPEATED)
                .addFields(TableFieldSchema.newBuilder()
                        .setName("id")
                        .setType(TableFieldSchema.Type.INT64)
                        .setMode(TableFieldSchema.Mode.REQUIRED)
                        .setPrecision(19))
                .addFields(TableFieldSchema.newBuilder()
                        .setName("comment")
                        .setType(TableFieldSchema.Type.STRING)
                        .setMaxLength(256)
                        .setDefaultValueExpression("'n/a'"))
                .build();
        TableSchema schema = TableSchema.newBuilder()
                .addFields(rangeField)
                .addFields(structField)
                .build();

        TableSchema parsed = TableSchema.parseFrom(schema.toByteArray());
        assertEquals(schema, parsed);
        assertEquals(2, parsed.getFieldsCount());
        assertTrue(parsed.getFields(0).hasRangeElementType());
        assertEquals(TableFieldSchema.Type.DATE, parsed.getFields(0).getRangeElementType().getType());
        assertEquals(TableFieldSchema.Mode.REPEATED, parsed.getFields(1).getMode());
        assertEquals("comment", parsed.getFields(1).getFields(1).getName());
        assertEquals("'n/a'", parsed.getFields(1).getFields(1).getDefaultValueExpression());
        assertEquals("TableSchema", TableSchema.getDescriptor().getName());
    }

    @Test
    void readSessionRequestsCarrySchemaReadOptionsAndStreamMetadata() throws Exception {
        Timestamp snapshotTime = Timestamp.newBuilder().setSeconds(1_700_000_000L).setNanos(123_000_000).build();
        ArrowSerializationOptions arrowOptions = ArrowSerializationOptions.newBuilder()
                .setBufferCompression(ArrowSerializationOptions.CompressionCodec.LZ4_FRAME)
                .build();
        ReadSession.TableReadOptions readOptions = ReadSession.TableReadOptions.newBuilder()
                .addSelectedFields("user_id")
                .addSelectedFields("event_time")
                .setRowRestriction("event_date >= '2024-01-01'")
                .setArrowSerializationOptions(arrowOptions)
                .setSamplePercentage(12.5D)
                .setResponseCompressionCodec(
                        ReadSession.TableReadOptions.ResponseCompressionCodec.RESPONSE_COMPRESSION_CODEC_LZ4)
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
                .setEstimatedTotalBytesScanned(4096L)
                .setEstimatedTotalPhysicalFileSize(2048L)
                .setEstimatedRowCount(64L)
                .setTraceId("trace-read-session")
                .build();
        CreateReadSessionRequest request = CreateReadSessionRequest.newBuilder()
                .setParent(ProjectName.format(PROJECT))
                .setReadSession(readSession)
                .setMaxStreamCount(3)
                .setPreferredMinStreamCount(1)
                .build();

        CreateReadSessionRequest parsed = CreateReadSessionRequest.parseFrom(
                new ByteArrayInputStream(request.toByteArray()));
        assertEquals(ProjectName.format(PROJECT), parsed.getParent());
        assertEquals(DataFormat.ARROW, parsed.getReadSession().getDataFormat());
        assertEquals(ReadSession.SchemaCase.ARROW_SCHEMA, parsed.getReadSession().getSchemaCase());
        assertEquals("event_time", parsed.getReadSession().getReadOptions().getSelectedFields(1));
        assertEquals(ReadSession.TableReadOptions.OutputFormatSerializationOptionsCase.ARROW_SERIALIZATION_OPTIONS,
                parsed.getReadSession().getReadOptions().getOutputFormatSerializationOptionsCase());
        assertEquals(ArrowSerializationOptions.CompressionCodec.LZ4_FRAME,
                parsed.getReadSession().getReadOptions().getArrowSerializationOptions().getBufferCompression());
        assertTrue(parsed.getReadSession().getReadOptions().hasSamplePercentage());
        assertEquals(12.5D, parsed.getReadSession().getReadOptions().getSamplePercentage(), 0.0001D);
        assertEquals(snapshotTime, parsed.getReadSession().getTableModifiers().getSnapshotTime());
        assertEquals(64L, parsed.getReadSession().getEstimatedRowCount());
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
        assertEquals("https://www.googleapis.com/auth/bigquery,https://www.googleapis.com/auth/bigquery.insertdata,"
                + "https://www.googleapis.com/auth/cloud-platform",
                writeService.getOptions().getExtension(ClientProto.oauthScopes));

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
        assertEquals("/v1/{read_session.table=projects/*/datasets/*/tables/*}", createReadSessionHttp.getPost());
        assertEquals("*", createReadSessionHttp.getBody());

        MethodDescriptor readRows = readService.findMethodByName("ReadRows");
        assertNotNull(readRows);
        assertEquals(ReadRowsRequest.getDescriptor(), readRows.getInputType());
        assertEquals(ReadRowsResponse.getDescriptor(), readRows.getOutputType());
        assertFalse(readRows.isClientStreaming());
        assertTrue(readRows.isServerStreaming());
        assertTrue(readRows.getOptions().hasExtension(com.google.api.AnnotationsProto.http));
        HttpRule readRowsHttp = readRows.getOptions().getExtension(com.google.api.AnnotationsProto.http);
        assertEquals(HttpRule.PatternCase.GET, readRowsHttp.getPatternCase());
        assertEquals("/v1/{read_stream=projects/*/locations/*/sessions/*/streams/*}", readRowsHttp.getGet());

        MethodDescriptor appendRows = writeService.findMethodByName("AppendRows");
        assertNotNull(appendRows);
        assertEquals(AppendRowsRequest.getDescriptor(), appendRows.getInputType());
        assertEquals(AppendRowsResponse.getDescriptor(), appendRows.getOutputType());
        assertTrue(appendRows.isClientStreaming());
        assertTrue(appendRows.isServerStreaming());
        assertTrue(appendRows.getOptions().hasExtension(com.google.api.AnnotationsProto.http));
        HttpRule appendRowsHttp = appendRows.getOptions().getExtension(com.google.api.AnnotationsProto.http);
        assertEquals(HttpRule.PatternCase.POST, appendRowsHttp.getPatternCase());
        assertEquals("/v1/{write_stream=projects/*/datasets/*/tables/*/streams/*}", appendRowsHttp.getPost());
        assertEquals("*", appendRowsHttp.getBody());
    }

    @Test
    void protoSchemaSupportsBigQueryColumnNameAnnotations() throws Exception {
        ExtensionRegistry registry = ExtensionRegistry.newInstance();
        AnnotationsProto.registerAllExtensions(registry);
        DescriptorProtos.FieldDescriptorProto field = DescriptorProtos.FieldDescriptorProto.newBuilder()
                .setName("customer_display_name")
                .setNumber(1)
                .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING)
                .setOptions(DescriptorProtos.FieldOptions.newBuilder()
                        .setExtension(AnnotationsProto.columnName, "Customer Display Name"))
                .build();
        ProtoSchema schema = ProtoSchema.newBuilder()
                .setProtoDescriptor(DescriptorProtos.DescriptorProto.newBuilder()
                        .setName("CustomerRow")
                        .addField(field))
                .build();

        ProtoSchema parsed = ProtoSchema.parseFrom(schema.toByteString(), registry);
        DescriptorProtos.FieldOptions parsedOptions = parsed.getProtoDescriptor().getField(0).getOptions();
        assertEquals("CustomerRow", parsed.getProtoDescriptor().getName());
        assertEquals("customer_display_name", parsed.getProtoDescriptor().getField(0).getName());
        assertTrue(parsedOptions.hasExtension(AnnotationsProto.columnName));
        assertEquals("Customer Display Name", parsedOptions.getExtension(AnnotationsProto.columnName));
    }

    @Test
    void appendRowsRequestSupportsProtoRowsMapInterpretationsAndOneofSwitching() throws Exception {
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
                .build();
        AppendRowsRequest request = AppendRowsRequest.newBuilder()
                .setWriteStream(WriteStreamName.format(PROJECT, DATASET, TABLE, WRITE_STREAM))
                .setOffset(Int64Value.of(42L))
                .setProtoRows(AppendRowsRequest.ProtoData.newBuilder()
                        .setWriterSchema(ProtoSchema.newBuilder().setProtoDescriptor(rowDescriptor))
                        .setRows(rows))
                .setTraceId("trace-append")
                .putMissingValueInterpretations("event_name",
                        AppendRowsRequest.MissingValueInterpretation.DEFAULT_VALUE)
                .setDefaultMissingValueInterpretation(AppendRowsRequest.MissingValueInterpretation.NULL_VALUE)
                .build();

        AppendRowsRequest parsed = AppendRowsRequest.parseFrom(request.toByteString());
        assertEquals(AppendRowsRequest.RowsCase.PROTO_ROWS, parsed.getRowsCase());
        assertEquals(42L, parsed.getOffset().getValue());
        assertEquals("EventRow", parsed.getProtoRows().getWriterSchema().getProtoDescriptor().getName());
        assertEquals(1, parsed.getProtoRows().getRows().getSerializedRowsCount());
        assertTrue(parsed.containsMissingValueInterpretations("event_name"));
        assertEquals(AppendRowsRequest.MissingValueInterpretation.DEFAULT_VALUE,
                parsed.getMissingValueInterpretationsMap().get("event_name"));
        assertEquals(AppendRowsRequest.MissingValueInterpretation.NULL_VALUE,
                parsed.getDefaultMissingValueInterpretation());

        AppendRowsRequest arrowRequest = parsed.toBuilder()
                .setArrowRows(AppendRowsRequest.ArrowData.newBuilder()
                        .setWriterSchema(ArrowSchema.newBuilder()
                                .setSerializedSchema(ByteString.copyFromUtf8("schema")))
                        .setRows(ArrowRecordBatch.newBuilder()
                                .setSerializedRecordBatch(ByteString.copyFromUtf8("batch"))
                                .setRowCount(2L)))
                .build();
        assertEquals(AppendRowsRequest.RowsCase.ARROW_ROWS, arrowRequest.getRowsCase());
        assertFalse(arrowRequest.hasProtoRows());
        assertEquals(2L, arrowRequest.getArrowRows().getRows().getRowCount());
    }

    @Test
    void appendRowsResponsesRepresentSuccessErrorsAndUpdatedSchemas() throws Exception {
        TableSchema updatedSchema = TableSchema.newBuilder()
                .addFields(TableFieldSchema.newBuilder().setName("user_id").setType(TableFieldSchema.Type.INT64))
                .build();
        RowError rowError = RowError.newBuilder()
                .setIndex(3L)
                .setCode(RowError.RowErrorCode.FIELDS_ERROR)
                .setMessage("missing user_id")
                .build();
        AppendRowsResponse success = AppendRowsResponse.newBuilder()
                .setAppendResult(AppendRowsResponse.AppendResult.newBuilder().setOffset(Int64Value.of(42L)))
                .setUpdatedSchema(updatedSchema)
                .addRowErrors(rowError)
                .setWriteStream(WriteStreamName.format(PROJECT, DATASET, TABLE, WRITE_STREAM))
                .build();

        AppendRowsResponse parsedSuccess = AppendRowsResponse.parseFrom(success.toByteArray());
        assertEquals(AppendRowsResponse.ResponseCase.APPEND_RESULT, parsedSuccess.getResponseCase());
        assertEquals(42L, parsedSuccess.getAppendResult().getOffset().getValue());
        assertTrue(parsedSuccess.hasUpdatedSchema());
        assertEquals(RowError.RowErrorCode.FIELDS_ERROR, parsedSuccess.getRowErrors(0).getCode());
        assertEquals("missing user_id", parsedSuccess.getRowErrors(0).getMessage());

        AppendRowsResponse error = parsedSuccess.toBuilder()
                .setError(Status.newBuilder().setCode(3).setMessage("invalid argument"))
                .build();
        assertEquals(AppendRowsResponse.ResponseCase.ERROR, error.getResponseCase());
        assertFalse(error.hasAppendResult());
        assertEquals("invalid argument", error.getError().getMessage());
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
                .setAvroRows(AvroRows.newBuilder()
                        .setSerializedBinaryRows(ByteString.copyFromUtf8("avro"))
                        .setRowCount(2L))
                .setRowCount(2L)
                .setStats(stats)
                .setThrottleState(ThrottleState.newBuilder().setThrottlePercent(15))
                .setAvroSchema(AvroSchema.newBuilder().setSchema("{\"type\":\"record\",\"name\":\"Row\"}"))
                .setUncompressedByteSize(128L)
                .build();

        ReadRowsResponse parsedAvro = ReadRowsResponse.parseFrom(avroResponse.toByteString());
        assertEquals(ReadRowsResponse.RowsCase.AVRO_ROWS, parsedAvro.getRowsCase());
        assertEquals(ReadRowsResponse.SchemaCase.AVRO_SCHEMA, parsedAvro.getSchemaCase());
        assertEquals(2L, parsedAvro.getAvroRows().getRowCount());
        assertEquals(0.5D, parsedAvro.getStats().getProgress().getAtResponseEnd(), 0.0001D);
        assertEquals(15, parsedAvro.getThrottleState().getThrottlePercent());
        assertTrue(parsedAvro.hasUncompressedByteSize());

        ReadRowsResponse arrowResponse = parsedAvro.toBuilder()
                .setArrowRecordBatch(ArrowRecordBatch.newBuilder()
                        .setSerializedRecordBatch(ByteString.copyFromUtf8("arrow"))
                        .setRowCount(4L))
                .setArrowSchema(ArrowSchema.newBuilder().setSerializedSchema(ByteString.copyFromUtf8("arrow-schema")))
                .build();
        assertEquals(ReadRowsResponse.RowsCase.ARROW_RECORD_BATCH, arrowResponse.getRowsCase());
        assertEquals(ReadRowsResponse.SchemaCase.ARROW_SCHEMA, arrowResponse.getSchemaCase());
        assertEquals(4L, arrowResponse.getArrowRecordBatch().getRowCount());

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
                .setWriteMode(WriteStream.WriteMode.INSERT)
                .setLocation(LOCATION)
                .build();

        CreateWriteStreamRequest createRequest = CreateWriteStreamRequest.newBuilder()
                .setParent(TableName.format(PROJECT, DATASET, TABLE))
                .setWriteStream(writeStream)
                .build();
        GetWriteStreamRequest getRequest = GetWriteStreamRequest.newBuilder()
                .setName(writeStream.getName())
                .setView(WriteStreamView.FULL)
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
        assertEquals(WriteStream.WriteMode.INSERT, createRequest.getWriteStream().getWriteMode());
        assertEquals(WriteStreamView.FULL, GetWriteStreamRequest.parseFrom(getRequest.toByteArray()).getView());
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
        AvroSerializationOptions avroOptions = AvroSerializationOptions.newBuilder()
                .setEnableDisplayNameAttribute(true)
                .build();
        assertTrue(AvroSerializationOptions.parseFrom(avroOptions.toByteArray()).getEnableDisplayNameAttribute());
        assertSame(DataFormat.ARROW, DataFormat.forNumber(DataFormat.ARROW_VALUE));
        assertSame(TableFieldSchema.Type.JSON,
                TableFieldSchema.Type.valueOf(TableFieldSchema.Type.JSON.getValueDescriptor()));
        assertSame(AppendRowsRequest.MissingValueInterpretation.DEFAULT_VALUE,
                AppendRowsRequest.MissingValueInterpretation.forNumber(
                        AppendRowsRequest.MissingValueInterpretation.DEFAULT_VALUE_VALUE));
        assertSame(StorageError.StorageErrorCode.KMS_PERMISSION_DENIED,
                StorageError.StorageErrorCode.internalGetValueMap().findValueByNumber(
                        StorageError.StorageErrorCode.KMS_PERMISSION_DENIED_VALUE));

        assertThrows(InvalidProtocolBufferException.class,
                () -> ReadSession.parser().parseFrom(ByteString.copyFrom(new byte[] {-1, 0, 1})));
        assertEquals(ReadSession.getDefaultInstance(), ReadSession.parseFrom(ByteString.EMPTY));
        assertEquals("ReadSession", ReadSession.getDefaultInstance().getDescriptorForType().getName());
    }
}
