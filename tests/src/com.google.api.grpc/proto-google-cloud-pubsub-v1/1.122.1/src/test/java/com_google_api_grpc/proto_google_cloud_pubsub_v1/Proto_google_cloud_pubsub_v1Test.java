/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_api_grpc.proto_google_cloud_pubsub_v1;

import java.nio.ByteBuffer;
import java.util.List;

import com.google.protobuf.ByteString;
import com.google.protobuf.Duration;
import com.google.protobuf.Timestamp;
import com.google.pubsub.v1.AcknowledgeRequest;
import com.google.pubsub.v1.BigQueryConfig;
import com.google.pubsub.v1.CloudStorageConfig;
import com.google.pubsub.v1.CommitSchemaRequest;
import com.google.pubsub.v1.CreateSchemaRequest;
import com.google.pubsub.v1.DeadLetterPolicy;
import com.google.pubsub.v1.DeleteSchemaRevisionRequest;
import com.google.pubsub.v1.Encoding;
import com.google.pubsub.v1.ExpirationPolicy;
import com.google.pubsub.v1.IngestionDataSourceSettings;
import com.google.pubsub.v1.JavaScriptUDF;
import com.google.pubsub.v1.MessageStoragePolicy;
import com.google.pubsub.v1.MessageTransform;
import com.google.pubsub.v1.ProjectSnapshotName;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.ProjectTopicName;
import com.google.pubsub.v1.PublishRequest;
import com.google.pubsub.v1.PublishResponse;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.PullRequest;
import com.google.pubsub.v1.PullResponse;
import com.google.pubsub.v1.PushConfig;
import com.google.pubsub.v1.ReceivedMessage;
import com.google.pubsub.v1.RetryPolicy;
import com.google.pubsub.v1.RollbackSchemaRequest;
import com.google.pubsub.v1.Schema;
import com.google.pubsub.v1.SchemaName;
import com.google.pubsub.v1.SchemaSettings;
import com.google.pubsub.v1.SnapshotName;
import com.google.pubsub.v1.Subscription;
import com.google.pubsub.v1.SubscriptionName;
import com.google.pubsub.v1.Topic;
import com.google.pubsub.v1.TopicName;
import com.google.pubsub.v1.TopicNames;
import com.google.pubsub.v1.ValidateMessageRequest;
import com.google.pubsub.v1.ValidateSchemaRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Proto_google_cloud_pubsub_v1Test {
    private static final String PROJECT_ID = "sample-project";
    private static final String TOPIC_ID = "orders";
    private static final String SUBSCRIPTION_ID = "orders-worker";
    private static final String SNAPSHOT_ID = "orders-snapshot";
    private static final String SCHEMA_ID = "order-schema";

    private static final String PROJECT_PATH = "projects/" + PROJECT_ID;
    private static final String TOPIC_PATH = PROJECT_PATH + "/topics/" + TOPIC_ID;
    private static final String SUBSCRIPTION_PATH = PROJECT_PATH + "/subscriptions/" + SUBSCRIPTION_ID;
    private static final String SNAPSHOT_PATH = PROJECT_PATH + "/snapshots/" + SNAPSHOT_ID;
    private static final String SCHEMA_PATH = PROJECT_PATH + "/schemas/" + SCHEMA_ID;

    @Test
    void resourceNamesRoundTripAndExposeFieldValues() {
        ProjectTopicName projectTopicName = ProjectTopicName.of(PROJECT_ID, TOPIC_ID);
        TopicName topicName = TopicName.ofProjectTopicName(PROJECT_ID, TOPIC_ID);
        ProjectSubscriptionName projectSubscriptionName = ProjectSubscriptionName.of(PROJECT_ID, SUBSCRIPTION_ID);
        SubscriptionName subscriptionName = SubscriptionName.of(PROJECT_ID, SUBSCRIPTION_ID);
        ProjectSnapshotName projectSnapshotName = ProjectSnapshotName.of(PROJECT_ID, SNAPSHOT_ID);
        SnapshotName snapshotName = SnapshotName.of(PROJECT_ID, SNAPSHOT_ID);
        SchemaName schemaName = SchemaName.of(PROJECT_ID, SCHEMA_ID);

        assertThat(projectTopicName.toString()).isEqualTo(TOPIC_PATH);
        assertThat(ProjectTopicName.parse(TOPIC_PATH)).isEqualTo(projectTopicName);
        TopicName parsedTopicName = TopicName.parse(TOPIC_PATH);
        TopicName parsedViaDispatcher = TopicNames.parse(TOPIC_PATH);
        assertThat(parsedTopicName.toString()).isEqualTo(TOPIC_PATH);
        assertThat(parsedTopicName.getProject()).isEqualTo(PROJECT_ID);
        assertThat(parsedTopicName.getTopic()).isEqualTo(TOPIC_ID);
        assertThat(parsedViaDispatcher.toString()).isEqualTo(TOPIC_PATH);
        assertThat(parsedViaDispatcher.getProject()).isEqualTo(PROJECT_ID);
        assertThat(parsedViaDispatcher.getTopic()).isEqualTo(TOPIC_ID);
        assertThat(TopicName.toStringList(TopicName.parseList(List.of(TOPIC_PATH))))
                .containsExactly(TOPIC_PATH);
        assertThat(topicName.getFieldValuesMap())
                .containsEntry("project", PROJECT_ID)
                .containsEntry("topic", TOPIC_ID);
        assertThat(topicName.getFieldValue("topic")).isEqualTo(TOPIC_ID);
        assertThat(topicName.toBuilder().build()).isEqualTo(topicName);
        assertThat(TopicName.isParsableFrom(TopicName.formatDeletedTopicName())).isTrue();
        assertThat(TopicName.ofDeletedTopicName().toString()).isEqualTo(TopicName.formatDeletedTopicName());

        assertThat(ProjectSubscriptionName.parse(SUBSCRIPTION_PATH).getSubscription()).isEqualTo(SUBSCRIPTION_ID);
        assertThat(ProjectSubscriptionName.toStringList(ProjectSubscriptionName.parseList(List.of(SUBSCRIPTION_PATH))))
                .containsExactly(SUBSCRIPTION_PATH);
        assertThat(subscriptionName.toString()).isEqualTo(SUBSCRIPTION_PATH);
        assertThat(SubscriptionName.parse(SUBSCRIPTION_PATH).getProject()).isEqualTo(PROJECT_ID);

        assertThat(projectSnapshotName.toString()).isEqualTo(SNAPSHOT_PATH);
        assertThat(ProjectSnapshotName.parse(SNAPSHOT_PATH).toBuilder().build()).isEqualTo(projectSnapshotName);
        assertThat(snapshotName.toString()).isEqualTo(SNAPSHOT_PATH);
        assertThat(SnapshotName.parseList(List.of(SNAPSHOT_PATH))).containsExactly(snapshotName);

        assertThat(schemaName.toString()).isEqualTo(SCHEMA_PATH);
        assertThat(SchemaName.parse(SCHEMA_PATH).getSchema()).isEqualTo(SCHEMA_ID);
        assertThat(SchemaName.toStringList(List.of(schemaName))).containsExactly(SCHEMA_PATH);
    }

    @Test
    void publishPullAndAckMessagesPreservePayloadAttributesAndDeliveryState() throws Exception {
        Timestamp publishTime = timestamp(1_700_000_000L, 123_000_000);
        PubsubMessage message = PubsubMessage.newBuilder()
                .setData(ByteString.copyFromUtf8("{\"orderId\":\"A-100\"}"))
                .putAttributes("content-type", "application/json")
                .putAttributes("tenant", "retail")
                .setMessageId("message-1")
                .setPublishTime(publishTime)
                .setOrderingKey("customer-42")
                .build();

        PubsubMessage parsedMessage = PubsubMessage.parseFrom(ByteBuffer.wrap(message.toByteArray()));
        assertThat(parsedMessage.getData().toStringUtf8()).contains("A-100");
        assertThat(parsedMessage.getAttributesMap())
                .containsEntry("content-type", "application/json")
                .containsEntry("tenant", "retail");
        assertThat(parsedMessage.getAttributesOrThrow("tenant")).isEqualTo("retail");
        assertThat(parsedMessage.hasPublishTime()).isTrue();
        assertThat(parsedMessage.getPublishTime()).isEqualTo(publishTime);
        assertThat(parsedMessage.getOrderingKey()).isEqualTo("customer-42");

        PublishRequest publishRequest = PublishRequest.newBuilder()
                .setTopic(TOPIC_PATH)
                .addMessages(message)
                .addMessages(PubsubMessage.newBuilder()
                        .setData(ByteString.copyFromUtf8("second"))
                        .putAttributes("content-type", "text/plain"))
                .build();
        PublishResponse publishResponse = PublishResponse.newBuilder()
                .addMessageIds("message-1")
                .addMessageIds("message-2")
                .build();
        ReceivedMessage receivedMessage = ReceivedMessage.newBuilder()
                .setAckId("ack-1")
                .setMessage(message)
                .setDeliveryAttempt(3)
                .build();
        PullResponse pullResponse = PullResponse.newBuilder()
                .addReceivedMessages(receivedMessage)
                .build();
        PullRequest pullRequest = PullRequest.newBuilder()
                .setSubscription(SUBSCRIPTION_PATH)
                .setMaxMessages(10)
                .build();
        AcknowledgeRequest acknowledgeRequest = AcknowledgeRequest.newBuilder()
                .setSubscription(SUBSCRIPTION_PATH)
                .addAckIds(receivedMessage.getAckId())
                .addAckIds("ack-2")
                .build();

        assertThat(publishRequest.getTopic()).isEqualTo(TOPIC_PATH);
        assertThat(publishRequest.getMessagesCount()).isEqualTo(2);
        assertThat(publishResponse.getMessageIdsList()).containsExactly("message-1", "message-2");
        assertThat(pullRequest.getSubscription()).isEqualTo(SUBSCRIPTION_PATH);
        assertThat(pullRequest.getMaxMessages()).isEqualTo(10);
        assertThat(pullResponse.getReceivedMessages(0).getMessage()).isEqualTo(message);
        assertThat(pullResponse.getReceivedMessages(0).getDeliveryAttempt()).isEqualTo(3);
        assertThat(acknowledgeRequest.getAckIdsList()).containsExactly("ack-1", "ack-2");
    }

    @Test
    void topicsSubscriptionsPushAndExportConfigurationsComposeNestedMessages() throws Exception {
        SchemaSettings schemaSettings = SchemaSettings.newBuilder()
                .setSchema(SCHEMA_PATH)
                .setEncoding(Encoding.JSON)
                .setFirstRevisionId("rev-1")
                .setLastRevisionId("rev-3")
                .build();
        MessageStoragePolicy storagePolicy = MessageStoragePolicy.newBuilder()
                .addAllowedPersistenceRegions("us-central1")
                .addAllowedPersistenceRegions("europe-west1")
                .setEnforceInTransit(true)
                .build();
        MessageTransform transform = MessageTransform.newBuilder()
                .setJavascriptUdf(JavaScriptUDF.newBuilder()
                        .setFunctionName("redact")
                        .setCode("function redact(message) { return message; }"))
                .setEnabled(true)
                .build();
        IngestionDataSourceSettings ingestionSettings = IngestionDataSourceSettings.newBuilder()
                .setCloudStorage(IngestionDataSourceSettings.CloudStorage.newBuilder()
                        .setBucket("incoming-orders")
                        .setMatchGlob("**/*.json")
                        .setTextFormat(IngestionDataSourceSettings.CloudStorage.TextFormat.newBuilder()
                                .setDelimiter("\n"))
                        .setMinimumObjectCreateTime(timestamp(1_700_000_100L, 0))
                        .setState(IngestionDataSourceSettings.CloudStorage.State.ACTIVE))
                .build();
        Topic topic = Topic.newBuilder()
                .setName(TOPIC_PATH)
                .putLabels("env", "test")
                .setMessageStoragePolicy(storagePolicy)
                .setSchemaSettings(schemaSettings)
                .setMessageRetentionDuration(duration(604_800))
                .setKmsKeyName("projects/sample-project/locations/global/keyRings/pubsub/cryptoKeys/orders")
                .setState(Topic.State.ACTIVE)
                .setIngestionDataSourceSettings(ingestionSettings)
                .addMessageTransforms(transform)
                .build();

        PushConfig pushConfig = PushConfig.newBuilder()
                .setPushEndpoint("https://example.test/push")
                .putAttributes("x-goog-version", "v1")
                .setOidcToken(PushConfig.OidcToken.newBuilder()
                        .setServiceAccountEmail("pubsub-push@example.iam.gserviceaccount.com")
                        .setAudience("https://example.test"))
                .setNoWrapper(PushConfig.NoWrapper.newBuilder().setWriteMetadata(true))
                .build();
        BigQueryConfig bigQueryConfig = BigQueryConfig.newBuilder()
                .setTable("sample-project:analytics.orders")
                .setUseTopicSchema(true)
                .setWriteMetadata(true)
                .setDropUnknownFields(true)
                .setState(BigQueryConfig.State.ACTIVE)
                .setServiceAccountEmail("pubsub-bq@example.iam.gserviceaccount.com")
                .build();
        CloudStorageConfig cloudStorageConfig = CloudStorageConfig.newBuilder()
                .setBucket("processed-orders")
                .setFilenamePrefix("orders/")
                .setFilenameSuffix(".avro")
                .setFilenameDatetimeFormat("YYYY/MM/DD/HH")
                .setAvroConfig(CloudStorageConfig.AvroConfig.newBuilder()
                        .setWriteMetadata(true)
                        .setUseTopicSchema(true))
                .setMaxDuration(duration(300))
                .setMaxBytes(10_000_000L)
                .setMaxMessages(1_000L)
                .setState(CloudStorageConfig.State.ACTIVE)
                .setServiceAccountEmail("pubsub-gcs@example.iam.gserviceaccount.com")
                .build();
        Subscription subscription = Subscription.newBuilder()
                .setName(SUBSCRIPTION_PATH)
                .setTopic(TOPIC_PATH)
                .setPushConfig(pushConfig)
                .setBigqueryConfig(bigQueryConfig)
                .setCloudStorageConfig(cloudStorageConfig)
                .setAckDeadlineSeconds(30)
                .setRetainAckedMessages(true)
                .setMessageRetentionDuration(duration(86_400))
                .putLabels("component", "worker")
                .setEnableMessageOrdering(true)
                .setExpirationPolicy(ExpirationPolicy.newBuilder().setTtl(duration(2_592_000)))
                .setFilter("attributes.tenant = \"retail\"")
                .setDeadLetterPolicy(DeadLetterPolicy.newBuilder()
                        .setDeadLetterTopic(PROJECT_PATH + "/topics/orders-dead-letter")
                        .setMaxDeliveryAttempts(5))
                .setRetryPolicy(RetryPolicy.newBuilder()
                        .setMinimumBackoff(duration(10))
                        .setMaximumBackoff(duration(600)))
                .setEnableExactlyOnceDelivery(true)
                .setState(Subscription.State.ACTIVE)
                .addMessageTransforms(transform)
                .build();

        Topic parsedTopic = Topic.parseFrom(topic.toByteString());
        Subscription parsedSubscription = Subscription.parseFrom(subscription.toByteArray());

        assertThat(parsedTopic.getLabelsMap()).containsEntry("env", "test");
        assertThat(parsedTopic.getSchemaSettings().getEncoding()).isEqualTo(Encoding.JSON);
        assertThat(parsedTopic.getMessageStoragePolicy().getAllowedPersistenceRegionsList())
                .containsExactly("us-central1", "europe-west1");
        assertThat(parsedTopic.getIngestionDataSourceSettings().getSourceCase())
                .isEqualTo(IngestionDataSourceSettings.SourceCase.CLOUD_STORAGE);
        assertThat(parsedTopic.getIngestionDataSourceSettings().getCloudStorage().getTextFormat().getDelimiter())
                .isEqualTo("\n");
        assertThat(parsedTopic.getMessageTransforms(0).getJavascriptUdf().getFunctionName()).isEqualTo("redact");

        assertThat(parsedSubscription.getPushConfig().getAuthenticationMethodCase())
                .isEqualTo(PushConfig.AuthenticationMethodCase.OIDC_TOKEN);
        assertThat(parsedSubscription.getPushConfig().getWrapperCase()).isEqualTo(PushConfig.WrapperCase.NO_WRAPPER);
        assertThat(parsedSubscription.getBigqueryConfig().getTable()).isEqualTo("sample-project:analytics.orders");
        assertThat(parsedSubscription.getCloudStorageConfig().getOutputFormatCase())
                .isEqualTo(CloudStorageConfig.OutputFormatCase.AVRO_CONFIG);
        assertThat(parsedSubscription.getDeadLetterPolicy().getMaxDeliveryAttempts()).isEqualTo(5);
        assertThat(parsedSubscription.getRetryPolicy().getMaximumBackoff()).isEqualTo(duration(600));
        assertThat(parsedSubscription.getFilter()).contains("tenant");
    }

    @Test
    void schemaLifecycleRequestsCarrySchemaDefinitionsRevisionsAndValidationPayloads() {
        Schema schema = Schema.newBuilder()
                .setName(SCHEMA_PATH)
                .setType(Schema.Type.PROTOCOL_BUFFER)
                .setDefinition("message Order { string id = 1; double total = 2; }")
                .setRevisionId("rev-3")
                .setRevisionCreateTime(timestamp(1_700_000_500L, 0))
                .build();
        CreateSchemaRequest createRequest = CreateSchemaRequest.newBuilder()
                .setParent(PROJECT_PATH)
                .setSchema(schema)
                .setSchemaId(SCHEMA_ID)
                .build();
        ValidateSchemaRequest validateSchemaRequest = ValidateSchemaRequest.newBuilder()
                .setParent(PROJECT_PATH)
                .setSchema(schema)
                .build();
        ValidateMessageRequest validateMessageBySchema = ValidateMessageRequest.newBuilder()
                .setParent(PROJECT_PATH)
                .setSchema(schema)
                .setMessage(ByteString.copyFromUtf8("{\"id\":\"A-100\",\"total\":42.5}"))
                .setEncoding(Encoding.JSON)
                .build();
        ValidateMessageRequest validateMessageByName = validateMessageBySchema.toBuilder()
                .clearSchema()
                .setName(SCHEMA_PATH)
                .build();
        CommitSchemaRequest commitRequest = CommitSchemaRequest.newBuilder()
                .setName(SCHEMA_PATH)
                .setSchema(schema.toBuilder().setRevisionId("rev-4"))
                .build();
        RollbackSchemaRequest rollbackRequest = RollbackSchemaRequest.newBuilder()
                .setName(SCHEMA_PATH)
                .setRevisionId("rev-2")
                .build();
        DeleteSchemaRevisionRequest deleteRevisionRequest = DeleteSchemaRevisionRequest.newBuilder()
                .setName(SCHEMA_PATH)
                .setRevisionId("rev-1")
                .build();

        assertThat(createRequest.hasSchema()).isTrue();
        assertThat(createRequest.getSchema().getType()).isEqualTo(Schema.Type.PROTOCOL_BUFFER);
        assertThat(createRequest.getSchemaId()).isEqualTo(SCHEMA_ID);
        assertThat(validateSchemaRequest.getSchema().getDefinition()).contains("message Order");
        assertThat(validateMessageBySchema.getSchemaSpecCase()).isEqualTo(ValidateMessageRequest.SchemaSpecCase.SCHEMA);
        assertThat(validateMessageBySchema.getEncoding()).isEqualTo(Encoding.JSON);
        assertThat(validateMessageByName.getSchemaSpecCase()).isEqualTo(ValidateMessageRequest.SchemaSpecCase.NAME);
        assertThat(validateMessageByName.getName()).isEqualTo(SCHEMA_PATH);
        assertThat(commitRequest.getSchema().getRevisionId()).isEqualTo("rev-4");
        assertThat(rollbackRequest.getRevisionId()).isEqualTo("rev-2");
        assertThat(deleteRevisionRequest.getRevisionId()).isEqualTo("rev-1");
    }

    private static Duration duration(long seconds) {
        return Duration.newBuilder().setSeconds(seconds).build();
    }

    private static Timestamp timestamp(long seconds, int nanos) {
        return Timestamp.newBuilder().setSeconds(seconds).setNanos(nanos).build();
    }
}
