/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_shrinkwrap.shrinkwrap_impl_base;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import org.jboss.shrinkwrap.api.Configuration;
import org.jboss.shrinkwrap.api.ConfigurationBuilder;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class ContainerBaseTest {
    private static final String FILE_RESOURCE = "container-base-file-resource.txt";
    private static final String NESTED_RESOURCE = "container-base-nested-resource.txt";

    @TempDir
    private Path temporaryDirectory;

    @Test
    void addClassByNameWithClassLoaderAddsClassEntry() {
        JavaArchive archive = newArchive(Thread.currentThread().getContextClassLoader());
        Class<?> classToAdd = ContainerBaseTest.class;

        archive.addClass(classToAdd.getName(), classToAdd.getClassLoader());

        assertThat(archive.contains("/" + classToAdd.getName().replace('.', '/') + ".class"))
            .isTrue();
    }

    @Test
    void addManifestResourceByNameAddsFileResourceFromThreadContextClassLoader() throws IOException {
        Path resource = temporaryDirectory.resolve(FILE_RESOURCE);
        Files.writeString(resource, "file resource", StandardCharsets.UTF_8);
        ClassLoader resourceClassLoader = new ContainerResourceClassLoader(
            Thread.currentThread().getContextClassLoader(), resource.toUri().toURL(), null);
        JavaArchive archive = newArchive(resourceClassLoader);

        withThreadContextClassLoader(resourceClassLoader,
            () -> archive.addAsManifestResource(FILE_RESOURCE, "copied.txt"));

        assertThat(archive.contains("/META-INF/copied.txt")).isTrue();
    }

    @Test
    void addManifestResourceByNameAddsResourceFromNestedJarUrl() throws IOException {
        Path jarFile = temporaryDirectory.resolve("container-base-resources.jar");
        Files.writeString(jarFile, "jar marker", StandardCharsets.UTF_8);
        String jarUrl = "jar:" + jarFile.toUri().toURL().toExternalForm() + "!/" + NESTED_RESOURCE;
        ClassLoader resourceClassLoader = new ContainerResourceClassLoader(
            Thread.currentThread().getContextClassLoader(), null, new URL(jarUrl));
        JavaArchive archive = newArchive(resourceClassLoader);

        withThreadContextClassLoader(resourceClassLoader,
            () -> archive.addAsManifestResource(NESTED_RESOURCE, "nested-copied.txt"));

        assertThat(archive.contains("/META-INF/nested-copied.txt")).isTrue();
    }

    private JavaArchive newArchive(ClassLoader classLoader) {
        Configuration configuration = new ConfigurationBuilder()
            .classLoaders(Collections.singletonList(classLoader))
            .build();
        return ShrinkWrap.createDomain(configuration).getArchiveFactory()
            .create(JavaArchive.class, "container-base-test.jar");
    }

    private void withThreadContextClassLoader(ClassLoader classLoader, ArchiveOperation operation) {
        Thread currentThread = Thread.currentThread();
        ClassLoader originalClassLoader = currentThread.getContextClassLoader();
        currentThread.setContextClassLoader(classLoader);
        try {
            operation.run();
        } finally {
            currentThread.setContextClassLoader(originalClassLoader);
        }
    }

    private interface ArchiveOperation {
        void run();
    }

    private static final class ContainerResourceClassLoader extends ClassLoader {
        private static final byte[] NESTED_RESOURCE_BYTES = "nested resource".getBytes(StandardCharsets.UTF_8);

        private final URL fileResourceUrl;
        private final URL nestedResourceUrl;

        private ContainerResourceClassLoader(ClassLoader parent, URL fileResourceUrl, URL nestedResourceUrl) {
            super(parent);
            this.fileResourceUrl = fileResourceUrl;
            this.nestedResourceUrl = nestedResourceUrl;
        }

        @Override
        public URL getResource(String name) {
            if (FILE_RESOURCE.equals(name)) {
                return fileResourceUrl;
            }
            if (NESTED_RESOURCE.equals(name)) {
                return nestedResourceUrl;
            }
            return super.getResource(name);
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            if (NESTED_RESOURCE.equals(name)) {
                return new ByteArrayInputStream(NESTED_RESOURCE_BYTES);
            }
            return super.getResourceAsStream(name);
        }
    }
}
