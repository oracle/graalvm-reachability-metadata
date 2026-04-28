/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_websocket;

import static org.assertj.core.api.Assertions.assertThat;

import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.Session;

import org.apache.tomcat.websocket.server.DefaultServerEndpointConfigurator;
import org.junit.jupiter.api.Test;

public class DefaultServerEndpointConfiguratorTest {

    @Test
    public void getEndpointInstanceCreatesEndpointWithPublicNoArgConstructor() throws Exception {
        ConstructedEndpoint.constructedCount = 0;
        DefaultServerEndpointConfigurator configurator = new DefaultServerEndpointConfigurator();

        ConstructedEndpoint endpoint = configurator.getEndpointInstance(ConstructedEndpoint.class);

        assertThat(endpoint).isInstanceOf(ConstructedEndpoint.class);
        assertThat(ConstructedEndpoint.constructedCount).isEqualTo(1);
    }

    public static class ConstructedEndpoint extends Endpoint {
        private static int constructedCount;

        public ConstructedEndpoint() {
            constructedCount++;
        }

        @Override
        public void onOpen(Session session, EndpointConfig config) {
        }
    }
}
