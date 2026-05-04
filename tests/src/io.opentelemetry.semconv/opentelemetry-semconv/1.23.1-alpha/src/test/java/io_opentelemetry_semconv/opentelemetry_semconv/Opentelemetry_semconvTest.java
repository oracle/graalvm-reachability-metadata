/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_opentelemetry_semconv.opentelemetry_semconv;

import java.util.List;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributeType;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.semconv.AttributeKeyTemplate;
import io.opentelemetry.semconv.ResourceAttributes;
import io.opentelemetry.semconv.SemanticAttributes;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Opentelemetry_semconvTest {
    @Test
    void semanticAttributeKeysExposeExpectedNamesAndTypes() {
        assertThat(SemanticAttributes.SCHEMA_URL)
                .isEqualTo(ResourceAttributes.SCHEMA_URL)
                .startsWith("https://opentelemetry.io/schemas/");

        assertKey(SemanticAttributes.HTTP_REQUEST_METHOD, "http.request.method", AttributeType.STRING);
        assertKey(SemanticAttributes.HTTP_REQUEST_BODY_SIZE, "http.request.body.size", AttributeType.LONG);
        assertKey(SemanticAttributes.HTTP_RESPONSE_STATUS_CODE, "http.response.status_code", AttributeType.LONG);
        assertKey(SemanticAttributes.URL_FULL, "url.full", AttributeType.STRING);
        assertKey(SemanticAttributes.SERVER_ADDRESS, "server.address", AttributeType.STRING);
        assertKey(SemanticAttributes.SERVER_PORT, "server.port", AttributeType.LONG);
        assertKey(SemanticAttributes.CLIENT_ADDRESS, "client.address", AttributeType.STRING);
        assertKey(SemanticAttributes.CLIENT_PORT, "client.port", AttributeType.LONG);
        assertKey(SemanticAttributes.NETWORK_TRANSPORT, "network.transport", AttributeType.STRING);
        assertKey(SemanticAttributes.NETWORK_PEER_PORT, "network.peer.port", AttributeType.LONG);
        assertKey(SemanticAttributes.RPC_GRPC_STATUS_CODE, "rpc.grpc.status_code", AttributeType.LONG);
        assertKey(SemanticAttributes.MESSAGING_DESTINATION_ANONYMOUS,
                "messaging.destination.anonymous", AttributeType.BOOLEAN);
        assertKey(SemanticAttributes.MESSAGING_ROCKETMQ_MESSAGE_KEYS,
                "messaging.rocketmq.message.keys", AttributeType.STRING_ARRAY);
        assertKey(SemanticAttributes.DB_CASSANDRA_IDEMPOTENCE, "db.cassandra.idempotence", AttributeType.BOOLEAN);
        assertKey(SemanticAttributes.DB_COSMOSDB_REQUEST_CHARGE, "db.cosmosdb.request_charge", AttributeType.DOUBLE);
        assertKey(SemanticAttributes.AWS_DYNAMODB_TABLE_NAMES, "aws.dynamodb.table_names", AttributeType.STRING_ARRAY);
        assertKey(SemanticAttributes.FAAS_COLDSTART, "faas.coldstart", AttributeType.BOOLEAN);
        assertKey(SemanticAttributes.EXCEPTION_ESCAPED, "exception.escaped", AttributeType.BOOLEAN);
        assertKey(SemanticAttributes.THREAD_ID, "thread.id", AttributeType.LONG);
    }

    @Test
    void resourceAttributeKeysExposeExpectedNamesAndTypes() {
        assertKey(ResourceAttributes.SERVICE_NAME, "service.name", AttributeType.STRING);
        assertKey(ResourceAttributes.SERVICE_VERSION, "service.version", AttributeType.STRING);
        assertKey(ResourceAttributes.TELEMETRY_SDK_LANGUAGE, "telemetry.sdk.language", AttributeType.STRING);
        assertKey(ResourceAttributes.CLOUD_PROVIDER, "cloud.provider", AttributeType.STRING);
        assertKey(ResourceAttributes.CLOUD_REGION, "cloud.region", AttributeType.STRING);
        assertKey(ResourceAttributes.CONTAINER_COMMAND_ARGS, "container.command_args", AttributeType.STRING_ARRAY);
        assertKey(ResourceAttributes.CONTAINER_IMAGE_TAGS, "container.image.tags", AttributeType.STRING_ARRAY);
        assertKey(ResourceAttributes.BROWSER_MOBILE, "browser.mobile", AttributeType.BOOLEAN);
        assertKey(ResourceAttributes.AWS_LOG_GROUP_NAMES, "aws.log.group.names", AttributeType.STRING_ARRAY);
        assertKey(ResourceAttributes.GCP_CLOUD_RUN_JOB_TASK_INDEX, "gcp.cloud_run.job.task_index", AttributeType.LONG);
        assertKey(ResourceAttributes.FAAS_MAX_MEMORY, "faas.max_memory", AttributeType.LONG);
        assertKey(ResourceAttributes.HOST_IP, "host.ip", AttributeType.STRING_ARRAY);
        assertKey(ResourceAttributes.HOST_CPU_CACHE_L2_SIZE, "host.cpu.cache.l2.size", AttributeType.LONG);
        assertKey(ResourceAttributes.K8S_CONTAINER_RESTART_COUNT, "k8s.container.restart_count", AttributeType.LONG);
        assertKey(ResourceAttributes.PROCESS_PID, "process.pid", AttributeType.LONG);
        assertKey(ResourceAttributes.OS_TYPE, "os.type", AttributeType.STRING);
    }

    @Test
    void valueConstantsMatchSemanticConventionWireValues() {
        assertThat(SemanticAttributes.HttpRequestMethodValues.GET).isEqualTo("GET");
        assertThat(SemanticAttributes.HttpRequestMethodValues.OTHER).isEqualTo("_OTHER");
        assertThat(SemanticAttributes.DbSystemValues.POSTGRESQL).isEqualTo("postgresql");
        assertThat(SemanticAttributes.DbSystemValues.COSMOSDB).isEqualTo("cosmosdb");
        assertThat(SemanticAttributes.NetworkTransportValues.TCP).isEqualTo("tcp");
        assertThat(SemanticAttributes.MessagingOperationValues.PUBLISH).isEqualTo("publish");
        assertThat(SemanticAttributes.RpcGrpcStatusCodeValues.OK).isZero();
        assertThat(SemanticAttributes.RpcGrpcStatusCodeValues.UNAUTHENTICATED).isEqualTo(16L);

        assertThat(ResourceAttributes.CloudProviderValues.AWS).isEqualTo("aws");
        assertThat(ResourceAttributes.CloudProviderValues.GCP).isEqualTo("gcp");
        assertThat(ResourceAttributes.HostArchValues.ARM64).isEqualTo("arm64");
        assertThat(ResourceAttributes.OsTypeValues.LINUX).isEqualTo("linux");
        assertThat(ResourceAttributes.TelemetrySdkLanguageValues.JAVA).isEqualTo("java");
        assertThat(ResourceAttributes.AwsEcsLaunchtypeValues.FARGATE).isEqualTo("fargate");
    }

    @Test
    void semanticAttributesCanBeUsedAsTypedAttributeKeys() {
        List<String> messageKeys = List.of("order-1", "customer-7");
        List<String> dynamoDbTables = List.of("orders", "customers");

        Attributes attributes = Attributes.builder()
                .put(SemanticAttributes.HTTP_REQUEST_METHOD, SemanticAttributes.HttpRequestMethodValues.POST)
                .put(SemanticAttributes.HTTP_RESPONSE_STATUS_CODE, 201L)
                .put(SemanticAttributes.URL_FULL, "https://example.test/orders/1")
                .put(SemanticAttributes.SERVER_ADDRESS, "example.test")
                .put(SemanticAttributes.SERVER_PORT, 443L)
                .put(SemanticAttributes.NETWORK_TRANSPORT, SemanticAttributes.NetworkTransportValues.TCP)
                .put(SemanticAttributes.MESSAGING_DESTINATION_ANONYMOUS, false)
                .put(SemanticAttributes.MESSAGING_ROCKETMQ_MESSAGE_KEYS, messageKeys)
                .put(SemanticAttributes.DB_SYSTEM, SemanticAttributes.DbSystemValues.POSTGRESQL)
                .put(SemanticAttributes.DB_COSMOSDB_REQUEST_CHARGE, 2.75)
                .put(SemanticAttributes.AWS_DYNAMODB_TABLE_NAMES, dynamoDbTables)
                .put(SemanticAttributes.FAAS_COLDSTART, true)
                .build();

        assertThat(attributes.get(SemanticAttributes.HTTP_REQUEST_METHOD)).isEqualTo("POST");
        assertThat(attributes.get(SemanticAttributes.HTTP_RESPONSE_STATUS_CODE)).isEqualTo(201L);
        assertThat(attributes.get(SemanticAttributes.SERVER_PORT)).isEqualTo(443L);
        assertThat(attributes.get(SemanticAttributes.MESSAGING_DESTINATION_ANONYMOUS)).isFalse();
        assertThat(attributes.get(SemanticAttributes.MESSAGING_ROCKETMQ_MESSAGE_KEYS)).isEqualTo(messageKeys);
        assertThat(attributes.get(SemanticAttributes.DB_COSMOSDB_REQUEST_CHARGE)).isEqualTo(2.75);
        assertThat(attributes.get(SemanticAttributes.AWS_DYNAMODB_TABLE_NAMES)).isEqualTo(dynamoDbTables);
        assertThat(attributes.get(SemanticAttributes.FAAS_COLDSTART)).isTrue();
        assertThat(attributes.asMap()).containsKeys(
                SemanticAttributes.URL_FULL,
                SemanticAttributes.NETWORK_TRANSPORT,
                SemanticAttributes.DB_SYSTEM);
    }

    @Test
    void cloudEventsAttributesCanDescribeEventEnvelope() {
        Attributes cloudEventAttributes = Attributes.builder()
                .put(SemanticAttributes.CLOUDEVENTS_EVENT_ID, "evt-0001")
                .put(SemanticAttributes.CLOUDEVENTS_EVENT_SOURCE, "urn:example:orders")
                .put(SemanticAttributes.CLOUDEVENTS_EVENT_SPEC_VERSION, "1.0")
                .put(SemanticAttributes.CLOUDEVENTS_EVENT_TYPE, "com.example.order.created")
                .put(SemanticAttributes.CLOUDEVENTS_EVENT_SUBJECT, "orders/123")
                .build();

        assertKey(SemanticAttributes.CLOUDEVENTS_EVENT_ID, "cloudevents.event_id", AttributeType.STRING);
        assertKey(SemanticAttributes.CLOUDEVENTS_EVENT_SOURCE, "cloudevents.event_source", AttributeType.STRING);
        assertKey(SemanticAttributes.CLOUDEVENTS_EVENT_SPEC_VERSION,
                "cloudevents.event_spec_version", AttributeType.STRING);
        assertKey(SemanticAttributes.CLOUDEVENTS_EVENT_TYPE, "cloudevents.event_type", AttributeType.STRING);
        assertKey(SemanticAttributes.CLOUDEVENTS_EVENT_SUBJECT, "cloudevents.event_subject", AttributeType.STRING);

        assertThat(cloudEventAttributes.get(SemanticAttributes.CLOUDEVENTS_EVENT_ID)).isEqualTo("evt-0001");
        assertThat(cloudEventAttributes.get(SemanticAttributes.CLOUDEVENTS_EVENT_SOURCE))
                .isEqualTo("urn:example:orders");
        assertThat(cloudEventAttributes.get(SemanticAttributes.CLOUDEVENTS_EVENT_SPEC_VERSION)).isEqualTo("1.0");
        assertThat(cloudEventAttributes.get(SemanticAttributes.CLOUDEVENTS_EVENT_TYPE))
                .isEqualTo("com.example.order.created");
        assertThat(cloudEventAttributes.get(SemanticAttributes.CLOUDEVENTS_EVENT_SUBJECT)).isEqualTo("orders/123");
        assertThat(cloudEventAttributes.asMap())
                .containsOnlyKeys(
                        SemanticAttributes.CLOUDEVENTS_EVENT_ID,
                        SemanticAttributes.CLOUDEVENTS_EVENT_SOURCE,
                        SemanticAttributes.CLOUDEVENTS_EVENT_SPEC_VERSION,
                        SemanticAttributes.CLOUDEVENTS_EVENT_TYPE,
                        SemanticAttributes.CLOUDEVENTS_EVENT_SUBJECT);
    }

    @Test
    void graphqlAttributesCanDescribeOperation() {
        String document = "query Order($id: ID!) { order(id: $id) { id total } }";

        Attributes graphqlAttributes = Attributes.builder()
                .put(SemanticAttributes.GRAPHQL_DOCUMENT, document)
                .put(SemanticAttributes.GRAPHQL_OPERATION_NAME, "Order")
                .put(SemanticAttributes.GRAPHQL_OPERATION_TYPE, SemanticAttributes.GraphqlOperationTypeValues.QUERY)
                .build();

        assertKey(SemanticAttributes.GRAPHQL_DOCUMENT, "graphql.document", AttributeType.STRING);
        assertKey(SemanticAttributes.GRAPHQL_OPERATION_NAME, "graphql.operation.name", AttributeType.STRING);
        assertKey(SemanticAttributes.GRAPHQL_OPERATION_TYPE, "graphql.operation.type", AttributeType.STRING);

        assertThat(SemanticAttributes.GraphqlOperationTypeValues.QUERY).isEqualTo("query");
        assertThat(SemanticAttributes.GraphqlOperationTypeValues.MUTATION).isEqualTo("mutation");
        assertThat(SemanticAttributes.GraphqlOperationTypeValues.SUBSCRIPTION).isEqualTo("subscription");
        assertThat(graphqlAttributes.get(SemanticAttributes.GRAPHQL_DOCUMENT)).isEqualTo(document);
        assertThat(graphqlAttributes.get(SemanticAttributes.GRAPHQL_OPERATION_NAME)).isEqualTo("Order");
        assertThat(graphqlAttributes.get(SemanticAttributes.GRAPHQL_OPERATION_TYPE)).isEqualTo("query");
        assertThat(graphqlAttributes.asMap())
                .containsOnlyKeys(
                        SemanticAttributes.GRAPHQL_DOCUMENT,
                        SemanticAttributes.GRAPHQL_OPERATION_NAME,
                        SemanticAttributes.GRAPHQL_OPERATION_TYPE);
    }

    @Test
    void resourceAttributesCanDescribeRuntimeAndDeployment() {
        List<String> commandArgs = List.of("--server.port=8080", "--profile=test");
        List<String> hostAddresses = List.of("192.0.2.10", "2001:db8::10");

        Attributes resourceAttributes = Attributes.builder()
                .put(ResourceAttributes.SERVICE_NAME, "checkout")
                .put(ResourceAttributes.SERVICE_VERSION, "test-build")
                .put(ResourceAttributes.TELEMETRY_SDK_LANGUAGE, ResourceAttributes.TelemetrySdkLanguageValues.JAVA)
                .put(ResourceAttributes.CLOUD_PROVIDER, ResourceAttributes.CloudProviderValues.AWS)
                .put(ResourceAttributes.CLOUD_REGION, "us-east-1")
                .put(ResourceAttributes.CONTAINER_COMMAND_ARGS, commandArgs)
                .put(ResourceAttributes.BROWSER_MOBILE, true)
                .put(ResourceAttributes.FAAS_MAX_MEMORY, 536_870_912L)
                .put(ResourceAttributes.HOST_ARCH, ResourceAttributes.HostArchValues.ARM64)
                .put(ResourceAttributes.HOST_IP, hostAddresses)
                .put(ResourceAttributes.HOST_CPU_CACHE_L2_SIZE, 262_144L)
                .put(ResourceAttributes.OS_TYPE, ResourceAttributes.OsTypeValues.LINUX)
                .put(ResourceAttributes.PROCESS_PID, 12_345L)
                .build();

        assertThat(resourceAttributes.get(ResourceAttributes.SERVICE_NAME)).isEqualTo("checkout");
        assertThat(resourceAttributes.get(ResourceAttributes.TELEMETRY_SDK_LANGUAGE)).isEqualTo("java");
        assertThat(resourceAttributes.get(ResourceAttributes.CLOUD_PROVIDER)).isEqualTo("aws");
        assertThat(resourceAttributes.get(ResourceAttributes.CONTAINER_COMMAND_ARGS)).isEqualTo(commandArgs);
        assertThat(resourceAttributes.get(ResourceAttributes.BROWSER_MOBILE)).isTrue();
        assertThat(resourceAttributes.get(ResourceAttributes.FAAS_MAX_MEMORY)).isEqualTo(536_870_912L);
        assertThat(resourceAttributes.get(ResourceAttributes.HOST_IP)).isEqualTo(hostAddresses);
        assertThat(resourceAttributes.get(ResourceAttributes.OS_TYPE)).isEqualTo("linux");
        assertThat(resourceAttributes.get(ResourceAttributes.PROCESS_PID)).isEqualTo(12_345L);
        assertThat(resourceAttributes.size()).isEqualTo(13);
    }

    @Test
    void attributeKeyTemplatesCreateTypedKeysWithNamespacedSuffixesAndCacheThem() {
        AttributeKey<List<String>> requestHeader = SemanticAttributes.HTTP_REQUEST_HEADER.getAttributeKey("content-type");
        AttributeKey<List<String>> sameRequestHeader = SemanticAttributes.HTTP_REQUEST_HEADER.getAttributeKey("content-type");
        AttributeKey<List<String>> grpcMetadata = SemanticAttributes.RPC_GRPC_REQUEST_METADATA
                .getAttributeKey("x-request-id");
        AttributeKey<String> containerLabel = ResourceAttributes.CONTAINER_LABELS.getAttributeKey("app");

        assertThat(requestHeader).isSameAs(sameRequestHeader);
        assertKey(requestHeader, "http.request.header.content-type", AttributeType.STRING_ARRAY);
        assertKey(grpcMetadata, "rpc.grpc.request.metadata.x-request-id", AttributeType.STRING_ARRAY);
        assertKey(containerLabel, "container.labels.app", AttributeType.STRING);

        AttributeKey<String> stringKey = AttributeKeyTemplate.stringKeyTemplate("custom")
                .getAttributeKey("name");
        AttributeKey<List<String>> stringArrayKey = AttributeKeyTemplate.stringArrayKeyTemplate("custom")
                .getAttributeKey("tags");
        AttributeKey<Boolean> booleanKey = AttributeKeyTemplate.booleanKeyTemplate("feature.flags")
                .getAttributeKey("enabled");
        AttributeKey<List<Boolean>> booleanArrayKey = AttributeKeyTemplate.booleanArrayKeyTemplate("feature.flags")
                .getAttributeKey("states");
        AttributeKey<Long> longKey = AttributeKeyTemplate.longKeyTemplate("queue")
                .getAttributeKey("depth");
        AttributeKey<List<Long>> longArrayKey = AttributeKeyTemplate.longArrayKeyTemplate("queue")
                .getAttributeKey("partitions");
        AttributeKey<Double> doubleKey = AttributeKeyTemplate.doubleKeyTemplate("cache")
                .getAttributeKey("hit_ratio");
        AttributeKey<List<Double>> doubleArrayKey = AttributeKeyTemplate.doubleArrayKeyTemplate("cache")
                .getAttributeKey("latencies");

        assertKey(stringKey, "custom.name", AttributeType.STRING);
        assertKey(stringArrayKey, "custom.tags", AttributeType.STRING_ARRAY);
        assertKey(booleanKey, "feature.flags.enabled", AttributeType.BOOLEAN);
        assertKey(booleanArrayKey, "feature.flags.states", AttributeType.BOOLEAN_ARRAY);
        assertKey(longKey, "queue.depth", AttributeType.LONG);
        assertKey(longArrayKey, "queue.partitions", AttributeType.LONG_ARRAY);
        assertKey(doubleKey, "cache.hit_ratio", AttributeType.DOUBLE);
        assertKey(doubleArrayKey, "cache.latencies", AttributeType.DOUBLE_ARRAY);

        Attributes attributes = Attributes.builder()
                .put(requestHeader, List.of("application/json"))
                .put(grpcMetadata, List.of("abc", "def"))
                .put(containerLabel, "checkout")
                .put(stringKey, "value")
                .put(stringArrayKey, List.of("blue", "green"))
                .put(booleanKey, true)
                .put(booleanArrayKey, List.of(true, false))
                .put(longKey, 42L)
                .put(longArrayKey, List.of(0L, 1L))
                .put(doubleKey, 0.95)
                .put(doubleArrayKey, List.of(1.5, 2.5))
                .build();

        assertThat(attributes.get(requestHeader)).containsExactly("application/json");
        assertThat(attributes.get(grpcMetadata)).containsExactly("abc", "def");
        assertThat(attributes.get(containerLabel)).isEqualTo("checkout");
        assertThat(attributes.get(stringKey)).isEqualTo("value");
        assertThat(attributes.get(stringArrayKey)).containsExactly("blue", "green");
        assertThat(attributes.get(booleanKey)).isTrue();
        assertThat(attributes.get(booleanArrayKey)).containsExactly(true, false);
        assertThat(attributes.get(longKey)).isEqualTo(42L);
        assertThat(attributes.get(longArrayKey)).containsExactly(0L, 1L);
        assertThat(attributes.get(doubleKey)).isEqualTo(0.95);
        assertThat(attributes.get(doubleArrayKey)).containsExactly(1.5, 2.5);
    }

    private static void assertKey(AttributeKey<?> key, String expectedName, AttributeType expectedType) {
        assertThat(key.getKey()).isEqualTo(expectedName);
        assertThat(key.getType()).isEqualTo(expectedType);
    }
}
