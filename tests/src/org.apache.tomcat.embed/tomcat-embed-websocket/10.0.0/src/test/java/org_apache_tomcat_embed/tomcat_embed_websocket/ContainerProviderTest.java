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

import jakarta.websocket.ContainerProvider;
import jakarta.websocket.WebSocketContainer;

import org.apache.tomcat.websocket.WsWebSocketContainer;
import org.junit.jupiter.api.Test;

public class ContainerProviderTest {
    private static final String CONTAINER_PROVIDER_SERVICE = "META-INF/services/" + ContainerProvider.class.getName();

    @Test
    void getWebSocketContainerFallsBackToTomcatContainer() {
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        WebSocketContainer container = null;
        try {
            Thread.currentThread()
                    .setContextClassLoader(new HidingServicesClassLoader(originalContextClassLoader));

            container = ContainerProvider.getWebSocketContainer();

            assertThat(container).isInstanceOf(WsWebSocketContainer.class);
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
            if (container instanceof WsWebSocketContainer) {
                ((WsWebSocketContainer) container).destroy();
            }
        }
    }

    private static final class HidingServicesClassLoader extends ClassLoader {
        HidingServicesClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public URL getResource(String name) {
            if (CONTAINER_PROVIDER_SERVICE.equals(name)) {
                return null;
            }
            return super.getResource(name);
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            if (CONTAINER_PROVIDER_SERVICE.equals(name)) {
                return Collections.emptyEnumeration();
            }
            return super.getResources(name);
        }
    }
}
