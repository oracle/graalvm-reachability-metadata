/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_vertx.vertx_core;

import io.vertx.core.file.impl.FileResolverImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FileResolverImplTest {

    private static final String NESTED_JAR = "nested.jar";
    private static final String NESTED_RESOURCE = "nested-resources/message.txt";

    @Test
    void resolveFileCopiesClasspathResourceToCache() throws Exception {
        FileResolverImpl resolver = new FileResolverImpl();
        try {
            File resolved = resolver.resolve("file-resolver-valid-classpath-resource.txt");

            assertTrue(resolved.isFile());
            assertEquals(
                    "resolved from the test classpath",
                    Files.readString(resolved.toPath(), StandardCharsets.UTF_8));
        } finally {
            resolver.close();
        }
    }

    @Test
    void resolveFileUnpacksResourceFromNestedJar(@TempDir Path tempDir) throws Exception {
        Path outerJar = tempDir.resolve("outer.jar");
        byte[] nestedJar = createNestedJar(NESTED_RESOURCE, "resolved from a nested jar");
        writeOuterJar(outerJar, nestedJar);

        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        ClassLoader nestedJarClassLoader = new NestedJarResourceClassLoader(originalClassLoader, outerJar);
        FileResolverImpl resolver = new FileResolverImpl();
        Thread.currentThread().setContextClassLoader(nestedJarClassLoader);
        try {
            File resolved = resolver.resolve(NESTED_RESOURCE);

            assertTrue(resolved.isFile());
            assertEquals("resolved from a nested jar", Files.readString(resolved.toPath(), StandardCharsets.UTF_8));
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
            resolver.close();
        }
    }

    private static byte[] createNestedJar(String resourceName, String content) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (JarOutputStream jar = new JarOutputStream(bytes)) {
            jar.putNextEntry(new JarEntry(resourceName));
            jar.write(content.getBytes(StandardCharsets.UTF_8));
            jar.closeEntry();
        }
        return bytes.toByteArray();
    }

    private static void writeOuterJar(Path outerJar, byte[] nestedJar) throws IOException {
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(outerJar))) {
            jar.putNextEntry(new JarEntry(NESTED_JAR));
            jar.write(nestedJar);
            jar.closeEntry();
        }
    }

    private static final class NestedJarResourceClassLoader extends ClassLoader {

        private final URL nestedJarUrl;
        private final URL nestedResourceUrl;

        private NestedJarResourceClassLoader(ClassLoader parent, Path outerJar) throws MalformedURLException {
            super(parent);
            String outerJarUrl = outerJar.toUri().toURL().toExternalForm();
            nestedJarUrl = new URL("jar:" + outerJarUrl + "!/" + NESTED_JAR);
            nestedResourceUrl = new URL("jar:" + outerJarUrl + "!/" + NESTED_JAR + "!/" + NESTED_RESOURCE);
        }

        @Override
        public URL getResource(String name) {
            if (NESTED_RESOURCE.equals(name)) {
                return nestedResourceUrl;
            }
            if (NESTED_JAR.equals(name)) {
                return nestedJarUrl;
            }
            return super.getResource(name);
        }
    }
}
