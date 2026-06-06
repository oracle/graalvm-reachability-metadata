/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_helidon_common_features.helidon_common_features_metadata;

import java.util.List;
import java.util.Properties;

import io.helidon.common.features.metadata.FeatureMetadata;
import io.helidon.common.features.metadata.FeatureRegistry;
import io.helidon.common.features.metadata.FeatureStatus;
import io.helidon.common.features.metadata.Flavor;
import io.helidon.metadata.hson.Hson;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Helidon_common_features_metadataTest {
    @Test
    void parsesFeatureRegistryV1PropertiesMetadata() {
        Properties properties = new Properties();
        properties.setProperty("m", "example.legacy.module");
        properties.setProperty("n", "Legacy Feature");
        properties.setProperty("d", "Feature loaded from a properties document");
        properties.setProperty("s", "initial");
        properties.setProperty("p", "root,legacy");
        properties.setProperty("in", Flavor.SE.name());
        properties.setProperty("not", Flavor.MP.name());
        properties.setProperty("aot", "true");
        properties.setProperty("aotd", "Uses only runtime metadata");
        properties.setProperty("dep", "false");
        properties.setProperty("deps", "not deprecated");

        FeatureMetadata metadata = FeatureRegistry.metadata("test-registry.properties", properties);

        assertThat(metadata.module()).isEqualTo("example.legacy.module");
        assertThat(metadata.name()).isEqualTo("Legacy Feature");
        assertThat(metadata.path()).containsExactly("root", "legacy");
        assertThat(metadata.description()).contains("Feature loaded from a properties document");
        assertThat(metadata.since()).contains("initial");
        assertThat(metadata.flavors()).containsExactly(Flavor.SE);
        assertThat(metadata.invalidFlavors()).containsExactly(Flavor.MP);
        assertThat(metadata.status()).isEqualTo(FeatureStatus.PRODUCTION);
        assertThat(metadata.aot()).hasValueSatisfying(aotMetadata -> {
            assertThat(aotMetadata.supported()).isTrue();
            assertThat(aotMetadata.description()).contains("Uses only runtime metadata");
        });
        assertThat(metadata.deprecation()).hasValueSatisfying(deprecationMetadata -> {
            assertThat(deprecationMetadata.isDeprecated()).isFalse();
            assertThat(deprecationMetadata.since()).contains("not deprecated");
        });
    }

    @Test
    void parsesFeatureRegistryV2Metadata() {
        Hson.Struct aot = Hson.Struct.builder()
                .set("supported", false)
                .set("description", "Requires build-time generated metadata")
                .build();
        Hson.Struct deprecation = Hson.Struct.builder()
                .set("deprecated", true)
                .set("description", "Use the replacement feature")
                .set("since", "next")
                .build();
        Hson.Struct registryEntry = Hson.Struct.builder()
                .set("version", 2)
                .set("module", "example.module")
                .set("name", "Example Feature")
                .setStrings("path", List.of("root", "feature"))
                .set("description", "Feature loaded from a registry document")
                .set("since", "initial")
                .setStrings("flavor", List.of(Flavor.SE.name()))
                .setStrings("invalid-flavor", List.of(Flavor.MP.name()))
                .set("status", FeatureStatus.DEPRECATED.name())
                .set("aot", aot)
                .set("deprecation", deprecation)
                .build();

        Hson.Array registry = Hson.Array.create(List.of(registryEntry));

        List<FeatureMetadata> metadata = FeatureRegistry.metadata("test-registry.hson", registry);

        assertThat(metadata).singleElement().satisfies(feature -> {
            assertThat(feature.module()).isEqualTo("example.module");
            assertThat(feature.name()).isEqualTo("Example Feature");
            assertThat(feature.path()).containsExactly("root", "feature");
            assertThat(feature.description()).contains("Feature loaded from a registry document");
            assertThat(feature.since()).contains("initial");
            assertThat(feature.flavors()).containsExactly(Flavor.SE);
            assertThat(feature.invalidFlavors()).containsExactly(Flavor.MP);
            assertThat(feature.status()).isEqualTo(FeatureStatus.DEPRECATED);
            assertThat(feature.aot()).hasValueSatisfying(aotMetadata -> {
                assertThat(aotMetadata.supported()).isFalse();
                assertThat(aotMetadata.description()).contains("Requires build-time generated metadata");
            });
            assertThat(feature.deprecation()).hasValueSatisfying(deprecationMetadata -> {
                assertThat(deprecationMetadata.isDeprecated()).isTrue();
                assertThat(deprecationMetadata.description()).contains("Use the replacement feature");
                assertThat(deprecationMetadata.since()).contains("next");
            });
        });
    }
}
