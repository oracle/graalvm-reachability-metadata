/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_cloud.google_cloud_pubsub;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutures;
import com.google.api.gax.batching.BatchingSettings;
import com.google.api.gax.batching.FlowControlSettings;
import com.google.api.gax.core.BackgroundResource;
import com.google.api.gax.core.FixedExecutorProvider;
import com.google.api.gax.core.InstantiatingExecutorProvider;
import com.google.api.gax.core.NoCredentialsProvider;
import com.google.api.gax.retrying.RetrySettings;
import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.AckReplyConsumerWithResponse;
import com.google.cloud.pubsub.v1.AckResponse;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloud.pubsub.v1.MessageReceiverWithAckResponse;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.cloud.pubsub.v1.SchemaServiceClient;
import com.google.cloud.pubsub.v1.SchemaServiceSettings;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.cloud.pubsub.v1.SubscriptionAdminClient;
import com.google.cloud.pubsub.v1.SubscriptionAdminSettings;
import com.google.cloud.pubsub.v1.TopicAdminClient;
import com.google.cloud.pubsub.v1.TopicAdminSettings;
import com.google.protobuf.ByteString;
import com.google.protobuf.FieldMask;
import com.google.protobuf.Timestamp;
import com.google.pubsub.v1.AcknowledgeRequest;
import com.google.pubsub.v1.BigQueryConfig;
import com.google.pubsub.v1.CloudStorageConfig;
import com.google.pubsub.v1.CommitSchemaRequest;
import com.google.pubsub.v1.CreateSchemaRequest;
import com.google.pubsub.v1.CreateSnapshotRequest;
import com.google.pubsub.v1.DeadLetterPolicy;
import com.google.pubsub.v1.DeleteSchemaRevisionRequest;
import com.google.pubsub.v1.Encoding;
import com.google.pubsub.v1.ExpirationPolicy;
import com.google.pubsub.v1.JavaScriptUDF;
import com.google.pubsub.v1.MessageStoragePolicy;
import com.google.pubsub.v1.MessageTransform;
import com.google.pubsub.v1.ModifyAckDeadlineRequest;
import com.google.pubsub.v1.ModifyPushConfigRequest;
import com.google.pubsub.v1.ProjectName;
import com.google.pubsub.v1.ProjectSubscriptionName;
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
import com.google.pubsub.v1.SeekRequest;
import com.google.pubsub.v1.Snapshot;
import com.google.pubsub.v1.SnapshotName;
import com.google.pubsub.v1.Subscription;
import com.google.pubsub.v1.SubscriptionName;
import com.google.pubsub.v1.Topic;
import com.google.pubsub.v1.TopicName;
import com.google.pubsub.v1.UpdateSnapshotRequest;
import com.google.pubsub.v1.UpdateSubscriptionRequest;
import com.google.pubsub.v1.UpdateTopicRequest;
import com.google.pubsub.v1.ValidateMessageRequest;
import com.google.pubsub.v1.ValidateSchemaRequest;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

public class Google_cloud_pubsubTest {
    private static final String PROJECT = "sample-project";
    private static final String TOPIC = "orders-topic";
    private static final String SUBSCRIPTION = "orders-subscription";
    private static final String SNAPSHOT = "orders-snapshot";
    private static final String SCHEMA = "orders-schema";
    private static final String LOCAL_ENDPOINT = "localhost:1";

    @Test
    void resourceNamesAndMessagingRequestsExposeConfiguredValues() {
        ProjectName projectName = ProjectName.of(PROJECT);
        TopicName topicName = TopicName.of(PROJECT, TOPIC);
        SubscriptionName subscriptionName = SubscriptionName.of(PROJECT, SUBSCRIPTION);
        SnapshotName snapshotName = SnapshotName.of(PROJECT, SNAPSHOT);
        SchemaName schemaName = SchemaName.of(PROJECT, SCHEMA);
        PubsubMessage message = PubsubMessage.newBuilder()
                .setData(ByteString.copyFromUtf8("{\"orderId\":\"order-1\"}"))
                .setMessageId("message-1")
                .setOrderingKey("customer-7")
                .putAttributes("eventType", "OrderCreated")
                .putAttributes("trace", "abc123")
                .build();
        ReceivedMessage receivedMessage = ReceivedMessage.newBuilder()
                .setAckId("ack-1")
                .setMessage(message)
                .setDeliveryAttempt(3)
                .build();
        PullResponse pullResponse = PullResponse.newBuilder().addReceivedMessages(receivedMessage).build();

        assertThat(projectName.toString()).isEqualTo("projects/" + PROJECT);
        assertThat(TopicName.parse(topicName.toString())).isEqualTo(topicName);
        assertThat(SubscriptionName.parse(subscriptionName.toString()).getSubscription()).isEqualTo(SUBSCRIPTION);
        assertThat(SnapshotName.parse(snapshotName.toString()).getSnapshot()).isEqualTo(SNAPSHOT);
        assertThat(SchemaName.parse(schemaName.toString()).getSchema()).isEqualTo(SCHEMA);
        assertThat(TopicName.ofDeletedTopicName().toString()).isEqualTo("_deleted-topic_");
        assertThat(TopicName.isParsableFrom(topicName.toString())).isTrue();
        assertThat(message.getData().toStringUtf8()).contains("order-1");
        assertThat(message.getAttributesMap()).containsEntry("eventType", "OrderCreated");
        assertThat(message.getOrderingKey()).isEqualTo("customer-7");
        assertThat(pullResponse.getReceivedMessages(0).getDeliveryAttempt()).isEqualTo(3);

        assertThat(PullRequest.newBuilder()
                .setSubscription(subscriptionName.toString())
                .setMaxMessages(10)
                .build()
                .getMaxMessages()).isEqualTo(10);
        assertThat(AcknowledgeRequest.newBuilder()
                .setSubscription(subscriptionName.toString())
                .addAckIds(receivedMessage.getAckId())
                .build()
                .getAckIdsList()).containsExactly("ack-1");
        assertThat(ModifyAckDeadlineRequest.newBuilder()
                .setSubscription(subscriptionName.toString())
                .addAckIds("ack-1")
                .setAckDeadlineSeconds(45)
                .build()
                .getAckDeadlineSeconds()).isEqualTo(45);
        assertThat(SeekRequest.newBuilder()
                .setSubscription(subscriptionName.toString())
                .setSnapshot(snapshotName.toString())
                .build()
                .getSnapshot()).isEqualTo(snapshotName.toString());
    }

    @Test
    void topicAndSubscriptionModelsSupportSchemasTransformsAndDeliveryConfiguration() {
        TopicName topicName = TopicName.of(PROJECT, TOPIC);
        SubscriptionName subscriptionName = SubscriptionName.of(PROJECT, SUBSCRIPTION);
        SchemaName schemaName = SchemaName.of(PROJECT, SCHEMA);
        MessageTransform transform = MessageTransform.newBuilder()
                .setJavascriptUdf(JavaScriptUDF.newBuilder()
                        .setFunctionName("redactEmail")
                        .setCode("function redactEmail(message) { return message; }")
                        .build())
                .setDisabled(false)
                .build();
        Topic topic = Topic.newBuilder()
                .setName(topicName.toString())
                .putLabels("env", "test")
                .setMessageStoragePolicy(MessageStoragePolicy.newBuilder()
                        .addAllowedPersistenceRegions("us-central1")
                        .addAllowedPersistenceRegions("us-east1")
                        .build())
                .setKmsKeyName("projects/sample-project/locations/us/keyRings/pubsub/cryptoKeys/default")
                .setSchemaSettings(SchemaSettings.newBuilder()
                        .setSchema(schemaName.toString())
                        .setEncoding(Encoding.JSON)
                        .setFirstRevisionId("rev-1")
                        .setLastRevisionId("rev-2")
                        .build())
                .setMessageRetentionDuration(com.google.protobuf.Duration.newBuilder().setSeconds(600).build())
                .addMessageTransforms(transform)
                .build();
        PushConfig pushConfig = PushConfig.newBuilder()
                .setPushEndpoint("https://example.com/pubsub/push")
                .putAttributes("x-goog-version", "v1")
                .setOidcToken(PushConfig.OidcToken.newBuilder()
                        .setServiceAccountEmail("pubsub-push@sample-project.iam.gserviceaccount.com")
                        .setAudience("https://example.com/pubsub/push")
                        .build())
                .setNoWrapper(PushConfig.NoWrapper.newBuilder().setWriteMetadata(true).build())
                .build();
        DeadLetterPolicy deadLetterPolicy = DeadLetterPolicy.newBuilder()
                .setDeadLetterTopic(TopicName.of(PROJECT, "orders-dead-letter").toString())
                .setMaxDeliveryAttempts(5)
                .build();
        RetryPolicy retryPolicy = RetryPolicy.newBuilder()
                .setMinimumBackoff(com.google.protobuf.Duration.newBuilder().setSeconds(10).build())
                .setMaximumBackoff(com.google.protobuf.Duration.newBuilder().setSeconds(120).build())
                .build();
        Subscription subscription = Subscription.newBuilder()
                .setName(subscriptionName.toString())
                .setTopic(topicName.toString())
                .setPushConfig(pushConfig)
                .setAckDeadlineSeconds(30)
                .setRetainAckedMessages(true)
                .setMessageRetentionDuration(com.google.protobuf.Duration.newBuilder().setSeconds(1_200).build())
                .setEnableMessageOrdering(true)
                .setExpirationPolicy(ExpirationPolicy.newBuilder()
                        .setTtl(com.google.protobuf.Duration.newBuilder().setSeconds(86_400).build())
                        .build())
                .setFilter("attributes.eventType = \"OrderCreated\"")
                .setDeadLetterPolicy(deadLetterPolicy)
                .setRetryPolicy(retryPolicy)
                .setEnableExactlyOnceDelivery(true)
                .addMessageTransforms(transform)
                .build();
        BigQueryConfig bigQueryConfig = BigQueryConfig.newBuilder()
                .setTable("sample-project.analytics.orders")
                .setUseTopicSchema(true)
                .setWriteMetadata(true)
                .setDropUnknownFields(true)
                .setServiceAccountEmail("pubsub-writer@sample-project.iam.gserviceaccount.com")
                .build();
        CloudStorageConfig cloudStorageConfig = CloudStorageConfig.newBuilder()
                .setBucket("orders-bucket")
                .setFilenamePrefix("pubsub/orders/")
                .setFilenameSuffix(".json")
                .setTextConfig(CloudStorageConfig.TextConfig.getDefaultInstance())
                .setMaxDuration(com.google.protobuf.Duration.newBuilder().setSeconds(300).build())
                .setMaxBytes(1_048_576L)
                .setMaxMessages(1_000L)
                .build();

        assertThat(topic.getLabelsMap()).containsEntry("env", "test");
        assertThat(topic.getMessageStoragePolicy().getAllowedPersistenceRegionsList())
                .containsExactly("us-central1", "us-east1");
        assertThat(topic.getSchemaSettings().getEncoding()).isEqualTo(Encoding.JSON);
        assertThat(topic.getMessageRetentionDuration().getSeconds()).isEqualTo(600L);
        assertThat(topic.getMessageTransforms(0).getJavascriptUdf().getFunctionName()).isEqualTo("redactEmail");
        assertThat(subscription.getPushConfig().getOidcToken().getServiceAccountEmail())
                .startsWith("pubsub-push@");
        assertThat(subscription.getPushConfig().getNoWrapper().getWriteMetadata()).isTrue();
        assertThat(subscription.getDeadLetterPolicy().getMaxDeliveryAttempts()).isEqualTo(5);
        assertThat(subscription.getRetryPolicy().getMaximumBackoff().getSeconds()).isEqualTo(120L);
        assertThat(subscription.getEnableExactlyOnceDelivery()).isTrue();
        assertThat(subscription.toBuilder().setAckDeadlineSeconds(40).build().getAckDeadlineSeconds()).isEqualTo(40);
        assertThat(bigQueryConfig.getTable()).endsWith("analytics.orders");
        assertThat(bigQueryConfig.getUseTopicSchema()).isTrue();
        assertThat(cloudStorageConfig.getBucket()).isEqualTo("orders-bucket");
        assertThat(cloudStorageConfig.getMaxMessages()).isEqualTo(1_000L);
    }

    @Test
    void schemaSnapshotAndUpdateRequestsPreserveLifecycleFields() {
        ProjectName projectName = ProjectName.of(PROJECT);
        TopicName topicName = TopicName.of(PROJECT, TOPIC);
        SubscriptionName subscriptionName = SubscriptionName.of(PROJECT, SUBSCRIPTION);
        SnapshotName snapshotName = SnapshotName.of(PROJECT, SNAPSHOT);
        SchemaName schemaName = SchemaName.of(PROJECT, SCHEMA);
        Timestamp revisionTime = Timestamp.newBuilder().setSeconds(1_700_000_000L).setNanos(123_000_000).build();
        Schema schema = Schema.newBuilder()
                .setName(schemaName.toString())
                .setType(Schema.Type.PROTOCOL_BUFFER)
                .setDefinition("syntax = \"proto3\"; message Order { string id = 1; }")
                .setRevisionId("rev-1")
                .setRevisionCreateTime(revisionTime)
                .build();
        Snapshot snapshot = Snapshot.newBuilder()
                .setName(snapshotName.toString())
                .setTopic(topicName.toString())
                .setExpireTime(Timestamp.newBuilder().setSeconds(1_800_000_000L).build())
                .putLabels("purpose", "integration-test")
                .build();

        assertThat(CreateSchemaRequest.newBuilder()
                .setParent(projectName.toString())
                .setSchema(schema)
                .setSchemaId(SCHEMA)
                .build()
                .getSchema()
                .getDefinition()).contains("message Order");
        assertThat(CommitSchemaRequest.newBuilder()
                .setName(schemaName.toString())
                .setSchema(schema.toBuilder().setRevisionId("rev-2"))
                .build()
                .getSchema()
                .getRevisionId()).isEqualTo("rev-2");
        assertThat(RollbackSchemaRequest.newBuilder()
                .setName(schemaName.toString())
                .setRevisionId("rev-1")
                .build()
                .getRevisionId()).isEqualTo("rev-1");
        assertThat(DeleteSchemaRevisionRequest.newBuilder()
                .setName(schemaName.toString())
                .setRevisionId("rev-old")
                .build()
                .getName()).isEqualTo(schemaName.toString());
        assertThat(ValidateSchemaRequest.newBuilder()
                .setParent(projectName.toString())
                .setSchema(schema)
                .build()
                .getSchema()
                .getType()).isEqualTo(Schema.Type.PROTOCOL_BUFFER);
        assertThat(ValidateMessageRequest.newBuilder()
                .setParent(projectName.toString())
                .setName(schemaName.toString())
                .setMessage(ByteString.copyFromUtf8("{\"id\":\"order-1\"}"))
                .setEncoding(Encoding.JSON)
                .build()
                .getEncoding()).isEqualTo(Encoding.JSON);
        assertThat(CreateSnapshotRequest.newBuilder()
                .setName(snapshotName.toString())
                .setSubscription(subscriptionName.toString())
                .putLabels("source", "integration-test")
                .build()
                .getLabelsMap()).containsEntry("source", "integration-test");
        assertThat(UpdateSnapshotRequest.newBuilder()
                .setSnapshot(snapshot.toBuilder().putLabels("updated", "true"))
                .setUpdateMask(FieldMask.newBuilder().addPaths("labels").build())
                .build()
                .getSnapshot()
                .getLabelsMap()).containsEntry("updated", "true");
        assertThat(UpdateTopicRequest.newBuilder()
                .setTopic(Topic.newBuilder().setName(topicName.toString()).putLabels("team", "checkout"))
                .setUpdateMask(FieldMask.newBuilder().addPaths("labels").build())
                .build()
                .getTopic()
                .getLabelsMap()).containsEntry("team", "checkout");
        assertThat(UpdateSubscriptionRequest.newBuilder()
                .setSubscription(Subscription.newBuilder()
                        .setName(subscriptionName.toString())
                        .setAckDeadlineSeconds(20))
                .setUpdateMask(FieldMask.newBuilder().addPaths("ack_deadline_seconds").build())
                .build()
                .getSubscription()
                .getAckDeadlineSeconds()).isEqualTo(20);
        assertThat(ModifyPushConfigRequest.newBuilder()
                .setSubscription(subscriptionName.toString())
                .setPushConfig(PushConfig.newBuilder().setPushEndpoint("https://example.com/new-push"))
                .build()
                .getPushConfig()
                .getPushEndpoint()).endsWith("new-push");
        assertThat(schema.getRevisionCreateTime()).isEqualTo(revisionTime);
        assertThat(snapshot.getLabelsMap()).containsEntry("purpose", "integration-test");
    }

    @Test
    void serviceSettingsBuildGrpcAndHttpJsonConfigurationsWithoutApplicationCredentials()
            throws IOException, InterruptedException {
        RetrySettings retrySettings = shortRetrySettings();
        TopicAdminSettings.Builder topicBuilder = TopicAdminSettings.newBuilder()
                .setCredentialsProvider(NoCredentialsProvider.create())
                .setEndpoint(LOCAL_ENDPOINT);
        topicBuilder.getTopicSettings().setRetrySettings(retrySettings);
        TopicAdminSettings topicSettings = topicBuilder.build();
        TopicAdminSettings httpTopicSettings = TopicAdminSettings.newHttpJsonBuilder()
                .setCredentialsProvider(NoCredentialsProvider.create())
                .setEndpoint("http://localhost:1")
                .build();
        SubscriptionAdminSettings.Builder subscriptionBuilder = SubscriptionAdminSettings.newBuilder()
                .setCredentialsProvider(NoCredentialsProvider.create())
                .setEndpoint(LOCAL_ENDPOINT);
        subscriptionBuilder.pullSettings().setRetrySettings(retrySettings);
        SubscriptionAdminSettings subscriptionSettings = subscriptionBuilder.build();
        SchemaServiceSettings.Builder schemaBuilder = SchemaServiceSettings.newBuilder()
                .setCredentialsProvider(NoCredentialsProvider.create())
                .setEndpoint(LOCAL_ENDPOINT);
        schemaBuilder.getSchemaSettings().setRetrySettings(retrySettings);
        SchemaServiceSettings schemaSettings = schemaBuilder.build();

        assertThat(topicSettings.getTopicSettings().getRetrySettings().getMaxAttempts()).isEqualTo(2);
        assertThat(topicSettings.listTopicsSettings()).isNotNull();
        assertThat(topicSettings.publishSettings().getBatchingSettings()).isNotNull();
        assertThat(topicSettings.testIamPermissionsSettings()).isNotNull();
        assertThat(httpTopicSettings.getTransportChannelProvider().getTransportName()).contains("httpjson");
        assertThat(subscriptionSettings.pullSettings().getRetrySettings().getTotalTimeoutDuration())
                .isEqualTo(java.time.Duration.ofSeconds(3));
        assertThat(subscriptionSettings.listSubscriptionsSettings()).isNotNull();
        assertThat(subscriptionSettings.seekSettings()).isNotNull();
        assertThat(schemaSettings.getSchemaSettings().getRetrySettings().getMaxAttempts()).isEqualTo(2);
        assertThat(schemaSettings.listSchemasSettings()).isNotNull();
        assertThat(schemaSettings.validateMessageSettings()).isNotNull();

        TopicAdminClient topicClient = TopicAdminClient.create(topicSettings);
        SubscriptionAdminClient subscriptionClient = SubscriptionAdminClient.create(subscriptionSettings);
        SchemaServiceClient schemaClient = SchemaServiceClient.create(schemaSettings);
        try {
            assertThat(topicClient.getSettings()).isSameAs(topicSettings);
            assertThat(subscriptionClient.getSettings()).isSameAs(subscriptionSettings);
            assertThat(schemaClient.getSettings()).isSameAs(schemaSettings);
            assertThat(topicClient.getStub()).isNotNull();
            assertThat(subscriptionClient.getStub()).isNotNull();
            assertThat(schemaClient.getStub()).isNotNull();
        } finally {
            shutdownNow(topicClient, subscriptionClient, schemaClient);
        }
    }

    @Test
    void publisherBuilderCreatesClosablePublisherWithCustomBatchingAndRetrySettings() throws Exception {
        TopicName topicName = TopicName.of(PROJECT, TOPIC);
        BatchingSettings batchingSettings = BatchingSettings.newBuilder()
                .setElementCountThreshold(3L)
                .setRequestByteThreshold(1_024L)
                .setDelayThresholdDuration(java.time.Duration.ofMillis(50))
                .setFlowControlSettings(FlowControlSettings.newBuilder()
                        .setMaxOutstandingElementCount(10L)
                        .setMaxOutstandingRequestBytes(10_240L)
                        .build())
                .build();
        Publisher publisher = Publisher.newBuilder(topicName)
                .setCredentialsProvider(NoCredentialsProvider.create())
                .setEndpoint(LOCAL_ENDPOINT)
                .setExecutorProvider(InstantiatingExecutorProvider.newBuilder().setExecutorThreadCount(1).build())
                .setBatchingSettings(batchingSettings)
                .setRetrySettings(shortRetrySettings().toBuilder()
                        .setTotalTimeoutDuration(java.time.Duration.ofSeconds(10))
                        .build())
                .setEnableMessageOrdering(true)
                .setEnableCompression(true)
                .setCompressionBytesThreshold(256L)
                .build();
        try {
            assertThat(publisher.getTopicName().toString()).isEqualTo(topicName.toString());
            assertThat(publisher.getTopicNameString()).isEqualTo(topicName.toString());
            assertThat(publisher.getBatchingSettings().getElementCountThreshold()).isEqualTo(3L);
            assertThat(publisher.getBatchingSettings().getRequestByteThreshold()).isEqualTo(1_024L);
            assertThat(Publisher.getApiMaxRequestElementCount()).isEqualTo(1_000L);
            assertThat(Publisher.getApiMaxRequestBytes()).isEqualTo(10_000_000L);
        } finally {
            publisher.shutdown();
            publisher.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void subscriberBuilderRetainsSubscriptionFlowControlAndDeliveryAttemptConfiguration() throws Exception {
        ProjectSubscriptionName subscriptionName = ProjectSubscriptionName.of(PROJECT, SUBSCRIPTION);
        FlowControlSettings flowControlSettings = FlowControlSettings.newBuilder()
                .setMaxOutstandingElementCount(25L)
                .setMaxOutstandingRequestBytes(4_096L)
                .build();
        MessageReceiver receiver = (message, consumer) -> consumer.ack();
        ScheduledExecutorService systemExecutor = Executors.newSingleThreadScheduledExecutor();
        Subscriber subscriber = null;
        try {
            subscriber = Subscriber.newBuilder(subscriptionName, receiver)
                    .setCredentialsProvider(NoCredentialsProvider.create())
                    .setEndpoint(LOCAL_ENDPOINT)
                    .setSystemExecutorProvider(FixedExecutorProvider.create(systemExecutor))
                    .setFlowControlSettings(flowControlSettings)
                    .setParallelPullCount(1)
                    .setMaxAckExtensionPeriodDuration(Duration.ofSeconds(30))
                    .setMinDurationPerAckExtensionDuration(Duration.ofSeconds(5))
                    .setMaxDurationPerAckExtensionDuration(Duration.ofSeconds(10))
                    .build();
            PubsubMessage messageWithDeliveryAttempt = PubsubMessage.newBuilder()
                    .putAttributes("googclient_deliveryattempt", "4")
                    .build();

            assertThat(subscriber.getSubscriptionNameString()).isEqualTo(subscriptionName.toString());
            assertThat(subscriber.getFlowControlSettings().getMaxOutstandingElementCount()).isEqualTo(25L);
            assertThat(subscriber.getFlowControlSettings().getMaxOutstandingRequestBytes()).isEqualTo(4_096L);
            assertThat(Subscriber.Builder.getDefaultFlowControlSettings().getMaxOutstandingElementCount()).isPositive();
            assertThat(Subscriber.getDeliveryAttempt(messageWithDeliveryAttempt)).isEqualTo(4);
            assertThat(Subscriber.getDeliveryAttempt(PubsubMessage.getDefaultInstance())).isNull();
        } finally {
            if (subscriber != null) {
                subscriber.stopAsync();
                subscriber.awaitTerminated(5, TimeUnit.SECONDS);
            }
            systemExecutor.shutdownNow();
            systemExecutor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void messageReceiversAndAckConsumersCanCoordinateUserProcessing() throws Exception {
        PubsubMessage message = PubsubMessage.newBuilder()
                .setMessageId("message-42")
                .setData(ByteString.copyFromUtf8("payload"))
                .putAttributes("kind", "test")
                .build();
        RecordingAckConsumer ackConsumer = new RecordingAckConsumer();
        AtomicReference<String> processedMessageId = new AtomicReference<>();
        MessageReceiver receiver = (receivedMessage, consumer) -> {
            processedMessageId.set(receivedMessage.getMessageId());
            consumer.ack();
        };
        RecordingAckReplyConsumerWithResponse responseConsumer = new RecordingAckReplyConsumerWithResponse();
        AtomicReference<AckResponse> ackResponse = new AtomicReference<>();
        MessageReceiverWithAckResponse receiverWithAckResponse = (receivedMessage, consumer) -> {
            consumer.ack();
            ackResponse.set(AckResponse.SUCCESSFUL);
            assertThat(receivedMessage.getAttributesMap()).containsEntry("kind", "test");
        };

        receiver.receiveMessage(message, ackConsumer);
        receiverWithAckResponse.receiveMessage(message, responseConsumer);
        responseConsumer.nack().get();

        assertThat(processedMessageId).hasValue("message-42");
        assertThat(ackConsumer.ackCount()).isEqualTo(1);
        assertThat(ackConsumer.nackCount()).isZero();
        assertThat(ackResponse).hasValue(AckResponse.SUCCESSFUL);
        assertThat(responseConsumer.responses()).containsExactly(AckResponse.SUCCESSFUL, AckResponse.OTHER);
    }

    private static RetrySettings shortRetrySettings() {
        return RetrySettings.newBuilder()
                .setMaxAttempts(2)
                .setInitialRetryDelayDuration(java.time.Duration.ofMillis(100))
                .setRetryDelayMultiplier(1.0)
                .setMaxRetryDelayDuration(java.time.Duration.ofMillis(100))
                .setInitialRpcTimeoutDuration(java.time.Duration.ofSeconds(1))
                .setRpcTimeoutMultiplier(1.0)
                .setMaxRpcTimeoutDuration(java.time.Duration.ofSeconds(1))
                .setTotalTimeoutDuration(java.time.Duration.ofSeconds(3))
                .build();
    }

    private static void shutdownNow(BackgroundResource... resources) throws InterruptedException {
        for (BackgroundResource resource : resources) {
            resource.shutdownNow();
        }
        for (BackgroundResource resource : resources) {
            resource.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static final class RecordingAckConsumer implements AckReplyConsumer {
        private final AtomicInteger ackCount = new AtomicInteger();
        private final AtomicInteger nackCount = new AtomicInteger();

        @Override
        public void ack() {
            ackCount.incrementAndGet();
        }

        @Override
        public void nack() {
            nackCount.incrementAndGet();
        }

        int ackCount() {
            return ackCount.get();
        }

        int nackCount() {
            return nackCount.get();
        }
    }

    private static final class RecordingAckReplyConsumerWithResponse implements AckReplyConsumerWithResponse {
        private final List<AckResponse> responses = new ArrayList<>();

        @Override
        public ApiFuture<AckResponse> ack() {
            responses.add(AckResponse.SUCCESSFUL);
            return ApiFutures.immediateFuture(AckResponse.SUCCESSFUL);
        }

        @Override
        public ApiFuture<AckResponse> nack() {
            responses.add(AckResponse.OTHER);
            return ApiFutures.immediateFuture(AckResponse.OTHER);
        }

        List<AckResponse> responses() {
            return responses;
        }
    }
}
