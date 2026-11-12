/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_websocket;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import jakarta.websocket.server.ServerEndpointConfig;

import org.junit.jupiter.api.Test;

public class ServerEndpointConfigInnerConfiguratorTest {
    @Test
    void delegatesProtocolAndOriginNegotiationToContainerDefault() {
        ServerEndpointConfig.Configurator configurator = new ServerEndpointConfig.Configurator();
        assertThat(configurator.getContainerDefaultConfigurator()).isNotNull();
        assertThat(configurator.getNegotiatedSubprotocol(List.of("events", "chat"), List.of("unknown", "chat")))
                .isEqualTo("chat");
        assertThat(configurator.getNegotiatedSubprotocol(List.of("events"), List.of("other"))).isEmpty();
        assertThat(configurator.checkOrigin("https://client.example")).isTrue();
        assertThat(configurator.getNegotiatedExtensions(List.of(), List.of())).isEmpty();
    }
}
