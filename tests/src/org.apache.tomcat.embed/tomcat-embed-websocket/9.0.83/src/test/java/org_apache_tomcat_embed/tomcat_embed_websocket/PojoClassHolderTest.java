/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_websocket;

import static org.assertj.core.api.Assertions.assertThat;

import javax.websocket.ClientEndpoint;
import javax.websocket.ClientEndpointConfig;
import javax.websocket.Endpoint;

import org.apache.tomcat.websocket.PojoClassHolder;
import org.apache.tomcat.websocket.pojo.PojoEndpointClient;
import org.junit.jupiter.api.Test;

public class PojoClassHolderTest {

    @Test
    void getInstanceCreatesPojoEndpointClientWithDefaultConstructor() throws Exception {
        CountingClientEndpoint.constructions = 0;
        ClientEndpointConfig clientEndpointConfig = ClientEndpointConfig.Builder.create().build();
        PojoClassHolder holder = new PojoClassHolder(CountingClientEndpoint.class, clientEndpointConfig);

        Endpoint endpoint = holder.getInstance(null);

        assertThat(endpoint).isInstanceOf(PojoEndpointClient.class);
        assertThat(holder.getClassName()).isEqualTo(CountingClientEndpoint.class.getName());
        assertThat(CountingClientEndpoint.constructions).isEqualTo(1);
    }

    @ClientEndpoint
    public static final class CountingClientEndpoint {
        private static int constructions;

        public CountingClientEndpoint() {
            constructions++;
        }
    }
}
