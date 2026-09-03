/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_istack.istack_commons_tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.sun.istack.tools.ParallelWorldClassLoader;

public class ParallelWorldClassLoaderTest {
    private static final String PREFIX = "parallel-world/";
    private static final String SIMPLE_RESOURCE_NAME = "resource.bin";

    @Test
    public void getResourceConsultsParentWithPrefixedName() throws IOException {
        final URL mappedResource = mappedResource();
        final ResourceOnlyParentClassLoader parent = new ResourceOnlyParentClassLoader(mappedResource);

        try (ExposedParallelWorldClassLoader loader = new ExposedParallelWorldClassLoader(parent, PREFIX)) {
            final URL resource = loader.findResourceForTest(SIMPLE_RESOURCE_NAME);

            assertThat(resource).isEqualTo(mappedResource);
            assertThat(parent.resourceRequests())
                    .containsExactly(PREFIX + SIMPLE_RESOURCE_NAME);
        }
    }

    @Test
    public void getResourcesConsultsParentWithPrefixedName() throws IOException {
        final URL mappedResource = mappedResource();
        final ResourceOnlyParentClassLoader parent = new ResourceOnlyParentClassLoader(mappedResource);

        try (ExposedParallelWorldClassLoader loader = new ExposedParallelWorldClassLoader(parent, PREFIX)) {
            final Enumeration<URL> resources = loader.findResourcesForTest(SIMPLE_RESOURCE_NAME);

            assertThat(resources).isNotNull();
            assertThat(parent.resourceEnumerationRequests())
                    .containsExactly(PREFIX + SIMPLE_RESOURCE_NAME);
        }
    }

    @Test
    public void loadClassReportsMissingMappedClass() throws IOException {
        final URL mappedResource = mappedResource();
        final ResourceOnlyParentClassLoader parent = new ResourceOnlyParentClassLoader(mappedResource);

        try (ExposedParallelWorldClassLoader loader = new ExposedParallelWorldClassLoader(parent, PREFIX)) {
            assertThatThrownBy(() -> loader.findClassForTest("example.MissingParallelWorldType"))
                    .isInstanceOf(ClassNotFoundException.class)
                    .hasMessage("example.MissingParallelWorldType");
            assertThat(parent.resourceRequests())
                    .contains(PREFIX + "example/MissingParallelWorldType.class");
        }
    }

    private static URL mappedResource() throws IOException {
        return new URL("file:/parallel-world/resource.bin");
    }

    private static final class ExposedParallelWorldClassLoader extends ParallelWorldClassLoader {
        ExposedParallelWorldClassLoader(ClassLoader parent, String prefix) {
            super(parent, prefix);
        }

        URL findResourceForTest(String name) {
            return findResource(name);
        }

        Enumeration<URL> findResourcesForTest(String name) throws IOException {
            return findResources(name);
        }

        Class<?> findClassForTest(String name) throws ClassNotFoundException {
            return findClass(name);
        }
    }

    private static final class ResourceOnlyParentClassLoader extends ClassLoader {
        private final URL mappedResource;
        private final List<String> resourceRequests = new ArrayList<>();
        private final List<String> resourceEnumerationRequests = new ArrayList<>();

        ResourceOnlyParentClassLoader(URL mappedResource) {
            super(ParallelWorldClassLoaderTest.class.getClassLoader());
            this.mappedResource = mappedResource;
        }

        @Override
        public URL getResource(String name) {
            resourceRequests.add(name);
            if ((PREFIX + SIMPLE_RESOURCE_NAME).equals(name)) {
                return mappedResource;
            }
            return null;
        }

        @Override
        public Enumeration<URL> getResources(String name) {
            resourceEnumerationRequests.add(name);
            if ((PREFIX + SIMPLE_RESOURCE_NAME).equals(name)) {
                return Collections.enumeration(List.of(mappedResource));
            }
            return Collections.emptyEnumeration();
        }

        List<String> resourceRequests() {
            return resourceRequests;
        }

        List<String> resourceEnumerationRequests() {
            return resourceEnumerationRequests;
        }
    }
}
