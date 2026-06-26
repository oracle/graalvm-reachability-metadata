/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_opentelemetry_contrib.opentelemetry_aws_resources;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.contrib.aws.resource.BeanstalkResource;
import io.opentelemetry.contrib.aws.resource.BeanstalkResourceProvider;
import io.opentelemetry.contrib.aws.resource.Ec2Resource;
import io.opentelemetry.contrib.aws.resource.Ec2ResourceProvider;
import io.opentelemetry.contrib.aws.resource.EcsResourceProvider;
import io.opentelemetry.contrib.aws.resource.EksResourceProvider;
import io.opentelemetry.contrib.aws.resource.LambdaResource;
import io.opentelemetry.contrib.aws.resource.LambdaResourceProvider;
import io.opentelemetry.sdk.autoconfigure.spi.ResourceProvider;
import io.opentelemetry.sdk.resources.Resource;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(OrderAnnotation.class)
public class Opentelemetry_aws_resourcesTest {
    private static final AttributeKey<String> CLOUD_PROVIDER = AttributeKey.stringKey("cloud.provider");
    private static final AttributeKey<String> CLOUD_PLATFORM = AttributeKey.stringKey("cloud.platform");
    private static final AttributeKey<String> CLOUD_REGION = AttributeKey.stringKey("cloud.region");
    private static final AttributeKey<String> CLOUD_AVAILABILITY_ZONE =
            AttributeKey.stringKey("cloud.availability_zone");
    private static final AttributeKey<String> CLOUD_ACCOUNT_ID = AttributeKey.stringKey("cloud.account.id");
    private static final AttributeKey<String> HOST_ID = AttributeKey.stringKey("host.id");
    private static final AttributeKey<String> HOST_IMAGE_ID = AttributeKey.stringKey("host.image.id");
    private static final AttributeKey<String> HOST_NAME = AttributeKey.stringKey("host.name");
    private static final AttributeKey<String> HOST_TYPE = AttributeKey.stringKey("host.type");
    private static final String SCHEMA_URL = "https://opentelemetry.io/schemas/1.25.0";

    @Test
    @Order(1)
    void ec2ResourceReadsInstanceIdentityMetadataFromImdsEndpointOverride() throws Exception {
        ConcurrentLinkedQueue<String> requests = new ConcurrentLinkedQueue<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        server.setExecutor(executor);
        server.createContext("/", exchange -> handleEc2MetadataRequest(exchange, requests));
        server.start();

        String previousEndpoint = System.getProperty("otel.aws.imds.endpointOverride");
        try {
            System.setProperty(
                    "otel.aws.imds.endpointOverride",
                    server.getAddress().getHostString() + ":" + server.getAddress().getPort());

            Resource resource = Ec2Resource.get();

            assertThat(resource.getSchemaUrl()).isEqualTo(SCHEMA_URL);
            assertThat(resource.getAttributes().size()).isEqualTo(9);
            assertThat(resource.getAttribute(CLOUD_PROVIDER)).isEqualTo("aws");
            assertThat(resource.getAttribute(CLOUD_PLATFORM)).isEqualTo("aws_ec2");
            assertThat(resource.getAttribute(CLOUD_REGION)).isEqualTo("us-east-1");
            assertThat(resource.getAttribute(CLOUD_AVAILABILITY_ZONE)).isEqualTo("us-east-1c");
            assertThat(resource.getAttribute(CLOUD_ACCOUNT_ID)).isEqualTo("123456789012");
            assertThat(resource.getAttribute(HOST_ID)).isEqualTo("i-0123456789abcdef0");
            assertThat(resource.getAttribute(HOST_IMAGE_ID)).isEqualTo("ami-1234567890abcdef0");
            assertThat(resource.getAttribute(HOST_NAME)).isEqualTo("ip-10-0-0-42.ec2.internal");
            assertThat(resource.getAttribute(HOST_TYPE)).isEqualTo("m7g.large");

            assertThat(requests)
                    .containsExactly(
                            "PUT /latest/api/token ttl=60 token=null",
                            "GET /latest/dynamic/instance-identity/document ttl=null token=ec2-token",
                            "GET /latest/meta-data/hostname ttl=null token=ec2-token");
        } finally {
            if (previousEndpoint == null) {
                System.clearProperty("otel.aws.imds.endpointOverride");
            } else {
                System.setProperty("otel.aws.imds.endpointOverride", previousEndpoint);
            }
            server.stop(0);
            executor.shutdownNow();
        }
    }

    @Test
    @Order(2)
    void resourceProvidersParticipateInAutoconfigureSpi() {
        List<ResourceProvider> providers = List.of(
                new BeanstalkResourceProvider(),
                new Ec2ResourceProvider(),
                new EcsResourceProvider(),
                new EksResourceProvider(),
                new LambdaResourceProvider());

        assertThat(providers).hasSize(5);
        assertThat(Resource.create(Attributes.of(CLOUD_PROVIDER, "aws")).getAttribute(CLOUD_PROVIDER)).isEqualTo("aws");
    }

    @Test
    @Order(3)
    void awsResourceProvidersAreDiscoverableThroughJavaServiceLoader() {
        List<String> providerClassNames = ServiceLoader.load(ResourceProvider.class).stream()
                .map(provider -> provider.type().getName())
                .toList();

        assertThat(providerClassNames)
                .contains(
                        BeanstalkResourceProvider.class.getName(),
                        Ec2ResourceProvider.class.getName(),
                        EcsResourceProvider.class.getName(),
                        EksResourceProvider.class.getName(),
                        LambdaResourceProvider.class.getName());
    }

    @Test
    @Order(4)
    void providerCreateResourceDelegatesToCachedPublicResourceInstances() {
        assertThat(new BeanstalkResourceProvider().createResource(null)).isSameAs(BeanstalkResource.get());
        assertThat(new LambdaResourceProvider().createResource(null)).isSameAs(LambdaResource.get());
    }

    @Test
    @Order(5)
    void cloudResourceProvidersOnlyApplyWhenCloudProviderIsUnset() {
        Resource resourceWithoutCloudProvider = Resource.empty();
        Resource resourceWithCloudProvider = Resource.create(Attributes.of(CLOUD_PROVIDER, "azure"));

        assertThat(new BeanstalkResourceProvider().shouldApply(null, resourceWithoutCloudProvider)).isTrue();
        assertThat(new BeanstalkResourceProvider().shouldApply(null, resourceWithCloudProvider)).isFalse();
        assertThat(new Ec2ResourceProvider().shouldApply(null, resourceWithoutCloudProvider)).isTrue();
        assertThat(new Ec2ResourceProvider().shouldApply(null, resourceWithCloudProvider)).isFalse();
        assertThat(new EcsResourceProvider().shouldApply(null, resourceWithoutCloudProvider)).isTrue();
        assertThat(new EcsResourceProvider().shouldApply(null, resourceWithCloudProvider)).isFalse();
        assertThat(new EksResourceProvider().shouldApply(null, resourceWithoutCloudProvider)).isTrue();
        assertThat(new EksResourceProvider().shouldApply(null, resourceWithCloudProvider)).isFalse();
        assertThat(new LambdaResourceProvider().shouldApply(null, resourceWithoutCloudProvider)).isTrue();
        assertThat(new LambdaResourceProvider().shouldApply(null, resourceWithCloudProvider)).isFalse();
    }

    private static void handleEc2MetadataRequest(
            HttpExchange exchange, ConcurrentLinkedQueue<String> requests) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        String ttl = exchange.getRequestHeaders().getFirst("X-aws-ec2-metadata-token-ttl-seconds");
        String token = exchange.getRequestHeaders().getFirst("X-aws-ec2-metadata-token");
        requests.add(method + " " + path + " ttl=" + ttl + " token=" + token);

        if ("PUT".equals(method) && "/latest/api/token".equals(path)) {
            writeResponse(exchange, 200, "ec2-token");
            return;
        }
        if ("GET".equals(method) && "/latest/dynamic/instance-identity/document".equals(path)) {
            writeResponse(exchange, 200, """
                    {
                      "instanceId": "i-0123456789abcdef0",
                      "availabilityZone": "us-east-1c",
                      "instanceType": "m7g.large",
                      "imageId": "ami-1234567890abcdef0",
                      "accountId": "123456789012",
                      "region": "us-east-1"
                    }
                    """);
            return;
        }
        if ("GET".equals(method) && "/latest/meta-data/hostname".equals(path)) {
            writeResponse(exchange, 200, "ip-10-0-0-42.ec2.internal");
            return;
        }
        writeResponse(exchange, 404, "not found");
    }

    private static void writeResponse(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
