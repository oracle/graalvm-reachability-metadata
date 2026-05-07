/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_api_grpc.proto_google_cloud_logging_v2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.api.Distribution;
import com.google.api.LabelDescriptor;
import com.google.api.MetricDescriptor;
import com.google.api.MonitoredResource;
import com.google.logging.type.HttpRequest;
import com.google.logging.type.LogSeverity;
import com.google.logging.v2.BigQueryDataset;
import com.google.logging.v2.BigQueryOptions;
import com.google.logging.v2.BucketMetadata;
import com.google.logging.v2.CmekSettings;
import com.google.logging.v2.CmekSettingsName;
import com.google.logging.v2.CopyLogEntriesMetadata;
import com.google.logging.v2.CopyLogEntriesRequest;
import com.google.logging.v2.CreateBucketRequest;
import com.google.logging.v2.CreateLinkRequest;
import com.google.logging.v2.CreateLogMetricRequest;
import com.google.logging.v2.DeleteLinkRequest;
import com.google.logging.v2.DeleteLogMetricRequest;
import com.google.logging.v2.GetLogMetricRequest;
import com.google.logging.v2.IndexConfig;
import com.google.logging.v2.IndexType;
import com.google.logging.v2.LifecycleState;
import com.google.logging.v2.Link;
import com.google.logging.v2.LinkMetadata;
import com.google.logging.v2.LinkName;
import com.google.logging.v2.ListLogEntriesRequest;
import com.google.logging.v2.ListLogEntriesResponse;
import com.google.logging.v2.ListLogMetricsRequest;
import com.google.logging.v2.ListLogMetricsResponse;
import com.google.logging.v2.LocationMetadata;
import com.google.logging.v2.LogBucket;
import com.google.logging.v2.LogBucketName;
import com.google.logging.v2.LogEntry;
import com.google.logging.v2.LogEntryOperation;
import com.google.logging.v2.LogEntryProto;
import com.google.logging.v2.LogEntrySourceLocation;
import com.google.logging.v2.LogExclusion;
import com.google.logging.v2.LogExclusionName;
import com.google.logging.v2.LogMetric;
import com.google.logging.v2.LogMetricName;
import com.google.logging.v2.LogName;
import com.google.logging.v2.LogSink;
import com.google.logging.v2.LogSinkName;
import com.google.logging.v2.LogSplit;
import com.google.logging.v2.LogView;
import com.google.logging.v2.LogViewName;
import com.google.logging.v2.LoggingConfigProto;
import com.google.logging.v2.LoggingMetricsProto;
import com.google.logging.v2.LoggingProto;
import com.google.logging.v2.OperationState;
import com.google.logging.v2.ProjectName;
import com.google.logging.v2.Settings;
import com.google.logging.v2.SettingsName;
import com.google.logging.v2.TailLogEntriesRequest;
import com.google.logging.v2.TailLogEntriesResponse;
import com.google.logging.v2.UpdateBucketRequest;
import com.google.logging.v2.UpdateCmekSettingsRequest;
import com.google.logging.v2.UpdateLogMetricRequest;
import com.google.logging.v2.UpdateSettingsRequest;
import com.google.logging.v2.WriteLogEntriesPartialErrors;
import com.google.logging.v2.WriteLogEntriesRequest;
import com.google.logging.v2.WriteLogEntriesResponse;
import com.google.protobuf.ByteString;
import com.google.protobuf.Duration;
import com.google.protobuf.FieldMask;
import com.google.protobuf.Struct;
import com.google.protobuf.Timestamp;
import com.google.protobuf.Value;
import com.google.rpc.Code;
import com.google.rpc.Status;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class Proto_google_cloud_logging_v2Test {
    private static final Timestamp CREATE_TIME = Timestamp.newBuilder()
            .setSeconds(1_700_000_000L)
            .setNanos(123)
            .build();
    private static final Timestamp UPDATE_TIME = Timestamp.newBuilder().setSeconds(1_700_000_120L).build();
    private static final String PROJECT_ID = "native-image-project";
    private static final String LOCATION = "global";
    private static final String BUCKET_ID = "application-logs";

    @Test
    void resourceNameHelpersFormatParseAndRoundTripAcrossResourceKinds() {
        LogName logName = LogName.ofProjectLogName(PROJECT_ID, "app.log");
        LogBucketName bucketName = LogBucketName.ofProjectLocationBucketName(PROJECT_ID, LOCATION, BUCKET_ID);
        LogViewName viewName = LogViewName.ofProjectLocationBucketViewName(PROJECT_ID, LOCATION, BUCKET_ID, "errors");
        LinkName linkName = LinkName.ofProjectLocationBucketLinkName(PROJECT_ID, LOCATION, BUCKET_ID, "analytics");
        LogSinkName sinkName = LogSinkName.ofProjectSinkName(PROJECT_ID, "bigquery-sink");
        LogMetricName metricName = LogMetricName.of(PROJECT_ID, "error-count");
        LogExclusionName exclusionName = LogExclusionName.ofProjectExclusionName(PROJECT_ID, "debug-noise");
        SettingsName settingsName = SettingsName.ofProjectName(PROJECT_ID);
        CmekSettingsName cmekSettingsName = CmekSettingsName.ofProjectCmekSettingsName(PROJECT_ID);

        assertThat(logName.toString()).isEqualTo("projects/native-image-project/logs/app.log");
        assertThat(LogName.parse(logName.toString())).isEqualTo(logName);
        assertThat(LogName.isParsableFrom(logName.toString())).isTrue();
        assertThat(logName.getFieldValuesMap()).containsEntry("project", PROJECT_ID).containsEntry("log", "app.log");

        assertThat(bucketName.toString())
                .isEqualTo("projects/native-image-project/locations/global/buckets/application-logs");
        assertThat(LogBucketName.parse(bucketName.toString()).getBucket()).isEqualTo(BUCKET_ID);
        assertThat(LogViewName.parse(viewName.toString()).getView()).isEqualTo("errors");
        assertThat(LinkName.parse(linkName.toString()).getLink()).isEqualTo("analytics");
        assertThat(LogSinkName.parse(sinkName.toString()).getSink()).isEqualTo("bigquery-sink");
        assertThat(LogMetricName.parse(metricName.toString()).getMetric()).isEqualTo("error-count");
        assertThat(LogExclusionName.parse(exclusionName.toString()).getExclusion()).isEqualTo("debug-noise");
        assertThat(SettingsName.parse(settingsName.toString()).getProject()).isEqualTo(PROJECT_ID);
        assertThat(CmekSettingsName.parse(cmekSettingsName.toString()).getProject()).isEqualTo(PROJECT_ID);
        assertThat(ProjectName.parse(ProjectName.format(PROJECT_ID)).getProject()).isEqualTo(PROJECT_ID);

        List<LogName> parsedLogs = LogName.parseList(List.of(logName.toString(), LogName.format(PROJECT_ID, "audit")));
        assertThat(LogName.toStringList(parsedLogs))
                .containsExactly(
                        "projects/native-image-project/logs/app.log",
                        "projects/native-image-project/logs/audit");
        assertThat(LogBucketName.isParsableFrom("projects/native-image-project/logs/not-a-bucket")).isFalse();
        assertThatThrownBy(() -> LogBucketName.parse("projects/native-image-project/logs/not-a-bucket"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void logEntrySupportsStructuredPayloadsLabelsAndParsing() throws Exception {
        MonitoredResource resource = MonitoredResource.newBuilder()
                .setType("gce_instance")
                .putLabels("project_id", PROJECT_ID)
                .putLabels("instance_id", "42")
                .putLabels("zone", "europe-west1-b")
                .build();
        LogEntryOperation operation = LogEntryOperation.newBuilder()
                .setId("startup")
                .setProducer("integration-test")
                .setFirst(true)
                .setLast(false)
                .build();
        LogEntrySourceLocation sourceLocation = LogEntrySourceLocation.newBuilder()
                .setFile("App.java")
                .setLine(27)
                .setFunction("main")
                .build();
        LogSplit split = LogSplit.newBuilder().setUid("split-1").setIndex(1).setTotalSplits(3).build();
        Struct jsonPayload = Struct.newBuilder()
                .putFields("message", Value.newBuilder().setStringValue("started").build())
                .putFields("attempt", Value.newBuilder().setNumberValue(2).build())
                .build();

        LogEntry textEntry = LogEntry.newBuilder()
                .setLogName(LogName.format(PROJECT_ID, "app"))
                .setResource(resource)
                .setTextPayload("service started")
                .setTimestamp(CREATE_TIME)
                .setReceiveTimestamp(UPDATE_TIME)
                .setSeverity(LogSeverity.INFO)
                .setInsertId("insert-1")
                .setHttpRequest(HttpRequest.newBuilder().setRequestMethod("GET").setStatus(200).build())
                .putLabels("component", "api")
                .putAllLabels(Map.of("environment", "test"))
                .setOperation(operation)
                .setTrace("projects/native-image-project/traces/abc")
                .setSpanId("span-1")
                .setTraceSampled(true)
                .setSourceLocation(sourceLocation)
                .setSplit(split)
                .build();

        assertThat(textEntry.getPayloadCase()).isEqualTo(LogEntry.PayloadCase.TEXT_PAYLOAD);
        assertThat(textEntry.getTextPayload()).isEqualTo("service started");
        assertThat(textEntry.getLabelsMap()).containsEntry("component", "api").containsEntry("environment", "test");
        assertThat(textEntry.getResource().getLabelsMap()).containsEntry("zone", "europe-west1-b");
        assertThat(textEntry.getHttpRequest().getStatus()).isEqualTo(200);
        assertThat(textEntry.getOperation().getId()).isEqualTo("startup");
        assertThat(textEntry.getSourceLocation().getLine()).isEqualTo(27);
        assertThat(textEntry.getSplit().getTotalSplits()).isEqualTo(3);

        LogEntry jsonEntry = textEntry.toBuilder().clearPayload().setJsonPayload(jsonPayload).build();
        assertThat(jsonEntry.getPayloadCase()).isEqualTo(LogEntry.PayloadCase.JSON_PAYLOAD);
        assertThat(jsonEntry.hasTextPayload()).isFalse();
        assertThat(jsonEntry.getJsonPayload().getFieldsOrThrow("message").getStringValue()).isEqualTo("started");

        byte[] serialized = jsonEntry.toByteArray();
        assertThat(LogEntry.parseFrom(ByteString.copyFrom(serialized))).isEqualTo(jsonEntry);
        assertThat(LogEntry.parseFrom(new ByteArrayInputStream(serialized))).isEqualTo(jsonEntry);
        assertThat(LogEntry.parser().parseFrom(serialized).getJsonPayload()).isEqualTo(jsonPayload);
        assertThat(LogEntryProto.getDescriptor().getMessageTypes())
                .anyMatch(descriptor -> descriptor.getName().equals("LogEntry"));
    }

    @Test
    void logConfigurationMessagesComposeNestedBucketSinkViewMetricAndLinkModels() throws Exception {
        CmekSettings cmekSettings = CmekSettings.newBuilder()
                .setName(CmekSettingsName.formatProjectCmekSettingsName(PROJECT_ID))
                .setKmsKeyName("projects/native-image-project/locations/global/keyRings/ring/cryptoKeys/log-key")
                .setKmsKeyVersionName(
                        "projects/native-image-project/locations/global/keyRings/ring/cryptoKeys/log-key/versions/1")
                .setServiceAccountId("service-123@gcp-sa-logging.iam.gserviceaccount.com")
                .build();
        IndexConfig indexConfig = IndexConfig.newBuilder()
                .setFieldPath("jsonPayload.request_id")
                .setType(IndexType.INDEX_TYPE_STRING)
                .setCreateTime(CREATE_TIME)
                .build();
        LogBucket bucket = LogBucket.newBuilder()
                .setName(LogBucketName.format(PROJECT_ID, LOCATION, BUCKET_ID))
                .setDescription("Bucket used by integration tests")
                .setCreateTime(CREATE_TIME)
                .setUpdateTime(UPDATE_TIME)
                .setRetentionDays(30)
                .setLocked(true)
                .setLifecycleState(LifecycleState.ACTIVE)
                .setAnalyticsEnabled(true)
                .addRestrictedFields("jsonPayload.secret")
                .addIndexConfigs(indexConfig)
                .setCmekSettings(cmekSettings)
                .build();
        LogView view = LogView.newBuilder()
                .setName(LogViewName.format(PROJECT_ID, LOCATION, BUCKET_ID, "errors"))
                .setDescription("Only errors")
                .setFilter("severity>=ERROR")
                .setCreateTime(CREATE_TIME)
                .setUpdateTime(UPDATE_TIME)
                .build();
        LogExclusion exclusion = LogExclusion.newBuilder()
                .setName(LogExclusionName.format(PROJECT_ID, "debug-noise"))
                .setDescription("Drop debug entries")
                .setFilter("severity=DEBUG")
                .setDisabled(false)
                .setCreateTime(CREATE_TIME)
                .setUpdateTime(UPDATE_TIME)
                .build();
        LogSink sink = LogSink.newBuilder()
                .setName(LogSinkName.format(PROJECT_ID, "bigquery-sink"))
                .setDestination("bigquery.googleapis.com/projects/native-image-project/datasets/logs")
                .setFilter("severity>=INFO")
                .setDescription("Export logs to BigQuery")
                .setDisabled(false)
                .addExclusions(exclusion)
                .setOutputVersionFormat(LogSink.VersionFormat.V2)
                .setWriterIdentity("serviceAccount:writer@example.iam.gserviceaccount.com")
                .setIncludeChildren(true)
                .setBigqueryOptions(BigQueryOptions.newBuilder()
                        .setUsePartitionedTables(true)
                        .setUsesTimestampColumnPartitioning(true))
                .setCreateTime(CREATE_TIME)
                .setUpdateTime(UPDATE_TIME)
                .build();
        LogMetric metric = newLogMetric();
        Link link = Link.newBuilder()
                .setName(LinkName.format(PROJECT_ID, LOCATION, BUCKET_ID, "analytics"))
                .setDescription("BigQuery analytics link")
                .setLifecycleState(LifecycleState.ACTIVE)
                .setCreateTime(CREATE_TIME)
                .setBigqueryDataset(BigQueryDataset.newBuilder().setDatasetId("logging_analytics"))
                .build();

        assertThat(bucket.getIndexConfigsList()).containsExactly(indexConfig);
        assertThat(bucket.getRestrictedFieldsList()).containsExactly("jsonPayload.secret");
        assertThat(bucket.getCmekSettings()).isEqualTo(cmekSettings);
        assertThat(view.getFilter()).isEqualTo("severity>=ERROR");
        assertThat(sink.getOptionsCase()).isEqualTo(LogSink.OptionsCase.BIGQUERY_OPTIONS);
        assertThat(sink.getBigqueryOptions().getUsePartitionedTables()).isTrue();
        assertThat(sink.getExclusionsList()).containsExactly(exclusion);
        assertThat(metric.getLabelExtractorsMap()).containsEntry("status", "EXTRACT(labels.status)");
        assertThat(link.getBigqueryDataset().getDatasetId()).isEqualTo("logging_analytics");

        assertThat(LogBucket.parseFrom(bucket.toByteArray())).isEqualTo(bucket);
        assertThat(LogSink.parseDelimitedFrom(delimitedStream(sink))).isEqualTo(sink);
        assertThat(LogMetric.parseFrom(metric.toByteArray()).getMetricDescriptor().getLabels(0).getKey())
                .isEqualTo("status");
        assertThat(LoggingConfigProto.getDescriptor().getMessageTypes())
                .anyMatch(descriptor -> descriptor.getName().equals("LogBucket"));
        assertThat(LoggingMetricsProto.getDescriptor().getMessageTypes())
                .anyMatch(descriptor -> descriptor.getName().equals("LogMetric"));
    }

    @Test
    void listWriteTailAndErrorMessagesRepresentLoggingServiceTraffic() throws Exception {
        LogEntry entry = minimalTextEntry("entry-1", "first payload");
        ListLogEntriesRequest listRequest = ListLogEntriesRequest.newBuilder()
                .addResourceNames(ProjectName.format(PROJECT_ID))
                .addResourceNames(LogBucketName.format(PROJECT_ID, LOCATION, BUCKET_ID))
                .setFilter("resource.type=gce_instance")
                .setOrderBy("timestamp desc")
                .setPageSize(10)
                .setPageToken("token-1")
                .build();
        ListLogEntriesResponse listResponse = ListLogEntriesResponse.newBuilder()
                .addEntries(entry)
                .setNextPageToken("token-2")
                .build();
        WriteLogEntriesRequest writeRequest = WriteLogEntriesRequest.newBuilder()
                .setLogName(LogName.format(PROJECT_ID, "app"))
                .setResource(MonitoredResource.newBuilder().setType("global").putLabels("project_id", PROJECT_ID))
                .putLabels("source", "integration")
                .addEntries(entry)
                .setPartialSuccess(true)
                .setDryRun(true)
                .build();
        WriteLogEntriesPartialErrors partialErrors = WriteLogEntriesPartialErrors.newBuilder()
                .putLogEntryErrors(0, Status.newBuilder()
                        .setCode(Code.INVALID_ARGUMENT_VALUE)
                        .setMessage("bad entry")
                        .build())
                .build();
        TailLogEntriesRequest tailRequest = TailLogEntriesRequest.newBuilder()
                .addResourceNames(ProjectName.format(PROJECT_ID))
                .setFilter("severity>=WARNING")
                .setBufferWindow(Duration.newBuilder().setSeconds(2))
                .build();
        TailLogEntriesResponse tailResponse = TailLogEntriesResponse.newBuilder()
                .addEntries(entry)
                .addSuppressionInfo(TailLogEntriesResponse.SuppressionInfo.newBuilder()
                        .setReason(TailLogEntriesResponse.SuppressionInfo.Reason.RATE_LIMIT)
                        .setSuppressedCount(7))
                .build();

        assertThat(listRequest.getResourceNamesList())
                .containsExactly(ProjectName.format(PROJECT_ID), LogBucketName.format(PROJECT_ID, LOCATION, BUCKET_ID));
        assertThat(listRequest.getOrderBy()).isEqualTo("timestamp desc");
        assertThat(listResponse.getEntriesList()).containsExactly(entry);
        assertThat(writeRequest.getLabelsMap()).containsEntry("source", "integration");
        assertThat(writeRequest.getEntries(0).getInsertId()).isEqualTo("entry-1");
        assertThat(WriteLogEntriesResponse.getDefaultInstance().isInitialized()).isTrue();
        assertThat(partialErrors.getLogEntryErrorsOrThrow(0).getMessage()).isEqualTo("bad entry");
        assertThat(tailRequest.getBufferWindow().getSeconds()).isEqualTo(2);
        assertThat(tailResponse.getSuppressionInfo(0).getReason())
                .isEqualTo(TailLogEntriesResponse.SuppressionInfo.Reason.RATE_LIMIT);

        assertThat(ListLogEntriesRequest.parseFrom(listRequest.toByteArray())).isEqualTo(listRequest);
        assertThat(WriteLogEntriesRequest.parseFrom(writeRequest.toByteArray()).getDryRun()).isTrue();
        assertThat(TailLogEntriesResponse.parseFrom(tailResponse.toByteArray())
                        .getSuppressionInfo(0)
                        .getSuppressedCount())
                .isEqualTo(7);
        assertThat(LoggingProto.getDescriptor().getMessageTypes())
                .anyMatch(descriptor -> descriptor.getName().equals("WriteLogEntriesRequest"));
    }

    @Test
    void logMetricServiceMessagesRepresentCrudAndPagination() {
        LogMetric metric = newLogMetric();
        LogMetric updatedMetric = metric.toBuilder()
                .setDescription("Updated count of error log entries")
                .setDisabled(true)
                .putLabelExtractors("severity", "EXTRACT(severity)")
                .build();
        String parent = ProjectName.format(PROJECT_ID);
        String metricName = metric.getName();

        CreateLogMetricRequest createRequest = CreateLogMetricRequest.newBuilder()
                .setParent(parent)
                .setMetric(metric)
                .build();
        ListLogMetricsRequest listRequest = ListLogMetricsRequest.newBuilder()
                .setParent(parent)
                .setPageSize(2)
                .setPageToken("first-page")
                .build();
        ListLogMetricsResponse listResponse = ListLogMetricsResponse.newBuilder()
                .addMetrics(metric)
                .addMetrics(updatedMetric)
                .setNextPageToken("second-page")
                .build();
        GetLogMetricRequest getRequest = GetLogMetricRequest.newBuilder().setMetricName(metricName).build();
        UpdateLogMetricRequest updateRequest = UpdateLogMetricRequest.newBuilder()
                .setMetricName(metricName)
                .setMetric(updatedMetric)
                .build();
        DeleteLogMetricRequest deleteRequest = DeleteLogMetricRequest.newBuilder().setMetricName(metricName).build();

        assertThat(createRequest.getParent()).isEqualTo(parent);
        assertThat(createRequest.hasMetric()).isTrue();
        assertThat(createRequest.getMetric().getMetricDescriptor().getType())
                .isEqualTo("logging.googleapis.com/user/error_count");
        assertThat(listRequest.getPageSize()).isEqualTo(2);
        assertThat(listRequest.getPageToken()).isEqualTo("first-page");
        assertThat(listResponse.getMetricsList()).containsExactly(metric, updatedMetric);
        assertThat(listResponse.getNextPageToken()).isEqualTo("second-page");
        assertThat(getRequest.getMetricName()).isEqualTo(metricName);
        assertThat(updateRequest.getMetric().getDisabled()).isTrue();
        assertThat(updateRequest.getMetric().getLabelExtractorsMap()).containsEntry("severity", "EXTRACT(severity)");
        assertThat(deleteRequest.getMetricName()).isEqualTo(metricName);
    }

    @Test
    void operationMetadataAndUpdateRequestsPreserveOneofsMasksAndSettings() throws Exception {
        LogBucket bucket = LogBucket.newBuilder()
                .setName(LogBucketName.format(PROJECT_ID, LOCATION, BUCKET_ID))
                .setRetentionDays(14)
                .build();
        CreateBucketRequest createBucketRequest = CreateBucketRequest.newBuilder()
                .setParent("projects/" + PROJECT_ID + "/locations/" + LOCATION)
                .setBucketId(BUCKET_ID)
                .setBucket(bucket)
                .build();
        UpdateBucketRequest updateBucketRequest = UpdateBucketRequest.newBuilder()
                .setName(bucket.getName())
                .setBucket(bucket.toBuilder().setDescription("updated"))
                .setUpdateMask(fieldMask("description", "retention_days"))
                .build();
        BucketMetadata bucketMetadata = BucketMetadata.newBuilder()
                .setStartTime(CREATE_TIME)
                .setEndTime(UPDATE_TIME)
                .setState(OperationState.OPERATION_STATE_RUNNING)
                .setCreateBucketRequest(createBucketRequest)
                .build();
        LinkMetadata deleteLinkMetadata = LinkMetadata.newBuilder()
                .setState(OperationState.OPERATION_STATE_SUCCEEDED)
                .setDeleteLinkRequest(DeleteLinkRequest.newBuilder()
                        .setName(LinkName.format(PROJECT_ID, LOCATION, BUCKET_ID, "analytics")))
                .build();
        LinkMetadata createLinkMetadata = LinkMetadata.newBuilder()
                .setState(OperationState.OPERATION_STATE_SCHEDULED)
                .setCreateLinkRequest(CreateLinkRequest.newBuilder()
                        .setParent(LogBucketName.format(PROJECT_ID, LOCATION, BUCKET_ID))
                        .setLinkId("analytics")
                        .setLink(Link.newBuilder().setDescription("analytics link")))
                .build();
        CopyLogEntriesRequest copyRequest = CopyLogEntriesRequest.newBuilder()
                .setName(LogBucketName.format(PROJECT_ID, LOCATION, BUCKET_ID))
                .setFilter("timestamp>\"2024-01-01T00:00:00Z\"")
                .setDestination(LogBucketName.format(PROJECT_ID, LOCATION, "archive"))
                .build();
        CopyLogEntriesMetadata copyMetadata = CopyLogEntriesMetadata.newBuilder()
                .setStartTime(CREATE_TIME)
                .setEndTime(UPDATE_TIME)
                .setState(OperationState.OPERATION_STATE_SUCCEEDED)
                .setCancellationRequested(false)
                .setRequest(copyRequest)
                .setProgress(100)
                .setWriterIdentity("serviceAccount:writer@example.iam.gserviceaccount.com")
                .build();
        Settings settings = Settings.newBuilder()
                .setName(SettingsName.formatProjectName(PROJECT_ID))
                .setKmsKeyName("projects/native-image-project/locations/global/keyRings/ring/cryptoKeys/log-key")
                .setKmsServiceAccountId("service-123@gcp-sa-logging.iam.gserviceaccount.com")
                .setStorageLocation("eu")
                .setDisableDefaultSink(true)
                .build();
        UpdateSettingsRequest updateSettingsRequest = UpdateSettingsRequest.newBuilder()
                .setName(settings.getName())
                .setSettings(settings)
                .setUpdateMask(fieldMask("kms_key_name", "disable_default_sink"))
                .build();
        CmekSettings cmekSettings = CmekSettings.newBuilder()
                .setName(CmekSettingsName.formatProjectCmekSettingsName(PROJECT_ID))
                .setKmsKeyName(settings.getKmsKeyName())
                .build();
        UpdateCmekSettingsRequest updateCmekSettingsRequest = UpdateCmekSettingsRequest.newBuilder()
                .setName(cmekSettings.getName())
                .setCmekSettings(cmekSettings)
                .setUpdateMask(fieldMask("kms_key_name"))
                .build();
        LocationMetadata locationMetadata = LocationMetadata.newBuilder().setLogAnalyticsEnabled(true).build();

        assertThat(bucketMetadata.getRequestCase()).isEqualTo(BucketMetadata.RequestCase.CREATE_BUCKET_REQUEST);
        assertThat(bucketMetadata.getCreateBucketRequest().getBucketId()).isEqualTo(BUCKET_ID);
        assertThat(updateBucketRequest.getUpdateMask().getPathsList()).containsExactly("description", "retention_days");
        assertThat(deleteLinkMetadata.getRequestCase()).isEqualTo(LinkMetadata.RequestCase.DELETE_LINK_REQUEST);
        assertThat(createLinkMetadata.getRequestCase()).isEqualTo(LinkMetadata.RequestCase.CREATE_LINK_REQUEST);
        assertThat(copyMetadata.getRequest()).isEqualTo(copyRequest);
        assertThat(copyMetadata.getProgress()).isEqualTo(100);
        assertThat(updateSettingsRequest.getSettings().getDisableDefaultSink()).isTrue();
        assertThat(updateCmekSettingsRequest.getCmekSettings().getKmsKeyName()).isEqualTo(settings.getKmsKeyName());
        assertThat(locationMetadata.getLogAnalyticsEnabled()).isTrue();

        assertThat(BucketMetadata.parseFrom(bucketMetadata.toByteArray())).isEqualTo(bucketMetadata);
        assertThat(LinkMetadata.parseFrom(deleteLinkMetadata.toByteArray()).getDeleteLinkRequest().getName())
                .isEqualTo(deleteLinkMetadata.getDeleteLinkRequest().getName());
        assertThat(CopyLogEntriesMetadata.parseFrom(copyMetadata.toByteArray()).getState())
                .isEqualTo(OperationState.OPERATION_STATE_SUCCEEDED);
        assertThat(UpdateSettingsRequest.parseFrom(updateSettingsRequest.toByteArray()).getUpdateMask().getPathsList())
                .containsExactly("kms_key_name", "disable_default_sink");
        assertThat(UpdateCmekSettingsRequest.parseFrom(updateCmekSettingsRequest.toByteArray()).getName())
                .isEqualTo(cmekSettings.getName());
    }

    private static LogEntry minimalTextEntry(String insertId, String payload) {
        return LogEntry.newBuilder()
                .setLogName(LogName.format(PROJECT_ID, "app"))
                .setTextPayload(payload)
                .setTimestamp(CREATE_TIME)
                .setSeverity(LogSeverity.WARNING)
                .setInsertId(insertId)
                .build();
    }

    private static LogMetric newLogMetric() {
        MetricDescriptor metricDescriptor = MetricDescriptor.newBuilder()
                .setType("logging.googleapis.com/user/error_count")
                .setMetricKind(MetricDescriptor.MetricKind.DELTA)
                .setValueType(MetricDescriptor.ValueType.INT64)
                .addLabels(LabelDescriptor.newBuilder()
                        .setKey("status")
                        .setValueType(LabelDescriptor.ValueType.STRING)
                        .setDescription("HTTP status class"))
                .build();
        return LogMetric.newBuilder()
                .setName(LogMetricName.format(PROJECT_ID, "error-count"))
                .setDescription("Count error log entries")
                .setFilter("severity>=ERROR")
                .setBucketName(LogBucketName.format(PROJECT_ID, LOCATION, BUCKET_ID))
                .setDisabled(false)
                .setMetricDescriptor(metricDescriptor)
                .setValueExtractor("EXTRACT(jsonPayload.latency_ms)")
                .putLabelExtractors("status", "EXTRACT(labels.status)")
                .setBucketOptions(Distribution.BucketOptions.newBuilder()
                        .setLinearBuckets(Distribution.BucketOptions.Linear.newBuilder()
                                .setNumFiniteBuckets(5)
                                .setWidth(100)
                                .setOffset(0)))
                .setCreateTime(CREATE_TIME)
                .setUpdateTime(UPDATE_TIME)
                .setVersion(LogMetric.ApiVersion.V2)
                .build();
    }

    private static FieldMask fieldMask(String firstPath, String... additionalPaths) {
        FieldMask.Builder builder = FieldMask.newBuilder().addPaths(firstPath);
        for (String path : additionalPaths) {
            builder.addPaths(path);
        }
        return builder.build();
    }

    private static ByteArrayInputStream delimitedStream(LogSink sink) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        sink.writeDelimitedTo(output);
        return new ByteArrayInputStream(output.toByteArray());
    }
}
