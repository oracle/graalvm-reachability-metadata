/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;

import javax.websocket.ClientEndpoint;
import javax.websocket.ClientEndpointConfig;
import javax.websocket.DeploymentException;

import org.apache.tomcat.websocket.WsWebSocketContainer;
import org.junit.jupiter.api.Test;

public class WsWebSocketContainerTest {

    @Test
    public void connectToAnnotatedEndpointInstantiatesCustomConfigurator() {
        TestConfigurator.constructed = false;
        WsWebSocketContainer container = new WsWebSocketContainer();

        assertThatThrownBy(() -> container.connectToServer(AnnotatedEndpoint.class, URI.create("ftp://localhost/test")))
                .isInstanceOf(DeploymentException.class);

        assertThat(TestConfigurator.constructed).isTrue();
    }

    @ClientEndpoint(configurator = TestConfigurator.class)
    public static class AnnotatedEndpoint {
    }

    public static class TestConfigurator extends ClientEndpointConfig.Configurator {
        private static boolean constructed;

        public TestConfigurator() {
            constructed = true;
        }
    }
}
