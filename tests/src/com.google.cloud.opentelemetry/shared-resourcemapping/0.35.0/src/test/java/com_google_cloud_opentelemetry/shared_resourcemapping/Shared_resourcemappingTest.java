/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_cloud_opentelemetry.shared_resourcemapping;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.cloud.opentelemetry.resource.GcpResource;
import com.google.cloud.opentelemetry.resource.ResourceTranslator;
import com.google.cloud.opentelemetry.resource.ResourceTranslator.AttributeMapping;
import com.google.cloud.opentelemetry.shadow.semconv.AttributeKeyTemplate;
import com.google.cloud.opentelemetry.shadow.semconv.HttpAttributes;
import com.google.cloud.opentelemetry.shadow.semconv.NetworkAttributes;
import com.google.cloud.opentelemetry.shadow.semconv.SchemaUrls;
import com.google.cloud.opentelemetry.shadow.semconv.ServerAttributes;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributeType;
import io.opentelemetry.sdk.resources.Resource;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

public class Shared_resourcemappingTest {
    private static final String AWS_EC2 = "aws_ec2";
    private static final String GCP_APP_ENGINE = "gcp_app_engine";
    private static final String GCP_COMPUTE_ENGINE = "gcp_compute_engine";

    private static final AttributeKey<String> CLOUD_ACCOUNT_ID = AttributeKey.stringKey("cloud.account.id");
    private static final AttributeKey<String> CLOUD_AVAILABILITY_ZONE =
            AttributeKey.stringKey("cloud.availability_zone");
    private static final AttributeKey<String> CLOUD_PLATFORM = AttributeKey.stringKey("cloud.platform");
    private static final AttributeKey<String> CLOUD_REGION = AttributeKey.stringKey("cloud.region");
    private static final AttributeKey<String> FAAS_INSTANCE = AttributeKey.stringKey("faas.instance");
    private static final AttributeKey<String> FAAS_NAME = AttributeKey.stringKey("faas.name");
    private static final AttributeKey<String> FAAS_VERSION = AttributeKey.stringKey("faas.version");
    private static final AttributeKey<String> HOST_ID = AttributeKey.stringKey("host.id");
    private static final AttributeKey<String> HOST_NAME = AttributeKey.stringKey("host.name");
    private static final AttributeKey<String> K8S_CLUSTER_NAME = AttributeKey.stringKey("k8s.cluster.name");
    private static final AttributeKey<String> K8S_CONTAINER_NAME = AttributeKey.stringKey("k8s.container.name");
    private static final AttributeKey<String> K8S_NAMESPACE_NAME = AttributeKey.stringKey("k8s.namespace.name");
    private static final AttributeKey<String> K8S_NODE_NAME = AttributeKey.stringKey("k8s.node.name");
    private static final AttributeKey<String> K8S_POD_NAME = AttributeKey.stringKey("k8s.pod.name");
    private static final AttributeKey<String> SERVICE_INSTANCE_ID = AttributeKey.stringKey("service.instance.id");
    private static final AttributeKey<String> SERVICE_NAME = AttributeKey.stringKey("service.name");
    private static final AttributeKey<String> SERVICE_NAMESPACE = AttributeKey.stringKey("service.namespace");

    @Test
    void mapsCloudPlatformResourcesToGcpMonitoredResourceTypes() {
        Resource gceResource = Resource.builder()
                .put(CLOUD_PLATFORM, GCP_COMPUTE_ENGINE)
                .put(CLOUD_AVAILABILITY_ZONE, "us-central1-b")
                .put(HOST_ID, "123456789")
                .put(CLOUD_REGION, "us-central1")
                .build();

        GcpResource gce = ResourceTranslator.mapResource(gceResource);

        assertThat(gce.getResourceType()).isEqualTo("gce_instance");
        assertThat(labels(gce)).isEqualTo(Map.of(
                "zone", "us-central1-b",
                "instance_id", "123456789"));

        Resource awsResource = Resource.builder()
                .put(CLOUD_PLATFORM, AWS_EC2)
                .put(HOST_ID, "i-0123456789abcdef0")
                .put(CLOUD_AVAILABILITY_ZONE, "us-east-1a")
                .put(CLOUD_ACCOUNT_ID, "111122223333")
                .build();

        GcpResource aws = ResourceTranslator.mapResource(awsResource);

        assertThat(aws.getResourceType()).isEqualTo("aws_ec2_instance");
        assertThat(labels(aws)).isEqualTo(Map.of(
                "instance_id", "i-0123456789abcdef0",
                "region", "us-east-1a",
                "aws_account", "111122223333"));

        Resource appEngineResource = Resource.builder()
                .put(CLOUD_PLATFORM, GCP_APP_ENGINE)
                .put(FAAS_NAME, "default")
                .put(FAAS_VERSION, "v42")
                .put(FAAS_INSTANCE, "instance-7")
                .put(CLOUD_REGION, "europe-west1")
                .build();

        GcpResource appEngine = ResourceTranslator.mapResource(appEngineResource);

        assertThat(appEngine.getResourceType()).isEqualTo("gae_instance");
        assertThat(labels(appEngine)).isEqualTo(Map.of(
                "module_id", "default",
                "version_id", "v42",
                "instance_id", "instance-7",
                "location", "europe-west1"));
    }

    @Test
    void mapsUnsupportedCloudPlatformUsingAvailableNonCloudResourceAttributes() {
        Resource resource = Resource.builder()
                .put(CLOUD_PLATFORM, "azure_functions")
                .put(CLOUD_REGION, "westus2")
                .put(SERVICE_NAMESPACE, "billing")
                .put(SERVICE_NAME, "settlement-worker")
                .put(SERVICE_INSTANCE_ID, "invocation-42")
                .build();

        GcpResource mappedResource = ResourceTranslator.mapResource(resource);

        assertThat(mappedResource.getResourceType()).isEqualTo("generic_task");
        assertThat(labels(mappedResource)).isEqualTo(Map.of(
                "location", "westus2",
                "namespace", "billing",
                "job", "settlement-worker",
                "task_id", "invocation-42"));
    }

    @Test
    void mapsKubernetesResourcesByMostSpecificAvailableIdentity() {
        Resource containerResource = Resource.builder()
                .put(K8S_CLUSTER_NAME, "cluster-a")
                .put(CLOUD_REGION, "us-central1")
                .put(CLOUD_AVAILABILITY_ZONE, "us-central1-c")
                .put(K8S_NAMESPACE_NAME, "payments")
                .put(K8S_CONTAINER_NAME, "collector")
                .put(K8S_POD_NAME, "collector-abc")
                .put(K8S_NODE_NAME, "node-1")
                .build();

        GcpResource container = ResourceTranslator.mapResource(containerResource);

        assertThat(container.getResourceType()).isEqualTo("k8s_container");
        assertThat(labels(container)).isEqualTo(Map.of(
                "location", "us-central1-c",
                "cluster_name", "cluster-a",
                "namespace_name", "payments",
                "container_name", "collector",
                "pod_name", "collector-abc"));

        Resource podResource = Resource.builder()
                .put(K8S_CLUSTER_NAME, "cluster-a")
                .put(CLOUD_REGION, "us-central1")
                .put(K8S_NAMESPACE_NAME, "payments")
                .put(K8S_POD_NAME, "collector-abc")
                .put(K8S_NODE_NAME, "node-1")
                .build();

        GcpResource pod = ResourceTranslator.mapResource(podResource);

        assertThat(pod.getResourceType()).isEqualTo("k8s_pod");
        assertThat(labels(pod)).isEqualTo(Map.of(
                "location", "us-central1",
                "cluster_name", "cluster-a",
                "namespace_name", "payments",
                "pod_name", "collector-abc"));

        Resource nodeResource = Resource.builder()
                .put(K8S_CLUSTER_NAME, "cluster-a")
                .put(CLOUD_REGION, "us-central1")
                .put(K8S_NODE_NAME, "node-1")
                .build();

        GcpResource node = ResourceTranslator.mapResource(nodeResource);

        assertThat(node.getResourceType()).isEqualTo("k8s_node");
        assertThat(labels(node)).isEqualTo(Map.of(
                "location", "us-central1",
                "cluster_name", "cluster-a",
                "node_name", "node-1"));

        Resource clusterResource = Resource.builder()
                .put(K8S_CLUSTER_NAME, "cluster-a")
                .put(CLOUD_REGION, "us-central1")
                .build();

        GcpResource cluster = ResourceTranslator.mapResource(clusterResource);

        assertThat(cluster.getResourceType()).isEqualTo("k8s_cluster");
        assertThat(labels(cluster)).isEqualTo(Map.of(
                "location", "us-central1",
                "cluster_name", "cluster-a"));
    }

    @Test
    void mapsGenericTaskWithServiceIdentityFallbacksAndUnknownServiceHandling() {
        Resource serviceTaskResource = Resource.builder()
                .put(CLOUD_REGION, "asia-northeast1")
                .put(SERVICE_NAMESPACE, "backend")
                .put(SERVICE_NAME, "checkout")
                .put(SERVICE_INSTANCE_ID, "task-17")
                .put(FAAS_NAME, "function-name")
                .put(FAAS_INSTANCE, "function-instance")
                .build();

        GcpResource serviceTask = ResourceTranslator.mapResource(serviceTaskResource);

        assertThat(serviceTask.getResourceType()).isEqualTo("generic_task");
        assertThat(labels(serviceTask)).isEqualTo(Map.of(
                "location", "asia-northeast1",
                "namespace", "backend",
                "job", "checkout",
                "task_id", "task-17"));

        Resource faasTaskResource = Resource.builder()
                .put(SERVICE_NAME, "unknown_service:java")
                .put(FAAS_NAME, "http-function")
                .put(FAAS_INSTANCE, "function-instance-1")
                .build();

        GcpResource faasTask = ResourceTranslator.mapResource(faasTaskResource);

        assertThat(faasTask.getResourceType()).isEqualTo("generic_task");
        assertThat(labels(faasTask)).isEqualTo(Map.of(
                "location", "global",
                "namespace", "",
                "job", "http-function",
                "task_id", "function-instance-1"));

        Resource unknownOnlyTaskResource = Resource.builder()
                .put(SERVICE_NAME, "unknown_service:java")
                .put(SERVICE_INSTANCE_ID, "task-18")
                .build();

        GcpResource unknownOnlyTask = ResourceTranslator.mapResource(unknownOnlyTaskResource);

        assertThat(unknownOnlyTask.getResourceType()).isEqualTo("generic_task");
        assertThat(labels(unknownOnlyTask)).isEqualTo(Map.of(
                "location", "global",
                "namespace", "",
                "job", "unknown_service:java",
                "task_id", "task-18"));
    }

    @Test
    void mapsGenericNodeWithHostIdentityAndDefaultLabels() {
        Resource namedNodeResource = Resource.builder()
                .put(CLOUD_AVAILABILITY_ZONE, "europe-west4-a")
                .put(SERVICE_NAMESPACE, "edge")
                .put(HOST_ID, "host-123")
                .put(HOST_NAME, "worker-1")
                .build();

        GcpResource namedNode = ResourceTranslator.mapResource(namedNodeResource);

        assertThat(namedNode.getResourceType()).isEqualTo("generic_node");
        assertThat(labels(namedNode)).isEqualTo(Map.of(
                "location", "europe-west4-a",
                "namespace", "edge",
                "node_id", "host-123"));

        GcpResource emptyNode = ResourceTranslator.mapResource(Resource.empty());

        assertThat(emptyNode.getResourceType()).isEqualTo("generic_node");
        assertThat(labels(emptyNode)).isEqualTo(Map.of(
                "location", "global",
                "namespace", "",
                "node_id", ""));
    }

    @Test
    void mapsGenericNodeUsingHostNameWhenHostIdIsUnavailable() {
        Resource hostNameOnlyResource = Resource.builder()
                .put(CLOUD_REGION, "australia-southeast1")
                .put(SERVICE_NAMESPACE, "edge")
                .put(HOST_NAME, "worker-2")
                .build();

        GcpResource hostNameOnlyNode = ResourceTranslator.mapResource(hostNameOnlyResource);

        assertThat(hostNameOnlyNode.getResourceType()).isEqualTo("generic_node");
        assertThat(labels(hostNameOnlyNode)).isEqualTo(Map.of(
                "location", "australia-southeast1",
                "namespace", "edge",
                "node_id", "worker-2"));
    }

    @Test
    void attributeMappingFactoriesExposeConfiguredPriorityAndFallbacks() {
        AttributeMapping singleKey = AttributeMapping.create("zone", CLOUD_AVAILABILITY_ZONE);

        assertThat(singleKey.getLabelName()).isEqualTo("zone");
        assertThat(singleKey.getOtelKeys()).containsExactly(CLOUD_AVAILABILITY_ZONE);
        assertThat(singleKey.fallbackLiteral()).isEmpty();

        AttributeMapping singleKeyWithFallback = AttributeMapping.create(
                "namespace", SERVICE_NAMESPACE, "default");

        assertThat(singleKeyWithFallback.getLabelName()).isEqualTo("namespace");
        assertThat(singleKeyWithFallback.getOtelKeys()).containsExactly(SERVICE_NAMESPACE);
        assertThat(singleKeyWithFallback.fallbackLiteral()).isEqualTo(Optional.of("default"));

        List<AttributeKey<?>> prioritizedKeys = List.of(
                CLOUD_AVAILABILITY_ZONE,
                CLOUD_REGION);
        AttributeMapping multipleKeys = AttributeMapping.create("location", prioritizedKeys);

        assertThat(multipleKeys.getLabelName()).isEqualTo("location");
        assertThat(multipleKeys.getOtelKeys()).containsExactlyElementsOf(prioritizedKeys);
        assertThat(multipleKeys.fallbackLiteral()).isEmpty();

        AttributeMapping multipleKeysWithFallback = AttributeMapping.create("location", prioritizedKeys, "global");

        assertThat(multipleKeysWithFallback.getLabelName()).isEqualTo("location");
        assertThat(multipleKeysWithFallback.getOtelKeys()).containsExactlyElementsOf(prioritizedKeys);
        assertThat(multipleKeysWithFallback.fallbackLiteral()).isEqualTo(Optional.of("global"));
    }

    @Test
    void semanticConventionKeysTemplatesAndValuesAreUsableWithOpenTelemetryAttributes() {
        assertAttributeKey(ServerAttributes.SERVER_ADDRESS, "server.address", AttributeType.STRING);
        assertAttributeKey(ServerAttributes.SERVER_PORT, "server.port", AttributeType.LONG);
        assertAttributeKey(HttpAttributes.HTTP_REQUEST_METHOD, "http.request.method", AttributeType.STRING);
        assertAttributeKey(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, "http.response.status_code", AttributeType.LONG);
        assertAttributeKey(NetworkAttributes.NETWORK_PROTOCOL_NAME, "network.protocol.name", AttributeType.STRING);
        assertAttributeKey(NetworkAttributes.NETWORK_TRANSPORT, "network.transport", AttributeType.STRING);

        assertThat(SchemaUrls.V1_23_1).isEqualTo("https://opentelemetry.io/schemas/1.23.1");
        assertThat(HttpAttributes.HttpRequestMethodValues.POST).isEqualTo("POST");
        assertThat(NetworkAttributes.NetworkTransportValues.TCP).isEqualTo("tcp");

        AttributeKey<List<String>> requestHeaderKey =
                HttpAttributes.HTTP_REQUEST_HEADER.getAttributeKey("content-type");
        assertAttributeKey(requestHeaderKey, "http.request.header.content-type", AttributeType.STRING_ARRAY);
        assertThat(HttpAttributes.HTTP_REQUEST_HEADER.getAttributeKey("content-type")).isSameAs(requestHeaderKey);

        AttributeKey<List<String>> responseHeaderKey =
                HttpAttributes.HTTP_RESPONSE_HEADER.getAttributeKey("x-trace-id");
        assertAttributeKey(responseHeaderKey, "http.response.header.x-trace-id", AttributeType.STRING_ARRAY);

        AttributeKeyTemplate<Boolean> featureTemplate = AttributeKeyTemplate.booleanKeyTemplate("feature.enabled");
        assertAttributeKey(featureTemplate.getAttributeKey("beta"), "feature.enabled.beta", AttributeType.BOOLEAN);

        AttributeKeyTemplate<List<Long>> bucketTemplate = AttributeKeyTemplate.longArrayKeyTemplate("bucket.bounds");
        assertAttributeKey(
                bucketTemplate.getAttributeKey("latency"), "bucket.bounds.latency", AttributeType.LONG_ARRAY);
    }

    private static Map<String, String> labels(GcpResource resource) {
        return resource.getResourceLabels().getLabels();
    }

    private static void assertAttributeKey(AttributeKey<?> key, String expectedName, AttributeType expectedType) {
        assertThat(key.getKey()).isEqualTo(expectedName);
        assertThat(key.getType()).isEqualTo(expectedType);
    }
}
