/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_istack.istack_commons_tools;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.istack.tools.ParallelWorldClassLoader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class ParallelWorldClassLoaderTest {
    private static final String PREFIX = "parallel-world/";
    private static final String CLASS_NAME = "parallel.world.LoadedFromParallelWorld";
    private static final String CLASS_RESOURCE = "parallel/world/LoadedFromParallelWorld.class";
    private static final byte[] CLASS_BYTES = Base64.getMimeDecoder().decode("""
            yv66vgAAAD0AEQoAAgADBwAEDAAFAAYBABBqYXZhL2xhbmcvT2JqZWN0AQAGPGluaXQ+AQADKClW
            CAAIAQAabG9hZGVkLWZyb20tcGFyYWxsZWwtd29ybGQHAAoBACZwYXJhbGxlbC93b3JsZC9Mb2Fk
            ZWRGcm9tUGFyYWxsZWxXb3JsZAEABENvZGUBAA9MaW5lTnVtYmVyVGFibGUBAAZtYXJrZXIBABQo
            KUxqYXZhL2xhbmcvU3RyaW5nOwEAClNvdXJjZUZpbGUBABxMb2FkZWRGcm9tUGFyYWxsZWxXb3Js
            ZC5qYXZhACEACQACAAAAAAACAAEABQAGAAEACwAAAB0AAQABAAAABSq3AAGxAAAAAQAMAAAABgAB
            AAAAAwABAA0ADgABAAsAAAAbAAEAAQAAAAMSB7AAAAABAAwAAAAGAAEAAAAFAAEADwAAAAIAEA==
            """);

    @Test
    public void loadClassReadsClassBytesFromPrefixedParentResource() throws Exception {
        ParallelWorldParentClassLoader parent = new ParallelWorldParentClassLoader()
                .addResource(PREFIX + CLASS_RESOURCE, CLASS_BYTES);

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
        ParallelWorldParentClassLoader parent = new ParallelWorldParentClassLoader()
                .addResource(PREFIX + resourceName, content);

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
        ParallelWorldParentClassLoader parent = new ParallelWorldParentClassLoader()
                .addResource(PREFIX + resourceName, "first".getBytes(StandardCharsets.UTF_8))
                .addResource(PREFIX + resourceName, "second".getBytes(StandardCharsets.UTF_8));

        try (ParallelWorldClassLoader loader = new ParallelWorldClassLoader(parent, PREFIX)) {
            Enumeration<URL> resources = loader.getResources(resourceName);
            Collections.list(resources);
        }

        assertThat(parent.requestedResourceNames()).contains(PREFIX + resourceName);
    }

    private static final class ParallelWorldParentClassLoader extends ClassLoader {
        private final Map<String, List<URL>> resources = new HashMap<>();
        private final List<String> requestedResourceNames = new ArrayList<>();

        private ParallelWorldParentClassLoader() {
            super(null);
        }

        private ParallelWorldParentClassLoader addResource(String name, byte[] content) {
            resources.computeIfAbsent(name, ignored -> new ArrayList<>())
                    .add(inMemoryUrl(name, content));
            return this;
        }

        @Override
        public URL getResource(String name) {
            requestedResourceNames.add(name);
            List<URL> urls = resources.get(name);
            if (urls == null || urls.isEmpty()) {
                return null;
            }
            return urls.get(0);
        }

        @Override
        public Enumeration<URL> getResources(String name) {
            requestedResourceNames.add(name);
            return Collections.enumeration(resources.getOrDefault(name, List.of()));
        }

        private List<String> requestedResourceNames() {
            return requestedResourceNames;
        }
    }

    private static URL inMemoryUrl(String name, byte[] content) {
        try {
            return new URL(null, "memory:/" + name, new URLStreamHandler() {
                @Override
                protected URLConnection openConnection(URL url) {
                    return new URLConnection(url) {
                        @Override
                        public void connect() throws IOException {
                            connected = true;
                        }

                        @Override
                        public InputStream getInputStream() {
                            return new ByteArrayInputStream(content);
                        }
                    };
                }
            });
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to create in-memory URL", exception);
        }
    }
}
