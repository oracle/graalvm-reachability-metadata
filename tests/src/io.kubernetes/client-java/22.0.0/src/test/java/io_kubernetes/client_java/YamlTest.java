/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_kubernetes.client_java;

import io.kubernetes.client.openapi.models.V1JSONSchemaProps;
import io.kubernetes.client.util.Yaml;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class YamlTest {
    @Test
    void roundTripsKubernetesSchemaExtensionsUsingTheirYamlNames() {
        String document = """
                type: object
                description: Embedded Kubernetes resource
                x-kubernetes-embedded-resource: true
                x-kubernetes-preserve-unknown-fields: true
                x-kubernetes-map-type: atomic
                """;

        V1JSONSchemaProps schema = Yaml.loadAs(document, V1JSONSchemaProps.class);

        assertThat(schema.getType()).isEqualTo("object");
        assertThat(schema.getDescription()).isEqualTo("Embedded Kubernetes resource");
        assertThat(schema.getxKubernetesEmbeddedResource()).isTrue();
        assertThat(schema.getxKubernetesPreserveUnknownFields()).isTrue();
        assertThat(schema.getxKubernetesMapType()).isEqualTo("atomic");

        String dumped = Yaml.dump(schema);

        assertThat(dumped).contains("x-kubernetes-embedded-resource: true",
                "x-kubernetes-preserve-unknown-fields: true", "x-kubernetes-map-type: atomic");
        assertThat(dumped).doesNotContain("xKubernetesEmbeddedResource", "xKubernetesPreserveUnknownFields",
                "xKubernetesMapType");
        assertThat(Yaml.loadAs(dumped, V1JSONSchemaProps.class)).isEqualTo(schema);
    }
}
