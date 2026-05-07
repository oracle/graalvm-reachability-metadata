/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_vertx.vertx_core;

import io.vertx.core.file.impl.FileResolverImpl;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FileResolverImplTest {

    private static final String NESTED_JAR_ENTRY = "nested-library.jar";
    private static final String NESTED_RESOURCE_ENTRY = "nested-resource.txt";
    private static final String NESTED_RESOURCE_CONTENT = "resolved from a nested jar URL";

    @Test
    void resolveFileCopiesClasspathResourceToCache() throws Exception {
        final FileResolverImpl resolver = new FileResolverImpl();
        try {
            final File resolved = resolver.resolve("file-resolver-valid-classpath-resource.txt");

            final String content = Files.readString(resolved.toPath(), StandardCharsets.UTF_8);

            assertTrue(resolved.isFile());
            assertEquals("resolved from the test classpath", content);
        } finally {
            resolver.close();
        }
    }

    @Test
    void resolveFileUnpacksResourceFromNestedJarUrl() throws Exception {
        final Path temporaryDirectory = Files.createTempDirectory("vertx-file-resolver-nested-jar");
        final Path outerJar = temporaryDirectory.resolve("outer.jar");
        final ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        final FileResolverImpl resolver = new FileResolverImpl();
        try {
            createNestedJar(outerJar);
            final ClassLoader nestedJarClassLoader = new NestedJarResourceClassLoader(originalClassLoader, outerJar);
            Thread.currentThread().setContextClassLoader(nestedJarClassLoader);

            final File resolved = resolver.resolve(NESTED_RESOURCE_ENTRY);
            final String content = Files.readString(resolved.toPath(), StandardCharsets.UTF_8);

            assertTrue(resolved.isFile());
            assertEquals(NESTED_RESOURCE_CONTENT, content);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
            resolver.close();
            deleteRecursively(temporaryDirectory);
        }
    }

    private static void createNestedJar(Path outerJar) throws IOException {
        final byte[] nestedJar = createInnerJar();
        try (JarOutputStream jarOutput = new JarOutputStream(Files.newOutputStream(outerJar))) {
            final JarEntry nestedJarEntry = new JarEntry(NESTED_JAR_ENTRY);
            jarOutput.putNextEntry(nestedJarEntry);
            jarOutput.write(nestedJar);
            jarOutput.closeEntry();
        }
    }

    private static byte[] createInnerJar() throws IOException {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (JarOutputStream jarOutput = new JarOutputStream(output)) {
            final JarEntry resourceEntry = new JarEntry(NESTED_RESOURCE_ENTRY);
            jarOutput.putNextEntry(resourceEntry);
            jarOutput.write(NESTED_RESOURCE_CONTENT.getBytes(StandardCharsets.UTF_8));
            jarOutput.closeEntry();
        }
        return output.toByteArray();
    }

    private static void deleteRecursively(Path directory) throws IOException {
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder())
                    .forEach(FileResolverImplTest::deletePath);
        }
    }

    private static void deletePath(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not delete temporary file " + path, exception);
        }
    }

    private static final class NestedJarResourceClassLoader extends ClassLoader {

        private final Path outerJar;

        NestedJarResourceClassLoader(ClassLoader parent, Path outerJar) {
            super(parent);
            this.outerJar = outerJar;
        }

        @Override
        public URL getResource(String name) {
            try {
                if (NESTED_RESOURCE_ENTRY.equals(name)) {
                    return nestedResourceUrl();
                }
                if (NESTED_JAR_ENTRY.equals(name)) {
                    return nestedJarUrl();
                }
                return super.getResource(name);
            } catch (IOException exception) {
                throw new IllegalStateException("Could not create nested jar resource URL", exception);
            }
        }

        private URL nestedResourceUrl() throws IOException {
            return new URL(nestedJarUrl().toExternalForm() + "!/" + NESTED_RESOURCE_ENTRY);
        }

        private URL nestedJarUrl() throws IOException {
            return new URL("jar:" + outerJar.toUri().toURL().toExternalForm() + "!/" + NESTED_JAR_ENTRY);
        }
    }
}
