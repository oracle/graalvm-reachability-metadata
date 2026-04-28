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

import org.apache.tomcat.websocket.EndpointClassHolder;
import org.junit.jupiter.api.Test;

public class EndpointClassHolderTest {

    @Test
    public void getInstanceCreatesEndpointWithPublicNoArgConstructor() throws Exception {
        ConstructedEndpoint.constructedCount = 0;
        EndpointClassHolder holder = new EndpointClassHolder(ConstructedEndpoint.class);

        Endpoint endpoint = holder.getInstance(null);

        assertThat(endpoint).isInstanceOf(ConstructedEndpoint.class);
        assertThat(holder.getClassName()).isEqualTo(ConstructedEndpoint.class.getName());
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
