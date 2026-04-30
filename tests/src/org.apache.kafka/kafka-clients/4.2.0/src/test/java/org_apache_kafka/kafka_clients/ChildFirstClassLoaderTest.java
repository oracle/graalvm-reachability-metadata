/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import org.apache.kafka.common.utils.ChildFirstClassLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class ChildFirstClassLoaderTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void delegatesClassLoadingToParentWhenClassIsNotInChildPath() throws Exception {
        Path childDirectory = Files.createDirectory(temporaryDirectory.resolve("classes"));

        try (ChildFirstClassLoader classLoader = new ChildFirstClassLoader(
                childDirectory.toString(),
                ChildFirstClassLoaderTest.class.getClassLoader())) {
            Class<?> loadedClass = classLoader.loadClass(String.class.getName());

            assertSame(String.class, loadedClass);
        }
    }

    @Test
    void findsResourceFromParentWhenResourceIsNotInChildPath() throws Exception {
        String resourceName = "parent-only-resource.txt";
        Path childDirectory = Files.createDirectory(temporaryDirectory.resolve("empty-child-resources"));
        Path parentDirectory = Files.createDirectory(temporaryDirectory.resolve("parent-resources"));
        Files.writeString(parentDirectory.resolve(resourceName), "from-parent", StandardCharsets.UTF_8);

        try (URLClassLoader parentLoader = urlClassLoader(parentDirectory);
                ChildFirstClassLoader classLoader = new ChildFirstClassLoader(childDirectory.toString(), parentLoader)) {
            URL resource = classLoader.getResource(resourceName);

            assertNotNull(resource);
            assertEquals("from-parent", readResource(resource));
        }
    }

    @Test
    void returnsChildResourcesBeforeParentResources() throws Exception {
        String resourceName = "shared-resource.txt";
        Path childDirectory = Files.createDirectory(temporaryDirectory.resolve("child-resources"));
        Path parentDirectory = Files.createDirectory(temporaryDirectory.resolve("parent-shared-resources"));
        Files.writeString(childDirectory.resolve(resourceName), "from-child", StandardCharsets.UTF_8);
        Files.writeString(parentDirectory.resolve(resourceName), "from-parent", StandardCharsets.UTF_8);

        try (URLClassLoader parentLoader = urlClassLoader(parentDirectory);
                ChildFirstClassLoader classLoader = new ChildFirstClassLoader(childDirectory.toString(), parentLoader)) {
            Enumeration<URL> resources = classLoader.getResources(resourceName);
            List<String> contents = new ArrayList<>();
            while (resources.hasMoreElements()) {
                contents.add(readResource(resources.nextElement()));
            }

            assertEquals(List.of("from-child", "from-parent"), contents);
            assertFalse(resources.hasMoreElements());
        }
    }

    private static URLClassLoader urlClassLoader(Path directory) throws IOException {
        return new URLClassLoader(new URL[] { directory.toUri().toURL() }, null);
    }

    private static String readResource(URL resource) throws IOException {
        try (InputStream inputStream = resource.openStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
