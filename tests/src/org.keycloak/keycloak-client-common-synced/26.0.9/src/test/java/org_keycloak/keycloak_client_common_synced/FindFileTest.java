/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_keycloak.keycloak_client_common_synced;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.keycloak.common.util.FindFile;

public class FindFileTest {
    @Test
    void opensPackagedClasspathResourceWithDefiningClassLoader() throws IOException {
        String serviceResource = "META-INF/services/"
                + "org.keycloak.protocol.oidc.client.authentication.ClientCredentialsProvider";

        try (InputStream inputStream = FindFile.findFile("classpath:" + serviceResource)) {
            assertThat(readText(inputStream)).contains("ClientIdAndSecretCredentialsProvider");
        }
    }

    @Test
    void fallsBackToContextClassLoaderForClasspathResource() throws IOException {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        String resourceName = "keycloak-context-only-resource.txt";
        String resourceContent = "from context classloader";

        try {
            ClassLoader contextClassLoader = new InMemoryResourceClassLoader(resourceName, resourceContent);
            Thread.currentThread().setContextClassLoader(contextClassLoader);

            try (InputStream inputStream = FindFile.findFile("classpath:" + resourceName)) {
                assertThat(readText(inputStream)).isEqualTo(resourceContent);
            }
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    private static String readText(InputStream inputStream) throws IOException {
        return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    }

    private static final class InMemoryResourceClassLoader extends ClassLoader {
        private final String resourceName;
        private final byte[] resourceContent;

        InMemoryResourceClassLoader(String resourceName, String resourceContent) {
            super(null);
            this.resourceName = resourceName;
            this.resourceContent = resourceContent.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        protected URL findResource(String name) {
            if (!resourceName.equals(name)) {
                return null;
            }

            try {
                return new URL(null, "memory:" + name, new InMemoryUrlStreamHandler(resourceContent));
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private static final class InMemoryUrlStreamHandler extends URLStreamHandler {
        private final byte[] resourceContent;

        InMemoryUrlStreamHandler(byte[] resourceContent) {
            this.resourceContent = resourceContent;
        }

        @Override
        protected URLConnection openConnection(URL url) {
            return new InMemoryUrlConnection(url, resourceContent);
        }
    }

    private static final class InMemoryUrlConnection extends URLConnection {
        private final byte[] resourceContent;

        InMemoryUrlConnection(URL url, byte[] resourceContent) {
            super(url);
            this.resourceContent = resourceContent;
        }

        @Override
        public void connect() {
            connected = true;
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(resourceContent);
        }
    }
}
