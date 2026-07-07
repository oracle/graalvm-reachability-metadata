/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_helidon_metadata.helidon_metadata;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import io.helidon.metadata.MetadataConstants;
import io.helidon.metadata.MetadataDiscovery;
import io.helidon.metadata.MetadataFile;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MetadataDiscoveryImplTest {
    @Test
    void discoversManifestAndDefaultMetadataResources() throws IOException {
        MetadataDiscovery discovery = MetadataDiscovery.create(MetadataDiscovery.Mode.AUTO);

        List<MetadataFile> serviceLoaderFiles = discovery.list(MetadataConstants.SERVICE_LOADER_FILE);
        List<MetadataFile> featureRegistryFiles = discovery.list(MetadataConstants.FEATURE_REGISTRY_FILE);
        List<MetadataFile> configMetadataFiles = discovery.list(MetadataConstants.CONFIG_METADATA_FILE);

        assertThat(serviceLoaderFiles)
                .extracting(MetadataFile::location)
                .contains("META-INF/helidon/generated/service.loader",
                          "META-INF/helidon/service.loader");
        assertThat(featureRegistryFiles)
                .singleElement()
                .extracting(MetadataFile::location)
                .isEqualTo("META-INF/helidon/generated/feature-registry.json");
        assertThat(configMetadataFiles)
                .singleElement()
                .extracting(MetadataFile::location)
                .isEqualTo("META-INF/helidon/config-metadata.json");

        assertThat(read(serviceLoaderFiles, "META-INF/helidon/generated/service.loader"))
                .contains("example.GeneratedService");
        assertThat(read(configMetadataFiles.getFirst()))
                .contains("Synthetic test config metadata");
    }

    private static String read(List<MetadataFile> files, String location) throws IOException {
        MetadataFile file = files.stream()
                .filter(it -> it.location().equals(location))
                .findFirst()
                .orElseThrow();
        return read(file);
    }

    private static String read(MetadataFile file) throws IOException {
        try (InputStream input = file.inputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
