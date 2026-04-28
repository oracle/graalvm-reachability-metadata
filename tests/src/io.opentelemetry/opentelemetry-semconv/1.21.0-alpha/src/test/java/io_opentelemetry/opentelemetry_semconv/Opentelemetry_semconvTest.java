/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_opentelemetry.opentelemetry_semconv;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributeType;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class Opentelemetry_semconvTest {

    @Test
    void resourceAttributesExposeTypedKeysAndWorkWithAttributesBuilders() {
        assertThat(ResourceAttributes.SCHEMA_URL).isEqualTo("https://opentelemetry.io/schemas/1.16.0");

        assertAttributeKey(ResourceAttributes.SERVICE_NAME, "service.name", AttributeType.STRING);
        assertAttributeKey(ResourceAttributes.CLOUD_PROVIDER, "cloud.provider", AttributeType.STRING);
        assertAttributeKey(ResourceAttributes.FAAS_MAX_MEMORY, "faas.max_memory", AttributeType.LONG);
        assertAttributeKey(ResourceAttributes.PROCESS_COMMAND_ARGS, "process.command_args", AttributeType.STRING_ARRAY);
        assertAttributeKey(ResourceAttributes.AWS_LOG_GROUP_NAMES, "aws.log.group.names", AttributeType.STRING_ARRAY);

        Attributes resourceAttributes = Attributes.builder()
                .put(ResourceAttributes.SERVICE_NAME, "metadata-forge")
                .put(ResourceAttributes.SERVICE_NAMESPACE, "tests")
                .put(ResourceAttributes.SERVICE_VERSION, "1.0.0")
                .put(ResourceAttributes.CLOUD_PROVIDER, ResourceAttributes.CloudProviderValues.AWS)
                .put(ResourceAttributes.CLOUD_PLATFORM, ResourceAttributes.CloudPlatformValues.AWS_EKS)
                .put(ResourceAttributes.AWS_ECS_LAUNCHTYPE, ResourceAttributes.AwsEcsLaunchtypeValues.FARGATE)
                .put(ResourceAttributes.HOST_ARCH, ResourceAttributes.HostArchValues.ARM64)
                .put(ResourceAttributes.OS_TYPE, ResourceAttributes.OsTypeValues.LINUX)
                .put(ResourceAttributes.TELEMETRY_SDK_LANGUAGE, ResourceAttributes.TelemetrySdkLanguageValues.JAVA)
                .put(ResourceAttributes.PROCESS_PID, 4242L)
                .put(ResourceAttributes.FAAS_MAX_MEMORY, 512L)
                .put(ResourceAttributes.PROCESS_COMMAND_ARGS, List.of("java", "-jar", "app.jar"))
                .put(ResourceAttributes.AWS_LOG_GROUP_NAMES, List.of("/aws/lambda/metadata-forge"))
                .build();

        assertThat(resourceAttributes.get(ResourceAttributes.SERVICE_NAME)).isEqualTo("metadata-forge");
        assertThat(resourceAttributes.get(ResourceAttributes.SERVICE_NAMESPACE)).isEqualTo("tests");
        assertThat(resourceAttributes.get(ResourceAttributes.CLOUD_PROVIDER))
                .isEqualTo(ResourceAttributes.CloudProviderValues.AWS);
        assertThat(resourceAttributes.get(ResourceAttributes.CLOUD_PLATFORM))
                .isEqualTo(ResourceAttributes.CloudPlatformValues.AWS_EKS);
        assertThat(resourceAttributes.get(ResourceAttributes.AWS_ECS_LAUNCHTYPE))
                .isEqualTo(ResourceAttributes.AwsEcsLaunchtypeValues.FARGATE);
        assertThat(resourceAttributes.get(ResourceAttributes.HOST_ARCH))
                .isEqualTo(ResourceAttributes.HostArchValues.ARM64);
        assertThat(resourceAttributes.get(ResourceAttributes.OS_TYPE))
                .isEqualTo(ResourceAttributes.OsTypeValues.LINUX);
        assertThat(resourceAttributes.get(ResourceAttributes.TELEMETRY_SDK_LANGUAGE))
                .isEqualTo(ResourceAttributes.TelemetrySdkLanguageValues.JAVA);
        assertThat(resourceAttributes.get(ResourceAttributes.PROCESS_PID)).isEqualTo(4242L);
        assertThat(resourceAttributes.get(ResourceAttributes.FAAS_MAX_MEMORY)).isEqualTo(512L);
        assertThat(resourceAttributes.get(ResourceAttributes.PROCESS_COMMAND_ARGS))
                .containsExactly("java", "-jar", "app.jar");
        assertThat(resourceAttributes.get(ResourceAttributes.AWS_LOG_GROUP_NAMES))
                .containsExactly("/aws/lambda/metadata-forge");

        Attributes updatedResourceAttributes = resourceAttributes.toBuilder()
                .remove(ResourceAttributes.AWS_ECS_LAUNCHTYPE)
                .put(ResourceAttributes.CONTAINER_NAME, "worker")
                .build();

        assertThat(updatedResourceAttributes.get(ResourceAttributes.AWS_ECS_LAUNCHTYPE)).isNull();
        assertThat(updatedResourceAttributes.get(ResourceAttributes.CONTAINER_NAME)).isEqualTo("worker");
        assertThat(updatedResourceAttributes.asMap())
                .containsEntry(ResourceAttributes.SERVICE_NAME, "metadata-forge")
                .containsEntry(ResourceAttributes.FAAS_MAX_MEMORY, 512L);
    }

    @Test
    void traceAttributesExposeTypedKeysAndSupportTypedAttributePayloads() {
        assertThat(SemanticAttributes.SCHEMA_URL).isEqualTo("https://opentelemetry.io/schemas/1.16.0");
        assertThat(SemanticAttributes.EXCEPTION_EVENT_NAME).isEqualTo("exception");

        assertAttributeKey(SemanticAttributes.DB_SYSTEM, "db.system", AttributeType.STRING);
        assertAttributeKey(SemanticAttributes.HTTP_STATUS_CODE, "http.status_code", AttributeType.LONG);
        assertAttributeKey(SemanticAttributes.EXCEPTION_ESCAPED, "exception.escaped", AttributeType.BOOLEAN);
        assertAttributeKey(
                SemanticAttributes.AWS_DYNAMODB_PROVISIONED_READ_CAPACITY,
                "aws.dynamodb.provisioned_read_capacity",
                AttributeType.DOUBLE
        );
        assertAttributeKey(
                SemanticAttributes.AWS_DYNAMODB_TABLE_NAMES,
                "aws.dynamodb.table_names",
                AttributeType.STRING_ARRAY
        );

        Attributes baseAttributes = Attributes.of(
                SemanticAttributes.DB_SYSTEM,
                SemanticAttributes.DbSystemValues.POSTGRESQL,
                SemanticAttributes.HTTP_STATUS_CODE,
                200L,
                SemanticAttributes.EXCEPTION_ESCAPED,
                false
        );

        Attributes traceAttributes = Attributes.builder()
                .putAll(baseAttributes)
                .put(SemanticAttributes.DB_CONNECTION_STRING, "jdbc:postgresql://localhost:5432/app")
                .put(SemanticAttributes.DB_CASSANDRA_CONSISTENCY_LEVEL,
                        SemanticAttributes.DbCassandraConsistencyLevelValues.QUORUM)
                .put(SemanticAttributes.FAAS_TRIGGER, SemanticAttributes.FaasTriggerValues.HTTP)
                .put(SemanticAttributes.FAAS_INVOKED_PROVIDER, SemanticAttributes.FaasInvokedProviderValues.AWS)
                .put(SemanticAttributes.NET_TRANSPORT, SemanticAttributes.NetTransportValues.IP_TCP)
                .put(SemanticAttributes.NET_SOCK_FAMILY, SemanticAttributes.NetSockFamilyValues.INET6)
                .put(SemanticAttributes.NET_HOST_CONNECTION_TYPE,
                        SemanticAttributes.NetHostConnectionTypeValues.WIFI)
                .put(SemanticAttributes.NET_HOST_CONNECTION_SUBTYPE,
                        SemanticAttributes.NetHostConnectionSubtypeValues.LTE)
                .put(SemanticAttributes.HTTP_FLAVOR, SemanticAttributes.HttpFlavorValues.HTTP_2_0)
                .put(SemanticAttributes.MESSAGING_DESTINATION_KIND,
                        SemanticAttributes.MessagingDestinationKindValues.TOPIC)
                .put(SemanticAttributes.MESSAGING_OPERATION,
                        SemanticAttributes.MessagingOperationValues.PROCESS)
                .put(SemanticAttributes.MESSAGING_ROCKETMQ_MESSAGE_TYPE,
                        SemanticAttributes.MessagingRocketmqMessageTypeValues.FIFO)
                .put(SemanticAttributes.MESSAGING_ROCKETMQ_CONSUMPTION_MODEL,
                        SemanticAttributes.MessagingRocketmqConsumptionModelValues.CLUSTERING)
                .put(SemanticAttributes.RPC_SYSTEM, SemanticAttributes.RpcSystemValues.GRPC)
                .put(SemanticAttributes.RPC_GRPC_STATUS_CODE,
                        SemanticAttributes.RpcGrpcStatusCodeValues.OK)
                .put(SemanticAttributes.GRAPHQL_OPERATION_TYPE,
                        SemanticAttributes.GraphqlOperationTypeValues.QUERY)
                .put(SemanticAttributes.MESSAGE_TYPE, SemanticAttributes.MessageTypeValues.SENT)
                .put(SemanticAttributes.AWS_DYNAMODB_TABLE_NAMES, List.of("users", "orders"))
                .put(SemanticAttributes.AWS_DYNAMODB_PROVISIONED_READ_CAPACITY, 12.5)
                .build();

        assertThat(traceAttributes.get(SemanticAttributes.DB_SYSTEM))
                .isEqualTo(SemanticAttributes.DbSystemValues.POSTGRESQL);
        assertThat(traceAttributes.get(SemanticAttributes.HTTP_STATUS_CODE)).isEqualTo(200L);
        assertThat(traceAttributes.get(SemanticAttributes.EXCEPTION_ESCAPED)).isFalse();
        assertThat(traceAttributes.get(SemanticAttributes.DB_CONNECTION_STRING))
                .isEqualTo("jdbc:postgresql://localhost:5432/app");
        assertThat(traceAttributes.get(SemanticAttributes.DB_CASSANDRA_CONSISTENCY_LEVEL))
                .isEqualTo(SemanticAttributes.DbCassandraConsistencyLevelValues.QUORUM);
        assertThat(traceAttributes.get(SemanticAttributes.FAAS_TRIGGER))
                .isEqualTo(SemanticAttributes.FaasTriggerValues.HTTP);
        assertThat(traceAttributes.get(SemanticAttributes.FAAS_INVOKED_PROVIDER))
                .isEqualTo(SemanticAttributes.FaasInvokedProviderValues.AWS);
        assertThat(traceAttributes.get(SemanticAttributes.NET_TRANSPORT))
                .isEqualTo(SemanticAttributes.NetTransportValues.IP_TCP);
        assertThat(traceAttributes.get(SemanticAttributes.NET_SOCK_FAMILY))
                .isEqualTo(SemanticAttributes.NetSockFamilyValues.INET6);
        assertThat(traceAttributes.get(SemanticAttributes.NET_HOST_CONNECTION_TYPE))
                .isEqualTo(SemanticAttributes.NetHostConnectionTypeValues.WIFI);
        assertThat(traceAttributes.get(SemanticAttributes.NET_HOST_CONNECTION_SUBTYPE))
                .isEqualTo(SemanticAttributes.NetHostConnectionSubtypeValues.LTE);
        assertThat(traceAttributes.get(SemanticAttributes.HTTP_FLAVOR))
                .isEqualTo(SemanticAttributes.HttpFlavorValues.HTTP_2_0);
        assertThat(traceAttributes.get(SemanticAttributes.MESSAGING_DESTINATION_KIND))
                .isEqualTo(SemanticAttributes.MessagingDestinationKindValues.TOPIC);
        assertThat(traceAttributes.get(SemanticAttributes.MESSAGING_OPERATION))
                .isEqualTo(SemanticAttributes.MessagingOperationValues.PROCESS);
        assertThat(traceAttributes.get(SemanticAttributes.MESSAGING_ROCKETMQ_MESSAGE_TYPE))
                .isEqualTo(SemanticAttributes.MessagingRocketmqMessageTypeValues.FIFO);
        assertThat(traceAttributes.get(SemanticAttributes.MESSAGING_ROCKETMQ_CONSUMPTION_MODEL))
                .isEqualTo(SemanticAttributes.MessagingRocketmqConsumptionModelValues.CLUSTERING);
        assertThat(traceAttributes.get(SemanticAttributes.RPC_SYSTEM))
                .isEqualTo(SemanticAttributes.RpcSystemValues.GRPC);
        assertThat(traceAttributes.get(SemanticAttributes.RPC_GRPC_STATUS_CODE))
                .isEqualTo(SemanticAttributes.RpcGrpcStatusCodeValues.OK);
        assertThat(traceAttributes.get(SemanticAttributes.GRAPHQL_OPERATION_TYPE))
                .isEqualTo(SemanticAttributes.GraphqlOperationTypeValues.QUERY);
        assertThat(traceAttributes.get(SemanticAttributes.MESSAGE_TYPE))
                .isEqualTo(SemanticAttributes.MessageTypeValues.SENT);
        assertThat(traceAttributes.get(SemanticAttributes.AWS_DYNAMODB_TABLE_NAMES))
                .containsExactly("users", "orders");
        assertThat(traceAttributes.get(SemanticAttributes.AWS_DYNAMODB_PROVISIONED_READ_CAPACITY))
                .isEqualTo(12.5);

        Map<String, Object> traceAttributesByName = new LinkedHashMap<>();
        traceAttributes.forEach((key, value) -> traceAttributesByName.put(key.getKey(), value));

        assertThat(traceAttributesByName)
                .containsEntry("db.system", SemanticAttributes.DbSystemValues.POSTGRESQL)
                .containsEntry("http.status_code", 200L)
                .containsEntry("rpc.grpc.status_code", 0L)
                .containsEntry("graphql.operation.type", SemanticAttributes.GraphqlOperationTypeValues.QUERY);
    }

    @Test
    void traceCloudEventsAttributesDescribeServerlessInvocationContext() {
        assertAttributeKey(SemanticAttributes.AWS_LAMBDA_INVOKED_ARN, "aws.lambda.invoked_arn", AttributeType.STRING);
        assertAttributeKey(SemanticAttributes.CLOUDEVENTS_EVENT_ID, "cloudevents.event_id", AttributeType.STRING);
        assertAttributeKey(SemanticAttributes.CLOUDEVENTS_EVENT_SOURCE, "cloudevents.event_source", AttributeType.STRING);
        assertAttributeKey(
                SemanticAttributes.CLOUDEVENTS_EVENT_SPEC_VERSION,
                "cloudevents.event_spec_version",
                AttributeType.STRING
        );
        assertAttributeKey(SemanticAttributes.CLOUDEVENTS_EVENT_TYPE, "cloudevents.event_type", AttributeType.STRING);
        assertAttributeKey(
                SemanticAttributes.CLOUDEVENTS_EVENT_SUBJECT,
                "cloudevents.event_subject",
                AttributeType.STRING
        );

        Attributes cloudEventAttributes = Attributes.builder()
                .put(
                        SemanticAttributes.AWS_LAMBDA_INVOKED_ARN,
                        "arn:aws:lambda:us-east-1:123456789012:function:metadata-handler"
                )
                .put(SemanticAttributes.CLOUDEVENTS_EVENT_ID, "evt-2026-04-27-0001")
                .put(SemanticAttributes.CLOUDEVENTS_EVENT_SOURCE, "urn:metadata-forge:test")
                .put(SemanticAttributes.CLOUDEVENTS_EVENT_SPEC_VERSION, "1.0")
                .put(SemanticAttributes.CLOUDEVENTS_EVENT_TYPE, "com.example.metadata.generated")
                .put(
                        SemanticAttributes.CLOUDEVENTS_EVENT_SUBJECT,
                        "libraries/io.opentelemetry/opentelemetry-semconv"
                )
                .build();

        assertThat(cloudEventAttributes.asMap())
                .containsEntry(
                        SemanticAttributes.AWS_LAMBDA_INVOKED_ARN,
                        "arn:aws:lambda:us-east-1:123456789012:function:metadata-handler"
                )
                .containsEntry(SemanticAttributes.CLOUDEVENTS_EVENT_ID, "evt-2026-04-27-0001")
                .containsEntry(SemanticAttributes.CLOUDEVENTS_EVENT_SOURCE, "urn:metadata-forge:test")
                .containsEntry(SemanticAttributes.CLOUDEVENTS_EVENT_SPEC_VERSION, "1.0")
                .containsEntry(SemanticAttributes.CLOUDEVENTS_EVENT_TYPE, "com.example.metadata.generated")
                .containsEntry(
                        SemanticAttributes.CLOUDEVENTS_EVENT_SUBJECT,
                        "libraries/io.opentelemetry/opentelemetry-semconv"
                );
    }

    @Test
    void resourceSemanticConventionValueClassesExposeExpectedTaxonomies() {
        assertThat(List.of(
                ResourceAttributes.CloudProviderValues.ALIBABA_CLOUD,
                ResourceAttributes.CloudProviderValues.AWS,
                ResourceAttributes.CloudProviderValues.AZURE,
                ResourceAttributes.CloudProviderValues.GCP,
                ResourceAttributes.CloudProviderValues.TENCENT_CLOUD
        )).containsExactly(
                "alibaba_cloud",
                "aws",
                "azure",
                "gcp",
                "tencent_cloud"
        );

        assertThat(List.of(
                ResourceAttributes.CloudPlatformValues.ALIBABA_CLOUD_ECS,
                ResourceAttributes.CloudPlatformValues.ALIBABA_CLOUD_FC,
                ResourceAttributes.CloudPlatformValues.AWS_EC2,
                ResourceAttributes.CloudPlatformValues.AWS_ECS,
                ResourceAttributes.CloudPlatformValues.AWS_EKS,
                ResourceAttributes.CloudPlatformValues.AWS_LAMBDA,
                ResourceAttributes.CloudPlatformValues.AWS_ELASTIC_BEANSTALK,
                ResourceAttributes.CloudPlatformValues.AWS_APP_RUNNER,
                ResourceAttributes.CloudPlatformValues.AZURE_VM,
                ResourceAttributes.CloudPlatformValues.AZURE_CONTAINER_INSTANCES,
                ResourceAttributes.CloudPlatformValues.AZURE_AKS,
                ResourceAttributes.CloudPlatformValues.AZURE_FUNCTIONS,
                ResourceAttributes.CloudPlatformValues.AZURE_APP_SERVICE,
                ResourceAttributes.CloudPlatformValues.GCP_COMPUTE_ENGINE,
                ResourceAttributes.CloudPlatformValues.GCP_CLOUD_RUN,
                ResourceAttributes.CloudPlatformValues.GCP_KUBERNETES_ENGINE,
                ResourceAttributes.CloudPlatformValues.GCP_CLOUD_FUNCTIONS,
                ResourceAttributes.CloudPlatformValues.GCP_APP_ENGINE,
                ResourceAttributes.CloudPlatformValues.TENCENT_CLOUD_CVM,
                ResourceAttributes.CloudPlatformValues.TENCENT_CLOUD_EKS,
                ResourceAttributes.CloudPlatformValues.TENCENT_CLOUD_SCF
        )).containsExactly(
                "alibaba_cloud_ecs",
                "alibaba_cloud_fc",
                "aws_ec2",
                "aws_ecs",
                "aws_eks",
                "aws_lambda",
                "aws_elastic_beanstalk",
                "aws_app_runner",
                "azure_vm",
                "azure_container_instances",
                "azure_aks",
                "azure_functions",
                "azure_app_service",
                "gcp_compute_engine",
                "gcp_cloud_run",
                "gcp_kubernetes_engine",
                "gcp_cloud_functions",
                "gcp_app_engine",
                "tencent_cloud_cvm",
                "tencent_cloud_eks",
                "tencent_cloud_scf"
        );

        assertThat(List.of(
                ResourceAttributes.AwsEcsLaunchtypeValues.EC2,
                ResourceAttributes.AwsEcsLaunchtypeValues.FARGATE
        )).containsExactly("ec2", "fargate");

        assertThat(List.of(
                ResourceAttributes.HostArchValues.AMD64,
                ResourceAttributes.HostArchValues.ARM32,
                ResourceAttributes.HostArchValues.ARM64,
                ResourceAttributes.HostArchValues.IA64,
                ResourceAttributes.HostArchValues.PPC32,
                ResourceAttributes.HostArchValues.PPC64,
                ResourceAttributes.HostArchValues.S390X,
                ResourceAttributes.HostArchValues.X86
        )).containsExactly("amd64", "arm32", "arm64", "ia64", "ppc32", "ppc64", "s390x", "x86");

        assertThat(List.of(
                ResourceAttributes.TelemetrySdkLanguageValues.CPP,
                ResourceAttributes.TelemetrySdkLanguageValues.DOTNET,
                ResourceAttributes.TelemetrySdkLanguageValues.ERLANG,
                ResourceAttributes.TelemetrySdkLanguageValues.GO,
                ResourceAttributes.TelemetrySdkLanguageValues.JAVA,
                ResourceAttributes.TelemetrySdkLanguageValues.NODEJS,
                ResourceAttributes.TelemetrySdkLanguageValues.PHP,
                ResourceAttributes.TelemetrySdkLanguageValues.PYTHON,
                ResourceAttributes.TelemetrySdkLanguageValues.RUBY,
                ResourceAttributes.TelemetrySdkLanguageValues.WEBJS,
                ResourceAttributes.TelemetrySdkLanguageValues.SWIFT
        )).containsExactly("cpp", "dotnet", "erlang", "go", "java", "nodejs", "php", "python", "ruby", "webjs", "swift");

        assertThat(List.of(
                ResourceAttributes.OsTypeValues.WINDOWS,
                ResourceAttributes.OsTypeValues.LINUX,
                ResourceAttributes.OsTypeValues.DARWIN,
                ResourceAttributes.OsTypeValues.FREEBSD,
                ResourceAttributes.OsTypeValues.NETBSD,
                ResourceAttributes.OsTypeValues.OPENBSD,
                ResourceAttributes.OsTypeValues.DRAGONFLYBSD,
                ResourceAttributes.OsTypeValues.HPUX,
                ResourceAttributes.OsTypeValues.AIX,
                ResourceAttributes.OsTypeValues.SOLARIS,
                ResourceAttributes.OsTypeValues.Z_OS
        )).containsExactly(
                "windows",
                "linux",
                "darwin",
                "freebsd",
                "netbsd",
                "openbsd",
                "dragonflybsd",
                "hpux",
                "aix",
                "solaris",
                "z_os"
        );
    }

    @Test
    void resourceAttributesSupportKubernetesWorkloadIdentity() {
        assertAttributeKey(ResourceAttributes.K8S_CLUSTER_NAME, "k8s.cluster.name", AttributeType.STRING);
        assertAttributeKey(ResourceAttributes.K8S_NAMESPACE_NAME, "k8s.namespace.name", AttributeType.STRING);
        assertAttributeKey(ResourceAttributes.K8S_CONTAINER_RESTART_COUNT, "k8s.container.restart_count", AttributeType.LONG);
        assertAttributeKey(ResourceAttributes.K8S_JOB_NAME, "k8s.job.name", AttributeType.STRING);
        assertAttributeKey(ResourceAttributes.K8S_CRONJOB_NAME, "k8s.cronjob.name", AttributeType.STRING);

        Attributes kubernetesAttributes = Attributes.builder()
                .put(ResourceAttributes.K8S_CLUSTER_NAME, "metadata-forge-cluster")
                .put(ResourceAttributes.K8S_NAMESPACE_NAME, "observability")
                .put(ResourceAttributes.K8S_NODE_NAME, "worker-node-a")
                .put(ResourceAttributes.K8S_POD_NAME, "metadata-forge-refresh-28671200-9x2hk")
                .put(ResourceAttributes.K8S_CONTAINER_NAME, "metadata-worker")
                .put(ResourceAttributes.K8S_CONTAINER_RESTART_COUNT, 3L)
                .put(ResourceAttributes.K8S_JOB_NAME, "metadata-forge-refresh-28671200")
                .put(ResourceAttributes.K8S_CRONJOB_NAME, "metadata-forge-refresh")
                .build();

        assertThat(kubernetesAttributes.get(ResourceAttributes.K8S_CLUSTER_NAME))
                .isEqualTo("metadata-forge-cluster");
        assertThat(kubernetesAttributes.get(ResourceAttributes.K8S_NAMESPACE_NAME)).isEqualTo("observability");
        assertThat(kubernetesAttributes.get(ResourceAttributes.K8S_NODE_NAME)).isEqualTo("worker-node-a");
        assertThat(kubernetesAttributes.get(ResourceAttributes.K8S_POD_NAME))
                .isEqualTo("metadata-forge-refresh-28671200-9x2hk");
        assertThat(kubernetesAttributes.get(ResourceAttributes.K8S_CONTAINER_NAME)).isEqualTo("metadata-worker");
        assertThat(kubernetesAttributes.get(ResourceAttributes.K8S_CONTAINER_RESTART_COUNT)).isEqualTo(3L);
        assertThat(kubernetesAttributes.get(ResourceAttributes.K8S_JOB_NAME))
                .isEqualTo("metadata-forge-refresh-28671200");
        assertThat(kubernetesAttributes.get(ResourceAttributes.K8S_CRONJOB_NAME))
                .isEqualTo("metadata-forge-refresh");

        assertThat(kubernetesAttributes.asMap())
                .containsEntry(ResourceAttributes.K8S_CLUSTER_NAME, "metadata-forge-cluster")
                .containsEntry(ResourceAttributes.K8S_CONTAINER_RESTART_COUNT, 3L)
                .containsEntry(ResourceAttributes.K8S_CRONJOB_NAME, "metadata-forge-refresh");
    }

    @Test
    void traceSemanticConventionValueClassesExposeExpectedTaxonomies() {
        assertThat(List.of(
                SemanticAttributes.OpentracingRefTypeValues.CHILD_OF,
                SemanticAttributes.OpentracingRefTypeValues.FOLLOWS_FROM
        )).containsExactly("child_of", "follows_from");

        assertThat(List.of(
                SemanticAttributes.DbCassandraConsistencyLevelValues.ALL,
                SemanticAttributes.DbCassandraConsistencyLevelValues.EACH_QUORUM,
                SemanticAttributes.DbCassandraConsistencyLevelValues.QUORUM,
                SemanticAttributes.DbCassandraConsistencyLevelValues.LOCAL_QUORUM,
                SemanticAttributes.DbCassandraConsistencyLevelValues.ONE,
                SemanticAttributes.DbCassandraConsistencyLevelValues.TWO,
                SemanticAttributes.DbCassandraConsistencyLevelValues.THREE,
                SemanticAttributes.DbCassandraConsistencyLevelValues.LOCAL_ONE,
                SemanticAttributes.DbCassandraConsistencyLevelValues.ANY,
                SemanticAttributes.DbCassandraConsistencyLevelValues.SERIAL,
                SemanticAttributes.DbCassandraConsistencyLevelValues.LOCAL_SERIAL
        )).containsExactly(
                "all",
                "each_quorum",
                "quorum",
                "local_quorum",
                "one",
                "two",
                "three",
                "local_one",
                "any",
                "serial",
                "local_serial"
        );

        assertThat(List.of(
                SemanticAttributes.DbSystemValues.OTHER_SQL,
                SemanticAttributes.DbSystemValues.MSSQL,
                SemanticAttributes.DbSystemValues.MYSQL,
                SemanticAttributes.DbSystemValues.ORACLE,
                SemanticAttributes.DbSystemValues.DB2,
                SemanticAttributes.DbSystemValues.POSTGRESQL,
                SemanticAttributes.DbSystemValues.REDSHIFT,
                SemanticAttributes.DbSystemValues.HIVE,
                SemanticAttributes.DbSystemValues.CLOUDSCAPE,
                SemanticAttributes.DbSystemValues.HSQLDB,
                SemanticAttributes.DbSystemValues.PROGRESS,
                SemanticAttributes.DbSystemValues.MAXDB,
                SemanticAttributes.DbSystemValues.HANADB,
                SemanticAttributes.DbSystemValues.INGRES,
                SemanticAttributes.DbSystemValues.FIRSTSQL,
                SemanticAttributes.DbSystemValues.EDB,
                SemanticAttributes.DbSystemValues.CACHE,
                SemanticAttributes.DbSystemValues.ADABAS,
                SemanticAttributes.DbSystemValues.FIREBIRD,
                SemanticAttributes.DbSystemValues.DERBY,
                SemanticAttributes.DbSystemValues.FILEMAKER,
                SemanticAttributes.DbSystemValues.INFORMIX,
                SemanticAttributes.DbSystemValues.INSTANTDB,
                SemanticAttributes.DbSystemValues.INTERBASE,
                SemanticAttributes.DbSystemValues.MARIADB,
                SemanticAttributes.DbSystemValues.NETEZZA,
                SemanticAttributes.DbSystemValues.PERVASIVE,
                SemanticAttributes.DbSystemValues.POINTBASE,
                SemanticAttributes.DbSystemValues.SQLITE,
                SemanticAttributes.DbSystemValues.SYBASE,
                SemanticAttributes.DbSystemValues.TERADATA,
                SemanticAttributes.DbSystemValues.VERTICA,
                SemanticAttributes.DbSystemValues.H2,
                SemanticAttributes.DbSystemValues.COLDFUSION,
                SemanticAttributes.DbSystemValues.CASSANDRA,
                SemanticAttributes.DbSystemValues.HBASE,
                SemanticAttributes.DbSystemValues.MONGODB,
                SemanticAttributes.DbSystemValues.REDIS,
                SemanticAttributes.DbSystemValues.COUCHBASE,
                SemanticAttributes.DbSystemValues.COUCHDB,
                SemanticAttributes.DbSystemValues.COSMOSDB,
                SemanticAttributes.DbSystemValues.DYNAMODB,
                SemanticAttributes.DbSystemValues.NEO4J,
                SemanticAttributes.DbSystemValues.GEODE,
                SemanticAttributes.DbSystemValues.ELASTICSEARCH,
                SemanticAttributes.DbSystemValues.MEMCACHED,
                SemanticAttributes.DbSystemValues.COCKROACHDB,
                SemanticAttributes.DbSystemValues.OPENSEARCH
        )).containsExactly(
                "other_sql",
                "mssql",
                "mysql",
                "oracle",
                "db2",
                "postgresql",
                "redshift",
                "hive",
                "cloudscape",
                "hsqldb",
                "progress",
                "maxdb",
                "hanadb",
                "ingres",
                "firstsql",
                "edb",
                "cache",
                "adabas",
                "firebird",
                "derby",
                "filemaker",
                "informix",
                "instantdb",
                "interbase",
                "mariadb",
                "netezza",
                "pervasive",
                "pointbase",
                "sqlite",
                "sybase",
                "teradata",
                "vertica",
                "h2",
                "coldfusion",
                "cassandra",
                "hbase",
                "mongodb",
                "redis",
                "couchbase",
                "couchdb",
                "cosmosdb",
                "dynamodb",
                "neo4j",
                "geode",
                "elasticsearch",
                "memcached",
                "cockroachdb",
                "opensearch"
        );

        assertThat(List.of(
                SemanticAttributes.FaasDocumentOperationValues.INSERT,
                SemanticAttributes.FaasDocumentOperationValues.EDIT,
                SemanticAttributes.FaasDocumentOperationValues.DELETE
        )).containsExactly("insert", "edit", "delete");

        assertThat(List.of(
                SemanticAttributes.FaasTriggerValues.DATASOURCE,
                SemanticAttributes.FaasTriggerValues.HTTP,
                SemanticAttributes.FaasTriggerValues.PUBSUB,
                SemanticAttributes.FaasTriggerValues.TIMER,
                SemanticAttributes.FaasTriggerValues.OTHER
        )).containsExactly("datasource", "http", "pubsub", "timer", "other");

        assertThat(List.of(
                SemanticAttributes.FaasInvokedProviderValues.ALIBABA_CLOUD,
                SemanticAttributes.FaasInvokedProviderValues.AWS,
                SemanticAttributes.FaasInvokedProviderValues.AZURE,
                SemanticAttributes.FaasInvokedProviderValues.GCP,
                SemanticAttributes.FaasInvokedProviderValues.TENCENT_CLOUD
        )).containsExactly(
                ResourceAttributes.CloudProviderValues.ALIBABA_CLOUD,
                ResourceAttributes.CloudProviderValues.AWS,
                ResourceAttributes.CloudProviderValues.AZURE,
                ResourceAttributes.CloudProviderValues.GCP,
                ResourceAttributes.CloudProviderValues.TENCENT_CLOUD
        );

        assertThat(List.of(
                SemanticAttributes.NetTransportValues.IP_TCP,
                SemanticAttributes.NetTransportValues.IP_UDP,
                SemanticAttributes.NetTransportValues.PIPE,
                SemanticAttributes.NetTransportValues.INPROC,
                SemanticAttributes.NetTransportValues.OTHER
        )).containsExactly("ip_tcp", "ip_udp", "pipe", "inproc", "other");

        assertThat(List.of(
                SemanticAttributes.NetSockFamilyValues.INET,
                SemanticAttributes.NetSockFamilyValues.INET6,
                SemanticAttributes.NetSockFamilyValues.UNIX
        )).containsExactly("inet", "inet6", "unix");

        assertThat(List.of(
                SemanticAttributes.NetHostConnectionTypeValues.WIFI,
                SemanticAttributes.NetHostConnectionTypeValues.WIRED,
                SemanticAttributes.NetHostConnectionTypeValues.CELL,
                SemanticAttributes.NetHostConnectionTypeValues.UNAVAILABLE,
                SemanticAttributes.NetHostConnectionTypeValues.UNKNOWN
        )).containsExactly("wifi", "wired", "cell", "unavailable", "unknown");

        assertThat(List.of(
                SemanticAttributes.NetHostConnectionSubtypeValues.GPRS,
                SemanticAttributes.NetHostConnectionSubtypeValues.EDGE,
                SemanticAttributes.NetHostConnectionSubtypeValues.UMTS,
                SemanticAttributes.NetHostConnectionSubtypeValues.CDMA,
                SemanticAttributes.NetHostConnectionSubtypeValues.EVDO_0,
                SemanticAttributes.NetHostConnectionSubtypeValues.EVDO_A,
                SemanticAttributes.NetHostConnectionSubtypeValues.CDMA2000_1XRTT,
                SemanticAttributes.NetHostConnectionSubtypeValues.HSDPA,
                SemanticAttributes.NetHostConnectionSubtypeValues.HSUPA,
                SemanticAttributes.NetHostConnectionSubtypeValues.HSPA,
                SemanticAttributes.NetHostConnectionSubtypeValues.IDEN,
                SemanticAttributes.NetHostConnectionSubtypeValues.EVDO_B,
                SemanticAttributes.NetHostConnectionSubtypeValues.LTE,
                SemanticAttributes.NetHostConnectionSubtypeValues.EHRPD,
                SemanticAttributes.NetHostConnectionSubtypeValues.HSPAP,
                SemanticAttributes.NetHostConnectionSubtypeValues.GSM,
                SemanticAttributes.NetHostConnectionSubtypeValues.TD_SCDMA,
                SemanticAttributes.NetHostConnectionSubtypeValues.IWLAN,
                SemanticAttributes.NetHostConnectionSubtypeValues.NR,
                SemanticAttributes.NetHostConnectionSubtypeValues.NRNSA,
                SemanticAttributes.NetHostConnectionSubtypeValues.LTE_CA
        )).containsExactly(
                "gprs",
                "edge",
                "umts",
                "cdma",
                "evdo_0",
                "evdo_a",
                "cdma2000_1xrtt",
                "hsdpa",
                "hsupa",
                "hspa",
                "iden",
                "evdo_b",
                "lte",
                "ehrpd",
                "hspap",
                "gsm",
                "td_scdma",
                "iwlan",
                "nr",
                "nrnsa",
                "lte_ca"
        );

        assertThat(List.of(
                SemanticAttributes.HttpFlavorValues.HTTP_1_0,
                SemanticAttributes.HttpFlavorValues.HTTP_1_1,
                SemanticAttributes.HttpFlavorValues.HTTP_2_0,
                SemanticAttributes.HttpFlavorValues.HTTP_3_0,
                SemanticAttributes.HttpFlavorValues.SPDY,
                SemanticAttributes.HttpFlavorValues.QUIC
        )).containsExactly("1.0", "1.1", "2.0", "3.0", "SPDY", "QUIC");

        assertThat(List.of(
                SemanticAttributes.GraphqlOperationTypeValues.QUERY,
                SemanticAttributes.GraphqlOperationTypeValues.MUTATION,
                SemanticAttributes.GraphqlOperationTypeValues.SUBSCRIPTION
        )).containsExactly("query", "mutation", "subscription");

        assertThat(List.of(
                SemanticAttributes.MessagingDestinationKindValues.QUEUE,
                SemanticAttributes.MessagingDestinationKindValues.TOPIC
        )).containsExactly("queue", "topic");

        assertThat(List.of(
                SemanticAttributes.MessagingOperationValues.RECEIVE,
                SemanticAttributes.MessagingOperationValues.PROCESS
        )).containsExactly("receive", "process");

        assertThat(List.of(
                SemanticAttributes.MessagingRocketmqMessageTypeValues.NORMAL,
                SemanticAttributes.MessagingRocketmqMessageTypeValues.FIFO,
                SemanticAttributes.MessagingRocketmqMessageTypeValues.DELAY,
                SemanticAttributes.MessagingRocketmqMessageTypeValues.TRANSACTION
        )).containsExactly("normal", "fifo", "delay", "transaction");

        assertThat(List.of(
                SemanticAttributes.MessagingRocketmqConsumptionModelValues.CLUSTERING,
                SemanticAttributes.MessagingRocketmqConsumptionModelValues.BROADCASTING
        )).containsExactly("clustering", "broadcasting");

        assertThat(List.of(
                SemanticAttributes.RpcSystemValues.GRPC,
                SemanticAttributes.RpcSystemValues.JAVA_RMI,
                SemanticAttributes.RpcSystemValues.DOTNET_WCF,
                SemanticAttributes.RpcSystemValues.APACHE_DUBBO
        )).containsExactly("grpc", "java_rmi", "dotnet_wcf", "apache_dubbo");

        assertThat(List.of(
                SemanticAttributes.MessageTypeValues.SENT,
                SemanticAttributes.MessageTypeValues.RECEIVED
        )).containsExactly("SENT", "RECEIVED");

        assertThat(List.of(
                SemanticAttributes.RpcGrpcStatusCodeValues.OK,
                SemanticAttributes.RpcGrpcStatusCodeValues.CANCELLED,
                SemanticAttributes.RpcGrpcStatusCodeValues.UNKNOWN,
                SemanticAttributes.RpcGrpcStatusCodeValues.INVALID_ARGUMENT,
                SemanticAttributes.RpcGrpcStatusCodeValues.DEADLINE_EXCEEDED,
                SemanticAttributes.RpcGrpcStatusCodeValues.NOT_FOUND,
                SemanticAttributes.RpcGrpcStatusCodeValues.ALREADY_EXISTS,
                SemanticAttributes.RpcGrpcStatusCodeValues.PERMISSION_DENIED,
                SemanticAttributes.RpcGrpcStatusCodeValues.RESOURCE_EXHAUSTED,
                SemanticAttributes.RpcGrpcStatusCodeValues.FAILED_PRECONDITION,
                SemanticAttributes.RpcGrpcStatusCodeValues.ABORTED,
                SemanticAttributes.RpcGrpcStatusCodeValues.OUT_OF_RANGE,
                SemanticAttributes.RpcGrpcStatusCodeValues.UNIMPLEMENTED,
                SemanticAttributes.RpcGrpcStatusCodeValues.INTERNAL,
                SemanticAttributes.RpcGrpcStatusCodeValues.UNAVAILABLE,
                SemanticAttributes.RpcGrpcStatusCodeValues.DATA_LOSS,
                SemanticAttributes.RpcGrpcStatusCodeValues.UNAUTHENTICATED
        )).containsExactly(0L, 1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L, 12L, 13L, 14L, 15L, 16L);
    }

    private static void assertAttributeKey(AttributeKey<?> attributeKey, String expectedKey, AttributeType expectedType) {
        assertThat(attributeKey.getKey()).isEqualTo(expectedKey);
        assertThat(attributeKey.getType()).isEqualTo(expectedType);
    }
}
