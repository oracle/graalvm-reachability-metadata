/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.compression.snappy.SnappyCodec;
import com.couchbase.client.core.config.GlobalConfig;
import com.couchbase.client.core.config.GlobalConfigParser;
import com.couchbase.client.core.env.CompressionConfig;
import com.couchbase.client.core.env.CoreEnvironment;
import com.couchbase.client.core.env.IoConfig;
import com.couchbase.client.core.env.NetworkResolution;
import com.couchbase.client.core.env.PasswordAuthenticator;
import com.couchbase.client.core.env.PropertyLoader;
import com.couchbase.client.core.env.TimeoutConfig;
import com.couchbase.client.core.env.UsernameAndPassword;
import com.couchbase.client.core.json.Mapper;
import com.couchbase.client.core.json.stream.JsonStreamParser;
import com.couchbase.client.core.projections.JsonPathParser;
import com.couchbase.client.core.projections.PathArray;
import com.couchbase.client.core.projections.PathObjectOrField;
import com.couchbase.client.core.service.ServiceType;
import com.couchbase.client.core.util.ConnectionString;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

public class Core_ioTest {

    @Test
    void parsesAndFormatsConnectionStringsWithTypedPortsAndParameters() {
        ConnectionString connectionString = ConnectionString.create(
                "couchbases://alice@node-a.example:11207=kv,node-b.example:8091=manager"
                        + "?network=external&compression=on");

        assertThat(connectionString.scheme()).isEqualTo(ConnectionString.Scheme.COUCHBASES);
        assertThat(connectionString.username()).isEqualTo("alice");
        assertThat(connectionString.hosts()).hasSize(2);
        assertThat(connectionString.hosts().get(0).host()).isEqualTo("node-a.example");
        assertThat(connectionString.hosts().get(0).port()).isEqualTo(11207);
        assertThat(connectionString.hosts().get(0).portType())
                .contains(ConnectionString.PortType.KV);
        assertThat(connectionString.hosts().get(1).portType())
                .contains(ConnectionString.PortType.MANAGER);
        assertThat(connectionString.params())
                .hasSize(2)
                .containsEntry("network", "external")
                .containsEntry("compression", "on");
        assertThat(ConnectionString.create(connectionString.original()).original())
                .isEqualTo(connectionString.original());
        assertThat(ConnectionString.fromHostnames(List.of("cluster.example")).dnsSrvCandidate())
                .contains("cluster.example");
    }

    @Test
    void loadsNestedEnvironmentPropertiesUsingDocumentedStringRepresentations() {
        CoreEnvironment.Builder<?> builder = CoreEnvironment.builder();
        builder.load(PropertyLoader.fromMap(Map.of(
                "compression.enable", "false",
                "compression.minSize", "256",
                "compression.minRatio", "0.75",
                "io.captureTraffic", "KV, QUERY",
                "io.enableDnsSrv", "false",
                "io.maxHttpConnections", "24",
                "io.networkResolution", "external",
                "maxNumRequestsInRetry", "4096",
                "timeout.connectTimeout", "12s",
                "timeout.kvTimeout", "10s")));

        CompressionConfig compression = builder.compressionConfig().build();
        IoConfig io = builder.ioConfig().build();
        TimeoutConfig timeouts = builder.timeoutConfig().build();

        assertThat(compression.enabled()).isFalse();
        assertThat(compression.minSize()).isEqualTo(256);
        assertThat(compression.minRatio()).isEqualTo(0.75);
        assertThat(io.dnsSrvEnabled()).isFalse();
        assertThat(io.maxHttpConnections()).isEqualTo(24);
        assertThat(io.networkResolution()).isEqualTo(NetworkResolution.EXTERNAL);
        assertThat(io.servicesToCapture()).containsExactlyInAnyOrder(ServiceType.KV, ServiceType.QUERY);
        assertThat(timeouts.connectTimeout()).isEqualTo(Duration.ofSeconds(12));
        assertThat(timeouts.kvTimeout()).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void createsAndClosesAnEnvironmentWithObservableRuntimeConfiguration() {
        CoreEnvironment environment = CoreEnvironment.builder()
                .maxNumRequestsInRetry(4096)
                .timeoutConfig(config -> config.disconnectTimeout(Duration.ofSeconds(10)))
                .build();
        try {
            assertThat(environment.maxNumRequestsInRetry()).isEqualTo(4096);
            assertThat(environment.userAgent().formattedLong()).contains("java-core");
            assertThat(environment.coreVersion()).isPresent();
            assertThat(environment.eventBus()).isNotNull();
            assertThat(environment.scheduler()).isNotNull();
        } finally {
            environment.shutdown(Duration.ofSeconds(10));
        }
    }

    @Test
    void parsesGlobalClusterConfigurationIntoTypedServicePorts() {
        String json = """
                {
                  "rev": 42,
                  "revEpoch": 7,
                  "nodesExt": [
                    {
                      "hostname": "node-a.example",
                      "serverGroup": "rack-a",
                      "services": {
                        "mgmt": 8091,
                        "mgmtSSL": 18091,
                        "kv": 11210,
                        "kvSSL": 11207,
                        "n1ql": 8093
                      }
                    }
                  ],
                  "clusterCapabilities": {}
                }
                """;

        GlobalConfig config = GlobalConfigParser.parse(json, "origin.example");

        assertThat(config.version().rev()).isEqualTo(42);
        assertThat(config.version().epoch()).isEqualTo(7);
        assertThat(config.portInfos()).hasSize(1);
        assertThat(config.portInfos().get(0).hostname()).isEqualTo("node-a.example");
        assertThat(config.portInfos().get(0).serverGroup()).isEqualTo("rack-a");
        assertThat(config.portInfos().get(0).ports())
                .containsEntry(ServiceType.MANAGER, 8091)
                .containsEntry(ServiceType.KV, 11210)
                .containsEntry(ServiceType.QUERY, 8093);
        assertThat(config.portInfos().get(0).sslPorts())
                .containsEntry(ServiceType.MANAGER, 18091)
                .containsEntry(ServiceType.KV, 11207);
    }

    @Test
    void mapsJsonCollectionsAndTreesThroughTheSdkMapper() {
        String json = """
                {
                  "name": "inventory",
                  "replicas": 2,
                  "services": ["kv", "query"]
                }
                """;

        Map<?, ?> decoded = Mapper.decodeInto(json, Map.class);
        byte[] encoded = Mapper.encodeAsBytes(decoded);

        assertThat(decoded.get("name")).isEqualTo("inventory");
        assertThat(decoded.get("replicas")).isEqualTo(2);
        assertThat(decoded.get("services")).isEqualTo(List.of("kv", "query"));
        assertThat(Mapper.decodeIntoTree(encoded).get("name").asText()).isEqualTo("inventory");
        assertThat(new String(encoded, StandardCharsets.UTF_8)).contains("\"replicas\":2");
    }

    @Test
    void parsesProjectionPathsIntoFieldAndArraySegments() {
        assertThat(JsonPathParser.parse("customer.orders[2].items[1].sku"))
                .containsExactly(
                        new PathObjectOrField("customer"),
                        new PathArray("orders", 2),
                        new PathArray("items", 1),
                        new PathObjectOrField("sku"));
    }

    @Test
    void extractsJsonPointerValuesFromChunkedInput() {
        List<String> skus = new ArrayList<>();
        List<Long> quantities = new ArrayList<>();
        byte[] json = """
                {"orders":[{"sku":"coffee","quantity":2},{"sku":"tea","quantity":5}]}
                """
                .getBytes(StandardCharsets.UTF_8);

        try (JsonStreamParser parser = JsonStreamParser.builder()
                .doOnValue("/orders/-/sku", value -> skus.add(value.readString()))
                .doOnValue("/orders/-/quantity", value -> quantities.add(value.readLong()))
                .build()) {
            for (int offset = 0; offset < json.length; offset += 7) {
                parser.feed(ByteBuffer.wrap(json, offset, Math.min(7, json.length - offset)));
            }
            parser.endOfInput();
        }

        assertThat(skus).containsExactly("coffee", "tea");
        assertThat(quantities).containsExactly(2L, 5L);
    }

    @Test
    void passwordAuthenticatorReadsUpdatedCredentialsAndEncodesHttpHeaders() {
        AtomicReference<UsernameAndPassword> credentials =
                new AtomicReference<>(new UsernameAndPassword("alice", "first-secret"));
        PasswordAuthenticator authenticator = PasswordAuthenticator.builder(credentials::get).build();

        assertThat(authenticator.getAuthHeaderValue()).isEqualTo("Basic YWxpY2U6Zmlyc3Qtc2VjcmV0");

        credentials.set(new UsernameAndPassword("bob", "second-secret"));

        assertThat(authenticator.getAuthHeaderValue()).isEqualTo("Basic Ym9iOnNlY29uZC1zZWNyZXQ=");
        assertThat(authenticator.requiresTls()).isFalse();
    }

    @Test
    void snappyCodecRoundTripsCompressibleBinaryPayload() {
        byte[] input = "couchbase-couchbase-couchbase-couchbase"
                .getBytes(StandardCharsets.UTF_8);

        byte[] compressed = SnappyCodec.instance().compress(input);
        byte[] restored = SnappyCodec.instance().decompress(compressed);

        assertThat(compressed).isNotEqualTo(input);
        assertThat(restored).isEqualTo(input);
    }
}
