/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_amazonaws.aws_lambda_java_events;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import com.amazonaws.services.lambda.runtime.events.ScheduledEvent;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LambdaEventsTest {

    private final Gson gson = new GsonBuilder().create();

    @Test
    void deserializeApiGatewayProxyRequest() {
        String json = """
                {
                  "resource": "/test",
                  "path": "/test",
                  "httpMethod": "GET",
                  "headers": {"Host": "example.com"},
                  "queryStringParameters": {"key": "value"},
                  "body": null,
                  "isBase64Encoded": false
                }
                """;
        APIGatewayProxyRequestEvent event = gson.fromJson(json, APIGatewayProxyRequestEvent.class);
        assertThat(event.getPath()).isEqualTo("/test");
        assertThat(event.getHttpMethod()).isEqualTo("GET");
        assertThat(event.getHeaders()).containsEntry("Host", "example.com");
    }

    @Test
    void deserializeApiGatewayProxyResponse() {
        String json = """
                {
                  "statusCode": 200,
                  "headers": {"Content-Type": "application/json"},
                  "body": "hello"
                }
                """;
        APIGatewayProxyResponseEvent event = gson.fromJson(json, APIGatewayProxyResponseEvent.class);
        assertThat(event.getStatusCode()).isEqualTo(200);
        assertThat(event.getBody()).isEqualTo("hello");
    }

    @Test
    void deserializeSnsEvent() {
        String json = """
                {
                  "records": [
                    {
                      "eventSource": "aws:sns",
                      "sns": {
                        "message": "hello sns",
                        "subject": "test subject"
                      }
                    }
                  ]
                }
                """;
        SNSEvent event = gson.fromJson(json, SNSEvent.class);
        assertThat(event.getRecords()).hasSize(1);
        assertThat(event.getRecords().get(0).getSNS().getMessage()).isEqualTo("hello sns");
    }

    @Test
    void deserializeScheduledEvent() {
        String json = """
                {
                  "source": "aws.events",
                  "detail-type": "Scheduled Event",
                  "account": "123456789012",
                  "region": "us-east-1"
                }
                """;
        ScheduledEvent event = gson.fromJson(json, ScheduledEvent.class);
        assertThat(event.getSource()).isEqualTo("aws.events");
        assertThat(event.getAccount()).isEqualTo("123456789012");
    }

    @Test
    void deserializeS3Event() {
        String json = """
                {
                  "records": [
                    {
                      "eventSource": "aws:s3",
                      "eventName": "ObjectCreated:Put",
                      "s3": {
                        "bucket": {"name": "my-bucket"},
                        "object": {"key": "my-key", "size": 1024}
                      }
                    }
                  ]
                }
                """;
        S3Event event = gson.fromJson(json, S3Event.class);
        assertThat(event.getRecords()).hasSize(1);
        assertThat(event.getRecords().get(0).getS3().getBucket().getName()).isEqualTo("my-bucket");
    }
}
