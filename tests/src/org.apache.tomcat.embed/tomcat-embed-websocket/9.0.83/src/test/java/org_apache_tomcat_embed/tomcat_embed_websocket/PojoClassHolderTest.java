/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_websocket;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;

import javax.websocket.ClientEndpoint;
import javax.websocket.ClientEndpointConfig;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;

import org.apache.tomcat.websocket.PojoClassHolder;
import org.apache.tomcat.websocket.pojo.PojoEndpointClient;
import org.junit.jupiter.api.Test;

public class PojoClassHolderTest {
    private static final AtomicInteger CONSTRUCTIONS = new AtomicInteger();

    @Test
    void getInstanceCreatesAnnotatedPojoWithPublicNoArgConstructor() throws DeploymentException {
        CONSTRUCTIONS.set(0);
        ClientEndpointConfig config = ClientEndpointConfig.Builder.create().build();
        PojoClassHolder holder = new PojoClassHolder(CountingPojoEndpoint.class, config);

        Endpoint endpoint = holder.getInstance(null);

        assertThat(endpoint).isInstanceOf(PojoEndpointClient.class);
        assertThat(holder.getClassName()).isEqualTo(CountingPojoEndpoint.class.getName());
        assertThat(CONSTRUCTIONS).hasValue(1);
    }

    @ClientEndpoint
    public static class CountingPojoEndpoint {
        public CountingPojoEndpoint() {
            CONSTRUCTIONS.incrementAndGet();
        }
    }
}
