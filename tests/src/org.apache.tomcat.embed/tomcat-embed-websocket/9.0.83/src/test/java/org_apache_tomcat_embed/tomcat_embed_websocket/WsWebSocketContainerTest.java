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
    void connectToServerInstantiatesCustomClientConfiguratorBeforeValidatingUri() {
        CountingClientConfigurator.constructions = 0;
        WsWebSocketContainer container = new WsWebSocketContainer();

        assertThatThrownBy(() -> container.connectToServer(ConfiguredClientEndpoint.class,
                URI.create("http://localhost/chat"))).isInstanceOf(DeploymentException.class);

        assertThat(CountingClientConfigurator.constructions).isEqualTo(1);
    }

    @ClientEndpoint(configurator = CountingClientConfigurator.class)
    public static final class ConfiguredClientEndpoint {
    }

    public static class CountingClientConfigurator extends ClientEndpointConfig.Configurator {
        private static int constructions;

        public CountingClientConfigurator() {
            constructions++;
        }
    }
}
