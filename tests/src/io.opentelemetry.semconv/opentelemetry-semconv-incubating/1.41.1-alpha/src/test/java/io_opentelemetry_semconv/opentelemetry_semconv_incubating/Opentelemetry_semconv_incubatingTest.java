/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_opentelemetry_semconv.opentelemetry_semconv_incubating;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.semconv.AttributeKeyTemplate;
import io.opentelemetry.semconv.incubating.CicdIncubatingAttributes;
import io.opentelemetry.semconv.incubating.CicdIncubatingAttributes.CicdPipelineActionNameIncubatingValues;
import io.opentelemetry.semconv.incubating.CicdIncubatingAttributes.CicdPipelineResultIncubatingValues;
import io.opentelemetry.semconv.incubating.CicdIncubatingAttributes.CicdPipelineRunStateIncubatingValues;
import io.opentelemetry.semconv.incubating.CicdIncubatingAttributes.CicdPipelineTaskRunResultIncubatingValues;
import io.opentelemetry.semconv.incubating.CicdIncubatingAttributes.CicdPipelineTaskTypeIncubatingValues;
import io.opentelemetry.semconv.incubating.CicdIncubatingAttributes.CicdWorkerStateIncubatingValues;
import io.opentelemetry.semconv.incubating.CloudIncubatingAttributes;
import io.opentelemetry.semconv.incubating.CloudIncubatingAttributes.CloudProviderIncubatingValues;
import io.opentelemetry.semconv.incubating.ContainerIncubatingAttributes;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbClientConnectionStateIncubatingValues;
import io.opentelemetry.semconv.incubating.FaasIncubatingAttributes;
import io.opentelemetry.semconv.incubating.FaasIncubatingAttributes.FaasDocumentOperationIncubatingValues;
import io.opentelemetry.semconv.incubating.FaasIncubatingAttributes.FaasTriggerIncubatingValues;
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes;
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GenAiOperationNameIncubatingValues;
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GenAiOutputTypeIncubatingValues;
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GenAiProviderNameIncubatingValues;
import io.opentelemetry.semconv.incubating.HostIncubatingAttributes;
import io.opentelemetry.semconv.incubating.HostIncubatingAttributes.HostArchIncubatingValues;
import io.opentelemetry.semconv.incubating.HttpIncubatingAttributes;
import io.opentelemetry.semconv.incubating.JvmIncubatingAttributes;
import io.opentelemetry.semconv.incubating.K8sIncubatingAttributes;
import io.opentelemetry.semconv.incubating.K8sIncubatingAttributes.K8sContainerStatusStateIncubatingValues;
import io.opentelemetry.semconv.incubating.K8sIncubatingAttributes.K8sPodStatusPhaseIncubatingValues;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MessagingOperationTypeIncubatingValues;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MessagingSystemIncubatingValues;
import io.opentelemetry.semconv.incubating.OsIncubatingAttributes;
import io.opentelemetry.semconv.incubating.OsIncubatingAttributes.OsTypeIncubatingValues;
import io.opentelemetry.semconv.incubating.OtelIncubatingAttributes;
import io.opentelemetry.semconv.incubating.OtelIncubatingAttributes.OtelComponentTypeIncubatingValues;
import io.opentelemetry.semconv.incubating.OtelIncubatingAttributes.OtelSpanSamplingResultIncubatingValues;
import io.opentelemetry.semconv.incubating.ProcessIncubatingAttributes;
import io.opentelemetry.semconv.incubating.ProcessIncubatingAttributes.ProcessContextSwitchTypeIncubatingValues;
import io.opentelemetry.semconv.incubating.ProcessIncubatingAttributes.ProcessStateIncubatingValues;
import io.opentelemetry.semconv.incubating.RpcIncubatingAttributes;
import io.opentelemetry.semconv.incubating.ServiceIncubatingAttributes;
import io.opentelemetry.semconv.incubating.ServiceIncubatingAttributes.ServiceCriticalityIncubatingValues;
import java.util.List;
import org.junit.jupiter.api.Test;

public class Opentelemetry_semconv_incubatingTest {
    @Test
    void cicdIncubatingAttributesRepresentPipelineTaskAndWorkerState() {
        Attributes attributes = Attributes.builder()
                .put(CicdIncubatingAttributes.CICD_PIPELINE_ACTION_NAME, CicdPipelineActionNameIncubatingValues.BUILD)
                .put(CicdIncubatingAttributes.CICD_PIPELINE_NAME, "release-pipeline")
                .put(CicdIncubatingAttributes.CICD_PIPELINE_RESULT, CicdPipelineResultIncubatingValues.SUCCESS)
                .put(CicdIncubatingAttributes.CICD_PIPELINE_RUN_ID, "run-123")
                .put(CicdIncubatingAttributes.CICD_PIPELINE_RUN_STATE, CicdPipelineRunStateIncubatingValues.EXECUTING)
                .put(CicdIncubatingAttributes.CICD_PIPELINE_RUN_URL_FULL, "https://ci.example.test/runs/run-123")
                .put(CicdIncubatingAttributes.CICD_PIPELINE_TASK_NAME, "native-test")
                .put(CicdIncubatingAttributes.CICD_PIPELINE_TASK_RUN_ID, "task-run-456")
                .put(
                        CicdIncubatingAttributes.CICD_PIPELINE_TASK_RUN_RESULT,
                        CicdPipelineTaskRunResultIncubatingValues.SUCCESS)
                .put(
                        CicdIncubatingAttributes.CICD_PIPELINE_TASK_RUN_URL_FULL,
                        "https://ci.example.test/runs/run-123/tasks/task-run-456")
                .put(CicdIncubatingAttributes.CICD_PIPELINE_TASK_TYPE, CicdPipelineTaskTypeIncubatingValues.TEST)
                .put(CicdIncubatingAttributes.CICD_SYSTEM_COMPONENT, "worker")
                .put(CicdIncubatingAttributes.CICD_WORKER_ID, "worker-7")
                .put(CicdIncubatingAttributes.CICD_WORKER_NAME, "linux-arm64-7")
                .put(CicdIncubatingAttributes.CICD_WORKER_STATE, CicdWorkerStateIncubatingValues.BUSY)
                .put(CicdIncubatingAttributes.CICD_WORKER_URL_FULL, "https://ci.example.test/workers/worker-7")
                .build();

        assertThat(CicdIncubatingAttributes.CICD_PIPELINE_ACTION_NAME)
                .isEqualTo(AttributeKey.stringKey("cicd.pipeline.action.name"));
        assertThat(CicdIncubatingAttributes.CICD_PIPELINE_RESULT)
                .isEqualTo(AttributeKey.stringKey("cicd.pipeline.result"));
        assertThat(CicdIncubatingAttributes.CICD_PIPELINE_RUN_STATE)
                .isEqualTo(AttributeKey.stringKey("cicd.pipeline.run.state"));
        assertThat(CicdIncubatingAttributes.CICD_PIPELINE_TASK_RUN_RESULT)
                .isEqualTo(AttributeKey.stringKey("cicd.pipeline.task.run.result"));
        assertThat(CicdIncubatingAttributes.CICD_PIPELINE_TASK_TYPE)
                .isEqualTo(AttributeKey.stringKey("cicd.pipeline.task.type"));
        assertThat(CicdIncubatingAttributes.CICD_WORKER_STATE)
                .isEqualTo(AttributeKey.stringKey("cicd.worker.state"));
        assertThat(attributes.get(CicdIncubatingAttributes.CICD_PIPELINE_ACTION_NAME)).isEqualTo("BUILD");
        assertThat(attributes.get(CicdIncubatingAttributes.CICD_PIPELINE_NAME)).isEqualTo("release-pipeline");
        assertThat(attributes.get(CicdIncubatingAttributes.CICD_PIPELINE_RESULT)).isEqualTo("success");
        assertThat(attributes.get(CicdIncubatingAttributes.CICD_PIPELINE_RUN_ID)).isEqualTo("run-123");
        assertThat(attributes.get(CicdIncubatingAttributes.CICD_PIPELINE_RUN_STATE)).isEqualTo("executing");
        assertThat(attributes.get(CicdIncubatingAttributes.CICD_PIPELINE_RUN_URL_FULL))
                .isEqualTo("https://ci.example.test/runs/run-123");
        assertThat(attributes.get(CicdIncubatingAttributes.CICD_PIPELINE_TASK_NAME)).isEqualTo("native-test");
        assertThat(attributes.get(CicdIncubatingAttributes.CICD_PIPELINE_TASK_RUN_ID)).isEqualTo("task-run-456");
        assertThat(attributes.get(CicdIncubatingAttributes.CICD_PIPELINE_TASK_RUN_RESULT)).isEqualTo("success");
        assertThat(attributes.get(CicdIncubatingAttributes.CICD_PIPELINE_TASK_RUN_URL_FULL))
                .isEqualTo("https://ci.example.test/runs/run-123/tasks/task-run-456");
        assertThat(attributes.get(CicdIncubatingAttributes.CICD_PIPELINE_TASK_TYPE)).isEqualTo("test");
        assertThat(attributes.get(CicdIncubatingAttributes.CICD_SYSTEM_COMPONENT)).isEqualTo("worker");
        assertThat(attributes.get(CicdIncubatingAttributes.CICD_WORKER_ID)).isEqualTo("worker-7");
        assertThat(attributes.get(CicdIncubatingAttributes.CICD_WORKER_NAME)).isEqualTo("linux-arm64-7");
        assertThat(attributes.get(CicdIncubatingAttributes.CICD_WORKER_STATE)).isEqualTo("busy");
        assertThat(attributes.get(CicdIncubatingAttributes.CICD_WORKER_URL_FULL))
                .isEqualTo("https://ci.example.test/workers/worker-7");
        assertThat(CicdPipelineActionNameIncubatingValues.SYNC).isEqualTo("SYNC");
        assertThat(CicdPipelineResultIncubatingValues.TIMEOUT).isEqualTo("timeout");
        assertThat(CicdPipelineRunStateIncubatingValues.FINALIZING).isEqualTo("finalizing");
        assertThat(CicdPipelineTaskRunResultIncubatingValues.SKIP).isEqualTo("skip");
        assertThat(CicdPipelineTaskTypeIncubatingValues.DEPLOY).isEqualTo("deploy");
        assertThat(CicdWorkerStateIncubatingValues.OFFLINE).isEqualTo("offline");
    }

    @Test
    void httpIncubatingAttributesExposeConnectionAndPayloadSizeKeys() {
        Attributes attributes = Attributes.builder()
                .put(HttpIncubatingAttributes.HTTP_CONNECTION_STATE, "active")
                .put(HttpIncubatingAttributes.HTTP_REQUEST_BODY_SIZE, 1024L)
                .put(HttpIncubatingAttributes.HTTP_REQUEST_SIZE, 1280L)
                .put(HttpIncubatingAttributes.HTTP_RESPONSE_BODY_SIZE, 2048L)
                .put(HttpIncubatingAttributes.HTTP_RESPONSE_SIZE, 2304L)
                .build();

        assertThat(HttpIncubatingAttributes.HTTP_CONNECTION_STATE)
                .isEqualTo(AttributeKey.stringKey("http.connection.state"));
        assertThat(HttpIncubatingAttributes.HTTP_REQUEST_BODY_SIZE)
                .isEqualTo(AttributeKey.longKey("http.request.body.size"));
        assertThat(HttpIncubatingAttributes.HTTP_REQUEST_SIZE)
                .isEqualTo(AttributeKey.longKey("http.request.size"));
        assertThat(HttpIncubatingAttributes.HTTP_RESPONSE_BODY_SIZE)
                .isEqualTo(AttributeKey.longKey("http.response.body.size"));
        assertThat(HttpIncubatingAttributes.HTTP_RESPONSE_SIZE)
                .isEqualTo(AttributeKey.longKey("http.response.size"));
        assertThat(attributes.get(HttpIncubatingAttributes.HTTP_CONNECTION_STATE)).isEqualTo("active");
        assertThat(attributes.get(HttpIncubatingAttributes.HTTP_REQUEST_BODY_SIZE)).isEqualTo(1024L);
        assertThat(attributes.get(HttpIncubatingAttributes.HTTP_REQUEST_SIZE)).isEqualTo(1280L);
        assertThat(attributes.get(HttpIncubatingAttributes.HTTP_RESPONSE_BODY_SIZE)).isEqualTo(2048L);
        assertThat(attributes.get(HttpIncubatingAttributes.HTTP_RESPONSE_SIZE)).isEqualTo(2304L);
    }

    @Test
    void databaseIncubatingAttributesCoverConnectionStateRowsAndParameterTemplates() {
        AttributeKey<String> operationParameter =
                DbIncubatingAttributes.DB_OPERATION_PARAMETER.getAttributeKey("order_id");
        AttributeKey<String> queryParameter = DbIncubatingAttributes.DB_QUERY_PARAMETER.getAttributeKey("limit");
        Attributes attributes = Attributes.builder()
                .put(DbIncubatingAttributes.DB_CLIENT_CONNECTION_POOL_NAME, "orders-pool")
                .put(DbIncubatingAttributes.DB_CLIENT_CONNECTION_STATE, DbClientConnectionStateIncubatingValues.USED)
                .put(DbIncubatingAttributes.DB_RESPONSE_RETURNED_ROWS, 3L)
                .put(operationParameter, "order-123")
                .put(queryParameter, "25")
                .build();

        assertThat(DbIncubatingAttributes.DB_CLIENT_CONNECTION_POOL_NAME)
                .isEqualTo(AttributeKey.stringKey("db.client.connection.pool.name"));
        assertThat(DbIncubatingAttributes.DB_CLIENT_CONNECTION_STATE)
                .isEqualTo(AttributeKey.stringKey("db.client.connection.state"));
        assertThat(DbIncubatingAttributes.DB_RESPONSE_RETURNED_ROWS)
                .isEqualTo(AttributeKey.longKey("db.response.returned_rows"));
        assertThat(operationParameter).isEqualTo(AttributeKey.stringKey("db.operation.parameter.order_id"));
        assertThat(queryParameter).isEqualTo(AttributeKey.stringKey("db.query.parameter.limit"));
        assertThat(DbIncubatingAttributes.DB_OPERATION_PARAMETER.getAttributeKey("order_id"))
                .isSameAs(operationParameter);
        assertThat(attributes.get(DbIncubatingAttributes.DB_CLIENT_CONNECTION_POOL_NAME)).isEqualTo("orders-pool");
        assertThat(attributes.get(DbIncubatingAttributes.DB_CLIENT_CONNECTION_STATE)).isEqualTo("used");
        assertThat(attributes.get(DbIncubatingAttributes.DB_RESPONSE_RETURNED_ROWS)).isEqualTo(3L);
        assertThat(attributes.get(operationParameter)).isEqualTo("order-123");
        assertThat(attributes.get(queryParameter)).isEqualTo("25");
        assertThat(DbClientConnectionStateIncubatingValues.IDLE).isEqualTo("idle");
    }

    @Test
    void genAiIncubatingAttributesCoverTypedRequestResponseUsageAndGeneratedValues() {
        Attributes attributes = Attributes.builder()
                .put(GenAiIncubatingAttributes.GEN_AI_OPERATION_NAME, GenAiOperationNameIncubatingValues.CHAT)
                .put(GenAiIncubatingAttributes.GEN_AI_PROVIDER_NAME, GenAiProviderNameIncubatingValues.OPENAI)
                .put(GenAiIncubatingAttributes.GEN_AI_OUTPUT_TYPE, GenAiOutputTypeIncubatingValues.JSON)
                .put(GenAiIncubatingAttributes.GEN_AI_REQUEST_MODEL, "gpt-test")
                .put(GenAiIncubatingAttributes.GEN_AI_REQUEST_CHOICE_COUNT, 2L)
                .put(GenAiIncubatingAttributes.GEN_AI_REQUEST_MAX_TOKENS, 512L)
                .put(GenAiIncubatingAttributes.GEN_AI_REQUEST_TEMPERATURE, 0.2D)
                .put(GenAiIncubatingAttributes.GEN_AI_REQUEST_TOP_P, 0.95D)
                .put(GenAiIncubatingAttributes.GEN_AI_REQUEST_STREAM, true)
                .put(GenAiIncubatingAttributes.GEN_AI_REQUEST_ENCODING_FORMATS, List.of("float", "base64"))
                .put(GenAiIncubatingAttributes.GEN_AI_REQUEST_STOP_SEQUENCES, List.of("\n\n", "END"))
                .put(GenAiIncubatingAttributes.GEN_AI_RESPONSE_ID, "chatcmpl-123")
                .put(GenAiIncubatingAttributes.GEN_AI_RESPONSE_MODEL, "gpt-test-2024")
                .put(GenAiIncubatingAttributes.GEN_AI_RESPONSE_FINISH_REASONS, List.of("stop", "length"))
                .put(GenAiIncubatingAttributes.GEN_AI_USAGE_INPUT_TOKENS, 15L)
                .put(GenAiIncubatingAttributes.GEN_AI_USAGE_OUTPUT_TOKENS, 20L)
                .put(GenAiIncubatingAttributes.GEN_AI_USAGE_REASONING_OUTPUT_TOKENS, 5L)
                .build();

        assertThat(GenAiIncubatingAttributes.GEN_AI_OPERATION_NAME)
                .isEqualTo(AttributeKey.stringKey("gen_ai.operation.name"));
        assertThat(GenAiIncubatingAttributes.GEN_AI_REQUEST_CHOICE_COUNT)
                .isEqualTo(AttributeKey.longKey("gen_ai.request.choice.count"));
        assertThat(GenAiIncubatingAttributes.GEN_AI_REQUEST_TEMPERATURE)
                .isEqualTo(AttributeKey.doubleKey("gen_ai.request.temperature"));
        assertThat(GenAiIncubatingAttributes.GEN_AI_REQUEST_STREAM)
                .isEqualTo(AttributeKey.booleanKey("gen_ai.request.stream"));
        assertThat(GenAiIncubatingAttributes.GEN_AI_REQUEST_ENCODING_FORMATS)
                .isEqualTo(AttributeKey.stringArrayKey("gen_ai.request.encoding_formats"));
        assertThat(attributes.get(GenAiIncubatingAttributes.GEN_AI_OPERATION_NAME)).isEqualTo("chat");
        assertThat(attributes.get(GenAiIncubatingAttributes.GEN_AI_PROVIDER_NAME)).isEqualTo("openai");
        assertThat(attributes.get(GenAiIncubatingAttributes.GEN_AI_OUTPUT_TYPE)).isEqualTo("json");
        assertThat(attributes.get(GenAiIncubatingAttributes.GEN_AI_REQUEST_MODEL)).isEqualTo("gpt-test");
        assertThat(attributes.get(GenAiIncubatingAttributes.GEN_AI_REQUEST_CHOICE_COUNT)).isEqualTo(2L);
        assertThat(attributes.get(GenAiIncubatingAttributes.GEN_AI_REQUEST_MAX_TOKENS)).isEqualTo(512L);
        assertThat(attributes.get(GenAiIncubatingAttributes.GEN_AI_REQUEST_TEMPERATURE)).isEqualTo(0.2D);
        assertThat(attributes.get(GenAiIncubatingAttributes.GEN_AI_REQUEST_TOP_P)).isEqualTo(0.95D);
        assertThat(attributes.get(GenAiIncubatingAttributes.GEN_AI_REQUEST_STREAM)).isTrue();
        assertThat(attributes.get(GenAiIncubatingAttributes.GEN_AI_REQUEST_ENCODING_FORMATS))
                .containsExactly("float", "base64");
        assertThat(attributes.get(GenAiIncubatingAttributes.GEN_AI_REQUEST_STOP_SEQUENCES))
                .containsExactly("\n\n", "END");
        assertThat(attributes.get(GenAiIncubatingAttributes.GEN_AI_RESPONSE_FINISH_REASONS))
                .containsExactly("stop", "length");
        assertThat(attributes.get(GenAiIncubatingAttributes.GEN_AI_USAGE_INPUT_TOKENS)).isEqualTo(15L);
        assertThat(attributes.get(GenAiIncubatingAttributes.GEN_AI_USAGE_OUTPUT_TOKENS)).isEqualTo(20L);
        assertThat(attributes.get(GenAiIncubatingAttributes.GEN_AI_USAGE_REASONING_OUTPUT_TOKENS)).isEqualTo(5L);
        assertThat(GenAiOperationNameIncubatingValues.EXECUTE_TOOL).isEqualTo("execute_tool");
        assertThat(GenAiProviderNameIncubatingValues.AWS_BEDROCK).isEqualTo("aws.bedrock");
        assertThat(GenAiOutputTypeIncubatingValues.IMAGE).isEqualTo("image");
    }

    @Test
    void messagingIncubatingAttributesRepresentCommonBrokerAndMessageDetails() {
        Attributes attributes = Attributes.builder()
                .put(MessagingIncubatingAttributes.MESSAGING_SYSTEM, MessagingSystemIncubatingValues.KAFKA)
                .put(
                        MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE,
                        MessagingOperationTypeIncubatingValues.PROCESS)
                .put(MessagingIncubatingAttributes.MESSAGING_BATCH_MESSAGE_COUNT, 4L)
                .put(MessagingIncubatingAttributes.MESSAGING_CLIENT_ID, "consumer-1")
                .put(MessagingIncubatingAttributes.MESSAGING_CONSUMER_GROUP_NAME, "checkout-workers")
                .put(MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME, "orders")
                .put(MessagingIncubatingAttributes.MESSAGING_DESTINATION_PARTITION_ID, "7")
                .put(MessagingIncubatingAttributes.MESSAGING_DESTINATION_ANONYMOUS, false)
                .put(MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID, "message-123")
                .put(MessagingIncubatingAttributes.MESSAGING_MESSAGE_BODY_SIZE, 128L)
                .put(MessagingIncubatingAttributes.MESSAGING_KAFKA_OFFSET, 42L)
                .put(MessagingIncubatingAttributes.MESSAGING_KAFKA_MESSAGE_TOMBSTONE, false)
                .put(MessagingIncubatingAttributes.MESSAGING_ROCKETMQ_MESSAGE_KEYS, List.of("key-a", "key-b"))
                .build();

        assertThat(MessagingIncubatingAttributes.MESSAGING_SYSTEM)
                .isEqualTo(AttributeKey.stringKey("messaging.system"));
        assertThat(MessagingIncubatingAttributes.MESSAGING_BATCH_MESSAGE_COUNT)
                .isEqualTo(AttributeKey.longKey("messaging.batch.message_count"));
        assertThat(MessagingIncubatingAttributes.MESSAGING_DESTINATION_ANONYMOUS)
                .isEqualTo(AttributeKey.booleanKey("messaging.destination.anonymous"));
        assertThat(MessagingIncubatingAttributes.MESSAGING_ROCKETMQ_MESSAGE_KEYS)
                .isEqualTo(AttributeKey.stringArrayKey("messaging.rocketmq.message.keys"));
        assertThat(attributes.get(MessagingIncubatingAttributes.MESSAGING_SYSTEM)).isEqualTo("kafka");
        assertThat(attributes.get(MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE)).isEqualTo("process");
        assertThat(attributes.get(MessagingIncubatingAttributes.MESSAGING_BATCH_MESSAGE_COUNT)).isEqualTo(4L);
        assertThat(attributes.get(MessagingIncubatingAttributes.MESSAGING_CLIENT_ID)).isEqualTo("consumer-1");
        assertThat(attributes.get(MessagingIncubatingAttributes.MESSAGING_CONSUMER_GROUP_NAME))
                .isEqualTo("checkout-workers");
        assertThat(attributes.get(MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME)).isEqualTo("orders");
        assertThat(attributes.get(MessagingIncubatingAttributes.MESSAGING_DESTINATION_PARTITION_ID)).isEqualTo("7");
        assertThat(attributes.get(MessagingIncubatingAttributes.MESSAGING_DESTINATION_ANONYMOUS)).isFalse();
        assertThat(attributes.get(MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID)).isEqualTo("message-123");
        assertThat(attributes.get(MessagingIncubatingAttributes.MESSAGING_MESSAGE_BODY_SIZE)).isEqualTo(128L);
        assertThat(attributes.get(MessagingIncubatingAttributes.MESSAGING_KAFKA_OFFSET)).isEqualTo(42L);
        assertThat(attributes.get(MessagingIncubatingAttributes.MESSAGING_KAFKA_MESSAGE_TOMBSTONE)).isFalse();
        assertThat(attributes.get(MessagingIncubatingAttributes.MESSAGING_ROCKETMQ_MESSAGE_KEYS))
                .containsExactly("key-a", "key-b");
        assertThat(MessagingSystemIncubatingValues.AWS_SNS).isEqualTo("aws.sns");
        assertThat(MessagingSystemIncubatingValues.AWS_SQS).isEqualTo("aws_sqs");
        assertThat(MessagingOperationTypeIncubatingValues.SETTLE).isEqualTo("settle");
    }

    @Test
    void kubernetesIncubatingAttributesUseTemplateKeysForLabelsAnnotationsAndSelectors() {
        AttributeKey<String> podLabel = K8sIncubatingAttributes.K8S_POD_LABEL.getAttributeKey("app");
        AttributeKey<String> podAnnotation = K8sIncubatingAttributes.K8S_POD_ANNOTATION
                .getAttributeKey("instrumentation.opentelemetry.io/inject-java");
        AttributeKey<String> serviceSelector = K8sIncubatingAttributes.K8S_SERVICE_SELECTOR.getAttributeKey("app");
        Attributes attributes = Attributes.builder()
                .put(K8sIncubatingAttributes.K8S_CLUSTER_NAME, "production")
                .put(K8sIncubatingAttributes.K8S_NAMESPACE_NAME, "checkout")
                .put(K8sIncubatingAttributes.K8S_POD_NAME, "checkout-7d4f")
                .put(K8sIncubatingAttributes.K8S_POD_UID, "pod-uid")
                .put(K8sIncubatingAttributes.K8S_POD_STATUS_PHASE, K8sPodStatusPhaseIncubatingValues.RUNNING)
                .put(K8sIncubatingAttributes.K8S_CONTAINER_NAME, "app")
                .put(K8sIncubatingAttributes.K8S_CONTAINER_RESTART_COUNT, 2L)
                .put(
                        K8sIncubatingAttributes.K8S_CONTAINER_STATUS_STATE,
                        K8sContainerStatusStateIncubatingValues.RUNNING)
                .put(K8sIncubatingAttributes.K8S_SERVICE_NAME, "checkout")
                .put(K8sIncubatingAttributes.K8S_SERVICE_TYPE, "ClusterIP")
                .put(K8sIncubatingAttributes.K8S_SERVICE_UID, "service-uid")
                .put(K8sIncubatingAttributes.K8S_SERVICE_PUBLISH_NOT_READY_ADDRESSES, true)
                .put(podLabel, "checkout")
                .put(podAnnotation, "true")
                .put(serviceSelector, "checkout")
                .build();

        assertThat(K8sIncubatingAttributes.K8S_CLUSTER_NAME).isEqualTo(AttributeKey.stringKey("k8s.cluster.name"));
        assertThat(K8sIncubatingAttributes.K8S_CONTAINER_RESTART_COUNT)
                .isEqualTo(AttributeKey.longKey("k8s.container.restart_count"));
        assertThat(K8sIncubatingAttributes.K8S_SERVICE_PUBLISH_NOT_READY_ADDRESSES)
                .isEqualTo(AttributeKey.booleanKey("k8s.service.publish_not_ready_addresses"));
        assertThat(podLabel).isEqualTo(AttributeKey.stringKey("k8s.pod.label.app"));
        assertThat(podAnnotation)
                .isEqualTo(AttributeKey.stringKey("k8s.pod.annotation.instrumentation.opentelemetry.io/inject-java"));
        assertThat(serviceSelector).isEqualTo(AttributeKey.stringKey("k8s.service.selector.app"));
        assertThat(K8sIncubatingAttributes.K8S_POD_LABEL.getAttributeKey("app")).isSameAs(podLabel);
        assertThat(attributes.get(K8sIncubatingAttributes.K8S_CLUSTER_NAME)).isEqualTo("production");
        assertThat(attributes.get(K8sIncubatingAttributes.K8S_NAMESPACE_NAME)).isEqualTo("checkout");
        assertThat(attributes.get(K8sIncubatingAttributes.K8S_POD_NAME)).isEqualTo("checkout-7d4f");
        assertThat(attributes.get(K8sIncubatingAttributes.K8S_POD_STATUS_PHASE)).isEqualTo("Running");
        assertThat(attributes.get(K8sIncubatingAttributes.K8S_CONTAINER_STATUS_STATE)).isEqualTo("running");
        assertThat(attributes.get(K8sIncubatingAttributes.K8S_CONTAINER_RESTART_COUNT)).isEqualTo(2L);
        assertThat(attributes.get(K8sIncubatingAttributes.K8S_SERVICE_TYPE)).isEqualTo("ClusterIP");
        assertThat(attributes.get(K8sIncubatingAttributes.K8S_SERVICE_UID)).isEqualTo("service-uid");
        assertThat(attributes.get(K8sIncubatingAttributes.K8S_SERVICE_PUBLISH_NOT_READY_ADDRESSES)).isTrue();
        assertThat(attributes.get(podLabel)).isEqualTo("checkout");
        assertThat(attributes.get(podAnnotation)).isEqualTo("true");
        assertThat(attributes.get(serviceSelector)).isEqualTo("checkout");
        assertThat(K8sPodStatusPhaseIncubatingValues.SUCCEEDED).isEqualTo("Succeeded");
        assertThat(K8sContainerStatusStateIncubatingValues.TERMINATED).isEqualTo("terminated");
    }

    @Test
    void cloudFaasRpcOtelAndServiceIncubatingAttributesCoverResourceAndComponentData() {
        AttributeKey<List<String>> rpcRequestMetadata =
                RpcIncubatingAttributes.RPC_REQUEST_METADATA.getAttributeKey("x-tenant-id");
        AttributeKey<List<String>> rpcResponseMetadata =
                RpcIncubatingAttributes.RPC_RESPONSE_METADATA.getAttributeKey("server-timing");
        Attributes attributes = Attributes.builder()
                .put(CloudIncubatingAttributes.CLOUD_PROVIDER, CloudProviderIncubatingValues.AWS)
                .put(CloudIncubatingAttributes.CLOUD_REGION, "us-east-1")
                .put(CloudIncubatingAttributes.CLOUD_ACCOUNT_ID, "123456789012")
                .put(FaasIncubatingAttributes.FAAS_COLDSTART, true)
                .put(FaasIncubatingAttributes.FAAS_TRIGGER, FaasTriggerIncubatingValues.HTTP)
                .put(FaasIncubatingAttributes.FAAS_DOCUMENT_OPERATION, FaasDocumentOperationIncubatingValues.INSERT)
                .put(FaasIncubatingAttributes.FAAS_NAME, "checkout-handler")
                .put(FaasIncubatingAttributes.FAAS_INVOCATION_ID, "invoke-123")
                .put(FaasIncubatingAttributes.FAAS_MAX_MEMORY, 512L)
                .put(RpcIncubatingAttributes.RPC_SYSTEM_NAME, "grpc")
                .put(RpcIncubatingAttributes.RPC_METHOD, "CreateOrder")
                .put(RpcIncubatingAttributes.RPC_RESPONSE_STATUS_CODE, "OK")
                .put(OtelIncubatingAttributes.OTEL_COMPONENT_NAME, "span-exporter")
                .put(
                        OtelIncubatingAttributes.OTEL_COMPONENT_TYPE,
                        OtelComponentTypeIncubatingValues.OTLP_GRPC_SPAN_EXPORTER)
                .put(OtelIncubatingAttributes.OTEL_SCOPE_SCHEMA_URL, "https://example.test/schema")
                .put(
                        OtelIncubatingAttributes.OTEL_SPAN_SAMPLING_RESULT,
                        OtelSpanSamplingResultIncubatingValues.RECORD_AND_SAMPLE)
                .put(ServiceIncubatingAttributes.SERVICE_CRITICALITY, ServiceCriticalityIncubatingValues.HIGH)
                .put(ServiceIncubatingAttributes.SERVICE_PEER_NAME, "payments")
                .put(ServiceIncubatingAttributes.SERVICE_PEER_NAMESPACE, "shop")
                .put(rpcRequestMetadata, List.of("tenant-a"))
                .put(rpcResponseMetadata, List.of("dur=12"))
                .build();

        assertThat(CloudIncubatingAttributes.CLOUD_PROVIDER).isEqualTo(AttributeKey.stringKey("cloud.provider"));
        assertThat(FaasIncubatingAttributes.FAAS_COLDSTART).isEqualTo(AttributeKey.booleanKey("faas.coldstart"));
        assertThat(FaasIncubatingAttributes.FAAS_MAX_MEMORY).isEqualTo(AttributeKey.longKey("faas.max_memory"));
        assertThat(RpcIncubatingAttributes.RPC_RESPONSE_STATUS_CODE)
                .isEqualTo(AttributeKey.stringKey("rpc.response.status_code"));
        assertThat(OtelIncubatingAttributes.OTEL_COMPONENT_TYPE)
                .isEqualTo(AttributeKey.stringKey("otel.component.type"));
        assertThat(ServiceIncubatingAttributes.SERVICE_CRITICALITY)
                .isEqualTo(AttributeKey.stringKey("service.criticality"));
        assertThat(rpcRequestMetadata).isEqualTo(AttributeKey.stringArrayKey("rpc.request.metadata.x-tenant-id"));
        assertThat(rpcResponseMetadata).isEqualTo(AttributeKey.stringArrayKey("rpc.response.metadata.server-timing"));
        assertThat(RpcIncubatingAttributes.RPC_REQUEST_METADATA.getAttributeKey("x-tenant-id"))
                .isSameAs(rpcRequestMetadata);
        assertThat(attributes.get(CloudIncubatingAttributes.CLOUD_PROVIDER)).isEqualTo("aws");
        assertThat(attributes.get(CloudIncubatingAttributes.CLOUD_REGION)).isEqualTo("us-east-1");
        assertThat(attributes.get(FaasIncubatingAttributes.FAAS_COLDSTART)).isTrue();
        assertThat(attributes.get(FaasIncubatingAttributes.FAAS_TRIGGER)).isEqualTo("http");
        assertThat(attributes.get(FaasIncubatingAttributes.FAAS_DOCUMENT_OPERATION)).isEqualTo("insert");
        assertThat(attributes.get(FaasIncubatingAttributes.FAAS_MAX_MEMORY)).isEqualTo(512L);
        assertThat(attributes.get(RpcIncubatingAttributes.RPC_SYSTEM_NAME)).isEqualTo("grpc");
        assertThat(attributes.get(RpcIncubatingAttributes.RPC_METHOD)).isEqualTo("CreateOrder");
        assertThat(attributes.get(RpcIncubatingAttributes.RPC_RESPONSE_STATUS_CODE)).isEqualTo("OK");
        assertThat(attributes.get(OtelIncubatingAttributes.OTEL_COMPONENT_NAME)).isEqualTo("span-exporter");
        assertThat(attributes.get(OtelIncubatingAttributes.OTEL_COMPONENT_TYPE)).isEqualTo("otlp_grpc_span_exporter");
        assertThat(attributes.get(OtelIncubatingAttributes.OTEL_SPAN_SAMPLING_RESULT)).isEqualTo("RECORD_AND_SAMPLE");
        assertThat(attributes.get(ServiceIncubatingAttributes.SERVICE_CRITICALITY)).isEqualTo("high");
        assertThat(attributes.get(ServiceIncubatingAttributes.SERVICE_PEER_NAME)).isEqualTo("payments");
        assertThat(attributes.get(rpcRequestMetadata)).containsExactly("tenant-a");
        assertThat(attributes.get(rpcResponseMetadata)).containsExactly("dur=12");
        assertThat(CloudProviderIncubatingValues.ORACLE_CLOUD).isEqualTo("oracle_cloud");
        assertThat(FaasTriggerIncubatingValues.PUBSUB).isEqualTo("pubsub");
        assertThat(FaasDocumentOperationIncubatingValues.DELETE).isEqualTo("delete");
        assertThat(OtelSpanSamplingResultIncubatingValues.DROP).isEqualTo("DROP");
        assertThat(ServiceCriticalityIncubatingValues.LOW).isEqualTo("low");
    }

    @Test
    void hostContainerProcessOsAndJvmIncubatingAttributesRepresentRuntimeEnvironment() {
        AttributeKey<String> containerLabel = ContainerIncubatingAttributes.CONTAINER_LABEL.getAttributeKey("team");
        AttributeKey<String> processEnvironmentVariable =
                ProcessIncubatingAttributes.PROCESS_ENVIRONMENT_VARIABLE.getAttributeKey("OTEL_SERVICE_NAME");
        Attributes attributes = Attributes.builder()
                .put(HostIncubatingAttributes.HOST_ARCH, HostArchIncubatingValues.ARM64)
                .put(HostIncubatingAttributes.HOST_NAME, "node-1")
                .put(HostIncubatingAttributes.HOST_IP, List.of("192.0.2.10", "2001:db8::1"))
                .put(HostIncubatingAttributes.HOST_MAC, List.of("00-00-5E-00-53-01"))
                .put(OsIncubatingAttributes.OS_TYPE, OsTypeIncubatingValues.LINUX)
                .put(OsIncubatingAttributes.OS_DESCRIPTION, "Linux test kernel")
                .put(ContainerIncubatingAttributes.CONTAINER_NAME, "checkout")
                .put(ContainerIncubatingAttributes.CONTAINER_COMMAND_ARGS, List.of("java", "-jar", "app.jar"))
                .put(ContainerIncubatingAttributes.CONTAINER_IMAGE_TAGS, List.of("1.0.0", "latest"))
                .put(ProcessIncubatingAttributes.PROCESS_PID, 1234L)
                .put(ProcessIncubatingAttributes.PROCESS_COMMAND_ARGS, List.of("java", "-jar", "app.jar"))
                .put(
                        ProcessIncubatingAttributes.PROCESS_CONTEXT_SWITCH_TYPE,
                        ProcessContextSwitchTypeIncubatingValues.VOLUNTARY)
                .put(ProcessIncubatingAttributes.PROCESS_INTERACTIVE, false)
                .put(ProcessIncubatingAttributes.PROCESS_STATE, ProcessStateIncubatingValues.RUNNING)
                .put(JvmIncubatingAttributes.JVM_BUFFER_POOL_NAME, "direct")
                .put(JvmIncubatingAttributes.JVM_GC_CAUSE, "System.gc()")
                .put(containerLabel, "payments")
                .put(processEnvironmentVariable, "checkout")
                .build();

        assertThat(HostIncubatingAttributes.HOST_ARCH).isEqualTo(AttributeKey.stringKey("host.arch"));
        assertThat(HostIncubatingAttributes.HOST_IP).isEqualTo(AttributeKey.stringArrayKey("host.ip"));
        assertThat(OsIncubatingAttributes.OS_TYPE).isEqualTo(AttributeKey.stringKey("os.type"));
        assertThat(ContainerIncubatingAttributes.CONTAINER_IMAGE_TAGS)
                .isEqualTo(AttributeKey.stringArrayKey("container.image.tags"));
        assertThat(ProcessIncubatingAttributes.PROCESS_PID).isEqualTo(AttributeKey.longKey("process.pid"));
        assertThat(ProcessIncubatingAttributes.PROCESS_INTERACTIVE)
                .isEqualTo(AttributeKey.booleanKey("process.interactive"));
        assertThat(JvmIncubatingAttributes.JVM_BUFFER_POOL_NAME)
                .isEqualTo(AttributeKey.stringKey("jvm.buffer.pool.name"));
        assertThat(containerLabel).isEqualTo(AttributeKey.stringKey("container.label.team"));
        assertThat(processEnvironmentVariable)
                .isEqualTo(AttributeKey.stringKey("process.environment_variable.OTEL_SERVICE_NAME"));
        assertThat(ContainerIncubatingAttributes.CONTAINER_LABEL.getAttributeKey("team")).isSameAs(containerLabel);
        assertThat(attributes.get(HostIncubatingAttributes.HOST_ARCH)).isEqualTo("arm64");
        assertThat(attributes.get(HostIncubatingAttributes.HOST_NAME)).isEqualTo("node-1");
        assertThat(attributes.get(HostIncubatingAttributes.HOST_IP)).containsExactly("192.0.2.10", "2001:db8::1");
        assertThat(attributes.get(OsIncubatingAttributes.OS_TYPE)).isEqualTo("linux");
        assertThat(attributes.get(ContainerIncubatingAttributes.CONTAINER_COMMAND_ARGS))
                .containsExactly("java", "-jar", "app.jar");
        assertThat(attributes.get(ContainerIncubatingAttributes.CONTAINER_IMAGE_TAGS))
                .containsExactly("1.0.0", "latest");
        assertThat(attributes.get(ProcessIncubatingAttributes.PROCESS_PID)).isEqualTo(1234L);
        assertThat(attributes.get(ProcessIncubatingAttributes.PROCESS_COMMAND_ARGS))
                .containsExactly("java", "-jar", "app.jar");
        assertThat(attributes.get(ProcessIncubatingAttributes.PROCESS_CONTEXT_SWITCH_TYPE)).isEqualTo("voluntary");
        assertThat(attributes.get(ProcessIncubatingAttributes.PROCESS_INTERACTIVE)).isFalse();
        assertThat(attributes.get(ProcessIncubatingAttributes.PROCESS_STATE)).isEqualTo("running");
        assertThat(attributes.get(JvmIncubatingAttributes.JVM_BUFFER_POOL_NAME)).isEqualTo("direct");
        assertThat(attributes.get(JvmIncubatingAttributes.JVM_GC_CAUSE)).isEqualTo("System.gc()");
        assertThat(attributes.get(containerLabel)).isEqualTo("payments");
        assertThat(attributes.get(processEnvironmentVariable)).isEqualTo("checkout");
        assertThat(HostArchIncubatingValues.S390X).isEqualTo("s390x");
        assertThat(OsTypeIncubatingValues.SOLARIS).isEqualTo("solaris");
        assertThat(ProcessContextSwitchTypeIncubatingValues.INVOLUNTARY).isEqualTo("involuntary");
        assertThat(ProcessStateIncubatingValues.DEFUNCT).isEqualTo("defunct");
    }

    @Test
    void standaloneAttributeKeyTemplateFactoryStillProducesTypedCachedKeysUsedByIncubatingFields() {
        AttributeKeyTemplate<String> stringTemplate = AttributeKeyTemplate.stringKeyTemplate("test.string");
        AttributeKeyTemplate<List<String>> stringArrayTemplate =
                AttributeKeyTemplate.stringArrayKeyTemplate("test.string_array");
        AttributeKeyTemplate<Boolean> booleanTemplate = AttributeKeyTemplate.booleanKeyTemplate("test.boolean");
        AttributeKeyTemplate<List<Boolean>> booleanArrayTemplate =
                AttributeKeyTemplate.booleanArrayKeyTemplate("test.boolean_array");
        AttributeKeyTemplate<Long> longTemplate = AttributeKeyTemplate.longKeyTemplate("test.long");
        AttributeKeyTemplate<List<Long>> longArrayTemplate =
                AttributeKeyTemplate.longArrayKeyTemplate("test.long_array");
        AttributeKeyTemplate<Double> doubleTemplate = AttributeKeyTemplate.doubleKeyTemplate("test.double");
        AttributeKeyTemplate<List<Double>> doubleArrayTemplate =
                AttributeKeyTemplate.doubleArrayKeyTemplate("test.double_array");

        assertThat(stringTemplate.getAttributeKey("name")).isEqualTo(AttributeKey.stringKey("test.string.name"));
        assertThat(stringArrayTemplate.getAttributeKey("names"))
                .isEqualTo(AttributeKey.stringArrayKey("test.string_array.names"));
        assertThat(booleanTemplate.getAttributeKey("enabled"))
                .isEqualTo(AttributeKey.booleanKey("test.boolean.enabled"));
        assertThat(booleanArrayTemplate.getAttributeKey("flags"))
                .isEqualTo(AttributeKey.booleanArrayKey("test.boolean_array.flags"));
        assertThat(longTemplate.getAttributeKey("count")).isEqualTo(AttributeKey.longKey("test.long.count"));
        assertThat(longArrayTemplate.getAttributeKey("counts"))
                .isEqualTo(AttributeKey.longArrayKey("test.long_array.counts"));
        assertThat(doubleTemplate.getAttributeKey("ratio")).isEqualTo(AttributeKey.doubleKey("test.double.ratio"));
        assertThat(doubleArrayTemplate.getAttributeKey("ratios"))
                .isEqualTo(AttributeKey.doubleArrayKey("test.double_array.ratios"));
        assertThat(stringTemplate.getAttributeKey("name")).isSameAs(stringTemplate.getAttributeKey("name"));
        assertThat(stringTemplate.getAttributeKey("other")).isNotSameAs(stringTemplate.getAttributeKey("name"));
    }
}
