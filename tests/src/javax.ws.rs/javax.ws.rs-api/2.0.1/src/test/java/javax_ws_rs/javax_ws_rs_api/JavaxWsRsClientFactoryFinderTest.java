/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_ws_rs.javax_ws_rs_api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.Map;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.Configuration;

import org.junit.jupiter.api.Test;

public class JavaxWsRsClientFactoryFinderTest {
    private static final String CLIENT_BUILDER_SERVICE = "META-INF/services/" + ClientBuilder.class.getName();
    private static final String PROVIDED_CLIENT_BUILDER = ProvidedClientBuilder.class.getName();

    @Test
    void newBuilderLoadsProviderFromContextClassLoaderServiceResource() {
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(JavaxWsRsClientFactoryFinderTest.class.getClassLoader());
        try {
            ClientBuilder builder = ClientBuilder.newBuilder();

            assertThat(builder).isInstanceOf(ProvidedClientBuilder.class);
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }
    }

    @Test
    void newBuilderLoadsProviderFromSystemServiceResourceWhenContextClassLoaderIsNull() {
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(null);
        try {
            ClientBuilder builder = ClientBuilder.newBuilder();

            assertThat(builder).isInstanceOf(ProvidedClientBuilder.class);
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }
    }

    @Test
    void newBuilderFallsBackToCurrentClassLoaderWhenContextClassLoaderCannotLoadProviderClass() {
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(new ServiceOnlyClassLoader(PROVIDED_CLIENT_BUILDER));
        try {
            ClientBuilder builder = ClientBuilder.newBuilder();

            assertThat(builder).isInstanceOf(ProvidedClientBuilder.class);
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }
    }

    public static class ProvidedClientBuilder extends ClientBuilder {
        @Override
        public ClientBuilder withConfig(Configuration config) {
            return this;
        }

        @Override
        public ClientBuilder sslContext(SSLContext sslContext) {
            return this;
        }

        @Override
        public ClientBuilder keyStore(KeyStore keyStore, char[] password) {
            return this;
        }

        @Override
        public ClientBuilder trustStore(KeyStore trustStore) {
            return this;
        }

        @Override
        public ClientBuilder hostnameVerifier(HostnameVerifier verifier) {
            return this;
        }

        @Override
        public Client build() {
            return null;
        }

        @Override
        public Configuration getConfiguration() {
            return null;
        }

        @Override
        public ClientBuilder property(String name, Object value) {
            return this;
        }

        @Override
        public ClientBuilder register(Class<?> componentClass) {
            return this;
        }

        @Override
        public ClientBuilder register(Class<?> componentClass, int priority) {
            return this;
        }

        @Override
        public ClientBuilder register(Class<?> componentClass, Class<?>... contracts) {
            return this;
        }

        @Override
        public ClientBuilder register(Class<?> componentClass, Map<Class<?>, Integer> contracts) {
            return this;
        }

        @Override
        public ClientBuilder register(Object component) {
            return this;
        }

        @Override
        public ClientBuilder register(Object component, int priority) {
            return this;
        }

        @Override
        public ClientBuilder register(Object component, Class<?>... contracts) {
            return this;
        }

        @Override
        public ClientBuilder register(Object component, Map<Class<?>, Integer> contracts) {
            return this;
        }
    }

    private static final class ServiceOnlyClassLoader extends ClassLoader {
        private final String providerClassName;

        private ServiceOnlyClassLoader(String providerClassName) {
            super(null);
            this.providerClassName = providerClassName;
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            if (!CLIENT_BUILDER_SERVICE.equals(name)) {
                return null;
            }
            return new ByteArrayInputStream((providerClassName + "\n").getBytes(StandardCharsets.UTF_8));
        }
    }
}
