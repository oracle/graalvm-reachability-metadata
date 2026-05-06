/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_websocket;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;

import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.Session;

import org.apache.tomcat.websocket.EndpointClassHolder;
import org.junit.jupiter.api.Test;

public class EndpointClassHolderTest {
    private static final AtomicInteger CONSTRUCTIONS = new AtomicInteger();

    @Test
    void getInstanceCreatesEndpointWithPublicNoArgConstructor() throws DeploymentException {
        CONSTRUCTIONS.set(0);
        EndpointClassHolder holder = new EndpointClassHolder(CountingEndpoint.class);

        Endpoint endpoint = holder.getInstance(null);

        assertThat(endpoint).isInstanceOf(CountingEndpoint.class);
        assertThat(holder.getClassName()).isEqualTo(CountingEndpoint.class.getName());
        assertThat(CONSTRUCTIONS).hasValue(1);
    }

    public static class CountingEndpoint extends Endpoint {
        public CountingEndpoint() {
            CONSTRUCTIONS.incrementAndGet();
        }

        @Override
        public void onOpen(Session session, EndpointConfig config) {
        }
    }
}
