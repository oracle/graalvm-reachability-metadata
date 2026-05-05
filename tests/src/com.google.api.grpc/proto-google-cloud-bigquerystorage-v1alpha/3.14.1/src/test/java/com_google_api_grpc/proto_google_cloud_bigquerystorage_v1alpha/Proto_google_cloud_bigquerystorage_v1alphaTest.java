/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_api_grpc.proto_google_cloud_bigquerystorage_v1alpha;

import com.google.api.AnnotationsProto;
import com.google.api.ClientProto;
import com.google.api.FieldBehavior;
import com.google.api.FieldBehaviorProto;
import com.google.api.HttpRule;
import com.google.api.ResourceDescriptor;
import com.google.api.ResourceProto;
import com.google.api.ResourceReference;
import com.google.cloud.bigquery.storage.v1alpha.BatchCreateMetastorePartitionsRequest;
import com.google.cloud.bigquery.storage.v1alpha.BatchCreateMetastorePartitionsResponse;
import com.google.cloud.bigquery.storage.v1alpha.BatchDeleteMetastorePartitionsRequest;
import com.google.cloud.bigquery.storage.v1alpha.BatchSizeTooLargeError;
import com.google.cloud.bigquery.storage.v1alpha.BatchUpdateMetastorePartitionsRequest;
import com.google.cloud.bigquery.storage.v1alpha.BatchUpdateMetastorePartitionsResponse;
import com.google.cloud.bigquery.storage.v1alpha.CreateMetastorePartitionRequest;
import com.google.cloud.bigquery.storage.v1alpha.FieldSchema;
import com.google.cloud.bigquery.storage.v1alpha.ListMetastorePartitionsRequest;
import com.google.cloud.bigquery.storage.v1alpha.ListMetastorePartitionsResponse;
import com.google.cloud.bigquery.storage.v1alpha.MetastorePartition;
import com.google.cloud.bigquery.storage.v1alpha.MetastorePartitionList;
import com.google.cloud.bigquery.storage.v1alpha.MetastorePartitionProto;
import com.google.cloud.bigquery.storage.v1alpha.MetastorePartitionServiceProto;
import com.google.cloud.bigquery.storage.v1alpha.MetastorePartitionValues;
import com.google.cloud.bigquery.storage.v1alpha.ReadStream;
import com.google.cloud.bigquery.storage.v1alpha.SerDeInfo;
import com.google.cloud.bigquery.storage.v1alpha.StorageDescriptor;
import com.google.cloud.bigquery.storage.v1alpha.StreamList;
import com.google.cloud.bigquery.storage.v1alpha.StreamMetastorePartitionsRequest;
import com.google.cloud.bigquery.storage.v1alpha.StreamMetastorePartitionsResponse;
import com.google.cloud.bigquery.storage.v1alpha.TableName;
import com.google.cloud.bigquery.storage.v1alpha.UpdateMetastorePartitionRequest;
import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.FieldMask;
import com.google.protobuf.Timestamp;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Proto_google_cloud_bigquerystorage_v1alphaTest {
    private static final String TABLE = "projects/project-one/datasets/dataset_one/tables/table_one";
    private static final String TABLE_WITH_LOCATION =
                    "projects/project-one/locations/us/datasets/dataset_one/tables/table_one";

    @Test
    void buildsNestedMetastorePartitionAndPreservesAllFields() throws Exception {
        MetastorePartition partition = samplePartition("2025", "05");

        assertThat(partition.getValuesList()).containsExactly("2025", "05");
        assertThat(partition.hasCreateTime()).isTrue();
        assertThat(partition.getCreateTime().getSeconds()).isEqualTo(1_746_057_600L);
        assertThat(partition.hasStorageDescriptor()).isTrue();
        assertThat(partition.getStorageDescriptor().getLocationUri())
                        .isEqualTo("gs://example-bucket/table/dt=2025/month=05");
        assertThat(partition.getStorageDescriptor().getInputFormat()).contains("OrcInputFormat");
        assertThat(partition.getStorageDescriptor().getOutputFormat()).contains("OrcOutputFormat");
        assertThat(partition.getStorageDescriptor().getSerdeInfo().getSerializationLibrary())
                        .contains("OrcSerde");
        assertThat(partition.getStorageDescriptor().getSerdeInfo().getParametersMap())
                        .containsEntry("serialization.format", "1")
                        .containsEntry("field.delim", "|");
        assertThat(partition.getParametersMap()).containsEntry("source", "unit-test");
        assertThat(partition.getParametersOrDefault("missing", "fallback")).isEqualTo("fallback");
        assertThat(partition.getFieldsList())
                        .extracting(FieldSchema::getName)
                        .containsExactly("customer_id", "amount");
        assertThat(partition.getFieldsList())
                        .extracting(FieldSchema::getType)
                        .containsExactly("string", "decimal(10,2)");

        MetastorePartition parsed = MetastorePartition.parseFrom(partition.toByteArray());
        assertThat(parsed).isEqualTo(partition);
        assertThat(MetastorePartition.parser().parseFrom(partition.toByteString())).isEqualTo(partition);

        MetastorePartition updated = parsed.toBuilder()
                        .setValues(1, "06")
                        .putParameters("quality", "validated")
                        .addFields(FieldSchema.newBuilder().setName("region").setType("string"))
                        .build();

        assertThat(updated.getValuesList()).containsExactly("2025", "06");
        assertThat(updated.getParametersMap()).containsEntry("quality", "validated");
        assertThat(updated.getFieldsCount()).isEqualTo(3);
    }

    @Test
    void constructsBatchRequestsResponsesAndMasks() throws Exception {
        MetastorePartition firstPartition = samplePartition("2025", "05");
        MetastorePartition secondPartition = samplePartition("2025", "06");
        CreateMetastorePartitionRequest createRequest = CreateMetastorePartitionRequest.newBuilder()
                        .setParent(TABLE)
                        .setMetastorePartition(firstPartition)
                        .build();
        FieldMask updateMask = FieldMask.newBuilder()
                        .addPaths("storage_descriptor.location_uri")
                        .addPaths("parameters")
                        .build();
        UpdateMetastorePartitionRequest updateRequest = UpdateMetastorePartitionRequest.newBuilder()
                        .setMetastorePartition(secondPartition)
                        .setUpdateMask(updateMask)
                        .build();

        BatchCreateMetastorePartitionsRequest batchCreateRequest = BatchCreateMetastorePartitionsRequest.newBuilder()
                        .setParent(TABLE_WITH_LOCATION)
                        .addRequests(createRequest)
                        .setSkipExistingPartitions(true)
                        .setTraceId("trace-create")
                        .build();
        BatchUpdateMetastorePartitionsRequest batchUpdateRequest = BatchUpdateMetastorePartitionsRequest.newBuilder()
                        .setParent(TABLE_WITH_LOCATION)
                        .addRequests(updateRequest)
                        .setTraceId("trace-update")
                        .build();
        BatchDeleteMetastorePartitionsRequest batchDeleteRequest = BatchDeleteMetastorePartitionsRequest.newBuilder()
                        .setParent(TABLE_WITH_LOCATION)
                        .addPartitionValues(MetastorePartitionValues.newBuilder().addValues("2025").addValues("05"))
                        .setTraceId("trace-delete")
                        .build();

        assertThat(batchCreateRequest.getParent()).isEqualTo(TABLE_WITH_LOCATION);
        assertThat(batchCreateRequest.getRequests(0).getMetastorePartition()).isEqualTo(firstPartition);
        assertThat(batchCreateRequest.getSkipExistingPartitions()).isTrue();
        assertThat(BatchCreateMetastorePartitionsRequest.parseFrom(batchCreateRequest.toByteString()))
                        .isEqualTo(batchCreateRequest);
        assertThat(batchUpdateRequest.getRequests(0).getUpdateMask().getPathsList())
                        .containsExactly("storage_descriptor.location_uri", "parameters");
        assertThat(BatchUpdateMetastorePartitionsRequest.parseFrom(batchUpdateRequest.toByteArray()))
                        .isEqualTo(batchUpdateRequest);
        assertThat(batchDeleteRequest.getPartitionValues(0).getValuesList()).containsExactly("2025", "05");
        assertThat(BatchDeleteMetastorePartitionsRequest.parseFrom(batchDeleteRequest.toByteArray()))
                        .isEqualTo(batchDeleteRequest);

        BatchCreateMetastorePartitionsResponse createResponse = BatchCreateMetastorePartitionsResponse.newBuilder()
                        .addAllPartitions(Arrays.asList(firstPartition, secondPartition))
                        .build();
        BatchUpdateMetastorePartitionsResponse updateResponse = BatchUpdateMetastorePartitionsResponse.newBuilder()
                        .addPartitions(secondPartition)
                        .build();

        assertThat(createResponse.getPartitionsList()).containsExactly(firstPartition, secondPartition);
        assertThat(updateResponse.getPartitionsList()).containsExactly(secondPartition);
        assertThat(BatchCreateMetastorePartitionsResponse.parseFrom(createResponse.toByteString()))
                        .isEqualTo(createResponse);
        assertThat(BatchUpdateMetastorePartitionsResponse.parseFrom(updateResponse.toByteString()))
                        .isEqualTo(updateResponse);
    }

    @Test
    void switchesListResponseBetweenPartitionAndStreamOneofVariants() throws Exception {
        MetastorePartition partition = samplePartition("2025", "07");
        MetastorePartitionList partitionList = MetastorePartitionList.newBuilder()
                        .addPartitions(partition)
                        .build();
        ListMetastorePartitionsResponse partitionsResponse = ListMetastorePartitionsResponse.newBuilder()
                        .setPartitions(partitionList)
                        .build();

        assertThat(partitionsResponse.getResponseCase())
                        .isEqualTo(ListMetastorePartitionsResponse.ResponseCase.PARTITIONS);
        assertThat(partitionsResponse.hasPartitions()).isTrue();
        assertThat(partitionsResponse.hasStreams()).isFalse();
        assertThat(partitionsResponse.getPartitions().getPartitionsList()).containsExactly(partition);

        ReadStream firstStream = ReadStream.newBuilder()
                        .setName("projects/project-one/locations/us/sessions/session-one/streams/stream-one")
                        .build();
        ReadStream secondStream = firstStream.toBuilder()
                        .setName("projects/project-one/locations/us/sessions/session-one/streams/stream-two")
                        .build();
        StreamList streamList = StreamList.newBuilder()
                        .addStreams(firstStream)
                        .addStreams(secondStream)
                        .build();
        ListMetastorePartitionsResponse streamsResponse = partitionsResponse.toBuilder()
                        .setStreams(streamList)
                        .build();

        assertThat(streamsResponse.getResponseCase()).isEqualTo(ListMetastorePartitionsResponse.ResponseCase.STREAMS);
        assertThat(streamsResponse.hasPartitions()).isFalse();
        assertThat(streamsResponse.hasStreams()).isTrue();
        assertThat(streamsResponse.getStreams().getStreamsList()).containsExactly(firstStream, secondStream);
        assertThat(ListMetastorePartitionsResponse.parseFrom(streamsResponse.toByteArray())).isEqualTo(streamsResponse);

        ListMetastorePartitionsRequest listRequest = ListMetastorePartitionsRequest.newBuilder()
                        .setParent(TABLE_WITH_LOCATION)
                        .setFilter("amount BETWEEN 1.0 AND 5.0")
                        .setTraceId("trace-list")
                        .build();

        assertThat(listRequest.getParent()).isEqualTo(TABLE_WITH_LOCATION);
        assertThat(listRequest.getFilter()).isEqualTo("amount BETWEEN 1.0 AND 5.0");
        assertThat(ListMetastorePartitionsRequest.parseFrom(listRequest.toByteString())).isEqualTo(listRequest);
    }

    @Test
    void handlesStreamingMessagesAndStructuredBatchSizeError() throws Exception {
        MetastorePartition firstPartition = samplePartition("2025", "08");
        MetastorePartition secondPartition = samplePartition("2025", "09");
        StreamMetastorePartitionsRequest streamRequest = StreamMetastorePartitionsRequest.newBuilder()
                        .setParent(TABLE_WITH_LOCATION)
                        .addMetastorePartitions(firstPartition)
                        .addMetastorePartitions(secondPartition)
                        .setSkipExistingPartitions(true)
                        .build();
        StreamMetastorePartitionsResponse streamResponse = StreamMetastorePartitionsResponse.newBuilder()
                        .setTotalPartitionsStreamedCount(2L)
                        .setTotalPartitionsInsertedCount(1L)
                        .build();
        BatchSizeTooLargeError error = BatchSizeTooLargeError.newBuilder()
                        .setMaxBatchSize(900L)
                        .setErrorMessage("too many metastore partitions")
                        .build();

        assertThat(streamRequest.getParent()).isEqualTo(TABLE_WITH_LOCATION);
        assertThat(streamRequest.getMetastorePartitionsList()).containsExactly(firstPartition, secondPartition);
        assertThat(streamRequest.getSkipExistingPartitions()).isTrue();
        assertThat(StreamMetastorePartitionsRequest.parseFrom(streamRequest.toByteArray())).isEqualTo(streamRequest);
        assertThat(streamResponse.getTotalPartitionsStreamedCount()).isEqualTo(2L);
        assertThat(streamResponse.getTotalPartitionsInsertedCount()).isEqualTo(1L);
        assertThat(StreamMetastorePartitionsResponse.parseFrom(streamResponse.toByteString()))
                        .isEqualTo(streamResponse);
        assertThat(error.getMaxBatchSize()).isEqualTo(900L);
        assertThat(error.getErrorMessage()).isEqualTo("too many metastore partitions");
        assertThat(BatchSizeTooLargeError.parseFrom(error.toByteString())).isEqualTo(error);
    }

    @Test
    void exposesServiceHttpAndResourceAnnotations() {
        Descriptors.FileDescriptor serviceDescriptor = MetastorePartitionServiceProto.getDescriptor();
        Descriptors.ServiceDescriptor service = serviceDescriptor.findServiceByName("MetastorePartitionService");

        assertThat(service.getOptions().getExtension(ClientProto.defaultHost))
                        .isEqualTo("bigquerystorage.googleapis.com");
        assertThat(service.getOptions().getExtension(ClientProto.oauthScopes))
                        .contains("https://www.googleapis.com/auth/bigquery")
                        .contains("https://www.googleapis.com/auth/cloud-platform");

        HttpRule createRule = service.findMethodByName("BatchCreateMetastorePartitions")
                        .getOptions()
                        .getExtension(AnnotationsProto.http);
        HttpRule deleteRule = service.findMethodByName("BatchDeleteMetastorePartitions")
                        .getOptions()
                        .getExtension(AnnotationsProto.http);
        HttpRule updateRule = service.findMethodByName("BatchUpdateMetastorePartitions")
                        .getOptions()
                        .getExtension(AnnotationsProto.http);
        HttpRule listRule = service.findMethodByName("ListMetastorePartitions")
                        .getOptions()
                        .getExtension(AnnotationsProto.http);

        assertThat(createRule.getPost())
                        .isEqualTo("/v1alpha/{parent=projects/*/datasets/*/tables/*}/partitions:batchCreate");
        assertThat(createRule.getBody()).isEqualTo("*");
        assertThat(deleteRule.getPost())
                        .isEqualTo("/v1alpha/{parent=projects/*/datasets/*/tables/*}/partitions:batchDelete");
        assertThat(deleteRule.getBody()).isEqualTo("*");
        assertThat(updateRule.getPost())
                        .isEqualTo("/v1alpha/{parent=projects/*/datasets/*/tables/*}/partitions:batchUpdate");
        assertThat(updateRule.getBody()).isEqualTo("*");
        assertThat(listRule.getGet())
                        .isEqualTo("/v1alpha/{parent=projects/*/locations/*/datasets/*/tables/*}/partitions:list");
        assertThat(service.findMethodByName("ListMetastorePartitions").getOptions()
                        .getExtension(ClientProto.methodSignature))
                        .containsExactly("parent");
        assertThat(service.findMethodByName("StreamMetastorePartitions").getOptions()
                        .hasExtension(AnnotationsProto.http))
                        .isFalse();

        List<ResourceDescriptor> resourceDefinitions = serviceDescriptor.getOptions()
                        .getExtension(ResourceProto.resourceDefinition);
        ResourceDescriptor readStreamResource = ReadStream.getDescriptor().getOptions()
                        .getExtension(ResourceProto.resource);

        assertThat(resourceDefinitions).extracting(ResourceDescriptor::getType)
                        .contains("bigquery.googleapis.com/Table");
        assertThat(resourceDefinitions.get(0).getPatternList())
                        .contains("projects/{project}/datasets/{dataset}/tables/{table}");
        assertThat(readStreamResource.getType()).isEqualTo("bigquerystorage.googleapis.com/ReadStream");
        assertThat(readStreamResource.getPatternList())
                        .contains("projects/{project}/locations/{location}/sessions/{session}/streams/{stream}");
    }

    @Test
    void exposesFieldBehaviorAndResourceReferenceAnnotations() {
        Descriptors.FileDescriptor partitionDescriptor = MetastorePartitionProto.getDescriptor();
        Descriptors.Descriptor partition = partitionDescriptor.findMessageTypeByName("MetastorePartition");
        Descriptors.Descriptor storageDescriptor = partitionDescriptor.findMessageTypeByName("StorageDescriptor");
        Descriptors.Descriptor serdeInfo = partitionDescriptor.findMessageTypeByName("SerDeInfo");
        Descriptors.Descriptor readStream = partitionDescriptor.findMessageTypeByName("ReadStream");
        Descriptors.FileDescriptor serviceDescriptor = MetastorePartitionServiceProto.getDescriptor();
        Descriptors.Descriptor createRequest = serviceDescriptor.findMessageTypeByName("CreateMetastorePartitionRequest");
        Descriptors.Descriptor batchCreateRequest =
                        serviceDescriptor.findMessageTypeByName("BatchCreateMetastorePartitionsRequest");
        Descriptors.Descriptor listRequest = serviceDescriptor.findMessageTypeByName("ListMetastorePartitionsRequest");

        assertThat(fieldBehaviors(partition, "values")).containsExactly(FieldBehavior.REQUIRED);
        assertThat(fieldBehaviors(partition, "create_time")).containsExactly(FieldBehavior.OUTPUT_ONLY);
        assertThat(fieldBehaviors(partition, "storage_descriptor")).containsExactly(FieldBehavior.OPTIONAL);
        assertThat(fieldBehaviors(partition, "parameters")).containsExactly(FieldBehavior.OPTIONAL);
        assertThat(fieldBehaviors(partition, "fields")).containsExactly(FieldBehavior.OPTIONAL);
        assertThat(fieldBehaviors(storageDescriptor, "serde_info")).containsExactly(FieldBehavior.OPTIONAL);
        assertThat(fieldBehaviors(serdeInfo, "serialization_library")).containsExactly(FieldBehavior.REQUIRED);
        assertThat(fieldBehaviors(readStream, "name"))
                        .containsExactly(FieldBehavior.OUTPUT_ONLY, FieldBehavior.IDENTIFIER);

        Descriptors.FieldDescriptor createParent = createRequest.findFieldByName("parent");
        Descriptors.FieldDescriptor batchCreateParent = batchCreateRequest.findFieldByName("parent");
        Descriptors.FieldDescriptor listParent = listRequest.findFieldByName("parent");
        ResourceReference createParentReference = createParent.getOptions().getExtension(ResourceProto.resourceReference);
        ResourceReference batchCreateParentReference =
                        batchCreateParent.getOptions().getExtension(ResourceProto.resourceReference);
        ResourceReference listParentReference = listParent.getOptions().getExtension(ResourceProto.resourceReference);

        assertThat(createParent.getOptions().getExtension(FieldBehaviorProto.fieldBehavior))
                        .containsExactly(FieldBehavior.REQUIRED);
        assertThat(batchCreateParent.getOptions().getExtension(FieldBehaviorProto.fieldBehavior))
                        .containsExactly(FieldBehavior.REQUIRED);
        assertThat(listParent.getOptions().getExtension(FieldBehaviorProto.fieldBehavior))
                        .containsExactly(FieldBehavior.REQUIRED);
        assertThat(fieldBehaviors(listRequest, "filter")).containsExactly(FieldBehavior.OPTIONAL);
        assertThat(fieldBehaviors(listRequest, "trace_id")).containsExactly(FieldBehavior.OPTIONAL);
        assertThat(createParentReference.getType()).isEqualTo("bigquery.googleapis.com/Table");
        assertThat(batchCreateParentReference.getType()).isEqualTo("bigquery.googleapis.com/Table");
        assertThat(listParentReference.getType()).isEqualTo("bigquery.googleapis.com/Table");
    }

    @Test
    void exposesResourceNameHelpersAndProtoDescriptors() {
        TableName tableName = TableName.of("project-one", "dataset_one", "table_one");
        String formatted = TableName.format("project-one", "dataset_one", "table_one");
        List<TableName> parsedTables = TableName.parseList(Arrays.asList(formatted, TABLE));

        assertThat(formatted).isEqualTo(TABLE);
        assertThat(TableName.isParsableFrom(TABLE)).isTrue();
        assertThat(TableName.parse(TABLE)).isEqualTo(tableName);
        assertThat(tableName.getProject()).isEqualTo("project-one");
        assertThat(tableName.getDataset()).isEqualTo("dataset_one");
        assertThat(tableName.getTable()).isEqualTo("table_one");
        assertThat(tableName.getFieldValuesMap())
                        .containsEntry("project", "project-one")
                        .containsEntry("dataset", "dataset_one")
                        .containsEntry("table", "table_one");
        assertThat(TableName.toStringList(parsedTables)).containsExactly(TABLE, TABLE);
        assertThat(TableName.newBuilder().setProject("p").setDataset("d").setTable("t").build().toString())
                        .isEqualTo("projects/p/datasets/d/tables/t");
        assertThatThrownBy(() -> TableName.parse("projects/project-one/tables/table_one"))
                        .isInstanceOf(IllegalArgumentException.class);

        Descriptors.FileDescriptor partitionDescriptor = MetastorePartitionProto.getDescriptor();
        Descriptors.FileDescriptor serviceDescriptor = MetastorePartitionServiceProto.getDescriptor();
        Descriptors.ServiceDescriptor service = serviceDescriptor.findServiceByName("MetastorePartitionService");

        Descriptors.Descriptor metastorePartitionDescriptor =
                        partitionDescriptor.findMessageTypeByName("MetastorePartition");
        assertThat(metastorePartitionDescriptor.findFieldByName("values").isRepeated()).isTrue();
        assertThat(partitionDescriptor.findMessageTypeByName("SerDeInfo").findFieldByName("parameters").isMapField())
                        .isTrue();
        assertThat(service.getMethods()).extracting(Descriptors.MethodDescriptor::getName)
                        .containsExactly(
                                        "BatchCreateMetastorePartitions",
                                        "BatchDeleteMetastorePartitions",
                                        "BatchUpdateMetastorePartitions",
                                        "ListMetastorePartitions",
                                        "StreamMetastorePartitions");
        assertThat(service.findMethodByName("StreamMetastorePartitions").isClientStreaming()).isTrue();
        assertThat(service.findMethodByName("StreamMetastorePartitions").isServerStreaming()).isTrue();
        assertThat(service.findMethodByName("ListMetastorePartitions").isServerStreaming()).isFalse();

        ExtensionRegistry registry = ExtensionRegistry.newInstance();
        MetastorePartitionProto.registerAllExtensions(registry);
        MetastorePartitionServiceProto.registerAllExtensions(registry);
        assertThat(registry.getUnmodifiable()).isNotNull();
    }

    private static List<FieldBehavior> fieldBehaviors(Descriptors.Descriptor descriptor, String fieldName) {
        return descriptor.findFieldByName(fieldName).getOptions().getExtension(FieldBehaviorProto.fieldBehavior);
    }

    private static MetastorePartition samplePartition(String year, String month) {
        SerDeInfo serDeInfo = SerDeInfo.newBuilder()
                        .setName("orc-serde")
                        .setSerializationLibrary("org.apache.hadoop.hive.ql.io.orc.OrcSerde")
                        .putParameters("serialization.format", "1")
                        .putAllParameters(Map.of("field.delim", "|"))
                        .build();
        StorageDescriptor storageDescriptor = StorageDescriptor.newBuilder()
                        .setLocationUri("gs://example-bucket/table/dt=" + year + "/month=" + month)
                        .setInputFormat("org.apache.hadoop.hive.ql.io.orc.OrcInputFormat")
                        .setOutputFormat("org.apache.hadoop.hive.ql.io.orc.OrcOutputFormat")
                        .setSerdeInfo(serDeInfo)
                        .build();
        Timestamp createTime = Timestamp.newBuilder()
                        .setSeconds(1_746_057_600L)
                        .setNanos(123_000_000)
                        .build();

        return MetastorePartition.newBuilder()
                        .addValues(year)
                        .addValuesBytes(ByteString.copyFromUtf8(month))
                        .setCreateTime(createTime)
                        .setStorageDescriptor(storageDescriptor)
                        .putParameters("source", "unit-test")
                        .addFields(FieldSchema.newBuilder().setName("customer_id").setType("string"))
                        .addFields(FieldSchema.newBuilder().setName("amount").setType("decimal(10,2)"))
                        .build();
    }

}
