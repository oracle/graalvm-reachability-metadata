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

import javax.websocket.ContainerProvider;
import javax.websocket.WebSocketContainer;

import org.apache.tomcat.websocket.WsWebSocketContainer;
import org.junit.jupiter.api.Test;

public class ContainerProviderTest {

    @Test
    public void getWebSocketContainerFallsBackToTomcatContainerWhenNoServiceProviderIsVisible() {
        Thread currentThread = Thread.currentThread();
        ClassLoader originalContextClassLoader = currentThread.getContextClassLoader();
        ClassLoader noServiceProviderClassLoader = new NoServiceProviderClassLoader(originalContextClassLoader);
        currentThread.setContextClassLoader(noServiceProviderClassLoader);
        try {
            WebSocketContainer container = ContainerProvider.getWebSocketContainer();

            assertThat(container).isInstanceOf(WsWebSocketContainer.class);
        } finally {
            currentThread.setContextClassLoader(originalContextClassLoader);
        }
    }

    private static final class NoServiceProviderClassLoader extends ClassLoader {
        private static final String CONTAINER_PROVIDER_SERVICE = "META-INF/services/javax.websocket.ContainerProvider";

        private NoServiceProviderClassLoader(ClassLoader parent) {
            super(parent);
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
