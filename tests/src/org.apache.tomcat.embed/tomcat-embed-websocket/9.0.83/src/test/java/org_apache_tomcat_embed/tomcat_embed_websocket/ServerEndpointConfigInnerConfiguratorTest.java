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

import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpointConfig;

import org.apache.tomcat.websocket.server.DefaultServerEndpointConfigurator;
import org.junit.jupiter.api.Test;

public class ServerEndpointConfigInnerConfiguratorTest {

    @Test
    public void builderFallsBackToTomcatDefaultConfiguratorWhenNoServiceProviderIsVisible() {
        Thread currentThread = Thread.currentThread();
        ClassLoader originalContextClassLoader = currentThread.getContextClassLoader();
        ClassLoader noConfiguratorServiceClassLoader = new NoConfiguratorServiceClassLoader();
        currentThread.setContextClassLoader(noConfiguratorServiceClassLoader);
        try {
            ServerEndpointConfig config = ServerEndpointConfig.Builder.create(TestEndpoint.class, "/fallback").build();

            assertThat(config.getConfigurator()).isInstanceOf(DefaultServerEndpointConfigurator.class);
        } finally {
            currentThread.setContextClassLoader(originalContextClassLoader);
        }
    }

    public static class TestEndpoint extends Endpoint {
        @Override
        public void onOpen(Session session, EndpointConfig config) {
        }
    }

    private static final class NoConfiguratorServiceClassLoader extends ClassLoader {
        private static final String CONFIGURATOR_SERVICE =
                "META-INF/services/javax.websocket.server.ServerEndpointConfig$Configurator";

        private NoConfiguratorServiceClassLoader() {
            super(null);
        }

        @Override
        public URL getResource(String name) {
            if (CONFIGURATOR_SERVICE.equals(name)) {
                return null;
            }
            return super.getResource(name);
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            if (CONFIGURATOR_SERVICE.equals(name)) {
                return Collections.emptyEnumeration();
            }
            return super.getResources(name);
        }
    }
}
