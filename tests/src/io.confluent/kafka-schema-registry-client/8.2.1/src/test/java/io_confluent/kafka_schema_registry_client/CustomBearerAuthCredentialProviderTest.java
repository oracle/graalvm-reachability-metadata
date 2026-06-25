/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_confluent.kafka_schema_registry_client;

import static org.assertj.core.api.Assertions.assertThat;

import io.confluent.kafka.schemaregistry.client.SchemaRegistryClientConfig;
import io.confluent.kafka.schemaregistry.client.security.bearerauth.CustomBearerAuthCredentialProvider;
import io.confluent.kafka.schemaregistry.client.security.bearerauth.StaticTokenCredentialProvider;
import java.net.URI;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class CustomBearerAuthCredentialProviderTest {

    @Test
    void configureInstantiatesAndDelegatesToConfiguredProvider() throws Exception {
        CustomBearerAuthCredentialProvider provider = new CustomBearerAuthCredentialProvider();
        Map<String, Object> configs = new HashMap<>();
        configs.put(
                SchemaRegistryClientConfig.BEARER_AUTH_CUSTOM_PROVIDER_CLASS,
                StaticTokenCredentialProvider.class.getName());
        configs.put(SchemaRegistryClientConfig.BEARER_AUTH_LOGICAL_CLUSTER, "logical-cluster-1");
        configs.put(SchemaRegistryClientConfig.BEARER_AUTH_IDENTITY_POOL_ID, "identity-pool-1");
        configs.put(SchemaRegistryClientConfig.BEARER_AUTH_TOKEN_CONFIG, "configured-token");

        provider.configure(configs);

        URL schemaRegistryUrl = URI.create("file:///schema-registry/subjects").toURL();
        assertThat(provider.alias()).isEqualTo("CUSTOM");
        assertThat(provider.getBearerToken(schemaRegistryUrl)).isEqualTo("configured-token");
        assertThat(provider.getTargetSchemaRegistry()).isEqualTo("logical-cluster-1");
        assertThat(provider.getTargetIdentityPoolId()).isEqualTo("identity-pool-1");

        provider.close();
    }
}
