/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_amazonaws.aws_lambda_java_events;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.amazonaws.services.lambda.runtime.events.CloudWatchLogsEvent;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.amazonaws.services.lambda.runtime.events.KinesisEvent;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.OperationType;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.StreamRecord;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.StreamViewType;
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification;
import java.util.List;
import java.util.Map;
import org.joda.time.DateTime;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Aws_lambda_java_eventsTest {
    @Test
    void apiGatewayEventsRetainRequestContextAndResponseDetails() {
        APIGatewayProxyRequestEvent.RequestIdentity identity =
                new APIGatewayProxyRequestEvent.RequestIdentity()
                        .withAccountId("123456789012")
                        .withSourceIp("192.0.2.1")
                        .withUserAgent("integration-test");
        APIGatewayProxyRequestEvent.ProxyRequestContext context =
                new APIGatewayProxyRequestEvent.ProxyRequestContext()
                        .withRequestId("request-1")
                        .withStage("prod")
                        .withHttpMethod("POST")
                        .withIdentity(identity);
        context.setAuthorizer(Map.of("principalId", "user-1"));
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
                .withResource("/orders/{id}")
                .withPath("/orders/42")
                .withHttpMethod("POST")
                .withHeaders(Map.of("content-type", "application/json"))
                .withMultiValueHeaders(Map.of("accept", List.of("application/json", "text/plain")))
                .withQueryStringParameters(Map.of("verbose", "true"))
                .withPathParameters(Map.of("id", "42"))
                .withStageVariables(Map.of("environment", "production"))
                .withRequestContext(context)
                .withBody("{\"name\":\"book\"}")
                .withIsBase64Encoded(false);

        assertThat(request.getRequestContext().getIdentity().getSourceIp()).isEqualTo("192.0.2.1");
        assertThat(request.getMultiValueHeaders().get("accept")).containsExactly("application/json", "text/plain");
        assertThat(request.clone()).isEqualTo(request).isNotSameAs(request);

        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent()
                .withStatusCode(201)
                .withHeaders(Map.of("location", "/orders/42"))
                .withBody("created")
                .withIsBase64Encoded(false);

        assertThat(response.getStatusCode()).isEqualTo(201);
        assertThat(response.getHeaders()).containsEntry("location", "/orders/42");
        assertThat(response.clone()).isEqualTo(response).isNotSameAs(response);
    }

    @Test
    void snsEventPreservesRecordMessageAttributesAndTimestamp() {
        SNSEvent.MessageAttribute attribute = new SNSEvent.MessageAttribute()
                .withType("String")
                .withValue("priority");
        DateTime timestamp = new DateTime(2020, 1, 2, 3, 4);
        SNSEvent.SNS sns = new SNSEvent.SNS()
                .withMessageId("message-1")
                .withTopicArn("arn:aws:sns:us-east-1:123456789012:orders")
                .withSubject("order-created")
                .withMessage("order 42")
                .withTimestamp(timestamp)
                .withMessageAttributes(Map.of("category", attribute));
        SNSEvent.SNSRecord record = new SNSEvent.SNSRecord()
                .withEventVersion("1.0")
                .withEventSource("aws:sns")
                .withEventSubscriptionArn("arn:aws:sns:subscription")
                .withSns(sns);
        SNSEvent event = new SNSEvent().withRecords(List.of(record));

        assertThat(event.getRecords()).hasSize(1);
        assertThat(event.getRecords().get(0).getSNS().getMessage()).isEqualTo("order 42");
        assertThat(event.getRecords().get(0).getSNS().getMessageAttributes().get("category").getValue())
                .isEqualTo("priority");
        assertThat(event.getRecords().get(0).getSNS().getTimestamp()).isEqualTo(timestamp);
        assertThat(event.clone()).isEqualTo(event).isNotSameAs(event);
    }

    @Test
    void dynamodbStreamEventRetainsImagesAndRecordMetadata() {
        AttributeValue orderId = new AttributeValue().withS("order-42");
        AttributeValue quantity = new AttributeValue().withN("3");
        StreamRecord streamRecord = new StreamRecord()
                .withKeys(Map.of("orderId", orderId))
                .withSequenceNumber("496080000000000000001")
                .withSizeBytes(128L)
                .withStreamViewType(StreamViewType.NEW_AND_OLD_IMAGES);
        streamRecord.addNewImageEntry("quantity", quantity);
        streamRecord.addOldImageEntry("quantity", new AttributeValue().withN("2"));

        DynamodbEvent.DynamodbStreamRecord record = new DynamodbEvent.DynamodbStreamRecord();
        record.setEventID("event-1");
        record.setEventName(OperationType.MODIFY);
        record.setEventSource("aws:dynamodb");
        record.setEventSourceARN("arn:aws:dynamodb:us-east-1:123456789012:table/orders/stream/2020-01-01");
        record.setDynamodb(streamRecord);
        DynamodbEvent event = new DynamodbEvent();
        event.setRecords(List.of(record));

        DynamodbEvent.DynamodbStreamRecord retainedRecord = event.getRecords().get(0);
        assertThat(retainedRecord.getEventName()).isEqualTo(OperationType.MODIFY.name());
        assertThat(retainedRecord.getDynamodb().getKeys().get("orderId").getS()).isEqualTo("order-42");
        assertThat(retainedRecord.getDynamodb().getNewImage().get("quantity").getN()).isEqualTo("3");
        assertThat(retainedRecord.getDynamodb().getOldImage().get("quantity").getN()).isEqualTo("2");
        assertThat(retainedRecord.getDynamodb().getStreamViewType())
                .isEqualTo(StreamViewType.NEW_AND_OLD_IMAGES.name());
    }

    @Test
    void s3EventNotificationDecodesObjectKeysAndRetainsRecordDetails() {
        S3EventNotification.UserIdentityEntity owner =
                new S3EventNotification.UserIdentityEntity("bucket-owner");
        S3EventNotification.S3BucketEntity bucket = new S3EventNotification.S3BucketEntity(
                "order-uploads", owner, "arn:aws:s3:::order-uploads");
        S3EventNotification.S3ObjectEntity object = new S3EventNotification.S3ObjectEntity(
                "orders%2Forder+42.json", 128L, "etag-1", "version-1", "sequencer-1");
        S3EventNotification.S3Entity s3 = new S3EventNotification.S3Entity("upload-config", bucket, object, "1.0");
        S3EventNotification.S3EventNotificationRecord record = new S3EventNotification.S3EventNotificationRecord(
                "us-east-1", "ObjectCreated:Put", "aws:s3", "2020-01-02T03:04:05.000Z", "2.1",
                new S3EventNotification.RequestParametersEntity("192.0.2.1"),
                new S3EventNotification.ResponseElementsEntity("id-2", "request-1"), s3,
                new S3EventNotification.UserIdentityEntity("uploader"));
        S3EventNotification event = new S3EventNotification(List.of(record));

        S3EventNotification.S3EventNotificationRecord retainedRecord = event.getRecords().get(0);
        assertThat(retainedRecord.getS3().getBucket().getName()).isEqualTo("order-uploads");
        assertThat(retainedRecord.getS3().getObject().getUrlDecodedKey()).isEqualTo("orders/order 42.json");
        assertThat(retainedRecord.getS3().getObject().getSizeAsLong()).isEqualTo(128L);
        assertThat(retainedRecord.getS3().getObject().getSequencer()).isEqualTo("sequencer-1");
        assertThat(retainedRecord.getRequestParameters().getSourceIPAddress()).isEqualTo("192.0.2.1");
        assertThat(retainedRecord.getEventTime()).isEqualTo(DateTime.parse("2020-01-02T03:04:05.000Z"));
    }

    @Test
    void streamAndLogEventsExposeNestedPayloads() {
        KinesisEvent.Record kinesisRecord = new KinesisEvent.Record();
        kinesisRecord.setKinesisSchemaVersion("1.0");
        KinesisEvent.KinesisEventRecord record = new KinesisEvent.KinesisEventRecord();
        record.setEventID("shardId-000:1");
        record.setEventSource("aws:kinesis");
        record.setAwsRegion("us-east-1");
        record.setKinesis(kinesisRecord);
        KinesisEvent kinesisEvent = new KinesisEvent();
        kinesisEvent.setRecords(List.of(record));

        CloudWatchLogsEvent.AWSLogs logs = new CloudWatchLogsEvent.AWSLogs().withData("compressed-log-data");
        CloudWatchLogsEvent logsEvent = new CloudWatchLogsEvent().withAwsLogs(logs);

        assertThat(kinesisEvent.getRecords().get(0).getKinesis().getKinesisSchemaVersion()).isEqualTo("1.0");
        assertThat(kinesisEvent.clone()).isEqualTo(kinesisEvent).isNotSameAs(kinesisEvent);
        assertThat(logsEvent.getAwsLogs().getData()).isEqualTo("compressed-log-data");
        assertThat(logsEvent.clone()).isEqualTo(logsEvent).isNotSameAs(logsEvent);
    }
}
