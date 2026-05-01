/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_istack.istack_commons_tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import com.sun.istack.tools.ParallelWorldClassLoader;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class ParallelWorldClassLoaderTest {
    private static final String PREFIX = "parallel-world/";
    private static final String CLASS_NAME = "parallel.world.LoadedFromParallelWorld";
    private static final String CLASS_RESOURCE = "parallel/world/LoadedFromParallelWorld.class";

    @Test
    public void loadClassReadsClassBytesFromPrefixedParentResource() throws Exception {
        assumeFalse(isNativeImageRuntime(), "Runtime class definition is not supported in native-image tests");
        ParallelWorldParentClassLoader parent = new ParallelWorldParentClassLoader();

        try (ParallelWorldClassLoader loader = new ParallelWorldClassLoader(parent, PREFIX)) {
            try {
                Class<?> loadedClass = loader.loadClass(CLASS_NAME);

                assertThat(loadedClass.getName()).isEqualTo(CLASS_NAME);
                assertThat(loadedClass.getClassLoader()).isSameAs(loader);
            } catch (Error error) {
                if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                    throw error;
                }
            }
        }

        assertThat(parent.requestedResourceNames()).contains(PREFIX + CLASS_RESOURCE);
    }

    @Test
    public void getResourceFindsPrefixedResourceFromParent() throws Exception {
        String resourceName = "sample-resource.txt";
        byte[] content = "parallel resource".getBytes(StandardCharsets.UTF_8);
        ParallelWorldParentClassLoader parent = new ParallelWorldParentClassLoader();

        try (ParallelWorldClassLoader loader = new ParallelWorldClassLoader(parent, PREFIX)) {
            URL resource = loader.getResource(resourceName);

            assertThat(resource).isNotNull();
            try (InputStream stream = resource.openStream()) {
                assertThat(stream.readAllBytes()).isEqualTo(content);
            }
        }

        assertThat(parent.requestedResourceNames()).contains(PREFIX + resourceName);
    }

    @Test
    public void getResourcesQueriesPrefixedResourcesFromParent() throws Exception {
        String resourceName = "listed-resource.txt";
        ParallelWorldParentClassLoader parent = new ParallelWorldParentClassLoader();

        try (ParallelWorldClassLoader loader = new ParallelWorldClassLoader(parent, PREFIX)) {
            Enumeration<URL> resources = loader.getResources(resourceName);
            Collections.list(resources);
        }

        assertThat(parent.requestedResourceNames()).contains(PREFIX + resourceName);
    }

    private static final class ParallelWorldParentClassLoader extends ClassLoader {
        private final List<String> requestedResourceNames = new ArrayList<>();
        private final ClassLoader resourceLoader = ParallelWorldClassLoaderTest.class.getClassLoader();

        private ParallelWorldParentClassLoader() {
            super(null);
        }

        @Override
        public URL getResource(String name) {
            requestedResourceNames.add(name);
            return resourceLoader.getResource(name);
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            requestedResourceNames.add(name);
            URL resource = resourceLoader.getResource(name);
            if (resource == null) {
                return Collections.emptyEnumeration();
            }
            return Collections.enumeration(List.of(resource));
        }

        private List<String> requestedResourceNames() {
            return requestedResourceNames;
        }
    }

    private static boolean isNativeImageRuntime() {
        return "runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"));
    }
}
