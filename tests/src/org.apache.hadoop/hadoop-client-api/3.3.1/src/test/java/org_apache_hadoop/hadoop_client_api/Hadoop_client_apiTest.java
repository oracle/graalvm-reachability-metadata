/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop.hadoop_client_api;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.StorageSize;
import org.apache.hadoop.conf.StorageUnit;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.permission.FsAction;
import org.apache.hadoop.fs.permission.FsPermission;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.MapWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.WritableComparator;
import org.apache.hadoop.io.compress.CompressionInputStream;
import org.apache.hadoop.io.compress.CompressionOutputStream;
import org.apache.hadoop.io.compress.GzipCodec;
import org.apache.hadoop.metrics2.AbstractMetric;
import org.apache.hadoop.metrics2.MetricType;
import org.apache.hadoop.metrics2.MetricsRecord;
import org.apache.hadoop.metrics2.MetricsRecordBuilder;
import org.apache.hadoop.metrics2.MetricsTag;
import org.apache.hadoop.metrics2.impl.MetricsCollectorImpl;
import org.apache.hadoop.metrics2.lib.MetricsRegistry;
import org.apache.hadoop.security.token.TokenIdentifier;
import org.apache.hadoop.util.ReflectionUtils;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import org.apache.hadoop.util.bloom.BloomFilter;
import org.apache.hadoop.util.bloom.Key;
import org.apache.hadoop.util.hash.Hash;
import org.apache.hadoop.yarn.api.records.ApplicationAttemptId;
import org.apache.hadoop.yarn.api.records.ApplicationId;
import org.apache.hadoop.yarn.api.records.ContainerId;
import org.apache.hadoop.yarn.api.records.ExecutionType;
import org.apache.hadoop.yarn.api.records.NodeId;
import org.apache.hadoop.yarn.api.records.Priority;
import org.apache.hadoop.yarn.api.records.Resource;
import org.apache.hadoop.yarn.security.ContainerTokenIdentifier;
import org.apache.hadoop.yarn.security.DockerCredentialTokenIdentifier;
import org.apache.hadoop.yarn.security.NMTokenIdentifier;
import org.apache.hadoop.yarn.security.client.ClientToAMTokenIdentifier;
import org.apache.hadoop.yarn.server.api.ContainerType;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(60)
public class Hadoop_client_apiTest {
    @Test
    void configurationLoadsXmlResourcesAndParsesTypedValues() throws Exception {
        Configuration configuration = new Configuration(false);
        String xml = """
                <configuration>
                  <property>
                    <name>service.hosts</name>
                    <value> nn1.example.test, nn2.example.test,nn3.example.test </value>
                  </property>
                  <property>
                    <name>worker.range</name>
                    <value>2-4,7</value>
                  </property>
                  <property>
                    <name>prefixed.alpha</name>
                    <value>a</value>
                  </property>
                </configuration>
                """;
        configuration.addResource(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), "in-memory.xml", false);
        configuration.setInt("replication", 3);
        configuration.setLong("block.size", 134_217_728L);
        configuration.setFloat("spill.percent", 0.75f);
        configuration.setDouble("sample.ratio", 0.125d);
        configuration.setBoolean("feature.enabled", true);
        configuration.setEnum("mode", SampleMode.ACTIVE);
        configuration.setTimeDuration("rpc.timeout", 1500L, TimeUnit.MILLISECONDS);
        configuration.setStorageSize("cache.size", 2.0d, StorageUnit.MB);
        configuration.setStrings("queue.names", "root.default", "root.analytics");

        assertThat(configuration.getTrimmedStringCollection("service.hosts"))
                .containsExactly("nn1.example.test", "nn2.example.test", "nn3.example.test");
        assertThat(configuration.getInt("replication", 0)).isEqualTo(3);
        assertThat(configuration.getLong("block.size", 0L)).isEqualTo(134_217_728L);
        assertThat(configuration.getFloat("spill.percent", 0.0f)).isEqualTo(0.75f);
        assertThat(configuration.getDouble("sample.ratio", 0.0d)).isEqualTo(0.125d);
        assertThat(configuration.getBoolean("feature.enabled", false)).isTrue();
        assertThat(configuration.getEnum("mode", SampleMode.PASSIVE)).isEqualTo(SampleMode.ACTIVE);
        assertThat(configuration.getTimeDuration("rpc.timeout", 0L, TimeUnit.MILLISECONDS)).isEqualTo(1500L);
        assertThat(configuration.getStorageSize("cache.size", 0.0d, StorageUnit.KB)).isEqualTo(2048.0d);
        assertThat(configuration.getStrings("queue.names")).containsExactly("root.default", "root.analytics");
        assertThat(configuration.getRange("worker.range", "").isIncluded(3)).isTrue();
        assertThat(configuration.getRange("worker.range", "").isIncluded(6)).isFalse();
        assertThat(configuration.getPropsWithPrefix("prefixed.")).containsEntry("alpha", "a");
    }

    @Test
    void storageSizesPathsAndFilePermissionsKeepHadoopSemantics() throws Exception {
        StorageSize size = StorageSize.parse("1.5GB");
        assertThat(size.getUnit()).isEqualTo(StorageUnit.GB);
        assertThat(size.getValue()).isEqualTo(1.5d);
        assertThat(StorageUnit.GB.toMBs(size.getValue())).isEqualTo(1536.0d);

        Path qualified = new Path("hdfs", "namenode.example.test:8020", "/warehouse/tables/events/part-0001");
        assertThat(qualified.toUri())
                .isEqualTo(new URI("hdfs://namenode.example.test:8020/warehouse/tables/events/part-0001"));
        assertThat(qualified.getName()).isEqualTo("part-0001");
        assertThat(qualified.getParent())
                .isEqualTo(new Path("hdfs://namenode.example.test:8020/warehouse/tables/events"));
        assertThat(qualified.suffix(".crc").toString()).endsWith("part-0001.crc");
        assertThat(Path.getPathWithoutSchemeAndAuthority(qualified))
                .isEqualTo(new Path("/warehouse/tables/events/part-0001"));
        assertThat(new Path("/warehouse/tables/events/part-0001").depth()).isEqualTo(4);

        FsPermission permission = new FsPermission("640");
        assertThat(permission.getUserAction()).isEqualTo(FsAction.READ_WRITE);
        assertThat(permission.getGroupAction()).isEqualTo(FsAction.READ);
        assertThat(permission.getOtherAction()).isEqualTo(FsAction.NONE);
        assertThat(permission.toShort()).isEqualTo((short) 0640);
        assertThat(permission.toString()).isEqualTo("rw-r-----");
        assertThat(new FsPermission("777").applyUMask(new FsPermission("027")).toString()).isEqualTo("rwxr-x---");
    }

    @Test
    void gzipCodecCompressesAndDecompressesStreams() throws Exception {
        GzipCodec codec = new GzipCodec();
        codec.setConf(new Configuration(false));
        byte[] expected = "row-1\nrow-2\nrow-3\n".getBytes(StandardCharsets.UTF_8);

        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (CompressionOutputStream output = codec.createOutputStream(compressed)) {
            output.write(expected);
            output.finish();
        }

        assertThat(compressed.toByteArray().length).isGreaterThan(0);
        assertThat(codec.getDefaultExtension()).isEqualTo(".gz");

        byte[] actual;
        try (CompressionInputStream input = codec.createInputStream(new ByteArrayInputStream(compressed.toByteArray()))) {
            actual = input.readAllBytes();
        }

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void writableValueTypesCompareCopyAndExposeUtf8Content() throws Exception {
        Text greeting = new Text("hello");
        greeting.append(" hadoop".getBytes(StandardCharsets.UTF_8), 0, " hadoop".length());
        ByteBuffer encoded = Text.encode("native image");
        byte[] encodedBytes = new byte[encoded.remaining()];
        encoded.get(encodedBytes);

        assertThat(greeting.toString()).isEqualTo("hello hadoop");
        assertThat(greeting.find("hadoop")).isEqualTo(6);
        assertThat(greeting.charAt(0)).isEqualTo('h');
        assertThat(Text.decode(encodedBytes)).isEqualTo("native image");
        assertThat(Text.utf8Length("\u00E9clair")).isGreaterThan("\u00E9clair".length());

        BytesWritable bytes = new BytesWritable("abcdef".getBytes(StandardCharsets.UTF_8));
        bytes.set(bytes.getBytes(), 1, 3);
        assertThat(bytes.copyBytes()).containsExactly((byte) 'b', (byte) 'c', (byte) 'd');

        MapWritable map = new MapWritable();
        map.put(new Text("attempts"), new IntWritable(2));
        map.put(new Text("status"), new Text("accepted"));
        MapWritable copy = new MapWritable(map);
        assertThat(copy).hasSize(2);
        assertThat(copy.get(new Text("attempts"))).isEqualTo(new IntWritable(2));
        assertThat(copy.get(new Text("status"))).isEqualTo(new Text("accepted"));

        WritableComparator comparator = WritableComparator.get(IntWritable.class, new Configuration(false));
        assertThat(comparator.compare(new IntWritable(2), new IntWritable(5))).isLessThan(0);
        assertThat(comparator.newKey()).isInstanceOf(IntWritable.class);
    }

    @Test
    void bloomFiltersAndHashImplementDeterministicMembershipChecks() {
        Hash murmur = Hash.getInstance(Hash.MURMUR_HASH);
        byte[] payload = "alpha".getBytes(StandardCharsets.UTF_8);
        assertThat(murmur.hash(payload)).isEqualTo(murmur.hash(payload));
        assertThat(murmur.hash(payload, 17)).isEqualTo(murmur.hash(payload, 17));
        assertThat(Hash.parseHashType("murmur")).isEqualTo(Hash.MURMUR_HASH);

        BloomFilter filter = new BloomFilter(2048, 5, Hash.MURMUR_HASH);
        Key alpha = new Key("alpha".getBytes(StandardCharsets.UTF_8));
        Key beta = new Key("beta".getBytes(StandardCharsets.UTF_8), 2.0d);
        filter.add(alpha);
        filter.add(beta);
        beta.incrementWeight();

        assertThat(filter.membershipTest(alpha)).isTrue();
        assertThat(filter.membershipTest(beta)).isTrue();
        assertThat(beta.getWeight()).isEqualTo(3.0d);
        assertThat(filter.getVectorSize()).isEqualTo(2048);
        assertThat(filter.toString()).isNotBlank();
    }

    @Test
    void metricsRegistrySnapshotsCountersGaugesAndTags() {
        MetricsRegistry registry = new MetricsRegistry("client");
        registry.setContext("unit-test");
        registry.tag("component", "component name", "client-api");
        registry.newCounter("requests", "completed requests", 1L).incr(4L);
        registry.newGauge("openConnections", "open connections", 2).incr();
        registry.newRate("latency", "request latency", false).add(10L);

        MetricsCollectorImpl collector = new MetricsCollectorImpl();
        MetricsRecordBuilder builder = collector.addRecord("snapshot");
        registry.snapshot(builder, true);
        List<?> records = collector.getRecords();

        assertThat(records).hasSize(1);
        MetricsRecord record = (MetricsRecord) records.get(0);
        assertThat(record.context()).isEqualTo("unit-test");
        assertThat(record.tags())
                .extracting(MetricsTag::name, MetricsTag::value)
                .contains(Tuple.tuple("component", "client-api"));
        assertThat(record.metrics())
                .extracting(AbstractMetric::name, AbstractMetric::value, AbstractMetric::type)
                .contains(
                        Tuple.tuple("requests", 5L, MetricType.COUNTER),
                        Tuple.tuple("openConnections", 3, MetricType.GAUGE));
    }

    @Test
    void reflectionUtilsCreatesConfiguredPublicTypesThroughHadoopFactoryApi() {
        Configuration configuration = new Configuration(false);
        configuration.set("component.name", "configured-component");

        ConfiguredComponent component = ReflectionUtils.newInstance(ConfiguredComponent.class, configuration);

        assertThat(component.getConf()).isSameAs(configuration);
        assertThat(component.getName()).isEqualTo("configured-component");
    }

    @Test
    void toolRunnerAppliesGenericOptionsAndPassesApplicationArguments() throws Exception {
        Configuration configuration = new Configuration(false);
        CapturingTool tool = new CapturingTool();

        int exitCode = ToolRunner.run(configuration, tool, new String[] {
                "-D", "client.queue=analytics",
                "-Dclient.timeout.ms=2500",
                "input/events",
                "output/results"
        });

        assertThat(exitCode).isZero();
        assertThat(tool.getQueue()).isEqualTo("analytics");
        assertThat(tool.getTimeoutMillis()).isEqualTo(2500L);
        assertThat(tool.getArguments()).containsExactly("input/events", "output/results");
        assertThat(configuration.get("client.queue")).isEqualTo("analytics");
    }

    @Test
    void yarnRecordsAndTokenIdentifiersExposeStablePublicState() {
        ApplicationId applicationId = ApplicationId.newInstance(123456789L, 42);
        ApplicationAttemptId attemptId = ApplicationAttemptId.newInstance(applicationId, 3);
        ContainerId containerId = ContainerId.newContainerId(attemptId, 9L);
        Resource resource = Resource.newInstance(4096, 2);
        Priority priority = Priority.newInstance(7);
        NodeId nodeId = NodeId.newInstance("worker.example.test", 8042);

        assertThat(ApplicationId.fromString(applicationId.toString())).isEqualTo(applicationId);
        assertThat(ApplicationAttemptId.fromString(attemptId.toString())).isEqualTo(attemptId);
        assertThat(ContainerId.fromString(containerId.toString())).isEqualTo(containerId);
        assertThat(nodeId.getHost()).isEqualTo("worker.example.test");
        assertThat(nodeId.getPort()).isEqualTo(8042);
        assertThat(resource.getMemorySize()).isEqualTo(4096L);
        assertThat(resource.getVirtualCores()).isEqualTo(2);
        assertThat(priority.getPriority()).isEqualTo(7);

        DockerCredentialTokenIdentifier dockerToken = new DockerCredentialTokenIdentifier(
                "registry.example.test", applicationId.toString());
        ClientToAMTokenIdentifier clientToken = new ClientToAMTokenIdentifier(attemptId, "alice");
        NMTokenIdentifier nmToken = new NMTokenIdentifier(attemptId, nodeId, "alice", 11);
        ContainerTokenIdentifier containerToken = new ContainerTokenIdentifier(
                containerId,
                1,
                "worker.example.test:8042",
                "alice",
                resource,
                10_000L,
                11,
                123456789L,
                priority,
                222L,
                null,
                "gpu",
                ContainerType.TASK,
                ExecutionType.GUARANTEED,
                99L,
                Set.of("analytics"));

        assertTokenKind(dockerToken, DockerCredentialTokenIdentifier.KIND);
        assertTokenKind(clientToken, ClientToAMTokenIdentifier.KIND_NAME);
        assertTokenKind(nmToken, NMTokenIdentifier.KIND);
        assertTokenKind(containerToken, ContainerTokenIdentifier.KIND);
        assertThat(dockerToken.getRegistryUrl()).isEqualTo("registry.example.test");
        assertThat(dockerToken.getApplicationId()).isEqualTo(applicationId.toString());
        assertThat(clientToken.getApplicationAttemptID()).isEqualTo(attemptId);
        assertThat(clientToken.getClientName()).isEqualTo("alice");
        assertThat(nmToken.getApplicationAttemptId()).isEqualTo(attemptId);
        assertThat(nmToken.getNodeId()).isEqualTo(nodeId);
        assertThat(nmToken.getKeyId()).isEqualTo(11);
        assertThat(containerToken.getContainerID()).isEqualTo(containerId);
        assertThat(containerToken.getApplicationSubmitter()).isEqualTo("alice");
        assertThat(containerToken.getNmHostAddress()).isEqualTo("worker.example.test:8042");
        assertThat(containerToken.getNodeLabelExpression()).isEqualTo("gpu");
        assertThat(containerToken.getContainerType()).isEqualTo(ContainerType.TASK);
        assertThat(containerToken.getExecutionType()).isEqualTo(ExecutionType.GUARANTEED);
        assertThat(containerToken.getAllcationTags()).containsExactly("analytics");
    }

    private static void assertTokenKind(TokenIdentifier identifier, Text expectedKind) {
        assertThat(identifier.getKind()).isEqualTo(expectedKind);
    }

    public enum SampleMode {
        ACTIVE,
        PASSIVE
    }

    public static class ConfiguredComponent extends Configured {
        public ConfiguredComponent() {
        }

        String getName() {
            return getConf().get("component.name");
        }
    }

    public static class CapturingTool extends Configured implements Tool {
        private List<String> arguments = List.of();
        private String queue;
        private long timeoutMillis;

        @Override
        public int run(String[] args) {
            arguments = List.of(args);
            queue = getConf().get("client.queue");
            timeoutMillis = getConf().getLong("client.timeout.ms", -1L);
            return 0;
        }

        List<String> getArguments() {
            return arguments;
        }

        String getQueue() {
            return queue;
        }

        long getTimeoutMillis() {
            return timeoutMillis;
        }
    }
}
