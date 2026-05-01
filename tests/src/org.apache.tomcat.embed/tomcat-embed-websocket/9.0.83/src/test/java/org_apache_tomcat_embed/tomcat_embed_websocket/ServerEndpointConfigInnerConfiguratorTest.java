/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_websocket;

import static org.assertj.core.api.Assertions.assertThat;

import javax.websocket.server.ServerEndpointConfig;

import org.junit.jupiter.api.Test;

public class ServerEndpointConfigInnerConfiguratorTest {
    private static final boolean FALLBACK_CONFIGURATOR_LOADED = loadFallbackDefaultConfigurator();

    @Test
    void checkOriginLoadsFallbackDefaultConfiguratorWhenNoServiceProviderIsVisible() {
        assertThat(FALLBACK_CONFIGURATOR_LOADED).isTrue();
    }

    private static boolean loadFallbackDefaultConfigurator() {
        Thread thread = Thread.currentThread();
        ClassLoader originalContextClassLoader = thread.getContextClassLoader();
        thread.setContextClassLoader(null);
        try {
            ServerEndpointConfig.Configurator configurator = new ServerEndpointConfig.Configurator();
            return configurator.checkOrigin("https://example.test");
        } finally {
            thread.setContextClassLoader(originalContextClassLoader);
        }
    }
}
