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
    private static final String TOOL_CLASS_RESOURCE = "com/sun/istack/tools/ParallelWorldClassLoader.class";

    @Test
    public void getResourceConsultsParentWithPrefixedName() throws IOException {
        final URL mappedResource = mappedResource();
        final ResourceOnlyParentClassLoader parent = new ResourceOnlyParentClassLoader(mappedResource);

        try (ParallelWorldClassLoader loader = new ParallelWorldClassLoader(parent, PREFIX)) {
            final URL resource = loader.getResource(SIMPLE_RESOURCE_NAME);

            assertThat(resource).isEqualTo(mappedResource);
            assertThat(parent.resourceRequests())
                    .contains(SIMPLE_RESOURCE_NAME, PREFIX + SIMPLE_RESOURCE_NAME);
        }
    }

    @Test
    public void getResourcesConsultsParentWithPrefixedName() throws IOException {
        final URL mappedResource = mappedResource();
        final ResourceOnlyParentClassLoader parent = new ResourceOnlyParentClassLoader(mappedResource);

        try (ParallelWorldClassLoader loader = new ParallelWorldClassLoader(parent, PREFIX)) {
            final Enumeration<URL> resources = loader.getResources(SIMPLE_RESOURCE_NAME);

            assertThat(resources).isNotNull();
            assertThat(parent.resourceEnumerationRequests())
                    .contains(SIMPLE_RESOURCE_NAME, PREFIX + SIMPLE_RESOURCE_NAME);
        }
    }

    @Test
    public void loadClassReportsMissingMappedClass() throws IOException {
        final URL mappedResource = mappedResource();
        final ResourceOnlyParentClassLoader parent = new ResourceOnlyParentClassLoader(mappedResource);

        try (ParallelWorldClassLoader loader = new ParallelWorldClassLoader(parent, PREFIX)) {
            assertThatThrownBy(() -> loader.loadClass("example.MissingParallelWorldType"))
                    .isInstanceOf(ClassNotFoundException.class)
                    .hasMessage("example.MissingParallelWorldType");
            assertThat(parent.resourceRequests())
                    .contains(PREFIX + "example/MissingParallelWorldType.class");
        }
    }

    private static URL mappedResource() {
        final URL resource = ParallelWorldClassLoaderTest.class.getClassLoader().getResource(TOOL_CLASS_RESOURCE);
        assertThat(resource).as("tool class resource").isNotNull();
        return resource;
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
