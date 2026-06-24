/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_awspring_cloud.spring_cloud_aws_parameter_store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.awspring.cloud.parameterstore.ParameterStorePropertySource;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParametersByPathRequest;
import software.amazon.awssdk.services.ssm.model.GetParametersByPathResponse;
import software.amazon.awssdk.services.ssm.model.Parameter;

public class Spring_cloud_aws_parameter_storeTest {
    @Test
    void loadsHierarchicalParametersFromAllPages() {
        RecordingSsmClient ssmClient = new RecordingSsmClient(
                response("next-page", parameter("/config/application/server/port", "8080")),
                response(null, parameter("/config/application/features/_0_/name", "alpha"),
                        parameter("/config/application/features/_1_/name", "beta")));
        ParameterStorePropertySource propertySource = new ParameterStorePropertySource(
                "/config/application", ssmClient);

        propertySource.init();

        assertThat(propertySource.getName()).isEqualTo("aws-parameterstore:/config/application");
        assertThat(propertySource.getPropertyNames()).containsExactly(".server.port", ".features.[0].name",
                ".features.[1].name");
        assertThat(propertySource.getProperty(".server.port")).isEqualTo("8080");
        assertThat(propertySource.getProperty(".features.[0].name")).isEqualTo("alpha");
        assertThat(propertySource.getProperty(".features.[1].name")).isEqualTo("beta");
        assertThat(propertySource.getProperty("missing")).isNull();
        assertThat(ssmClient.requests()).hasSize(2);
        assertRequest(ssmClient.requests().get(0), "/config/application", null);
        assertRequest(ssmClient.requests().get(1), "/config/application", "next-page");
    }

    @Test
    void addsConfiguredPrefixToHierarchicalPropertyNames() {
        RecordingSsmClient ssmClient = new RecordingSsmClient(
                response(null, parameter("/config/service/database/url", "jdbc:postgresql://localhost/test"),
                        parameter("/config/service/database/username", "test-user")));
        ParameterStorePropertySource propertySource = new ParameterStorePropertySource(
                "/config/service?prefix=aws", ssmClient);

        propertySource.init();

        assertThat(propertySource.getName()).isEqualTo("aws-parameterstore:/config/service?prefix=aws");
        assertThat(propertySource.getPropertyNames()).containsExactly("aws.database.url", "aws.database.username");
        assertThat(propertySource.getProperty("aws.database.url")).isEqualTo("jdbc:postgresql://localhost/test");
        assertThat(propertySource.getProperty("aws.database.username")).isEqualTo("test-user");
        assertThat(ssmClient.requests()).singleElement()
                .satisfies(request -> assertRequest(request, "/config/service", null));
    }

    @Test
    void addsPrefixAsIsToSlashTerminatedParameterPath() {
        RecordingSsmClient ssmClient = new RecordingSsmClient(
                response(null, parameter("/config/my-datasource/url", "jdbc:mysql://localhost:3306"),
                        parameter("/config/my-datasource/username", "db-user")));
        ParameterStorePropertySource propertySource = new ParameterStorePropertySource(
                "/config/my-datasource/?prefix=spring.datasource.", ssmClient);

        propertySource.init();

        assertThat(propertySource.getPropertyNames()).containsExactly("spring.datasource.url",
                "spring.datasource.username");
        assertThat(propertySource.getProperty("spring.datasource.url")).isEqualTo("jdbc:mysql://localhost:3306");
        assertThat(propertySource.getProperty("spring.datasource.username")).isEqualTo("db-user");
        assertThat(propertySource.getProperty("url")).isNull();
        assertThat(propertySource.getProperty("spring.datasourceurl")).isNull();
        assertThat(ssmClient.requests()).singleElement()
                .satisfies(request -> assertRequest(request, "/config/my-datasource/", null));
    }

    @Test
    void loadsJavaPropertiesDocumentFromParameterValue() {
        String propertiesDocument = """
                service.name=orders
                service.timeout=PT5S
                feature.enabled=true
                """;
        RecordingSsmClient ssmClient = new RecordingSsmClient(
                response(null, parameter("/config/orders/application.properties", propertiesDocument)));
        ParameterStorePropertySource propertySource = new ParameterStorePropertySource(
                "/config/orders?extension=properties", ssmClient);

        propertySource.init();

        assertThat(propertySource.getPropertyNames()).containsExactlyInAnyOrder("service.name", "service.timeout",
                "feature.enabled");
        assertThat(propertySource.getProperty("service.name")).isEqualTo("orders");
        assertThat(propertySource.getProperty("service.timeout")).isEqualTo("PT5S");
        assertThat(propertySource.getProperty("feature.enabled")).isEqualTo("true");
        assertThat(ssmClient.requests()).singleElement()
                .satisfies(request -> assertRequest(request, "/config/orders", null));
    }

    @Test
    void loadsYamlDocumentFromParameterValue() {
        String yamlDocument = """
                service:
                  name: billing
                  retry:
                    attempts: 3
                endpoints:
                  - https://one.example.test
                  - https://two.example.test
                """;
        RecordingSsmClient ssmClient = new RecordingSsmClient(
                response(null, parameter("/config/billing/application.yaml", yamlDocument)));
        ParameterStorePropertySource propertySource = new ParameterStorePropertySource(
                "/config/billing?extension=yaml", ssmClient);

        propertySource.init();

        assertThat(propertySource.getPropertyNames())
                .containsExactlyInAnyOrder("service.name", "service.retry.attempts", "endpoints[0]", "endpoints[1]");
        assertThat(propertySource.getProperty("service.name")).isEqualTo("billing");
        assertThat(propertySource.getProperty("service.retry.attempts")).isEqualTo(3);
        assertThat(propertySource.getProperty("endpoints[0]")).isEqualTo("https://one.example.test");
        assertThat(propertySource.getProperty("endpoints[1]")).isEqualTo("https://two.example.test");
    }

    @Test
    void loadsJsonDocumentAsYamlCompatibleParameterValue() {
        String jsonDocument = """
                {
                  "service": {
                    "name": "inventory",
                    "port": 9090
                  }
                }
                """;
        RecordingSsmClient ssmClient = new RecordingSsmClient(
                response(null, parameter("/config/inventory/application.json", jsonDocument)));
        ParameterStorePropertySource propertySource = new ParameterStorePropertySource(
                "/config/inventory?extension=json", ssmClient);

        propertySource.init();

        assertThat(propertySource.getPropertyNames()).containsExactlyInAnyOrder("service.name", "service.port");
        assertThat(propertySource.getProperty("service.name")).isEqualTo("inventory");
        assertThat(propertySource.getProperty("service.port")).isEqualTo(9090);
    }

    @Test
    void acceptsDocumentExtensionValuesCaseInsensitively() {
        String propertiesDocument = """
                client.name=parameter-store
                client.region=us-east-1
                """;
        RecordingSsmClient ssmClient = new RecordingSsmClient(
                response(null, parameter("/config/case/application.properties", propertiesDocument)));
        ParameterStorePropertySource propertySource = new ParameterStorePropertySource(
                "/config/case?extension=PrOpErTiEs", ssmClient);

        propertySource.init();

        assertThat(propertySource.getPropertyNames()).containsExactlyInAnyOrder("client.name", "client.region");
        assertThat(propertySource.getProperty("client.name")).isEqualTo("parameter-store");
        assertThat(propertySource.getProperty("client.region")).isEqualTo("us-east-1");
        assertThat(ssmClient.requests()).singleElement()
                .satisfies(request -> assertRequest(request, "/config/case", null));
    }

    @Test
    void copyCreatesIndependentPropertySourceWithSameContextAndClient() {
        RecordingSsmClient ssmClient = new RecordingSsmClient(
                response(null, parameter("/config/copy/source/value", "original")),
                response(null, parameter("/config/copy/source/value", "copied")));
        ParameterStorePropertySource propertySource = new ParameterStorePropertySource(
                "/config/copy/source", ssmClient);

        ParameterStorePropertySource copy = propertySource.copy();
        propertySource.init();
        copy.init();

        assertThat(copy).isNotSameAs(propertySource);
        assertThat(copy.getName()).isEqualTo(propertySource.getName());
        assertThat(propertySource.getProperty(".value")).isEqualTo("original");
        assertThat(copy.getProperty(".value")).isEqualTo("copied");
        assertThat(ssmClient.requests()).hasSize(2);
        assertRequest(ssmClient.requests().get(0), "/config/copy/source", null);
        assertRequest(ssmClient.requests().get(1), "/config/copy/source", null);
    }

    @Test
    void rejectsUnknownDocumentExtension() {
        RecordingSsmClient ssmClient = new RecordingSsmClient();

        assertThatThrownBy(() -> new ParameterStorePropertySource("/config/application?extension=toml", ssmClient))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid prefixType: toml")
                .hasMessageContaining("properties, json, or yaml");
        assertThat(ssmClient.requests()).isEmpty();
    }

    private static void assertRequest(GetParametersByPathRequest request, String path, String nextToken) {
        assertThat(request.path()).isEqualTo(path);
        assertThat(request.recursive()).isTrue();
        assertThat(request.withDecryption()).isTrue();
        assertThat(request.nextToken()).isEqualTo(nextToken);
    }

    private static GetParametersByPathResponse response(String nextToken, Parameter... parameters) {
        return GetParametersByPathResponse.builder().parameters(parameters).nextToken(nextToken).build();
    }

    private static Parameter parameter(String name, String value) {
        return Parameter.builder().name(name).value(value).build();
    }

    private static final class RecordingSsmClient implements SsmClient {
        private final List<GetParametersByPathResponse> responses;
        private final List<GetParametersByPathRequest> requests = new ArrayList<>();
        private int responseIndex;

        private RecordingSsmClient(GetParametersByPathResponse... responses) {
            this.responses = List.of(responses);
        }

        @Override
        public GetParametersByPathResponse getParametersByPath(GetParametersByPathRequest request) {
            requests.add(request);
            assertThat(responseIndex).isLessThan(responses.size());
            return responses.get(responseIndex++);
        }

        @Override
        public String serviceName() {
            return "ssm";
        }

        @Override
        public void close() {
        }

        private List<GetParametersByPathRequest> requests() {
            return requests;
        }
    }
}
