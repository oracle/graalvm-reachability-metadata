/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_websocket;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.tomcat.websocket.server.DefaultServerEndpointConfigurator;
import org.junit.jupiter.api.Test;

public class DefaultServerEndpointConfiguratorTest {

    @Test
    void getEndpointInstanceCreatesEndpointWithPublicNoArgumentConstructor() throws InstantiationException {
        DefaultServerEndpointConfigurator configurator = new DefaultServerEndpointConfigurator();
        PublicEndpoint.constructions = 0;

        PublicEndpoint endpoint = configurator.getEndpointInstance(PublicEndpoint.class);

        assertThat(endpoint).isNotNull();
        assertThat(endpoint.createdByConstructor).isTrue();
        assertThat(PublicEndpoint.constructions).isEqualTo(1);
    }

    public static class PublicEndpoint {
        private static int constructions;
        private final boolean createdByConstructor;

        public PublicEndpoint() {
            constructions++;
            createdByConstructor = true;
        }
    }
}
