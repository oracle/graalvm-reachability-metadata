/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_helidon_metadata.helidon_metadata;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.helidon.metadata.MetadataConstants;
import io.helidon.metadata.MetadataDiscovery;
import io.helidon.metadata.MetadataDiscovery.Mode;
import io.helidon.metadata.MetadataFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

public class MetadataDiscoveryImplTest {
    private static final String HELIDON_MANIFEST = MetadataConstants.LOCATION
            + "/" + MetadataConstants.MANIFEST_FILE;
    private static final String GENERATED_SERVICE_REGISTRY = MetadataConstants.LOCATION
            + "/generated/" + MetadataConstants.SERVICE_REGISTRY_FILE;

    @Test
    void autoModeDiscoversMetadataListedInHelidonManifest(@TempDir Path tempDir)
            throws IOException {
        URL firstJarManifest = writeResource(tempDir,
                                             "first/META-INF/MANIFEST.MF",
                                             "Manifest-Version: 1.0\n");
        URL secondJarManifest = writeResource(tempDir,
                                              "second/META-INF/MANIFEST.MF",
                                              "Manifest-Version: 1.0\n");
        URL helidonManifest = writeResource(tempDir, HELIDON_MANIFEST, """
                # metadata entries produced by a Helidon application build
                META-INF/helidon/generated/service-registry.json
                """);
        URL serviceRegistry = writeResource(tempDir, GENERATED_SERVICE_REGISTRY, """
                {"services":[]}
                """);

        ResourceClassLoader classLoader = new ResourceClassLoader(
                getClass().getClassLoader(),
                Map.of(GENERATED_SERVICE_REGISTRY, serviceRegistry),
                Map.of("META-INF/MANIFEST.MF",
                       List.of(firstJarManifest, secondJarManifest),
                       HELIDON_MANIFEST,
                       List.of(helidonManifest)));

        MetadataDiscovery discovery = createWithContextClassLoader(classLoader, Mode.AUTO);

        assertThat(discovery.list(MetadataConstants.SERVICE_REGISTRY_FILE))
                .singleElement()
                .satisfies(file -> assertMetadataFile(file, serviceRegistry));
    }

    private static URL writeResource(Path tempDir,
                                     String resourceName,
                                     String content) throws IOException {
        Path path = tempDir.resolve(resourceName);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, UTF_8);
        return path.toUri().toURL();
    }

    private static MetadataDiscovery createWithContextClassLoader(ClassLoader classLoader,
                                                                  Mode mode) {
        Thread thread = Thread.currentThread();
        ClassLoader previousClassLoader = thread.getContextClassLoader();
        thread.setContextClassLoader(classLoader);
        try {
            return MetadataDiscovery.create(mode);
        } finally {
            thread.setContextClassLoader(previousClassLoader);
        }
    }

    private static void assertMetadataFile(MetadataFile file, URL serviceRegistry) {
        assertThat(file.fileName()).isEqualTo(MetadataConstants.SERVICE_REGISTRY_FILE);
        assertThat(file.location()).isEqualTo(GENERATED_SERVICE_REGISTRY);
        assertThat(file.absoluteLocation()).isEqualTo(serviceRegistry.getPath());
    }

    private static class ResourceClassLoader extends ClassLoader {
        private final Map<String, URL> namedResources;
        private final Map<String, List<URL>> resources;

        ResourceClassLoader(ClassLoader parent,
                            Map<String, URL> namedResources,
                            Map<String, List<URL>> resources) {
            super(parent);
            this.namedResources = new HashMap<>(namedResources);
            this.resources = new HashMap<>(resources);
        }

        @Override
        public URL getResource(String name) {
            URL resource = namedResources.get(name);
            if (resource != null) {
                return resource;
            }
            return super.getResource(name);
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            List<URL> found = resources.get(name);
            if (found != null) {
                return Collections.enumeration(found);
            }
            return super.getResources(name);
        }
    }
}
