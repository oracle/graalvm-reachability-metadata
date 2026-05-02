/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.support.PropertiesLoaderUtils;

public class PropertiesLoaderUtilsTest {

    private static final String RESOURCE_NAME = "properties-loader-utils-test.properties";

    @TempDir
    Path tempDir;

    @Test
    void loadsAllMatchingPropertiesFromExplicitClassLoader() throws IOException {
        URL firstResource = writeProperties("first.properties", "first.value=alpha\nshared=first\n");
        URL secondResource = writeProperties("second.properties", "second.value=bravo\nshared=second\n");
        ClassLoader classLoader = new FixedResourcesClassLoader(RESOURCE_NAME, List.of(firstResource, secondResource));

        Properties properties = PropertiesLoaderUtils.loadAllProperties(RESOURCE_NAME, classLoader);

        assertThat(properties)
                .containsEntry("first.value", "alpha")
                .containsEntry("second.value", "bravo")
                .containsEntry("shared", "second");
    }

    @Test
    void loadsAllPropertiesFromThreadContextClassLoaderWhenNoClassLoaderIsProvided() throws IOException {
        URL resource = writeProperties("context.properties", "context.value=charlie\n");
        ClassLoader classLoader = new FixedResourcesClassLoader(RESOURCE_NAME, List.of(resource));

        Properties properties = withContextClassLoader(
                classLoader,
                () -> PropertiesLoaderUtils.loadAllProperties(RESOURCE_NAME)
        );

        assertThat(properties).containsEntry("context.value", "charlie");
    }

    @Test
    void returnsEmptyPropertiesWhenNoDefaultClassLoaderFindsTheResource() throws IOException {
        Properties properties = withContextClassLoader(
                null,
                () -> PropertiesLoaderUtils.loadAllProperties("missing-properties-loader-utils-test.properties")
        );

        assertThat(properties).isEmpty();
    }

    private URL writeProperties(String fileName, String content) throws IOException {
        Path propertiesFile = tempDir.resolve(fileName);
        Files.writeString(propertiesFile, content, StandardCharsets.ISO_8859_1);
        return propertiesFile.toUri().toURL();
    }

    private static <T> T withContextClassLoader(ClassLoader classLoader, ThrowingSupplier<T> supplier)
            throws IOException {
        Thread thread = Thread.currentThread();
        ClassLoader previousClassLoader = thread.getContextClassLoader();
        thread.setContextClassLoader(classLoader);
        try {
            return supplier.get();
        }
        finally {
            thread.setContextClassLoader(previousClassLoader);
        }
    }

    private interface ThrowingSupplier<T> {

        T get() throws IOException;
    }

    private static final class FixedResourcesClassLoader extends ClassLoader {

        private final String resourceName;

        private final List<URL> resources;

        private FixedResourcesClassLoader(String resourceName, List<URL> resources) {
            super(null);
            this.resourceName = resourceName;
            this.resources = resources;
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            if (!this.resourceName.equals(name)) {
                return Collections.emptyEnumeration();
            }
            return Collections.enumeration(this.resources);
        }
    }
}
