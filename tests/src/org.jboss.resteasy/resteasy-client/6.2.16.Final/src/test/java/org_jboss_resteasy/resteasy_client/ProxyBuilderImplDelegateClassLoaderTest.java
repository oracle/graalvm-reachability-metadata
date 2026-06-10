/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_resteasy.resteasy_client;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

import org.jboss.resteasy.client.jaxrs.internal.proxy.ProxyBuilderImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProxyBuilderImplDelegateClassLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void loadClassUsesParentWhenParentCanResolveIt() throws ClassNotFoundException {
        ClassLoader delegate = new EmptyClassLoader();
        ClassLoader parent = TestResourceApi.class.getClassLoader();
        ProxyBuilderImpl<TestResourceApi>.DelegateClassLoader loader = newDelegateClassLoader(delegate, parent);

        Class<?> resolvedClass = loader.loadClass(TestResourceApi.class.getName());

        assertThat(resolvedClass).isSameAs(TestResourceApi.class);
    }

    @Test
    void loadClassFallsBackToDelegateWhenParentCannotResolveIt() throws ClassNotFoundException {
        ClassLoader delegate = TestResourceApi.class.getClassLoader();
        ClassLoader parent = new EmptyClassLoader();
        ProxyBuilderImpl<TestResourceApi>.DelegateClassLoader loader = newDelegateClassLoader(delegate, parent);

        Class<?> resolvedClass = loader.loadClass(TestResourceApi.class.getName());

        assertThat(resolvedClass).isSameAs(TestResourceApi.class);
    }

    @Test
    void getResourceUsesParentFirstAndThenDelegate() throws IOException {
        URL parentUrl = createResourceUrl("parent-resource.txt", "parent");
        URL delegateUrl = createResourceUrl("delegate-resource.txt", "delegate");
        ClassLoader parent = new ResourceClassLoader(
                Map.of("parent-resource", parentUrl),
                Map.of(),
                Map.of());
        ClassLoader delegate = new ResourceClassLoader(
                Map.of("delegate-resource", delegateUrl),
                Map.of(),
                Map.of());
        ProxyBuilderImpl<TestResourceApi>.DelegateClassLoader loader = newDelegateClassLoader(delegate, parent);

        URL resourceFromParent = loader.getResource("parent-resource");
        URL resourceFromDelegate = loader.getResource("delegate-resource");

        assertThat(resourceFromParent).isEqualTo(parentUrl);
        assertThat(resourceFromDelegate).isEqualTo(delegateUrl);
    }

    @Test
    void getResourcesCombinesParentAndDelegateResources() throws IOException {
        URL parentUrl = createResourceUrl("parent-enumeration.txt", "parent");
        URL delegateUrl = createResourceUrl("delegate-enumeration.txt", "delegate");
        ClassLoader parent = new ResourceClassLoader(
                Map.of(),
                Map.of(),
                Map.of("shared-resource", List.of(parentUrl)));
        ClassLoader delegate = new ResourceClassLoader(
                Map.of(),
                Map.of(),
                Map.of("shared-resource", List.of(delegateUrl)));
        ProxyBuilderImpl<TestResourceApi>.DelegateClassLoader loader = newDelegateClassLoader(delegate, parent);

        List<URL> resources = Collections.list(loader.getResources("shared-resource"));

        assertThat(resources).containsExactly(parentUrl, delegateUrl);
    }

    @Test
    void getResourceAsStreamUsesParentFirstAndThenDelegate() throws IOException {
        ClassLoader parent = new ResourceClassLoader(
                Map.of(),
                Map.of("parent-stream", "parent stream".getBytes(StandardCharsets.UTF_8)),
                Map.of());
        ClassLoader delegate = new ResourceClassLoader(
                Map.of(),
                Map.of("delegate-stream", "delegate stream".getBytes(StandardCharsets.UTF_8)),
                Map.of());
        ProxyBuilderImpl<TestResourceApi>.DelegateClassLoader loader = newDelegateClassLoader(delegate, parent);

        try (InputStream parentStream = loader.getResourceAsStream("parent-stream");
                InputStream delegateStream = loader.getResourceAsStream("delegate-stream")) {
            assertThat(readText(parentStream)).isEqualTo("parent stream");
            assertThat(readText(delegateStream)).isEqualTo("delegate stream");
        }
    }

    private ProxyBuilderImpl<TestResourceApi>.DelegateClassLoader newDelegateClassLoader(
            final ClassLoader delegate,
            final ClassLoader parent) {
        ProxyBuilderImpl<TestResourceApi> proxyBuilder = new ProxyBuilderImpl<>(TestResourceApi.class, null);
        return proxyBuilder.new DelegateClassLoader(delegate, parent);
    }

    private URL createResourceUrl(final String fileName, final String content) throws IOException {
        Path resourceFile = tempDir.resolve(fileName);
        Files.writeString(resourceFile, content, StandardCharsets.UTF_8);
        return resourceFile.toUri().toURL();
    }

    private String readText(final InputStream inputStream) throws IOException {
        assertThat(inputStream).isNotNull();
        return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    }

    private interface TestResourceApi {
    }

    private static final class EmptyClassLoader extends ClassLoader {
        private EmptyClassLoader() {
            super(null);
        }
    }

    private static final class ResourceClassLoader extends ClassLoader {
        private final Map<String, URL> resourcesByName;
        private final Map<String, byte[]> streamsByName;
        private final Map<String, List<URL>> resourceCollectionsByName;

        private ResourceClassLoader(
                final Map<String, URL> resourcesByName,
                final Map<String, byte[]> streamsByName,
                final Map<String, List<URL>> resourceCollectionsByName) {
            super(null);
            this.resourcesByName = resourcesByName;
            this.streamsByName = streamsByName;
            this.resourceCollectionsByName = resourceCollectionsByName;
        }

        @Override
        public URL getResource(final String name) {
            return resourcesByName.get(name);
        }

        @Override
        public Enumeration<URL> getResources(final String name) {
            List<URL> resources = resourceCollectionsByName.getOrDefault(name, List.of());
            return Collections.enumeration(resources);
        }

        @Override
        public InputStream getResourceAsStream(final String name) {
            byte[] bytes = streamsByName.get(name);
            return (bytes == null) ? null : new ByteArrayInputStream(bytes);
        }
    }
}
