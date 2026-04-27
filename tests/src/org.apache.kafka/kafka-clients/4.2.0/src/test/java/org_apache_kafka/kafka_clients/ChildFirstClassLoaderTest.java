/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

import org.apache.kafka.common.utils.ChildFirstClassLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class ChildFirstClassLoaderTest {

    private static final String RESOURCE_NAME = "org_apache_kafka/kafka_clients/child-first-classloader-resource.txt";

    @TempDir
    Path tempDir;

    @Test
    void loadClassDelegatesToParentWhenChildClasspathCannotResolveIt() throws ClassNotFoundException, IOException {
        ClassLoader parent = new ResourceParentClassLoader(ChildFirstClassLoaderTest.class.getClassLoader(), Map.of(), Map.of());

        try (ChildFirstClassLoader loader = new ChildFirstClassLoader("", parent)) {
            Class<?> loadedClass = loader.loadClass(ChildFirstClassLoader.class.getName());

            assertThat(loadedClass).isSameAs(ChildFirstClassLoader.class);
        }
    }

    @Test
    void getResourceFallsBackToParentWhenChildHasNoMatchingResource() throws IOException {
        URL parentResource = createResource(tempDir.resolve("parent-resource-root"), RESOURCE_NAME, "parent");
        ClassLoader parent = new ResourceParentClassLoader(
            ChildFirstClassLoaderTest.class.getClassLoader(),
            Map.of(RESOURCE_NAME, parentResource),
            Map.of()
        );

        try (ChildFirstClassLoader loader = new ChildFirstClassLoader("", parent)) {
            URL resolvedResource = loader.getResource(RESOURCE_NAME);

            assertThat(resolvedResource).isEqualTo(parentResource);
        }
    }

    @Test
    void getResourcesReturnsChildResourcesBeforeParentResources() throws IOException {
        Path childResourceRoot = Files.createDirectories(tempDir.resolve("child-resource-root"));
        URL childResource = createResource(childResourceRoot, RESOURCE_NAME, "child");
        URL parentResource = createResource(tempDir.resolve("parent-enumeration-root"), RESOURCE_NAME, "parent");
        ClassLoader parent = new ResourceParentClassLoader(
            ChildFirstClassLoaderTest.class.getClassLoader(),
            Map.of(),
            Map.of(RESOURCE_NAME, List.of(parentResource))
        );

        try (ChildFirstClassLoader loader = new ChildFirstClassLoader(childResourceRoot.toString(), parent)) {
            List<URL> resources = Collections.list(loader.getResources(RESOURCE_NAME));

            assertThat(resources).containsExactly(childResource, parentResource);
        }
    }

    private URL createResource(Path root, String resourceName, String content) throws IOException {
        Path resourcePath = root.resolve(resourceName);
        Files.createDirectories(resourcePath.getParent());
        Files.writeString(resourcePath, content, StandardCharsets.UTF_8);
        return resourcePath.toUri().toURL();
    }

    private static final class ResourceParentClassLoader extends ClassLoader {
        private final Map<String, URL> resourcesByName;
        private final Map<String, List<URL>> resourceCollectionsByName;

        private ResourceParentClassLoader(
                ClassLoader parent,
                Map<String, URL> resourcesByName,
                Map<String, List<URL>> resourceCollectionsByName) {
            super(parent);
            this.resourcesByName = resourcesByName;
            this.resourceCollectionsByName = resourceCollectionsByName;
        }

        @Override
        public URL getResource(String name) {
            URL resource = resourcesByName.get(name);
            return resource != null ? resource : super.getResource(name);
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            List<URL> resources = resourceCollectionsByName.get(name);
            return resources != null ? Collections.enumeration(resources) : super.getResources(name);
        }
    }
}
