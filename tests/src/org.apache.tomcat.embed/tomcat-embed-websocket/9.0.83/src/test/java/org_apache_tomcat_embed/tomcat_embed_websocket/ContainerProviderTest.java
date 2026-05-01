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
    void getWebSocketContainerUsesReflectiveFallbackWhenServiceProviderIsHidden() {
        Thread thread = Thread.currentThread();
        ClassLoader originalClassLoader = thread.getContextClassLoader();
        ClassLoader hidingClassLoader = new HidingServiceClassLoader(originalClassLoader);

        try {
            thread.setContextClassLoader(hidingClassLoader);

            WebSocketContainer container = ContainerProvider.getWebSocketContainer();

            assertThat(container).isInstanceOf(WsWebSocketContainer.class);
        } finally {
            thread.setContextClassLoader(originalClassLoader);
        }
    }

    private static final class HidingServiceClassLoader extends ClassLoader {
        private static final String CONTAINER_PROVIDER_SERVICE = "META-INF/services/javax.websocket.ContainerProvider";

        private HidingServiceClassLoader(ClassLoader parent) {
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
