/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_grizzly.grizzly_http_servlet;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.glassfish.grizzly.servlet.ServletContextImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ServletContextImplTest {
    private static final String RESOURCE_NAME = "org_glassfish_grizzly/grizzly_http_servlet/context-resource.txt";
    private static final String RESOURCE_PATH = "/" + RESOURCE_NAME;
    private static final String RESOURCE_CONTENT = "resource loaded by the thread context class loader";

    @Test
    void getResourceDelegatesToThreadContextClassLoader() throws Exception {
        ResourceClassLoader classLoader = new ResourceClassLoader(
                Thread.currentThread().getContextClassLoader(), RESOURCE_NAME, RESOURCE_CONTENT);

        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(classLoader);
        try {
            ServletContextImpl servletContext = new ServletContextImpl();

            URL resource = servletContext.getResource(RESOURCE_PATH);

            assertThat(resource).isNotNull();
            try (InputStream input = resource.openStream()) {
                assertThat(new String(input.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo(RESOURCE_CONTENT);
            }
            assertThat(classLoader.resourceRequests()).containsExactly(RESOURCE_NAME);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    @Test
    void getResourceAsStreamDelegatesToThreadContextClassLoader() throws IOException {
        ResourceClassLoader classLoader = new ResourceClassLoader(
                Thread.currentThread().getContextClassLoader(), RESOURCE_NAME, RESOURCE_CONTENT);

        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(classLoader);
        try {
            ServletContextImpl servletContext = new ServletContextImpl();

            try (InputStream resourceStream = servletContext.getResourceAsStream(RESOURCE_PATH)) {
                assertThat(resourceStream).isNotNull();
                assertThat(new String(resourceStream.readAllBytes(), StandardCharsets.UTF_8))
                        .isEqualTo(RESOURCE_CONTENT);
            }
            assertThat(classLoader.resourceStreamRequests()).containsExactly(RESOURCE_NAME);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    private static final class ResourceClassLoader extends ClassLoader {
        private final String resourceName;
        private final byte[] resourceContent;
        private final List<String> resourceRequests = new ArrayList<>();
        private final List<String> resourceStreamRequests = new ArrayList<>();

        ResourceClassLoader(ClassLoader parent, String resourceName, String resourceContent) {
            super(parent);
            this.resourceName = resourceName;
            this.resourceContent = resourceContent.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public URL getResource(String name) {
            resourceRequests.add(name);
            if (!resourceName.equals(name)) {
                return super.getResource(name);
            }
            try {
                return inMemoryResourceUrl();
            } catch (MalformedURLException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            resourceStreamRequests.add(name);
            if (!resourceName.equals(name)) {
                return super.getResourceAsStream(name);
            }
            return new ByteArrayInputStream(resourceContent);
        }

        List<String> resourceRequests() {
            return resourceRequests;
        }

        List<String> resourceStreamRequests() {
            return resourceStreamRequests;
        }

        private URL inMemoryResourceUrl() throws MalformedURLException {
            return new URL(null, "memory://servlet-context-resource", new URLStreamHandler() {
                @Override
                protected URLConnection openConnection(URL url) {
                    return new URLConnection(url) {
                        @Override
                        public void connect() {
                        }

                        @Override
                        public InputStream getInputStream() {
                            return new ByteArrayInputStream(resourceContent);
                        }
                    };
                }
            });
        }
    }
}
