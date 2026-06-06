/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_websocket;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.websocket.server.ServerEndpointConfig;

import org.apache.tomcat.websocket.server.DefaultServerEndpointConfigurator;
import org.junit.jupiter.api.Test;

public class ServerEndpointConfigInnerConfiguratorTest {
    private static final String SERVER_ENDPOINT_CONFIGURATOR_SERVICE =
            "META-INF/services/" + ServerEndpointConfig.Configurator.class.getName();
    private static final AtomicInteger CONSTRUCTIONS = new AtomicInteger();

    @Test
    void builderFallsBackToTomcatDefaultConfiguratorWhenNoServiceIsVisible() throws InstantiationException {
        assertFallbackDefaultConfiguratorAvailable();
    }

    static void assertFallbackDefaultConfiguratorAvailable() throws InstantiationException {
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        CONSTRUCTIONS.set(0);
        try {
            Thread.currentThread()
                    .setContextClassLoader(new HidingServicesClassLoader(originalContextClassLoader));

            ServerEndpointConfig config = ServerEndpointConfig.Builder.create(CountingEndpoint.class, "/counting")
                    .build();
            ServerEndpointConfig.Configurator configurator = config.getConfigurator();
            CountingEndpoint endpoint = configurator.getEndpointInstance(CountingEndpoint.class);

            assertThat(configurator).isInstanceOf(DefaultServerEndpointConfigurator.class);
            assertThat(endpoint).isInstanceOf(CountingEndpoint.class);
            assertThat(CONSTRUCTIONS).hasValue(1);
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }
    }

    public static class CountingEndpoint {
        public CountingEndpoint() {
            CONSTRUCTIONS.incrementAndGet();
        }
    }

    private static final class HidingServicesClassLoader extends ClassLoader {
        HidingServicesClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public URL getResource(String name) {
            if (SERVER_ENDPOINT_CONFIGURATOR_SERVICE.equals(name)) {
                return null;
            }
            return super.getResource(name);
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            if (SERVER_ENDPOINT_CONFIGURATOR_SERVICE.equals(name)) {
                return Collections.emptyEnumeration();
            }
            return super.getResources(name);
        }
    }
}
