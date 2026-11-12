/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_websocket;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import jakarta.websocket.OnMessage;
import jakarta.websocket.server.ServerEndpoint;
import jakarta.websocket.server.ServerEndpointConfig;

import org.junit.jupiter.api.Test;

public class DefaultServerEndpointConfiguratorTest {
    @Test
    void defaultConfiguratorCreatesIndependentEndpointInstances() throws Exception {
        ServerEndpointConfig config = ServerEndpointConfig.Builder.create(GreetingEndpoint.class, "/greet").build();
        ServerEndpointConfig.Configurator configurator = config.getConfigurator();
        GreetingEndpoint first = configurator.getEndpointInstance(GreetingEndpoint.class);
        GreetingEndpoint second = configurator.getEndpointInstance(GreetingEndpoint.class);

        assertThat(first).isNotSameAs(second);
        assertThat(first.greet("Ada")).isEqualTo("1: Hello Ada");
        assertThat(first.greet("Lin")).isEqualTo("2: Hello Lin");
        assertThat(second.greet("Sam")).isEqualTo("1: Hello Sam");
        assertThat(configurator.getNegotiatedSubprotocol(List.of("chat", "events"), List.of("events")))
                .isEqualTo("events");
    }

    @ServerEndpoint("/greet")
    public static class GreetingEndpoint {
        private int greetings;

        @OnMessage
        public String greet(String name) {
            return ++greetings + ": Hello " + name;
        }
    }
}
